# MMDLib build scripts

The root build owns identity, toolchains, Forge mappings, development launches,
resource processing and archive creation. Supporting scripts are grouped by
purpose:

- `dependencies.gradle` defines mapped compile-only APIs and original fixture
  artifacts from `legacy-dependencies.json`.
- `stage-legacy-dependencies.py` stages checksum-verified originals for local
  and hosted builds; the shell wrapper is used by GitHub Actions.
- `compatibility.gradle` generates compiler exports for the old Java namespace
  and verifies them against `verification/legacy-api-baseline.json`.
- `verification/support.gradle` checks toolchains, target metadata,
  dependencies, JSON resources and the production source baseline.
- `verification/runtime.gradle` builds test-only probes and runs bounded
  packaged-server and opt-in client acceptance.
- `release/artifacts.gradle` audits jar identity, Java 8 bytecode,
  reobfuscation, package isolation and deterministic checksums.
- `release/publishing.gradle` publishes audited or prepared artifacts using
  explicitly supplied Maven credentials.
- `ide/eclipse.gradle` configures Buildship, quotes paths containing spaces,
  and excludes optional APIs and test code from normal launches.
- `verification/workflows.gradle` checks action pinning and required CI/release
  gates.

Shared script contracts use small immutable maps. Keep task names stable
because the workflows call them directly.

## Packaged server acceptance

Prepare empty runtime directories using Java 8:

```text
bash gradle/prepare-packaged-forge-runtime.sh /runtime/forge2847 /java8/bin/java
bash gradle/prepare-packaged-forge-runtime.sh /runtime/forge2859 /java8/bin/java compat
./gradlew packagedForgeRuntimeTest -PpackagedForgeRuntimeRoot=/runtime/forge2847 -PpackagedForgeCompatRuntimeRoot=/runtime/forge2859 -PlegacyDependencyVerificationRepository=/dependency-mirror
```

Each profile has a fresh and reload phase, a 240-second process limit, a saved
world and an explicit acceptance marker. Each marker also requires
`legacy-api=passed` and `canonical-namespace=zone.moddev.mc.mmdlib`.
Profiles cover the standalone library
on both Forge 2847 and Forge 2859,
Base Metals/OreSpawn, Tinkers/Construct's Armory, Mekanism/Tesla/CoFH/CraftTweaker,
standalone IC2, and Thaumcraft/Baubles. Worlds live under `build/`.

IC2 is qualified separately: combining IC2 2.8.73 with the full machine stack
causes a bronze-recipe conflict with both the historical published MMDLib and
this candidate. This migration preserves that existing behavior.

## Packaged client acceptance

First build and audit the Forge 2847 candidate. On a machine with a graphics
display, launch these profiles sequentially; Linux CI can use `xvfb-run`:

```text
./gradlew runClient -PmmdLibClientSmoke=Library -PmmdLibRunDirectory=build/client-library-run -PpackagedForgeCompatRuntimeRoot=/runtime/forge2859 -PpackagedClientAssetsRoot=/minecraft/assets -PlegacyDependencyVerificationRepository=/dependency-mirror
./gradlew runClient -PmmdLibClientSmoke=BaseMetals -PmmdLibRunDirectory=build/client-basemetals-run -PpackagedForgeCompatRuntimeRoot=/runtime/forge2859 -PpackagedClientAssetsRoot=/minecraft/assets -PlegacyDependencyVerificationRepository=/dependency-mirror
./gradlew runClient -PmmdLibClientSmoke=Integrations -PmmdLibRunDirectory=build/client-integrations-run -PpackagedForgeCompatRuntimeRoot=/runtime/forge2859 -PpackagedClientAssetsRoot=/minecraft/assets -PlegacyDependencyVerificationRepository=/dependency-mirror
./gradlew runClient -PmmdLibClientSmoke=IC2 -PmmdLibRunDirectory=build/client-ic2-run -PpackagedForgeCompatRuntimeRoot=/runtime/forge2859 -PpackagedClientAssetsRoot=/minecraft/assets -PlegacyDependencyVerificationRepository=/dependency-mirror
```

Smoke launches use the already audited main jar, exclude IDE production output,
load the specified optional original jars, create an integrated world, write
`mmdlib-client-probe.properties`, and exit. The client process has a four-minute
limit. A successful Gradle exit must be accompanied by the marker and a saved
`saves/mmdlib-smoke/level.dat` before calling acceptance complete. Use distinct
run directories to avoid retaining mods from another profile.

These opt-in launches use production Forge 2859, vanilla Minecraft classes,
and the original SRG integration jars, including their access transformers.
They keep the candidate's compile target pinned to Forge 2847. Assets must
already include the Minecraft `1.12` index and its objects. The vanilla client
defaults to Mavenizer's downloaded `minecraft_tasks/1.12.2/client.jar` in the
selected Gradle cache; override it with `-PpackagedClientVanillaJar=/path/client.jar`
when using another launcher cache. Native libraries come from the resolved
Forge client dependency classpath and are extracted into the disposable run.

## Production baseline

`verification/source-baseline.json` preserves the upstream production inventory
at `30b73053366f8bd1ba6599c35081f4aa26afc06e`, normalizing text line endings and
the package relocation, entry point version constant, resource class pointers,
and two integration annotation queries that now accept both namespaces.
Its Java hashes also include the reviewed comment and Javadoc corrections.
Those edits were checked to retain exactly the same executable Java tokens;
the production inventory and resource hashes were preserved. Normal builds
compare complete normalized file hashes, including comments, to catch further
accidental implementation and resource changes.
`verification/legacy-api-baseline.json` separately freezes every old class,
superclass, interface, public/protected descriptor, generic signature and
constant value. An intentional
future gameplay change must update the baseline and its own regression tests;
do not silently weaken this guard.

## Java namespace compatibility

Production Java sources are under `src/main/java/zone/moddev/mc/mmdlib`.
`generateLegacyApiExports` builds old compiler signatures and an exact alias
inventory from those classes. API generation is a dependency of `classes` and
`processResources`, so jars and Eclipse development output receive the same
exports and alias resource. The generator in `src/buildSupport` is never
included in release jars.

`compileLegacyRuntimeTestJava` compiles the acceptance consumer against the
checksum-pinned historical `1.0.0-rc2.36` jar, without new implementation output
on its compiler classpath. `compileLegacySourceTestJava` compiles that same
source against the new exports. Packaged runtime probes prove that the old
compiled consumer links to the canonical implementation.

The frozen legacy API baseline was captured from the audited pre-relocation
deobf candidate. Only a deliberate API inventory update should use
`captureLegacyApiBaseline -PlegacyApiBaselineJar=/verified/old-deobf.jar`;
normal builds must never recapture their own baseline.

Normal development/Eclipse launches explicitly load
`zone.moddev.mc.mmdlib.compat.LegacyNamespaceBootstrap` using
`fml.coreMods.load`, since their merged output directory has no jar manifest.
Eclipse launches also include the generated legacy class and alias resource
directories, exclude build-only/test output, and show the legacy fixture source
folder once despite its two Gradle compiler acceptance tasks.
Production launches load it from the release manifest. Details are in
[`docs/NAMESPACE-COMPATIBILITY.md`](../docs/NAMESPACE-COMPATIBILITY.md).
