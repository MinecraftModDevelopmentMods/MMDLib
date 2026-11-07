# CraftTweaker integration parity plan

Status: phases 1–5 complete within the integration-only scope. The phase 2
per-tool durability-scaling acceptance item remains deferred pending a core fix.
See [the API inventory and runtime audit](crafttweaker-api-inventory.md).

Baseline: MMDLib `1862428`; ContentTweaker `1.20.1` at
[`049253e587d92a99f49e31c10ca2c9c20c967eb6`](https://github.com/CraftTweaker/ContentTweaker/tree/049253e587d92a99f49e31c10ca2c9c20c967eb6).

## Scope and acceptance boundary

Bring the scripting integration closer to ContentTweaker using functionality that
MMDLib already implements. Preserve current script syntax and registry identities.
No core schema, registrar, event-contract, or standard-runtime changes are planned.
Implementation belongs in `compat/CraftTweaker`, its generated resources, tests,
and documentation. A feature that needs changes elsewhere is deferred and recorded
with the missing runtime capability rather than silently widening this scope.

Use the public definition-source, module, implementation, and event APIs. Do not
replace standard factories, register parallel script-only versions of standard
items/blocks, or inject components after registration to simulate missing support.
Do not claim parity merely because a value can be stored in a definition.

## Capability decisions

| ContentTweaker capability | Proposed MMDLib treatment | Boundary |
| --- | --- | --- |
| Common block/item settings | Add familiar fluent aliases over existing properties | Integration only; preserve existing validation |
| Deferred object references | Add typed ID wrappers accepted by new overloads | Store IDs; resolve only where existing runtime already resolves them |
| Clone properties | Copy supported MMDLib module/builder configuration | No arbitrary vanilla/modded object cloning promise |
| Tool configuration | Expose material runtime settings and typed helpers for existing tool factory data | Material-derived tier; no new tier registry |
| Material/block/item families | Retain current material and module APIs | Already broader than this ContentTweaker branch |
| Gameplay callbacks | Retain current hooks and improve discovery/examples | No additional core event contracts |
| Generated resources and drops | Expose names and simple loot options through the existing generated pack | Generated resources only; no change to block runtime loot-source properties |
| Food, rarity, arbitrary attributes | Defer | RuntimeProperties does not apply these as configurable definition properties |
| Creative tabs and sound registration | Defer | No existing matching MMDLib public definition/registration path |
| Advanced block predicates/properties | Defer | Current block schema and runtime do not represent/apply them |

## Phase 1: establish the supported surface

- [x] Inventory every exposed constructor, property, method, callback context, and
  inherited member. Map each to its actual definition and runtime consumer.
- [x] Correct `crafttweaker.md`: use duration and animation are implemented; food,
  lore, attributes, and tool actions must be described individually by runtime support.
- [x] Document module inheritance versus copying properties from a game object,
  startup-only definitions, namespace restrictions, side rules, and restart needs.
- [x] Publish a capability table distinguishing script-exposed, runtime-supported
  but unexposed, schema-only, and unavailable features.

Acceptance: every proposed addition has a named existing runtime consumer or an
existing generated-resource consumer. Unsupported fields remain unexposed.

## Phase 2: expose material runtime and tool settings

- [x] Add MaterialBuilder accessors for existing MaterialRuntimeSettings fields:
  repair item, incorrect-block tag, base block, block-set type, block sound, and map
  color. Preserve current generated texture/armor defaults.
- [x] Add named helpers for existing material special stats: tool durability,
  efficiency, attack damage, and enchantability, after checking MaterialStats
  precedence and validation. Keep `addSpecial` available.
- [x] Add a typed configuration helper for the existing `mmdlib:tool` factory:
  mineable/fast-on tags, abilities, attack damage/speed, durability and speed scaling.
  Serialize to the existing factory_data keys and use its existing decoder.
- [x] Document material-wide settings versus per-module settings and which factories
  actually consume each value. Do not imply the generic tool helper configures all
  vanilla-shaped tool factories.

Acceptance: a script-defined material tool demonstrates repair, harvest restrictions,
stats, and configured abilities in the server runtime. No synthetic independent tier
or numeric harvest-level mapping is introduced.

Runtime limitation discovered during phase 2: `StandardItems.Tool` computes scaled
durability before invoking vanilla `TieredItem`, whose constructor calls
`properties.durability(tier.getUses())`. Thus material durability works, but the
helper's durability scaling does not affect the final item. Core changes remain
out of scope; this part of acceptance stays open:

- [ ] Verify effective per-tool durability scaling after a separately authorized
  core fix. Do not advertise it as implemented runtime behavior.

Verification: `./gradlew --offline check` passed (185 unit tests, 83 MMDLib
GameTests, 52 companion GameTests, packaging and static assets). No client smoke
check was run for this phase.

## Phase 3: references and configuration copying

- [x] Add typed wrappers for item, block, module, and block-tag IDs where consumed
  by current builders. Keep string forms for compatibility.
- [x] Add overloads that unwrap references into existing definition IDs. References
  must not expose eager registry getters in the startup loader.
- [x] Add explicit configuration-copy operations for supported MMDLib modules and,
  where useful, unbuilt builders. Snapshot collections/maps; define overrides as
  source configuration first, caller edits second.
- [x] Exclude registry identity, aliases, and callback ownership from blind copying.
  Specify how callback bindings are reused without sharing mutable handler maps.
- [x] Reject unknown source modules and wrong reference kinds with script-local
  errors. Leave cross-definition runtime references to the existing resolution path.

Acceptance: copied configurations can be edited independently; forward ID references
work wherever the existing runtime supports them; invalid references fail without
creating partial content. Arbitrary registry-object cloning remains documented as
unsupported.

Implementation notes: explicit `ItemModule.copy` and `BlockModule.copy` snapshot
unbuilt or built module builders. Standalone builder cloning and arbitrary game
object cloning are not added. Locally owned callbacks get new IDs and independent
handler maps; captured variables and external callback IDs remain shared. Typed
references do not add registry getters or relax existing resolution order. Same-run
item-module tag lookup now reads the active script collection.

Phase 3 verification: `./gradlew --offline check` passed with 187 unit tests,
84 MMDLib GameTests, 52 companion GameTests, packaging and static assets. No client
smoke test was run. The previous core durability limitation remains deferred.

## Phase 4: familiar builder conveniences and resources

- [x] Add selected fluent aliases: `stacksTo`, `craftRemainder`, `destroyTime`,
  `explosionResistance`, `strength`, `noCollisions`, `noOcclusion`,
  `requiresToolToDrop`, and `noCorrespondingItem`. Delegate to existing setters.
  Use collision-free names when properties already occupy a scripting member name.
- [x] Keep existing constructors and `build()` behavior; do not change return types
  or adopt ContentTweaker syntax wholesale.
- [x] Add display-name overrides and simple generated loot choices: default/self,
  no drops, or an existing loot-table reference, subject to vanilla 1.21.1 loot
  semantics and the existing generated pack's resource priority.
- [x] Document external resource overrides for complex loot/models and retain the
  current material texture generation and directional block templates.

Acceptance: generated resource contents and override precedence are checked; a
server break test verifies drop behavior. Aliases and property syntax produce the
same definitions and the same validation errors.

Phase 4 verification: `./gradlew --offline check` passed with 190 unit tests,
85 MMDLib GameTests, 52 companion GameTests, packaging checks, and static assets.
The server test proves external loot overrides take priority. No client smoke test
was performed; the phase 2 core durability limitation remains deferred.

## Phase 5: verify and document the supported parity

- [x] Add focused unit coverage for reference kinds, snapshots, override precedence,
  aliases, validation, and generated resources.
- [x] Extend the existing CraftTweaker GameTest scripts with representative material,
  tool, copy, and loot scenarios. Run `./gradlew check` and package verification.
- [x] Check a launch without CraftTweaker and a real client launch with it installed.
  Verify names/models/textures and directional rendering separately from server tests.
- [x] Update examples, API documentation, and changelog. Record passed checks and
  unresolved client/runtime checks separately; leave unverified tasks unchecked.

Final phase 5 evidence: normal `./gradlew --offline check build` passed with
190 unit tests, 85 MMDLib GameTests, 52 companion GameTests, packaging and static
assets. Isolated real-client launches passed with CraftTweaker present and absent.
Generated names/textures/models and four horizontal front-face rotations were
asserted, and both rendered captures were inspected. The release JAR contains the
integration classes and excludes smoke classes/scripts/resources. See the
[verification report](crafttweaker-verification.md) for commands, captures and limits.

## Deferred features and reasons

- Food creation: food fields exist in the definition, but RuntimeProperties.item
  currently applies only stack size, durability, fire resistance, and remainder.
- Rarity, feature flags, general attribute configuration, lore, and new item actions:
  lack a complete applicable runtime path for this integration scope.
- Full CreativeTabBuilder, SoundEventBuilder, and custom SoundTypeFactory: would
  require a new registration capability or a parallel integration-owned system.
- State-dependent light/map color, spawning/suffocation/view/emissive predicates,
  piston reaction, instrument, offsets, and solidity flags: need new core property
  representation/application. `factoryData` alone cannot make these work.
- Independent tool-tier factories and arbitrary vanilla/modded object cloning:
  cannot be represented faithfully through current material/module settings.
- Inventory tick, tooltip, crafting, pickup/toss, entity-inside, and fall hooks:
  require additional event contracts or runtime overrides.

## Source anchors

- [Current scripting API](crafttweaker.md)
- [Runtime property application](../src/main/java/zone/moddev/mc/lib/common/runtime/RuntimeProperties.java)
- [Material runtime settings](../src/main/java/zone/moddev/mc/lib/common/types/MaterialRuntimeSettings.java)
- [Material stats](../src/main/java/zone/moddev/mc/lib/common/runtime/MaterialStats.java)
- [Standard factories](../src/main/java/zone/moddev/mc/lib/common/runtime/StandardImplementations.java)
- [Public extension API](../src/main/java/zone/moddev/mc/lib/api/MaterialExtensions.java)
- [Script resource generation](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptMaterialResources.java)
- [ContentTweaker builders](https://github.com/CraftTweaker/ContentTweaker/tree/049253e/vanilla/src/api/java/com/blamejared/contenttweaker/vanilla/api/zen/builder)
- [ContentTweaker references](https://github.com/CraftTweaker/ContentTweaker/blob/049253e/core/src/api/java/com/blamejared/contenttweaker/core/api/zen/object/Reference.java)
