# MMDLib

Data-driven material and machine library for **NeoForge 1.21.1 / Java 21**. Package root
`zone.moddev.mc.lib`. Docs are in `docs/`; start with
[docs/mod-author-guide.md](docs/mod-author-guide.md).

## Machine-related docs

| Topic | Doc | Code |
|---|---|---|
| How storage, capabilities, screens and recipes fit together | [machines](docs/machines.md) | |
| Block-entity definitions, handles, `mmdlib:machine` | [block entities](docs/block-entities.md) | `api`, `common.runtime` |
| `ItemStorage`, `FluidStorage`, `EnergyBuffer`, `SideConfig`, `MachineCapabilities`, `Transfer` | [capabilities](docs/capabilities.md) | `api.capabilities` |
| XML screens, `ScreenHost`, menu, sync, JEI | [GUI screens](docs/gui-screens.md), [sprites](docs/gui-sprites.md), [gauges](docs/gauge-textures.md) | `gui.*`, `client.jei` |
| `gui-click` / `gui-hover` and other handlers | [method events](docs/method-events.md) | |
| `mmdlib:crushing` | [runtime behaviours](docs/runtime-behaviors.md) | `common.recipes` |
| Proposed machine module: ingredients, recipes, transactions, JSON-only machines, 26.x/Fabric plan | [machine module](docs/machine-module/README.md) | not built |

## Facts that are easy to get wrong

- MMDLib has no machine-ingredient or machine-recipe abstraction; recipes are vanilla `Recipe`
  types with vanilla `Ingredient`s, and processing is the mod's own ticker. Everything under
  `docs/machine-module/` is a design; none of its types exist yet.
- Every face exposes every slot with the same numbering; `IoMode` and `Role` only allow or refuse
  operations. Out-of-range indexes are refused, not thrown.
- The block entity bypasses roles and rates (`insertResult`, `tank(i)`, `consume`); automation
  goes through `view(...)`.
- Screens show values only through public getters named in the XML; the client never calls them.
- `ScreenHost.screenSides()` keys are the element ids in `<side-config groups>`; combining several
  group `SideConfig`s into one capability view is the mod's job.
- Format with `./gradlew format` (needs `tools/gjf.jar`); `./gradlew check` runs the regression
  checks.
