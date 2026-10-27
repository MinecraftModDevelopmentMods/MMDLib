# Load-time method events

An extension file declares Java methods directly. The loader resolves and retains
an actual `MethodHandle` before registering the binding under its ID. No callback
registration in the contributing mod is required for these handlers.

```json
{
  "events": [
    {
      "type": "player-tick",
      "handler": "zone.moddev.mc.basemetals.events.AquariumEvents#onPlayerTick",
      "id": "basemetals:acquarium",
      "name": "Aquarium Armor Effects"
    }
  ]
}
```

`EventExtension.CODEC` is the DFU schema. `handler` must identify a class and a
method separated by `#`. The class and method must be public; the method must be
static and return void. Its parameter types must exactly match the event contract.
Overloads are resolved by that signature. `id` is required, and `name` is an
optional descriptive label (defaulting to the ID).

The array declares handlers; it does not run every entry for every player or
attach every entry to every material. Distinct IDs may handle the same event type.
IDs must be unique across loaded files and code-registered callbacks.

## Built-in contracts

| `type` | Java handler parameters | Dispatched |
|---|---|---|
| `player-tick` | `Player player` | Yes |
| `weapon-hit` | `Player player, LivingEntity target` | Yes |
| `tool-left-click` | `Player player, MaterialEventContexts.ToolTarget target` | Yes |
| `tool-right-click` | `Player player, MaterialEventContexts.ToolTarget target` | Yes |
| `tool-harvest` | `Player player, MaterialEventContexts.Harvest harvest` | Yes |
| `block-right-click` | `Player player, MaterialEventContexts.BlockUse use` | Yes |
| `projectile-damage` | `Projectile projectile, LivingEntity target, ItemStack ammo, DamageContainer damage` | Yes |
| `projectile-hit` | `Projectile projectile, LivingEntity target, ItemStack ammo` | Yes |
| `redstone-changed` | `MaterialEventContexts.RedstoneChange change` | Yes |

`ItemEventBridge` dispatches every built-in contract; see [item and material
bindings](#item-and-material-bindings) for the NeoForge events and sides.

### Block contracts

`block-right-click` is keyed by the **clicked block's** definition (a block module's
`template.events`), not the held item. `BlockUse` carries the level, position, state,
clicked face, hand, and held stack. A handler that acts on the click calls
`use.consume()` on both sides; the bridge then cancels the interaction as handled, so
neither the held item's binding nor vanilla item use (e.g. placing a block) follows.
It does not require a block entity: anvils and crafting-table-style blocks build their
menu on demand. MMDLib's anvils dogfood this, binding the generic `mmdlib:open_menu`
handler (`BlockEvents#openMenu`, declared in `mmdlib/extension/blocks.json`), which
opens the block's own menu provider:

```json
{"factory": "mmdlib:anvil", "template": {"events": {"block-right-click": "mmdlib:open_menu"}}}
```

`redstone-changed` is dispatched by the block itself rather than the bridge: a
`mmdlib:block` with `factory_data.redstone.input` set stores the power it receives and,
on the server only, fires once each time that power changes (not on every neighbour
update). `RedstoneChange` carries the level, position, new state, and the previous and
new power (0-15). It is keyed by the block's definition, falling back to its material's
`event` map. See [runtime behaviors](runtime-behaviors.md#redstone).

```java
public static void lamp(MaterialEventContexts.RedstoneChange change) {
    if (change.powered() != (change.previous() > 0)) RedstoneBlock.setOutput(change.level(), change.position(), change.power());
}
```

### Projectile contracts

Both run on the logical server, for projectiles whose ammunition stack has an MMDLib
definition. Arrows and bolts resolve that stack from `AbstractArrow`'s pickup stack,
and thrown items from their item stack, so material arrows need no custom item class. The shooter is `projectile.getOwner()`
and may be null (dispensers) or a non-player (skeletons).

- `projectile-damage` fires from NeoForge's `LivingIncomingDamageEvent`, **before**
  any mitigation. `DamageContainer` lets the handler change the amount or remove a
  reduction stage, e.g. armor-piercing ammunition:

  ```java
  public static void pierceArmor(Projectile projectile, LivingEntity target, ItemStack ammo,
          DamageContainer damage) {
      damage.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> 0F);
  }
  ```

- `projectile-hit` fires from `LivingDamageEvent.Post`, only once damage was actually
  dealt (as vanilla tipped arrows apply their effects). Use it for effects:

  ```java
  public static void levitate(Projectile projectile, LivingEntity target, ItemStack ammo) {
      target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100));
  }
  ```

Potion-component data could later cover effect-only ammunition without Java, but it
cannot express damage changes such as ignoring armor; these contracts cover both.

`LivingEntity` is the Minecraft 1.21.1 type corresponding to the older
`EntityLiving`. Player handlers can inspect equipment directly:

```java
public final class AquariumEvents {
    public static void onPlayerTick(Player player) {
        // Inspect equipped aquarium armor and apply its effects.
    }
}
```

Left/right click describes the action, not whether the target is a block or an
entity. Both tool contracts therefore use `ToolTarget`, whose variants are:

- `BlockTarget`: level, immutable block position, state, clicked face, hand, stack.
- `EntityTarget`: any Entity, hand, stack. This includes sheep and dropped ItemEntity objects.
- `AirTarget`: hand and stack, for a use without a target.

The stack is the actual triggering stack, not a copy; use these contexts only for
the duration of the call. For example:

```java
public static void useTool(Player player, MaterialEventContexts.ToolTarget target) {
    if (target instanceof MaterialEventContexts.BlockTarget block) {
        // Work with block.level(), block.position(), and block.state().
    } else if (target instanceof MaterialEventContexts.EntityTarget entity) {
        // Work with entity.entity(), including dropped item entities.
    }
}
```

## Loading and retaining handles

Register any custom event contracts first, then load extension files after the
parallel discovery stage and before decoding materials that reference callbacks:

```java
var events = MaterialExtensions.GLOBAL.events();
var loaded = events.loadExtension(extensionFile, modClassLoader);
var resolved = events.resolvedHandler(ResourceLocation.parse("basemetals:acquarium"));
MethodHandle handle = resolved.methodHandle();
```

Pass a class loader that can see the contributing mod's classes. A mod entrypoint's
loader is appropriate when it can see those classes; the loader of a mod-container
implementation is not a reliable substitute. The caller supplies the loader
explicitly; the API does not guess it from filesystem paths or thread context.

Alternatively, decode with `EventExtension.CODEC`, then call
`events.loadExtension(definition, modClassLoader, sourceDescription)`. The source
label is included in resolution errors. The Path overload also reports JSON/codec
errors with the filename. Unknown event types, missing classes/methods, inaccessible
or instance methods, incorrect signatures, and duplicate IDs fail during loading.
An invalid file installs none of its callbacks. Resolving handles does not execute
handlers; normal Java class initialization can happen on first invocation.

The entrypoint collects root-level `mmdlib/extension/*.json` files and resolves
these handlers before module decoding and material decoding. See
[startup definition loading](definition-loading.md). Loading a handler does not
subscribe it to global NeoForge gameplay events.

## Calling by ID or material binding

Armor or its event bridge calls the retained handler by ID:

```java
events.dispatch(ResourceLocation.parse("basemetals:acquarium"),
        MaterialEventTypes.PLAYER_TICK, player);
```

Weapon and tool code supply their contexts:

```java
events.dispatch(weaponHandlerId, MaterialEventTypes.WEAPON_HIT,
        new MaterialEventContexts.WeaponUse(player, livingTarget));

var target = new MaterialEventContexts.EntityTarget(itemEntity, hand, toolStack);
events.dispatch(toolHandlerId, MaterialEventTypes.TOOL_RIGHT_CLICK,
        new MaterialEventContexts.ToolUse(player, target));
```

Dispatch selects an already-resolved callback by ID and checks its event/context
contract. There is no class or method lookup in the gameplay path. For custom
plumbing, `resolved.methodHandle()` exposes the original typed handle directly.

Material bindings reference loaded handlers the same way:

```json
{"event":{"player-tick":"basemetals:acquarium", "projectile-hit":"basemetals:levitation_shot"}}
```

The bridge uses a material's `event` map as the fallback when an item's own
definition has no binding for the event, so a material's generated arrows and bolts
pick up its `projectile-*` bindings and all its items share its `player-tick`.

Here `events` arrays belong to **extension files**; the material's `event` map
references their resolved IDs. The material codec validates those references.
Code-registered Consumers remain supported alongside method-backed handlers.

For the built-in contracts, `ItemEventBridge` (below) chooses the NeoForge hook,
side, and affected item/material; its only equipment scan is `player-tick`'s check of
a player's armor and hands. Direct `dispatch` calls leave those choices to the caller.
There are no automatic effects, cancellation semantics, or scheduling. Existing explicit
`MaterialEvents.listen` bridges for native NeoForge event context types remain available.

Runtime exceptions and Errors propagate from a handler. Checked exceptions are
wrapped with its ID and method reference while preserving their cause.

## Item and material bindings

Item templates bind handlers by event name in `events`:

```json
{"template": {"events": {"tool-right-click": "mmdlib:crackhammer_crush"}}}
```

`ItemEventBridge`, registered by MMDLib on the NeoForge game bus, dispatches these
for any registered item or block whose ID has a loaded MMDLib definition (including
custom types registered through the extension API), whatever its Java class. The item definition's binding wins; otherwise the item's material binding
applies. Click and tick events fire on both logical sides; world-changing handlers
must check the side. It never cancels the NeoForge event.

| NeoForge event | Binding | Target |
|---|---|---|
| `RightClickBlock` | `tool-right-click` | `BlockTarget` |
| `RightClickItem` | `tool-right-click` | `AirTarget` |
| `EntityInteract` | `tool-right-click` | `EntityTarget` |
| `LeftClickBlock` (START only) | `tool-left-click` | `BlockTarget` |
| `LeftClickEmpty` (client only) | `tool-left-click` | `AirTarget` |
| `AttackEntityEvent` | `tool-left-click`, plus `weapon-hit` for living targets | `EntityTarget` |
| `BlockDropsEvent` (server only, player breaker) | `tool-harvest` | `Harvest` |
| `RightClickBlock` (clicked block's definition; skipped when sneaking with an item, as vanilla) | `block-right-click`, before the item's `tool-right-click` | `BlockUse` |
| `PlayerTickEvent.Post` (worn armor and both hands) | `player-tick` | `Player` |
| `LivingIncomingDamageEvent` (server, projectile direct source) | `projectile-damage` | `ProjectileDamage` |
| `LivingDamageEvent.Post` (server, projectile direct source) | `projectile-hit` | `ProjectileHit` |

`player-tick` runs each bound callback once per tick however many worn or held items
share it: a material binding is a per-material effect, and its handler can count the
equipped pieces itself. Items bound to different callbacks each run theirs.

`Harvest` carries the server level, position, broken state, breaking tool stack, and
NeoForge's live drop list; handlers may replace, remove, or add drop entities before
they spawn.

Vanilla follows an unconsumed block click with an item use, so a right-click on a
block can dispatch a `BlockTarget` and then an `AirTarget`. Item bindings are
validated against the callback catalog when materials load.

## Custom method signatures

Mods can register additional contracts, with adapters from typed contexts to the
actual method parameters:

```java
record RepairContext(Player player, ItemStack stack) {}
var repair = events.registerEvent("example:repair", RepairContext.class,
        MethodType.methodType(void.class, Player.class, ItemStack.class),
        (method, context) -> { method.invokeExact(context.player(), context.stack()); });
```

A handler for this event must be `public static void method(Player, ItemStack)`.
A simple `registerEvent(name, Context.class)` accepts one `Context` argument by
default. Event contracts are extensible even though the initial player, weapon,
and tool contracts are bundled.

`verifyMethodEvents` checks real method resolution and invocation, caller-supplied
class loaders, ID/material dispatch, multiple handlers for one type, error and
exception paths, failed-file atomicity, and JSON/NBT round trips. Built-in method
signatures are resolved without constructing a Minecraft world. In-game armor,
weapon, and tool behavior still needs verification in the consuming mod.

See [startup definition loading](definition-loading.md) for folder dispatch, IDs, loading stages, and access to loaded material plans.
