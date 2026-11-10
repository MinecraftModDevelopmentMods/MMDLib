# Machine companion

The optional `mmdlib_companion` mod contains six playable machine examples.
It uses Minecraft 1.21.1, NeoForge 21.1.247 and this branch's MMDLib. Its classes and resources live
in `src/companion` and are packaged separately from the library.

## Play

Run `./gradlew runCompanionClient`, create a creative world, and open the **MMDLib Machines** tab.

For a personal window size, add `mmdlibClientWidth=2560` and `mmdlibClientHeight=1440` to
`~/.gradle/gradle.properties` (or the file in your `GRADLE_USER_HOME`). These optional settings
apply to `runClient` and `runCompanionClient` only and stay outside the repository. Without them,
Minecraft uses its default window size. Automated smoke runs retain their own fixed dimensions.

Place a **Creative Power Source** next to a machine, then right-click the machine to open its screen.

Open the Creative Power Source and use its right-hand side configuration tab to configure each
output face. Its screen has only side configuration, Info and Statistics panels, with no player
inventory, equipment drawers or addon/filter slots.
Machines face the player when placed. Left/right in the face grid are viewed from that front,
so neighbor icons and side controls follow the same orientation. Rotation and mirroring update
that facing. Blocks placed before facing was recorded keep the north default; replace them to
choose their front from placement. Walking around a machine does not change its configured front.

Faces cycle between Output and Disabled. Disabled faces block both automatic delivery and
external extraction; settings survive reloads. Existing sources start with all six faces enabled.

JEI and Jade are included in playable companion development runs. Add `-PwithoutJei`
to run without JEI; Jade remains installed. Neither mod is bundled into the companion jar.
Mock server GameTests exclude Jade and Curios because their fake connections cannot negotiate
those mods' login payloads; real client smoke runs include them.

| Block ID (prefix `mmdlib_companion:`) | What it does |
| --- | --- |
| `crusher` | One input slot, two output slots, one processor, and a water/lava preview tank |
| `transmuter` | One slot limited to one item; converts an iron ingot into a gold ingot |
| `dual_crusher` | Two independently pausable processors share the same input and output slots |
| `logistics` | Seven independent resource groups for testing automation, filters and side configuration; no recipe processing |
| `creative_power` | Refills its buffer and supplies up to 1,000 FE per enabled adjacent face per tick |
| `simple_crusher` | XML-only definition with inherited power, generated screen contents, and tag-generated ore-to-ingot recipes; in the **Data-defined Machines** tab |

The crusher recipes take 100 ticks and 2,000 FE per cycle: cobblestone becomes gravel, gravel becomes
sand, and copper/iron/gold ore becomes two of the corresponding raw material. The converter takes
200 ticks and 4,000 FE. The dual crusher runs the same recipes in both lanes when there are enough
inputs and output space.

SimpleCrusher converts one ore to two ingots. Its `.10` hardness scaling increases
the base 100 ticks / 2,000 FE to 130 ticks / 2,600 FE for regular iron ore.
Its recipe generator joins `c:ores/<metal>` and `c:ingots/<metal>` tags, including
metals added by other mods. It has no companion Java block or entity class. See
[XML machines](xml-machines.md) for the complete definition and generator contract.
For focused verification of this example, run `./gradlew runSimpleCrusherClientSmoke`.
It opens the generated screen, checks the model and synchronized recipes, reloads
server resources, and saves before/after screenshots under
`build/simple-crusher-client-smoke/screenshots/`.

The Crusher's two outputs share one inventory and colour/side configuration. For testing, both
allow insertion and extraction, so you can manually fill them with any item; reservations still
protect promised output space. New Crushers default this group to input/output on all faces. Outputs may
split across the slots: with 63 raw iron in each output, one iron ore reserves `+1` in each slot
and finishes at 64 + 64. With only one free space total, that two-item recipe waits without taking
input or energy. Existing Crusher saves retain their input and first output, with the new output
starting empty. The Dual Crusher keeps its existing shared single-output layout.

The bottom of each processor panel has a compact, right-aligned row of icon buttons above the inventory,
with thin dark borders and flat grey backgrounds.
The generic processor button shows its **current** state: pause when manually paused, stop while
working or held, play while idle. Tooltips distinguish waiting for power, blocked completion and
host holds. Click to unpause, pause and release reservations, or pause idle work, respectively.
The pause tooltip explicitly says progress is kept. The Dual Crusher targets each lane explicitly;
pausing one releases only its share. Single-processor machines inherit a keyless button from the base.

Redstone face settings control whether processing may run. A redstone hold keeps progress and
reservations; manual pause takes priority and is never undone by a signal change. With input
watching configured, the drawer icon adds a green play or yellow pause overlay for the resolved
gate. A small red dot on a processor button marks disagreement with manual pause; its compact
multiline tooltip explains which setting prevents work. Settings and manual pause survive saves.
See [redstone face controls](gui-screens.md#redstone-face-drawer) for the per-face modes and ranges.

Every screen inherits the yellow Info popout from `mmdlib:base_powered_machine`. Its translation is
`{machineId}.info`, such as `mmdlib_companion:transmuter.info`, plus dynamic warnings from the base
entity. The base reports processor power stalls, blocked finishes and host holds; Crusher can also
report its configured energy reserve. Click the icon to close the drawer; scroll longer text.
All six screens place a vertical battery on the right using `<energy-gauge>`.
Its red charge bars fill upwards inside a slim casing.
The energy and fluid gauges share matching edges and medium-grey empty backgrounds, darker than slots.
Energy bars are thin with a soft reflection; cylindrical glass shading covers only the fluid.
The Crusher centers its input, a circular progress dial and two adjacent output slots. The dial is a
custom widget registered by the companion through MMDLib's [widget API](gui-screens.md#registering-custom-widget-types).
Click the dial to pause or resume; its tooltip shows progress in ticks. The Crusher's Recipes
button opens crushing recipes when JEI is installed. The other processors retain their progress
arrows, which open recipes when clicked with JEI installed. Hover the energy display for stored
and maximum FE.

The Crusher's **Settings** button opens three examples of mod-defined interactive widgets:

- **Machine name:** type up to 32 characters and choose Apply, or Cancel to restore the last
  confirmed value. Empty names are rejected. The draft survives resizing; leaving Settings
  discards an unapplied draft.
- **Energy reserve:** drag to preview a percentage and release to save. The processor holds
  while stored energy is below that percentage. Scroll or use arrow keys for one-point changes,
  Shift+arrow for ten points, or Home/End for 0/100. The default is zero.
- **Lane A:** choose Run or Pause. The action sends the lane and desired state together; the
  server validates both.

Tab cycles through the editors; Escape cancels an edit or focus before returning to the machine
view. Name and reserve survive save/load. The companion's `client` package contains the input
implementations, while `CompanionWidgets` owns their common-side validation and typed actions.

The Crusher also has a **4,000 mB (four-bucket) tank** on the left. Right-click the block with a
water or lava bucket to fill it, or an empty bucket to drain it; open the screen with an empty hand
to see the fluid's animated texture. Inside the screen, pick up a fluid container onto the cursor
and left-click the tank gauge to pour into it, or right-click to fill the container from it.
This works with buckets and modded items exposing NeoForge's fluid-container capability, including
partial transfers. One container is handled per click; when carrying a stack, the resulting container
goes into your inventory (or drops beside you if it is full).
In creative mode the held or cursor container stays unchanged while fluid
transfers. Fluid pipes can fill and drain on any face. The tank holds one fluid at a time: drain it
fully before switching between water and lava. Rejected bucket transfers leave the contents intact.
The tank saves with the machine and is only a display example; crushing recipes do not consume it.
Breaking the machine discards its fluid.

The Logistics Workbench's four gauges and the generated Simple Lava Furnace gauge use the same
library interaction. Only the clicked tank is affected. Players can drain input tanks; output
tanks refuse insertion, and recipe reservations protect both fluid and output space.

Hoppers and pipes obey each group's configured side modes and storage roles. Recipe-linked
inputs accept only ingredients allowed by compatible recipes; decorative tanks retain their own
filters. Recipe reloads update those admission rules. The
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
# Optional full graphical check; normally covered by CI:
./gradlew runCompanionClientSmoke
```

Install both `build/libs/mmdlib-<version>.jar` and
`build/companion/libs/mmdlib-companion-<version>.jar` in a NeoForge instance. JEI is optional.
Both jars use the build's version (`2.0.0.121012-development` by default, or the release tag).
`companionJar` contains only the companion; `build` continues to build the library.
The companion output is separate from the library's CI/release upload directory and Maven
publication. Release uploads select only the exact library, sources and Javadoc filenames for
the tag. Run `./gradlew clean` when updating a checkout that still has companion jars in
`build/libs`; packaging verification rejects those stale jars.

`check` includes the companion server game tests and checks both jars for leaked classes and
resources. `runCompanionGameTestServer` runs just those tests. They cover all five registrations and
menus, recipe loading, adjacent power, the one-slot limit and interruption through the menu, two
shared lanes across save/load, command replacement versus destruction, tank capacity and persistence,
refusal to mix fluids, bucket filling/draining through the block interaction, and cursor-container
transfers through the screen (including stacked buckets and partial modded containers). Shift-click
regressions check chunk save flags and saved item counts after merging into idle machines and
partially extracting their output, plus a rejected transfer that must leave the save flag unchanged.
Redstone tests cover all modes on all three processors, signal changes, independent manual pause,
retained reservations, saved settings, and safe defaults for older or invalid saves.

`runCompanionClientSmoke` creates a separate test world under `build/companion-client-smoke`, opens
all five menus over the network and captures them under its `screenshots` directory. It is separate
from `check` because it needs a graphical client. It also accepts `-PwithoutJei`.
The shared `build_status.yml` workflow runs `check`, builds both jars, and runs the companion
and library GUI smoke tests. The library smoke is exercised with and without JEI.
The Crusher capture shows water; `crusher-lava.png` verifies a live change to lava in the same menu.
It also clicks the gauge to drain a lava bucket and pour it back, verifying cursor and tank sync.
The client smoke also clicks pause and redstone controls and waits for their synchronized states;
`crusher-paused.png` and `crusher-redstone.png` capture their alternate icons.

All five entities extend `BasePoweredMachine`, and all five XML screens extend the library base.
Creative Power and Logistics remove the inherited processor-control area with an empty fill.
Crusher replaces the body to retain its settings context. The shared Info popup is never duplicated
in a child screen; each machine supplies its own language entry.

Stable slot/tank IDs preserve contents when definitions move. Displaced contents remain in recovery
and are handed to the companion's Recovery Capsule on removal; use the capsule on a machine to
restore items, fluids or power, retaining any remainder. Ordinary fluid and energy destruction is
still separate from displaced-content recovery. See [storage recovery](capabilities.md#stable-storage-identities-and-recovery).

For local edits, run focused XML/layout/processor tests and relevant server GameTests. Do not run
the full UI smoke suite after every edit or PR update; CI supplies full GUI coverage. Run it locally
when explicitly requested or when a broad regression needs it. The base regression fixture also
checks omitted power registration, common warnings, custom client packets and world-save isolation.

## Code to read

- `CompanionMod`: registers blocks, items, block-entity types, recipe types, capabilities and actions.
- `DemoBlock`: block creation, server ticking, menu opening and the contents-drop hook.
- `BasePoweredMachine` (in MMDLib): owned power, common ticking/persistence, warnings and client-sync hooks.
- `ProcessorEntity`: resource groups, recipe-linked inputs, shared lanes and machine-specific metadata.
- `CreativePowerEntity`: the external energy capability and adjacent transfer.
- `src/companion/resources/mmdlib/screen`: XML screens, bound to public getters at startup.
- `src/companion/resources/data/mmdlib_companion/recipe`: data-driven machine recipes.

The example registers its blocks in Java and uses MMDLib for processing, storage, screens and
recipe types. For data-defined blocks and factory binding, see [block entities](block-entities.md).
