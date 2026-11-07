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
