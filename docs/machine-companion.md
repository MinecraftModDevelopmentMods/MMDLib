# Machine companion

The optional `mmdlib_companion` mod contains four playable examples of the machine processor.
It uses Minecraft 1.21.1, NeoForge 21.1.247 and this branch's MMDLib. Its classes and resources live
in `src/companion` and are packaged separately from the library.

## Play

Run `./gradlew runCompanionClient`, create a creative world, and open the **MMDLib Machines** tab.
Place a **Creative Power Source** next to a machine, then right-click the machine to open its screen.
The source supplies all six adjacent faces. JEI is included in development runs; add `-PwithoutJei`
to run without it.

| Block ID (prefix `mmdlib_companion:`) | What it does |
| --- | --- |
| `crusher` | One input slot, one output slot, one processor |
| `transmuter` | One slot limited to one item; converts an iron ingot into a gold ingot |
| `dual_crusher` | Two independently pausable processors share the same input and output slots |
| `creative_power` | Refills its buffer and supplies up to 1,000 FE per adjacent face per tick |

The crusher recipes take 100 ticks and 2,000 FE per cycle: cobblestone becomes gravel, gravel becomes
sand, and copper/iron/gold ore becomes two of the corresponding raw material. The converter takes
200 ticks and 4,000 FE. The dual crusher runs the same recipes in both lanes when there are enough
inputs and output space.

Check **Paused (free items)** to interrupt a lane and release its reservations. Uncheck it to resume.
This control works without power. The dual crusher releases only the chosen lane's share; the other
lane can continue. A warning tab reports a power stall or a blocked finish. Hover the energy meter
for stored and maximum FE; click the first progress arrow to see recipes when JEI is installed.

Hoppers and pipes can insert into input slots and extract from output slots on any face. The
converter's single slot permits both operations, subject to reservations. Its capacity of one is
deliberate: leftover iron would prevent a gold product from fitting. Remove the gold before adding
the next iron. Because the converter shares one slot for input and output, an extracting hopper can
also remove unreserved iron before a cycle starts.

The machines have creative-tab entries and can be obtained with `/give`; this demonstration mod
does not provide crafting recipes for the machines. Their models reuse Minecraft textures. Break
them with a stone-tier or better pickaxe to recover the blocks. Inventory contents drop on removal,
including reserved inputs. Normal `/setblock` and `/fill` replacement clears contents; their
`destroy` mode drops them.

## Build and verify

```sh
./gradlew build companionJar
./gradlew check
./gradlew runCompanionClientSmoke
```

Install both `build/libs/mmdlib-2.0.0-alpha.jar` and
`build/libs/mmdlib-companion-2.0.0-alpha.jar` in a NeoForge instance. JEI is optional.
`companionJar` contains only the companion; `build` continues to build the library.

`check` includes the companion's six server game tests and checks both jars for leaked classes and
resources. `runCompanionGameTestServer` runs just those tests. They cover all four registrations and
menus, recipe loading, adjacent power, the one-slot limit and interruption through the menu, two
shared lanes across save/load, and command replacement versus destruction.

`runCompanionClientSmoke` creates a separate test world under `build/companion-client-smoke`, opens
all four menus over the network and captures them under its `screenshots` directory. It is separate
from `check` because it needs a graphical client. It also accepts `-PwithoutJei`.

## Code to read

- `CompanionMod`: registers blocks, items, block-entity types, recipe types, capabilities and actions.
- `DemoBlock`: block creation, server ticking, menu opening and the contents-drop hook.
- `ProcessorEntity`: storages, group keys, shared lanes, saving after storages, and feature sync.
- `CreativePowerEntity`: the external energy capability and adjacent transfer.
- `src/companion/resources/mmdlib/screen`: XML screens, bound to public getters at startup.
- `src/companion/resources/data/mmdlib_companion/recipe`: data-driven machine recipes.

The example registers its blocks in Java and uses MMDLib for processing, storage, screens and
recipe types. For data-defined blocks and factory binding, see [block entities](block-entities.md).
