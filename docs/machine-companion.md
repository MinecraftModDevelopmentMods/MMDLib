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
| `crusher` | One input slot, one output slot, one processor, and a water/lava preview tank |
| `transmuter` | One slot limited to one item; converts an iron ingot into a gold ingot |
| `dual_crusher` | Two independently pausable processors share the same input and output slots |
| `creative_power` | Refills its buffer and supplies up to 1,000 FE per adjacent face per tick |

The crusher recipes take 100 ticks and 2,000 FE per cycle: cobblestone becomes gravel, gravel becomes
sand, and copper/iron/gold ore becomes two of the corresponding raw material. The converter takes
200 ticks and 4,000 FE. The dual crusher runs the same recipes in both lanes when there are enough
inputs and output space.

Check **Paused (free items)** to interrupt a lane and release its reservations. Uncheck it to resume.
This control works without power. The dual crusher releases only the chosen lane's share; the other
lane can continue. The yellow info tab opens a matching drawer to the left with machine guidance and
any current power-stall or blocked-finish warnings. Click the icon again to close it; scroll
inside to read longer text. All four screens place a vertical battery on the right, using the
reusable `<energy-gauge>` XML widget. Its red charge bars fill upwards inside a slim casing.
The energy and fluid gauges share matching edges and medium-grey empty backgrounds, darker than slots.
Energy bars are thin with a soft reflection; cylindrical glass shading covers only the fluid.
The Crusher centers its input, progress arrow and output alongside it.
Hover the energy display
for stored and maximum FE; click the first progress arrow to see recipes when JEI is installed.

The Crusher also has a **4,000 mB (four-bucket) tank** on the left. Right-click the block with a
water or lava bucket to fill it, or an empty bucket to drain it; open the screen with an empty hand
to see the fluid's animated texture. Inside the screen, pick up a fluid container onto the cursor
and click the tank gauge to transfer: it tries pouring into the tank first, then filling the container.
This works with buckets and modded items exposing NeoForge's fluid-container capability, including
partial transfers. One container is handled per click; when carrying a stack, the resulting container
goes into your inventory (or drops beside you if it is full).
In creative mode the held or cursor container stays unchanged while fluid
transfers. Fluid pipes can fill and drain on any face. The tank holds one fluid at a time: drain it
fully before switching between water and lava. Rejected bucket transfers leave the contents intact.
The tank saves with the machine and is only a display example; crushing recipes do not consume it.
Breaking the machine discards its fluid.

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

Install both `build/libs/mmdlib-<version>.jar` and
`build/libs/mmdlib-companion-<version>.jar` in a NeoForge instance. JEI is optional.
Both jars use the build's version (`2.0.0.121012-development` by default, or the release tag).
`companionJar` contains only the companion; `build` continues to build the library.

`check` includes the companion's eleven server game tests and checks both jars for leaked classes and
resources. `runCompanionGameTestServer` runs just those tests. They cover all four registrations and
menus, recipe loading, adjacent power, the one-slot limit and interruption through the menu, two
shared lanes across save/load, command replacement versus destruction, tank capacity and persistence,
refusal to mix fluids, bucket filling/draining through the block interaction, and cursor-container
transfers through the screen (including stacked buckets and partial modded containers). Shift-click
regressions check chunk save flags and saved item counts after merging into idle machines and
partially extracting their output, plus a rejected transfer that must leave the save flag unchanged.

`runCompanionClientSmoke` creates a separate test world under `build/companion-client-smoke`, opens
all four menus over the network and captures them under its `screenshots` directory. It is separate
from `check` because it needs a graphical client. It also accepts `-PwithoutJei`.
The shared `build_status.yml` workflow runs `check`, builds both jars, and runs the companion
and library GUI smoke tests. The library smoke is exercised with and without JEI.
The Crusher capture shows water; `crusher-lava.png` verifies a live change to lava in the same menu.
It also clicks the gauge to drain a lava bucket and pour it back, verifying cursor and tank sync.

## Code to read

- `CompanionMod`: registers blocks, items, block-entity types, recipe types, capabilities and actions.
- `DemoBlock`: block creation, server ticking, menu opening and the contents-drop hook.
- `ProcessorEntity`: storages, group keys, shared lanes, saving after storages, and feature sync.
- `CreativePowerEntity`: the external energy capability and adjacent transfer.
- `src/companion/resources/mmdlib/screen`: XML screens, bound to public getters at startup.
- `src/companion/resources/data/mmdlib_companion/recipe`: data-driven machine recipes.

The example registers its blocks in Java and uses MMDLib for processing, storage, screens and
recipe types. For data-defined blocks and factory binding, see [block entities](block-entities.md).
