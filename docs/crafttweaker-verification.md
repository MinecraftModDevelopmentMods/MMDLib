# CraftTweaker integration verification

Verification of phases 1–5 on the current working tree, 7 November 2026.
No independent core feature extensions are included. The previously identified
TieredItem durability-scaling limitation remains deferred.

## Reproduce

```sh
LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe xvfb-run -a ./gradlew --offline runClient -I scripts/crafttweaker-smoke.init.gradle
LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe xvfb-run -a ./gradlew --offline runClient -I scripts/crafttweaker-smoke.init.gradle -PwithoutCraftTweaker
./gradlew --offline check build
```

The smoke initializer uses separate build/ct-smoke-present and
build/ct-smoke-absent directories; it does not touch a normal development world or
scripts folder. It injects only src/clientSmoke/java for the development launch.
Always run normal verification afterward to restore main output and prove test
classes are absent from the release JAR. The test task rejects a missing pass
marker or any MMDLIB_CT_CLIENT_FAIL marker.

Software rendering was needed here: the native WSL graphics path crashed in the
system loader before mod loading. The retry used llvmpipe/Mesa under Xvfb; these
results do not validate a particular hardware GPU driver.

## Client evidence

The installed-mod run passed its assertions and opened an integrated world:

- Startup script content registered on client and integrated server.
- Material English label and standalone display-name override matched.
- Five representative item models and their particle sprites resolved.
- Each horizontal gate facing selected the front texture on the matching face.
- Actual rendered captures were inspected: colored material ingot/pickaxe/block,
  named widget, gate item, and four rotated gate blocks with distinct side/front/top.

Local captures (generated build artifacts, not release resources):

- [Item and name gallery](../build/ct-smoke-present/screenshots/ct-items-and-names.png)
- [Directional world scene](../build/ct-smoke-present/screenshots/ct-directional-world.png)

Client logs are under each smoke directory's logs/latest.log. Inspect
MMDLIB_CT_CLIENT_PASS and the actual mod list when reproducing. The absent-mod run
must report CraftTweaker installed=false and exclude script-only content.

## Automated coverage

The earlier full phase 4 check passed 190 unit tests, 85 MMDLib GameTests and
52 companion GameTests, plus release/companion packaging and static assets.
Coverage includes typed IDs and malformed references, module configuration/tag and
callback snapshots, same-run tag inheritance, forward item remainder resolution,
material/tool settings, fluent/property equivalence and validation, generated names
and loot, actual player block breaks, and folder-pack loot override precedence.

Executable examples:

- [Material and tool settings](../src/runtimeTest/scripts/mmdlib_tools.zs)
- [Typed references and copies](../src/runtimeTest/scripts/mmdlib_references.zs)
- [Fluent builders and loot](../src/runtimeTest/scripts/mmdlib_resources.zs)
- [Real-client resource fixture](../src/clientSmoke/scripts/content.zs)

The client fixture is representative rather than an exhaustive visual check of
every material part. English names are tested; other locales are authored through
normal resource overrides. Captured variables inside copied script functions remain
shared, while callback IDs/maps are independent. Reference wrappers preserve the
existing runtime's dependency-resolution restrictions.

## Final result

Both client launches passed: installed=true and installed=false. The present-mod
captures linked above were visually inspected. Normal `./gradlew --offline check
build` then passed: 190 unit tests, 85 MMDLib GameTests, 52 companion GameTests,
static assets and packaging. Inspection of
`build/libs/mmdlib-2.0.0.121012-development.jar` confirmed that the integration
classes are included and smoke classes, fixture scripts and fixture resource IDs
are absent. No commit or remote publication was performed.

## Shared crushing recipe follow-up

The separate recipe integration is documented in [crushing recipe scripting](crafttweaker-crusher.md).
Normal `check build` passed with 190 unit tests, 89 MMDLib GameTests and 52
companion GameTests. The ordinary recipe fixture covers add/query/remove/replace,
JSON additions, tag ingredients, invalid arguments, and crackhammer consumption.
The client smoke now reloads server resources and verifies recipes are reapplied
without duplicate IDs; both installed and absent-mod client launches passed.
Release JAR inspection confirmed the two crusher integration classes are included
and smoke classes/scripts remain excluded.
