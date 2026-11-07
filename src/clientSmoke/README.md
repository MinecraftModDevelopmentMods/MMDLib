# CraftTweaker real-client smoke fixtures

These sources are excluded from normal source sets. Only
`scripts/crafttweaker-smoke.init.gradle` injects the Java smoke class into main for
a development launch. Run a normal `./gradlew --offline check build` afterward to
restore normal outputs and verify no smoke classes reach the release JAR.

The present-mod run copies only this directory's scripts to an isolated build
folder. It checks startup registrations, English labels, baked model/texture
availability, and front-face model rotation for four horizontal facings. It opens
an integrated world and captures actual world rendering and an item/name gallery.
The absent-mod run copies no scripts and checks the script-only item is absent,
then creates an integrated world and exits successfully. Both runs use a bounded
timeout and explicit pass/fail log markers.

Texture provenance (test fixtures only):

- `client_gate.png`: copied from MMDLib's existing copper_block.png.
- `client_gate_front.png` and `client_gate_top.png`: vanilla Minecraft 1.21.1
  furnace_front.png and furnace_top.png, extracted from cached Minecraft resources.
- `client_widget.png`: MMDLib's existing grayscale gear template; see
  src/main/resources/mmdlib_templates/provenance.json for its Base Metals source.

The distinct front/top/side textures make directional rotation visible. These
fixtures are not intended as production artwork or additional release content.
