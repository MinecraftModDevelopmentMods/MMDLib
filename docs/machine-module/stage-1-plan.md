# Machine module stage 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build machine internals (sockets, part files, the stat pipeline, wear and swap rules)
and phase 1 of the ingredients/inventory/recipes core (ingredients, groups, snapshot
transactions, the recipe matcher) as a Java API with no GUI.

**Architecture:** New package `zone.moddev.mc.lib.machine`. Everything outside
`machine.neoforge` imports only vanilla, DFU and Gson, so it can later move into the shared core
of the Stonecutter build unchanged. `machine.neoforge` adapts MMDLib's existing `ItemStorage`,
`FluidStorage` and `EnergyBuffer` to the core's groups and registers the reload listener and the
data component. Nothing is registered as a recipe type yet (that is phase 2), and no screen code
changes (internals' engine view is phase 17's GUI half).

**Tech Stack:** Java 21, NeoForge 21.1.247 (NeoGradle userdev), DFU codecs, plain-Java checks in
`src/builderTest`, the server smoke run in `src/runtimeTest`.

**Spec:** [rebuild-plan.md](rebuild-plan.md) (core interfaces, phase 1), [recipes.md](recipes.md)
(matching rules, all-or-nothing), [machine-ui.md](machine-ui.md#internals) (sockets, part files,
how stats combine). Read those sections first.

**Branch:** `machine-module`, from `master-2.0-1.21.1-neo` at `9083780`.

## Global Constraints

- Package root `zone.moddev.mc.lib.machine`. Subpackages: `expr`, `stat`, `internals`,
  `ingredient`, `inventory`, `recipe`, `neoforge`.
- No file under `machine/` except `machine/neoforge/` may import `net.neoforged.*`. Enforced by
  `verifyMachineImports` (Task 1).
- Stats combine with vanilla's `AttributeModifier.Operation` (`add_value`,
  `add_multiplied_base`, `add_multiplied_total`), in that order, then clamp to `min`/`max`.
- Energy is `long` everywhere. Fluid amounts are `int` mB.
- Recipes and part logic run as the machine: they bypass slot `Role`s and `IoMode`s, like
  `ItemStorage.insertResult` does today. Automation keeps going through `view(...)`.
- Bad data fails at load with the file name, never during a tick.
- Style: Google Java Style (2-space indent), javadoc on every public type and method, same
  comment density as `api/capabilities`. `./gradlew format` needs `tools/gjf.jar`; if it is
  missing, format by hand and say so in the commit message.
- Checks follow the repo's pattern: builder checks are `main` programs with a private
  `require(boolean)`; smoke checks are `static void check(ServerLevel)` classes called from
  `RuntimeSmoke`, with `require(boolean, String label)`.
- No GUI: no changes under `gui/` or `client/`.

## Review Focus

1. **A part changes a stat the machine doesn't declare** (a gear template that also sets
   `max_temperature`, installed in a machine without it): the modifier is ignored, the rest
   apply. Pinned in Task 2 and Task 6.
2. **A machine saved with parts its definition no longer has room for** (socket renamed or
   `count` reduced in an update): the parts are kept as orphans and dropped when the block breaks,
   never deleted on load. Pinned in Task 6.
3. **`/reload` changes part files while machines are loaded**: the next `stats()` read uses the
   new values, without a block update. Pinned in Task 6.
4. **An expression that isn't finite for some material** (`10 / $material.hardness` with hardness
   0): that item is not a part, one error is logged, nothing throws in a tick. Pinned in Task 4.
5. **Two item ingredients in one group that accept the same items** (2 × iron ingot and
   2 × `#c:ingots` against 3 iron ingots): items are claimed once, so the recipe does not match.
   Pinned in Task 10.

---

### Task 0: Branch and baseline

- [x] **Step 1: Create the branch** — `git switch -c machine-module master-2.0-1.21.1-neo`. The
  uncommitted machine-module docs and the earlier doc/`ScreenHost` javadoc edits came across with
  it.
- [ ] **Step 2: Commit the existing doc work as the branch's first commit** (Face to confirm):
  `git add CLAUDE.md docs .gitignore src/main/java/zone/moddev/mc/lib/gui/runtime/ScreenHost.java`,
  message `docs: machine module design and current machine docs`.
- [ ] **Step 3: Record the baseline** — run `./gradlew check` (not `--offline`: NeoGradle needs
  to download the asset index the first time); note any check that already fails so later tasks
  aren't blamed for it.

### Task 1: Scaffold, import rule, expressions

**Files:**
- Create: `src/main/java/zone/moddev/mc/lib/machine/package-info.java`
- Create: `src/main/java/zone/moddev/mc/lib/machine/expr/Expr.java`
- Create: `src/builderTest/java/zone/moddev/mc/lib/machine/MachineCoreTest.java`
- Modify: `gradle/verification.gradle` (add `verifyMachineCore` to `builderChecks`, add `verifyMachineImports`)

**Interfaces:**
- Produces: `Expr.parse(String) -> Expr`, `double Expr.eval(Function<String, OptionalDouble> vars)`,
  `Set<String> Expr.variables()`, `Expr.constant(double)`, `Codec<Expr> Expr.CODEC` (a JSON
  number or a string expression).

Grammar: numbers, `+ - * /`, parentheses, unary minus, and variables `$name(.segment)*` (the
variable name passed to `vars` has no `$`, e.g. `material.hardness`). Usual precedence, left
associative. `parse` throws `IllegalArgumentException` naming the 0-based position of the bad
character. `eval` throws `IllegalArgumentException` if a variable is absent from `vars`, and
returns non-finite results as they are (callers decide).

- [ ] **Step 1: Write the failing test** — `MachineCoreTest.main` calls `expressions()`:

```java
require(Expr.parse("0.08 * ($material.hardness - 3)").eval(v("material.hardness", 6)) == 0.24);
require(Expr.parse("-2 + 3 * 4").eval(none()) == 10);
require(Expr.parse("(1 + 2) / 4").eval(none()) == 0.75);
require(Expr.parse("$a.b").variables().equals(Set.of("a.b")));
require(Double.isInfinite(Expr.parse("1 / $x").eval(v("x", 0))));
expectFailure(() -> Expr.parse("2 *"));
expectFailure(() -> Expr.parse("2 $"));
expectFailure(() -> Expr.parse("$missing").eval(none()));
require(Expr.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(12)).getOrThrow().eval(none()) == 12);
```

(Compare doubles with a 1e-9 tolerance helper `near(a, b)` instead of `==` where the value isn't
exact.) Register it: `verifyMachineCore : ['zone.moddev.mc.lib.machine.MachineCoreTest']`.

- [ ] **Step 2: Run** `./gradlew verifyMachineCore` — expected: compile failure, `Expr` missing.
- [ ] **Step 3: Implement `Expr`** as a small recursive-descent parser into a tree of records.
- [ ] **Step 4: Add `verifyMachineImports`** — a task in `verification.gradle` that fails if any
  `src/main/java/zone/moddev/mc/lib/machine/**/*.java` outside `machine/neoforge/` contains
  `import net.neoforged.`, listing the files. `check` depends on it.
- [ ] **Step 5: Run** `./gradlew verifyMachineCore verifyMachineImports` — expected: both pass.
- [ ] **Step 6: Commit** — `feat(machine): expression parser and core import rule`.

### Task 2: Stats and the pipeline

**Files:**
- Create: `machine/stat/StatDefinition.java`, `machine/stat/StatModifier.java`, `machine/stat/MachineStats.java`
- Test: `MachineCoreTest.stats()`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces:
  - `record StatDefinition(double base, OptionalDouble min, OptionalDouble max)`, `Codec<StatDefinition> CODEC` (`{"base": 1.0, "min": 0.25, "max": 8}`; `min`/`max` optional; error if `min > max` or any value non-finite).
  - `record StatModifier(String stat, AttributeModifier.Operation op, double amount)` — `op` uses vanilla `AttributeModifier.Operation.CODEC`.
  - `final class MachineStats`: `static MachineStats compute(Map<String, StatDefinition> stats, Collection<StatModifier> modifiers)`, `double get(String stat)` (throws `IllegalArgumentException` for an undeclared stat), `Map<String, Double> values()`, `static final MachineStats EMPTY`.

Order per stat: add every `add_value`; then add (that result × sum of `add_multiplied_base`); then
multiply by (1 + amount) for each `add_multiplied_total`; clamp. Modifiers for stats not in
`stats` are ignored (Review Focus 1). Non-finite modifier amounts are rejected by the
`StatModifier` constructor.

- [ ] **Step 1: Write the failing test** — the worked table from machine-ui.md (example hardness: iron 4, invar 6):

```java
var defs = Map.of("speed", new StatDefinition(1, OptionalDouble.of(.25), OptionalDouble.of(8)),
                  "energy_per_tick", new StatDefinition(40, OptionalDouble.of(1), OptionalDouble.empty()));
// 2 × iron gear: speed +0.08 base each, energy +0.12 base each
require(near(stats(defs, base("speed", .08), base("speed", .08), base("energy_per_tick", .12), base("energy_per_tick", .12)).get("speed"), 1.16));
// ... energy_per_tick 49.6
// 2 × invar gear: 1.48 / 54.4
// + electrum motor (speed total +0.15, energy value +8): 1.70 (1.702) / 65.28
// + speed upgrade II (total +0.5 on both): 2.553 / 97.92
require(stats(defs, total("speed", 10)).get("speed") == 8);            // clamp max
require(stats(defs, value("speed", -5)).get("speed") == .25);          // clamp min
require(stats(defs, value("max_temperature", 400)).values().size() == 2); // undeclared stat ignored
expectFailure(() -> MachineStats.EMPTY.get("speed"));
expectFailure(() -> parseStat("{\"base\":1,\"min\":5,\"max\":2}"));
```

Also check the result doesn't depend on modifier order (shuffle the motor/upgrade list, same value).

- [ ] **Step 2: Run** `./gradlew verifyMachineCore` — expected: FAIL (types missing).
- [ ] **Step 3: Implement** the three types.
- [ ] **Step 4: Run** — expected: PASS.
- [ ] **Step 5: Commit** — `feat(machine): stat definitions and the modifier pipeline`.

### Task 3: Internals definition

**Files:**
- Create: `machine/internals/ItemMatch.java`, `machine/internals/SwapRule.java`, `machine/internals/SocketDefinition.java`, `machine/internals/InternalsDefinition.java`
- Test: `MachineCoreTest.internalsDefinition()`

**Interfaces:**
- Consumes: `StatDefinition` (Task 2).
- Produces:
  - `record ItemMatch(Either<TagKey<Item>, ResourceLocation> value)`, `Codec<ItemMatch> CODEC` from a string: `#ns:path` is a tag, otherwise an item ID. `boolean test(ItemStack)`. No registry access when decoding.
  - `enum SwapRule implements StringRepresentable { ALWAYS, WHEN_IDLE, NEVER }`, `boolean allows(boolean running)`.
  - `record SocketDefinition(int count, Optional<ItemMatch> accepts, boolean optional)`; `count` 1–64.
  - `record InternalsDefinition(Optional<ItemMatch> openWith, SwapRule swap, SortedMap<String, SocketDefinition> sockets, Map<String, StatDefinition> stats)`, `Codec<InternalsDefinition> CODEC` with JSON keys `open_with`, `swap` (default `when_idle`), `sockets`, `stats`. Sockets sorted by name: that order is the slot layout.
  - `int slotCount()`, `List<Integer> slots(String socket)` (throws for an unknown socket).

A mod puts this codec inside its `BlockEntityFactory.settingsCodec()` (for example as
`factory_data.internals`), so a bad definition fails at startup like other settings.

- [ ] **Step 1: Write the failing test** — decode the `internals` JSON from machine-ui.md "Sockets and stats" and assert: swap `WHEN_IDLE`; sockets in order `coil, gears, motor`; `slotCount() == 4`; `slots("gears") == List.of(1, 2)`; `coil` optional; `open_with` is tag `c:tools/wrench`. Failures: `count: 0`, `swap: "sometimes"`, `accepts: "#"`, `slots("pump")`.
- [ ] **Step 2: Run** — expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — expected: PASS.
- [ ] **Step 5: Commit** — `feat(machine): internals definition codec`.

### Task 4: Part files and resolution

**Files:**
- Create: `machine/internals/PartModifier.java`, `machine/internals/PartDefinition.java`, `machine/internals/PartSubject.java`, `machine/internals/ResolvedPart.java`, `machine/internals/MachineParts.java`
- Test: `MachineCoreTest.parts()`

**Interfaces:**
- Consumes: `Expr` (Task 1), `StatModifier` (Task 2).
- Produces:
  - `record PartModifier(String stat, AttributeModifier.Operation op, Expr amount)`.
  - `record PartDefinition(Either<ResourceLocation, ResourceLocation> target, String socket, List<PartModifier> modifiers, Optional<Expr> durability)` — JSON has exactly one of `part` (a material part, matched against `MMDItem.template()`, e.g. `mmdlib:gear`) or `item`. Allowed variables: `material.hardness`, `material.strength`, `material.magic`, `material.damage`, `material.special.<key>`; any other variable fails decoding.
  - `record PartSubject(ResourceLocation item, Optional<ResourceLocation> part, Optional<MaterialProperties> material)`.
  - `record ResolvedPart(String socket, List<StatModifier> modifiers, OptionalInt durability)`.
  - `final class MachineParts`: `static MachineParts parse(Map<ResourceLocation, JsonElement> files, Function<ResourceLocation, Optional<PartSubject>> subjects)`, `Optional<ResolvedPart> resolve(ResourceLocation item)`, `int generation()`, `static MachineParts current()`, `static void install(MachineParts)` (bumps the generation), `static final MachineParts NONE`.

Rules:
- An `item` file beats a `part` template for that item.
- Two files claiming the same item or part: the lower resource ID wins, an error names both.
- A file that fails to decode is logged with its resource ID and skipped (as vanilla does for recipes).
- `material.special.<key>` missing on a material evaluates to 0.
- An item without a material that uses `$material.*` is not a part (logged once).
- A non-finite modifier amount or durability for some item: that item is not a part, logged once (Review Focus 4).
- Durability is rounded down and must be ≥ 1; no `durability` means the part never wears.
- Results are cached per item ID until the next `install`.

- [ ] **Step 1: Write the failing test** — subjects are a fake map, so no registry is needed:

```java
var subjects = Map.of(
    id("test:iron_gear"),  new PartSubject(id("test:iron_gear"),  Optional.of(id("mmdlib:gear")), Optional.of(props(4))),
    id("test:invar_gear"), new PartSubject(id("test:invar_gear"), Optional.of(id("mmdlib:gear")), Optional.of(props(6))),
    id("test:soft_gear"),  new PartSubject(id("test:soft_gear"),  Optional.of(id("mmdlib:gear")), Optional.of(props(0))),
    id("test:diamond_coated_gear"), new PartSubject(id("test:diamond_coated_gear"), Optional.of(id("mmdlib:gear")), Optional.of(props(10))));
```

Files: the gear template and the diamond-coated override from machine-ui.md "Part files", plus a
`test:broken.json` with `"amount": "$material.colour"` and a second template `test:zz_gear.json`
also claiming `mmdlib:gear`. Assert:
- iron gear: socket `gears`, speed amount 0.08, energy amount 0.12, durability 2000;
- invar gear: speed 0.24;
- diamond-coated gear uses the item file (speed `add_multiplied_total` 0.4);
- a template using `10 / $material.hardness` makes `test:soft_gear` resolve empty;
- `test:unknown` resolves empty;
- the broken file and the duplicate don't stop the others loading.

- [ ] **Step 2: Run** — expected: FAIL.
- [ ] **Step 3: Implement.** `MaterialProperties` is in `common.types` and has no NeoForge
  imports, so the core may use it.
- [ ] **Step 4: Run** — expected: PASS.
- [ ] **Step 5: Commit** — `feat(machine): part files with material templates`.

### Task 5: NeoForge wiring for parts and wear

**Files:**
- Create: `machine/neoforge/MachinePartsLoader.java`, `machine/neoforge/MachineComponents.java`
- Modify: `src/main/java/zone/moddev/mc/lib/MMDLib.java` (register the component register on the mod bus; add the reload listener on `AddReloadListenerEvent`)

**Interfaces:**
- Consumes: `MachineParts.parse/install`, `PartSubject` (Task 4).
- Produces:
  - `MachinePartsLoader extends SimpleJsonResourceReloadListener`, directory `mmdlib/machine_part` (files at `data/<ns>/mmdlib/machine_part/*.json`); its `apply` calls `MachineParts.install(MachineParts.parse(files, MachinePartsLoader::subject))`.
  - `static Optional<PartSubject> MachinePartsLoader.subject(ResourceLocation item)` — part from `MMDLib.itemDefinitions().get(item).template()`, material from `MMDLib.itemMaterials().get(item).properties()`; an item MMDLib didn't make gets a subject with both empty.
  - `MachineComponents.TYPES` (`DeferredRegister.DataComponents` for `mmdlib`), `MachineComponents.PART_WEAR` (`DataComponentType<Integer>`: wear points used; persistent `Codec.INT`, network `ByteBufCodecs.VAR_INT`).

- [ ] **Step 1: Implement both classes and the two `MMDLib` lines.** No new check here; Task 6's
  smoke test exercises them on a running server.
- [ ] **Step 2: Run** `./gradlew compileJava verifyMachineImports` — expected: PASS
  (the NeoForge imports are allowed in `machine/neoforge`).
- [ ] **Step 3: Commit** — `feat(machine): part-file reload listener and wear component`.

### Task 6: `MachineInternals`

**Files:**
- Create: `machine/neoforge/MachineInternals.java`
- Create: `src/runtimeTest/java/zone/moddev/mc/lib/smoke/MachineInternalsSmoke.java`
- Modify: `src/runtimeTest/java/zone/moddev/mc/lib/smoke/RuntimeSmoke.java:194` (call `MachineInternalsSmoke.check(level)` after `MachineSmoke.check(level)`)

**Interfaces:**
- Consumes: `InternalsDefinition` (Task 3), `MachineParts`, `ResolvedPart` (Task 4), `MachineComponents.PART_WEAR` (Task 5), `MachineStats` (Task 2), `ItemStorage`.
- Produces, on `public final class MachineInternals`:
  - `MachineInternals(InternalsDefinition definition, Supplier<MachineParts> parts)` (block entities pass `MachineParts::current`), `onChange(Runnable)`.
  - `ItemStorage sockets()` — one slot per socket position, role `MANUAL`, limit 1, filter: the part resolves, its `socket` equals that slot's socket, and `accepts` (if any) matches. Menus use this later.
  - `MachineStats stats()` and `MachineStats stats(Collection<StatModifier> extra)`. Cached; recomputed when sockets change or `parts.get().generation()` changes (Review Focus 3).
  - `Optional<String> missing()` — the first required socket (in slot order) with an empty position, e.g. `gears`.
  - `boolean canOpen(ItemStack held)` — true when `open_with` is absent or matches.
  - `boolean canSwap(boolean running)` — `definition.swap().allows(running)`.
  - `ItemStack install(String socket, int index, ItemStack stack, boolean running)` — returns what was not installed (all of it if refused).
  - `ItemStack remove(String socket, int index, boolean running)` — `EMPTY` if refused or empty.
  - `int wear(String socket, int points)` — adds `points` to `PART_WEAR` on every part in that socket that has a durability; a part reaching its durability is removed and destroyed; returns how many broke.
  - `CompoundTag save(HolderLookup.Provider)` / `void load(HolderLookup.Provider, CompoundTag)` — saved by socket name (`{"gears": [stack, ...]}`), not slot index. Parts with no slot left go to `orphans()` (Review Focus 2).
  - `List<ItemStack> orphans()`, `void dropAll(Level, BlockPos)` (sockets and orphans).

- [ ] **Step 1: Write the failing smoke test.** Use MMDLib's own gears and the definition JSON from
  Task 3's test with `accepts` removed from `motor`/`coil`. Read real hardness values from
  `MMDLib.itemMaterials()` instead of the example values. Install parts from
  `MachineParts.parse(...)` with the gear template (durability `"3"`) via
  `MachineParts.install`. Check:
  - with an empty machine, `missing()` is `gears` and `stats().get("speed") == 1`;
  - an iron gear in `gears` 0 and 1 gives speed `1 + 2 × 0.08 × (hardness − 3)`;
  - a stone block is refused in `gears`, and a gear is refused in `motor` (wrong socket);
  - `install` while running is refused under `when_idle` and allowed under `always`;
  - `wear("gears", 1)` three times breaks both gears and makes `missing()` `gears` again;
  - save, then load into a definition with `gears.count` 1: one gear in the socket, one in `orphans()`;
  - re-`install` parts with a different speed amount: `stats()` changes without touching the sockets;
  - `canOpen` with a non-wrench is false when `open_with` is set;
  - a gear template that also modifies `max_temperature` still applies its speed (Review Focus 1).
- [ ] **Step 2: Run** `./gradlew runServer -I scripts/runtime-smoke.init.gradle` —
  expected: FAIL (`MachineInternals` missing). Afterwards run `./gradlew build` to
  restore normal main output (as `scripts/runtime-smoke.init.gradle` says).
- [ ] **Step 3: Implement `MachineInternals`.**
- [ ] **Step 4: Run the smoke** — expected: `MMDLIB_RUNTIME_SMOKE_PASS` in
  `build/runtime-smoke/server/logs/latest.log`; then `./gradlew build`.
- [ ] **Step 5: Commit** — `feat(machine): machine internals with sockets, stats, wear and swap rules`.

### Task 7: Ingredients

**Files:**
- Create: `machine/ingredient/MachineIngredient.java` (with nested or sibling `ItemPart`, `FluidPart`, `EnergyPart`), `machine/ingredient/FluidAmount.java`, `machine/ingredient/AmountMatch.java`
- Test: `MachineCoreTest.amountMatch()`; codecs in `MachineRecipeSmoke` (Task 10), because `Ingredient` and `HolderSet<Fluid>` need registry ops.

**Interfaces:**
- Produces (as in [rebuild-plan.md](rebuild-plan.md#ingredients)):
  - `sealed interface MachineIngredient permits ItemPart, FluidPart, EnergyPart { long amount(); }`, `Codec<MachineIngredient> CODEC`.
  - `record ItemPart(Ingredient items, int count)` — JSON `{"items": <vanilla ingredient>, "count": 1}`, count default 1, ≥ 1.
  - `record FluidPart(HolderSet<Fluid> fluids, int amount)` — `{"fluids": "minecraft:water" | "#c:water" | [...], "amount": 250}`, ≥ 1.
  - `record EnergyPart(long amount)` — `{"energy": 4000}`, ≥ 1.
  - `record FluidAmount(Holder<Fluid> fluid, DataComponentPatch components, int amount)`, `Codec<FluidAmount> CODEC` (`{"id", "amount", "components"?}`), `static final FluidAmount EMPTY`, `boolean isEmpty()`, `boolean sameFluid(FluidAmount)` (fluid and components).
  - `enum AmountMatch { EXACT, AT_LEAST, ANY_AMOUNT; boolean test(long required, long available); }`.

`CODEC` picks the record by which of `items`, `fluids`, `energy` is present; none or more than one
is an error naming the three keys.

- [ ] **Step 1: Write the failing test** — `AmountMatch`: `EXACT.test(5,5)` true, `EXACT.test(5,6)` false; `AT_LEAST.test(5,6)` true, `AT_LEAST.test(5,4)` false; `ANY_AMOUNT.test(5,0)` true; `AT_LEAST.test(3_000_000_000L, 3_000_000_001L)` true.
- [ ] **Step 2: Run** `./gradlew verifyMachineCore` — expected: FAIL.
- [ ] **Step 3: Implement** the records and codecs.
- [ ] **Step 4: Run** — expected: PASS.
- [ ] **Step 5: Commit** — `feat(machine): machine ingredients and fluid amounts`.

### Task 8: Groups, inventory and the snapshot transaction

**Files:**
- Create: `machine/inventory/MachineTx.java`, `machine/inventory/SnapshotTx.java`, `machine/inventory/SlotGroup.java` (`ItemGroup`, `FluidGroup`, `EnergyGroup`), `machine/inventory/MachineInventory.java`
- Create: `machine/neoforge/StorageGroups.java`
- Create: `src/runtimeTest/java/zone/moddev/mc/lib/smoke/MachineInventorySmoke.java` (called from `RuntimeSmoke` like Task 6)

**Interfaces:**
- Consumes: `FluidAmount` (Task 7).
- Produces:
  - `interface MachineTx extends AutoCloseable { void commit(); void close(); void track(Object key, Supplier<Runnable> capture); }` — `track` runs `capture` the first time `key` is touched in this tx and keeps the returned undo. `close()` without `commit()` runs the undos in reverse order. Calls after `close()` throw `IllegalStateException`.
  - `final class SnapshotTx implements MachineTx` (core; no nesting in stage 1).
  - `sealed interface SlotGroup permits ItemGroup, FluidGroup, EnergyGroup`.
  - `non-sealed interface ItemGroup`: `int size()`, `ItemStack peek(int slot)` (a copy), `int extract(int slot, int count, MachineTx tx)`, `int insert(ItemStack stack, MachineTx tx)` (stacks onto matching stacks first, then empty slots; returns how many fitted), `boolean fits(List<ItemStack> stacks)` (works on copies, changes nothing).
  - `non-sealed interface FluidGroup`: `int tanks()`, `FluidAmount peek(int tank)`, `int extract(int tank, int amount, MachineTx tx)`, `int insert(FluidAmount fluid, MachineTx tx)`, `boolean fits(List<FluidAmount> fluids)`.
  - `non-sealed interface EnergyGroup`: `long stored()`, `long capacity()`, `long extract(long amount, MachineTx tx)`, `long insert(long amount, MachineTx tx)`.
  - `final class MachineInventory`: `static Builder builder()`, `Builder.group(String key, SlotGroup group)` (duplicate key throws), `Optional<SlotGroup> group(String key)`, `Set<String> keys()`, `MachineTx begin()`.
  - `StorageGroups.items(ItemStorage storage, int... slots)`, `StorageGroups.fluids(FluidStorage storage, int... tanks)`, `StorageGroups.energy(EnergyBuffer buffer)`. Group indexes are 0-based within the group. They work on the storages as the owner: items through `extractItem`/`insertResult`, fluids through `tank(i)`, energy ignoring the buffer's insert/extract rates but not its capacity. Out-of-range indexes take or insert nothing.

- [ ] **Step 1: Write the failing smoke test.** Storages: `ItemStorage(4)` with slot 0 `INPUT` and
  slots 1–3 `OUTPUT`; `FluidStorage` with one 4,000 mB tank; `EnergyBuffer`
  with capacity 10,000,000,000, rates 100. Groups `input` = 0, `output` = 1,2, `byproduct` = 3,
  `water` = tank 0, `energy`. Check:
  - `insert` into `output` succeeds although the slots are `OUTPUT` (owner bypass);
  - a tx that inserts into `output` and `water`, then closes without commit, leaves both as before;
  - the same with `commit()` keeps both;
  - inserting 5,000,000,000 FE in one tx works (above 2³¹ and above the rate);
  - `fits` on a full `byproduct` slot is false and the storage's change counter is unchanged;
  - `extract(9, 1, tx)` on `input` returns 0;
  - using a closed tx throws `IllegalStateException`.
- [ ] **Step 2: Run the smoke** — expected: FAIL (types missing).
- [ ] **Step 3: Implement** core types, then `StorageGroups`.
- [ ] **Step 4: Run the smoke** — expected: PASS; then `./gradlew build`.
- [ ] **Step 5: Commit** — `feat(machine): slot groups, machine inventory and snapshot transactions`.

### Task 9: Products and the recipe record

**Files:**
- Create: `machine/recipe/MachineProduct.java` (`ItemProduct`, `FluidProduct`), `machine/recipe/MachineRecipe.java`

**Interfaces:**
- Consumes: `MachineIngredient`, `FluidAmount` (Task 7).
- Produces:
  - `sealed interface MachineProduct permits ItemProduct, FluidProduct { float chance(); }`, `Codec<MachineProduct> CODEC`.
  - `record ItemProduct(ItemStack stack, float chance)` — `{"item": {"id": ..., "count": 2}, "chance": 0.25}`.
  - `record FluidProduct(FluidAmount fluid, float chance)` — `{"fluid": {"id": ..., "amount": 250}, "chance": 1}`.
  - Chance defaults to 1 and must be in (0, 1].
  - `record MachineRecipe(Map<String, List<MachineIngredient>> inputs, Map<String, List<MachineProduct>> outputs, int time)`, `MapCodec<MachineRecipe> CODEC`; `time` ≥ 1; at least one input. `StreamCodec` and registration wait for phase 2.

- [ ] **Step 1: Implement.** Decoding is checked in Task 10's smoke test with the crusher recipe from recipes.md.
- [ ] **Step 2: Run** `./gradlew compileJava verifyMachineImports` — expected: PASS.
- [ ] **Step 3: Commit** — `feat(machine): machine products and recipe record`.

### Task 10: `RecipeMatcher`

**Files:**
- Create: `machine/recipe/MatchResult.java`, `machine/recipe/RecipeMatcher.java`
- Create: `src/runtimeTest/java/zone/moddev/mc/lib/smoke/MachineRecipeSmoke.java` (called from `RuntimeSmoke`)

**Interfaces:**
- Consumes: everything from Tasks 7–9.
- Produces:
  - `record MatchResult(Reason reason, @Nullable String group, int index)` with `enum Reason { OK, NO_GROUP, WRONG_KIND, MISSING_INPUT, NO_ROOM }` (`index` is the position in that group's input or output list, −1 when not applicable), `boolean ok()`, `static final MatchResult OK`.
  - `RecipeMatcher.check(MachineRecipe, MachineInventory) -> MatchResult` — changes nothing and calls no storage mutator.
  - `RecipeMatcher.consume(MachineRecipe, MachineInventory) -> boolean` — takes item and fluid inputs in one tx, all or nothing; energy is not taken.
  - `RecipeMatcher.produce(MachineRecipe, MachineInventory, RandomSource) -> boolean` — rolls each chance, inserts the hits in one tx, all or nothing.

Rules, from recipes.md:
- Groups are joined by key. A missing key is `NO_GROUP`; an item ingredient on a fluid group is `WRONG_KIND`.
- Item parts in one group claim matching stacks in list order; a later part can't count what an earlier one claimed (Review Focus 5).
- Fluids total across the group's tanks of fluids in the set.
- Energy needs only one tick's share: `ceil(amount / time)`.
- `check` needs room for every product with chance treated as 1.

- [ ] **Step 1: Write the failing smoke test.** Decode the crusher recipe from recipes.md through
  `RegistryOps.create(JsonOps.INSTANCE, level.registryAccess())`: `input` 1 × `#c:ores/copper`,
  `water` 250 mB of `minecraft:water`, `energy` 4,000 FE, `time` 200; outputs `output`
  2 × `minecraft:raw_copper` (vanilla has no copper dust) and `byproduct` 1 × `minecraft:gravel`
  at chance 0.25. Use `minecraft:copper_ore` as the ore. Check:
  - `check` is `OK` with 3 copper ore, 1,000 mB water and 20 FE, and `MISSING_INPUT` (group `energy`) at 19 FE;
  - `check` is `NO_GROUP` when the inventory has no `water` group;
  - `check` is `NO_ROOM` (group `byproduct`) when slot 3 holds 64 cobblestone, and the change counter didn't move;
  - `consume` leaves 2 ore and 750 mB and doesn't touch energy;
  - `produce` with a full `byproduct` slot and a random that always hits returns false and leaves `output` empty (whole tx rolled back);
  - `produce` with a random that always misses inserts only the copper;
  - Review Focus 5: inputs 2 × iron ingot and 2 × `#c:ingots` against 3 iron ingots is `MISSING_INPUT` at index 1;
  - an energy cost of 6,000,000,000 FE over 2 ticks matches with 3,000,000,000 stored;
  - a recipe JSON with both `items` and `energy` in one ingredient fails to decode, naming the keys.

  For the random: a `RandomSource` whose `nextFloat()` returns a fixed value (`0f` always hits, `0.99f` always misses).
- [ ] **Step 2: Run the smoke** — expected: FAIL.
- [ ] **Step 3: Implement `MatchResult` and `RecipeMatcher`.**
- [ ] **Step 4: Run the smoke** — expected: PASS; then `./gradlew build`.
- [ ] **Step 5: Commit** — `feat(machine): recipe matcher with all-or-nothing consume and produce`.

### Task 11: Docs and final check

**Files:**
- Create: `docs/machine-module/stage-1.md` — what exists now: the Java API by package, the
  `internals` and part-file JSON, how a block entity owns `MachineInternals` and a
  `MachineInventory`, what is deliberately not there yet (recipe type, processor, GUI, client sync
  of part files).
- Modify: `docs/machine-module/README.md` (status line and a row for stage 1), `CLAUDE.md` (the
  machine module row's Code column → `machine`; the "none of its types exist yet" fact narrowed to
  what is still unbuilt), `docs/roadmap.md` (machine module entry: stage 1 built).

- [ ] **Step 1: Write the docs.**
- [ ] **Step 2: Run** `./gradlew format` if `tools/gjf.jar` exists, then `./gradlew check` —
  expected: everything that passed in the Task 0 baseline still passes, plus `verifyMachineCore`
  and `verifyMachineImports`.
- [ ] **Step 3: Run the smoke once more** — expected: `MMDLIB_RUNTIME_SMOKE_PASS`; then `./gradlew build`.
- [ ] **Step 4: Commit** — `docs(machine): document stage 1`.

## Not in this stage

- Recipe type registration, `StreamCodec`, JEI, datagen, moving `mmdlib:crushing` (phase 2).
- `FeatureHost`, `RecipeProcessor`, energy per tick, stats feeding recipe time (phase 3).
- Syncing part files to clients for the hover preview, the engine view, wrench opening, socket
  menu slots (phase 17's GUI half).
- Upgrade cards as a stat source and face cards (phases 15–16). `stats(extra)` is the hook they will use.
