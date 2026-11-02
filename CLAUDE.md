# MMDLib

Data-driven material and machine library for **NeoForge 1.21.1 / Java 21**. Package root
`zone.moddev.mc.lib`. Docs are in `docs/`; start with
[docs/mod-author-guide.md](docs/mod-author-guide.md). Machine docs, design and testing notes are
in the private `mod-design` repo, checked out next to this one.

New design docs go in `../mod-design/mmdlib/docs/`, not here.

## Machine-related docs

| Topic | Doc | Code |
|---|---|---|
| How storage, capabilities, screens and recipes fit together | [machines](../mod-design/mmdlib/docs/machines.md) | |
| Block-entity definitions, handles, `mmdlib:machine` | [block entities](docs/block-entities.md) | `api`, `common.runtime` |
| `ItemStorage`, `FluidStorage`, `EnergyBuffer`, `SideConfig`, `MachineCapabilities`, `Transfer` | [capabilities](docs/capabilities.md) | `api.capabilities` |
| XML screens, `ScreenHost`, menu, sync, JEI | [GUI screens](docs/gui-screens.md), [sprites](docs/gui-sprites.md), [gauges](docs/gauge-textures.md) | `gui.*`, `client.jei` |
| `gui-click` / `gui-hover` and other handlers | [method events](docs/method-events.md) | |
| `mmdlib:crushing` | [runtime behaviours](docs/runtime-behaviors.md) | `common.recipes` |
| Machine module: internals, ingredients, groups, transactions, matching (stage 1, built) | [stage 1](../mod-design/mmdlib/docs/machine-module/stage-1.md) | `machine.*` |
| Machine module design: recipe type, processor, JSON-only machines, UI, 26.x/Fabric plan | [machine module](../mod-design/mmdlib/docs/machine-module/README.md) | not built |

## Facts that are easy to get wrong

- The machine module so far is stage 1 only ([stage 1](../mod-design/mmdlib/docs/machine-module/stage-1.md)):
  `MachineRecipe` is a data shape that `RecipeMatcher` checks, not a registered recipe type, and
  there is no processor; `mmdlib:crushing` is still a plain vanilla `Recipe`. Everything else
  under `../mod-design/mmdlib/docs/machine-module/` is design.
- Only `machine/neoforge` may import NeoForge (`verifyMachineImports`). Machine part files live in
  `data/<ns>/mmdlib/machine_part/`; MMDLib ships none.
- Every face exposes every slot with the same numbering; `IoMode` and `Role` only allow or refuse
  operations. Out-of-range indexes are refused, not thrown.
- The block entity bypasses roles and rates (`insertResult`, `tank(i)`, `consume`); automation
  goes through `view(...)`.
- Screens show values only through public getters named in the XML; the client never calls them.
- `ScreenHost.screenSides()` keys are the element ids in `<side-config groups>`; combining several
  group `SideConfig`s into one capability view is the mod's job.
- Format with `./gradlew format` (needs `tools/gjf.jar`); `./gradlew check` runs the regression
  checks.
