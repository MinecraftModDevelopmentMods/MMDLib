# Machine module: stage 1 (built)

Stage 1 is the first code from the machine module design. It is a Java API with no screens:

- **Internals:** parts in sockets that change a machine's stats, with part files, wear and swap
  rules ([machine UI → internals](machine-ui.md#internals) is the design).
- **Phase 1 of the recipe core:** machine ingredients, slot groups with transactions, and the
  recipe matcher ([rebuild plan](rebuild-plan.md#core-interfaces), [recipes](recipes.md)).

Everything is in `zone.moddev.mc.lib.machine`. Only `machine.neoforge` imports NeoForge;
`./gradlew verifyMachineImports` fails if any other machine file does.

| Package | Types | What for |
|---|---|---|
| `machine.expr` | `Expr` | Arithmetic over `$variables`, as in `"0.08 * ($material.hardness - 3)"` |
| `machine.stat` | `StatDefinition`, `StatModifier`, `MachineStats` | Declared stats and how modifiers combine |
| `machine.internals` | `InternalsDefinition`, `SocketDefinition`, `ItemMatch`, `SwapRule`, `PartDefinition`, `PartModifier`, `PartSubject`, `ResolvedPart`, `MachineParts` | The `internals` JSON and part files |
| `machine.ingredient` | `MachineIngredient` (`ItemPart`, `FluidPart`, `EnergyPart`), `FluidAmount`, `AmountMatch` | What recipes consume |
| `machine.inventory` | `MachineInventory`, `SlotGroup` (`ItemGroup`, `FluidGroup`, `EnergyGroup`), `MachineTx`, `SnapshotTx` | Storages grouped by key, changed all or nothing |
| `machine.recipe` | `MachineRecipe`, `MachineProduct` (`ItemProduct`, `FluidProduct`), `RecipeMatcher`, `MatchResult` | Matching, consuming and producing |
| `machine.neoforge` | `MachineInternals`, `StorageGroups`, `MachinePartsLoader`, `MachineComponents` | Runtime internals, groups over MMDLib's storages, the part-file reload listener, the wear component |

## Internals

### The definition

`InternalsDefinition.CODEC` reads this shape. A mod puts it in its block entity's settings
(`BlockEntityFactory.settingsCodec()`, for example as `factory_data.internals`), so a bad
definition fails at startup like other settings.

```json
{
  "open_with": "#c:tools/wrench",
  "swap": "when_idle",
  "sockets": {
    "gears": { "count": 2, "accepts": "#c:gears" },
    "motor": { "count": 1 },
    "coil":  { "count": 1, "optional": true }
  },
  "stats": {
    "speed":           { "base": 1.0, "min": 0.25, "max": 8 },
    "energy_per_tick": { "base": 40, "min": 1 }
  }
}
```

| Key | Default | Meaning |
|---|---|---|
| `open_with` | none (bare hands) | `#tag` or item ID the player must hold to open the engine view (`MachineInternals.canOpen`) |
| `swap` | `when_idle` | `always`, `when_idle` or `never` (`canSwap(running)`) |
| `sockets.<name>.count` | 1 | Positions in the socket, 1 to 64 |
| `sockets.<name>.accepts` | any part for this socket | `#tag` or item ID, checked on top of the part file's `socket` |
| `sockets.<name>.optional` | false | Whether the machine runs with positions empty |
| `stats.<name>` | | `base`, optional `min` and `max` |

Slots are laid out socket by socket **in name order**: above, `coil` is slot 0, `gears` slots 1–2
and `motor` slot 3.

### Part files

Part files are datapack files at `data/<ns>/mmdlib/machine_part/*.json`. They are loaded on every
`/reload` and available as `MachineParts.current()`. MMDLib ships none; mods and packs write
their own.

```json
{
  "part": "mmdlib:gear",
  "socket": "gears",
  "modifiers": [
    { "stat": "speed",           "op": "add_multiplied_base", "amount": "0.08 * ($material.hardness - 3)" },
    { "stat": "energy_per_tick", "op": "add_multiplied_base", "amount": "0.03 * $material.hardness" }
  ],
  "durability": "500 * $material.hardness"
}
```

- **What a file applies to:** `part` names a material part, matched against the item's
  `MMDItem.template()`. One file then covers that part for every material. `item` names one item
  instead; an `item` file beats a `part` template.
- **Variables:** `$material.hardness`, `strength`, `magic` and `damage`, and
  `$material.special.<key>` (a missing key counts as 0). Any other variable fails at load.
- **Operations:** `op` takes vanilla's attribute operations: `add_value`, `add_multiplied_base`
  and `add_multiplied_total`.
- **Wear:** `durability` is optional. Without it, a part never wears.
- **Errors:**
  - A file that doesn't decode is logged and skipped.
  - When two files claim the same part or item, the lower resource ID wins and the error names
    both.
  - An item whose numbers can't be worked out is not a part, and the error is logged once. This
    covers a missing material, a result that isn't finite, and a durability below 1.

### How stats combine

For each declared stat, starting from `base`:

1. Add every `add_value` amount.
2. Add (result of step 1) × the sum of the `add_multiplied_base` amounts.
3. Multiply by (1 + amount) for each `add_multiplied_total` amount.
4. Clamp to `min` and `max`.

Amounts are combined in sorted order, so the order parts are installed in doesn't matter.
Modifiers for a stat the machine doesn't declare are ignored. `verifyMachineCore` checks the
worked table from [machine UI](machine-ui.md#how-stats-combine): 1.16 / 49.6, 1.48 / 54.4,
1.70 / 65.28, 2.55 / 97.92.

### In a block entity

```java
private final MachineInternals internals =
    new MachineInternals(settings.internals(), MachineParts::current).onChange(this::setChanged);

void serverTick() {
  if (internals.missing().isPresent()) return;          // e.g. "gears": none installed
  double speed = internals.stats().get("speed");
  // ... after each run:
  internals.wear("gears", 1);
}

protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
  tag.put("internals", internals.save(provider));
}

protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
  internals.load(provider, tag.getCompound("internals"));
}
```

What `MachineInternals` does:

- **Slots:** `sockets()` is an `ItemStorage` with one `MANUAL` slot of limit 1 per position, for
  a menu to show later. A slot only takes an item that resolves to a part for that socket and
  matches `accepts`.
- **Installing and removing:** `install` and `remove` check the swap rule.
- **Stats:** `stats()` is cached and recomputed after a socket change or a part-file reload.
  `stats(extra)` adds other modifiers, such as upgrade cards.
- **Wear:** `wear` adds to the `mmdlib:part_wear` component on each part that has a durability,
  and destroys a part when its wear reaches that durability.
- **Saving:** parts are saved by socket name, under `sockets`. If a later version shrinks a socket, the parts that
  no longer fit are kept as `orphans()`, are saved too, and drop with `dropAll` when the block
  breaks.

## Ingredients, groups and matching

### Groups and transactions

```java
var inventory = MachineInventory.builder()
    .group("input", StorageGroups.items(items, 0))
    .group("output", StorageGroups.items(items, 1, 2))
    .group("byproduct", StorageGroups.items(items, 3))
    .group("water", StorageGroups.fluids(fluids, 0))
    .group("energy", StorageGroups.energy(buffer))
    .build();
```

- **Groups act as the machine:** slot roles and filters don't apply (items go in through
  `insertResult`), and energy ignores the buffer's transfer rates.
- **Indexes:** they are relative to the group. An index outside the group takes or inserts
  nothing.
- **Transactions:** under one `MachineTx`, changes land together or not at all. Closing without
  `commit()` restores every slot, tank and buffer the transaction touched. A transaction can't be
  used after commit or close.
- **`fits(...)`** tells whether stacks or fluids would fit, working on copies, so it never marks
  the block entity changed.

### Recipes

The JSON shape (registering it as a recipe type is phase 2):

```json
{
  "inputs": {
    "input":  [{ "items": { "tag": "c:ores/copper" }, "count": 1 }],
    "water":  [{ "fluids": "minecraft:water", "amount": 250 }],
    "energy": [{ "energy": 4000 }]
  },
  "outputs": {
    "output":    [{ "item": { "id": "minecraft:raw_copper", "count": 2 } }],
    "byproduct": [{ "item": { "id": "minecraft:gravel" }, "chance": 0.25 }],
    "water":     [{ "fluid": { "id": "minecraft:water", "amount": 100 } }]
  },
  "time": 200
}
```

Decode it with `MachineRecipe.CODEC` through `RegistryOps`, because item tags and fluid sets need
registry access.

- **Inputs:** each entry has exactly one of `items`, `fluids` or `energy`. Energy is a `long`.
- **Outputs:** each entry has exactly one of `item` or `fluid`; an entry with both, or neither, is
  an error. One recipe can make items and fluids together, each into a group of its kind, as
  above.

| Call | Does |
|---|---|
| `RecipeMatcher.check(recipe, inventory)` | Every input present and room for every product (chance counted as certain). Changes nothing. Returns a `MatchResult` naming the reason, the group key and the list index |
| `RecipeMatcher.consume(recipe, inventory)` | Takes item and fluid inputs, all or nothing. Energy is not taken |
| `RecipeMatcher.produce(recipe, inventory, random)` | Rolls chances and inserts the hits, all or nothing: if the fluid doesn't fit, the items aren't made either |

Matching rules:

- **Groups:** joined by key. A recipe key the machine lacks gives `NO_GROUP`. An item part on a
  fluid group gives `WRONG_KIND`.
- **Items:** item parts in one group claim items in list order. 2 × iron ingot plus 2 × `#c:ingots`
  needs four ingots, not three.
- **Energy:** a recipe can start with one tick's share, `ceil(energy / time)`. 4,000 FE over 200
  ticks needs 20 FE.

## Not built yet

| Missing | Comes in |
|---|---|
| Registering machine recipes as a recipe type, `StreamCodec`, JEI, datagen, moving `mmdlib:crushing` | Phase 2 |
| `FeatureHost`, `RecipeProcessor` (energy per tick, stalls, stats feeding the recipe time) | Phase 3 |
| Engine view, socket menu slots, wrench opening, sending part files to clients for the hover preview | Phase 17 (GUI half) |
| Upgrade cards and face cards as stat sources; `stats(extra)` is their hook | Phases 15–16 |

## Checks

- `./gradlew verifyMachineCore`: expressions, stats, the internals definition, part files and
  `AmountMatch`, without a running game.
- `./gradlew verifyMachineImports`: no NeoForge imports outside `machine/neoforge`.
- The server smoke run, `./gradlew runServer -I scripts/runtime-smoke.init.gradle`, adds
  `MachinePartsLoaderSmoke`, `MachineInternalsSmoke`, `MachineInventorySmoke` and
  `MachineRecipeSmoke`. They use MMDLib's real iron gears and vanilla items. Run `./gradlew build`
  afterwards to restore the normal main output.
- The init script copies `src/runtimeTest/datapack` into the smoke world's `datapacks` folder.
  `MachinePartsLoaderSmoke` checks that its part files arrive through the reload listener, and
  that a file that doesn't decode is skipped.
