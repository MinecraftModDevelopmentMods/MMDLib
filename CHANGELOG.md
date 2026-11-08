# Changelog

## Unreleased — CraftTweaker integration

Changes on `crafttweaker-integration` since `master-2.0-1.21.1-neo`.

- Add optional CraftTweaker support for defining materials, standalone blocks and
  items, and reusable block and item modules in ZenScript.
- Add the `#loader mmdlib` startup loader. Definitions run before registry events
  and use MMDLib's existing validation and registration path. Changes require a
  restart; ordinary CraftTweaker scripts remain the place for recipes.
- Generate blockstates, models, English names, loot tables, and tags for script
  content. Supply material texture templates tinted by material color, and load
  `scripts/mmdlibdata` as an override pack for custom resources.
- Expose `MaterialExtensions.registerDefinitionSource` for code-built definitions.
  Definition sources can register modules before material files are decoded.
- Add the material runtime `tinted` setting so precolored textures can opt out of
  material tinting.
- Keep CraftTweaker optional, with its integration isolated under
  `compat/CraftTweaker` and initialized only when CraftTweaker is installed.
- Document the scripting API, generated resources, startup ordering, and current
  limitations in [CraftTweaker definitions](docs/crafttweaker.md) and
  [definition loading](docs/definition-loading.md).

+- Expose material repair/harvest settings, block runtime overrides, and named tool
+  stat methods to CraftTweaker. Add a typed helper for existing generic-tool factory
+  data, with script integration tests and an API/runtime capability inventory.
+  Per-tool durability scaling remains limited by vanilla TieredItem resetting the
+  computed durability to the material tier's uses; material durability works.
- Add typed item/block/tag/module ID references and independent module snapshots
  for CraftTweaker. Snapshot copies rebind locally owned handlers; module tags are
  now available to subsequent builders during the same script run.
- Add fluent item/block builder conveniences, generated English name overrides,
  and default/self/empty/referenced loot controls. Existing external resource
  overrides retain priority; shape-aware default loot remains supported.
- Add reproducible isolated client-smoke runs with CraftTweaker present and absent,
  generated-content rendering captures, and a verification report for the parity work.
- Add a typed CraftTweaker manager for shared `mmdlib:crushing` recipes under a
  separate crusher integration package. Reuse standard recipe actions, removals,
  queries and reload handling; test script-added recipes through the crackhammer.
