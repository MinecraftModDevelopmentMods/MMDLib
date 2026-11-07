# Fluid bottles

Vanilla glass bottles only pick up water. MMDLib lets them hold any fluid in the
`#mmdlib:bottleable` fluid tag, without a mixin. A filled bottle is a single item,
`mmdlib:fluid_bottle`, that holds **333 mB**, a third of a bucket, of one fluid.

## Making a fluid bottleable

Add its **source** fluid to the tag with a datapack or your mod's data:

```json
// data/mmdlib/tags/fluid/bottleable.json
{ "replace": false, "values": ["example:molten_tin"] }
```

MMDLib ships the tag empty, so packs and mods decide what can be bottled.
Vanilla's own fluids (water, lava) are always left to vanilla, even if tagged:
a water bottle is still vanilla's water potion.

## What works

* **Right-click a source block** with a glass bottle to fill it. The source block
  stays, as it does for water. Protected blocks (`Level.mayInteract`) are skipped.
* **Tanks and pipes** fill and empty bottles through the fluid item capability
  (`Capabilities.FluidHandler.ITEM`), for example with `FluidUtil`. Filling takes
  exactly 333 mB. Emptying a bottle leaves a glass bottle.
* **Name**: "Bottle of" plus the fluid's name, such as "Bottle of Molten Copper".
* **Look**: vanilla's potion bottle, with the fluid's still texture showing
  through the potion's liquid area. It is tinted with whatever color the fluid
  type registers on the client (`IClientFluidTypeExtensions#getTintColor`);
  fluids without a tint show their texture as drawn. Glowing fluids glow.

## Other mods' glass bottle capabilities

If another mod already gives glass bottles a fluid capability, MMDLib doesn't
register its own: it logs that at startup and leaves filling glass bottles from
tanks to that mod. Right-clicking bottleable fluids, and filled MMDLib bottles,
work either way. MMDLib registers at the lowest priority, so mods registering at
the usual priority are seen first.

## Code

`zone.moddev.mc.lib.common.fluid.FluidBottles` holds the tag (`BOTTLEABLE`), the
capacity, the item (`BOTTLE`) and its fluid component (`CONTENT`).
`FluidBottleItem.filled(fluid)` makes a full bottle, and
`FluidBottleItem.contents(stack)` reads one. `FluidBottles.bottleable(fluid)` is
the single rule for what can be bottled.

`FluidBottleSmoke` (game tests) checks filling from the world, water left to
vanilla, untagged fluids refused, and filling and emptying through tanks.
