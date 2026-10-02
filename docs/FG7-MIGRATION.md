# MMDLib 1.12 ForgeGradle 7 migration

This build migration starts at upstream `master-1.12` commit
`30b73053366f8bd1ba6599c35081f4aa26afc06e`. It follows the current Base Metals
1.12 build structure while preserving MMDLib's production logic and saved
identities. The implementation Java packages move to `zone.moddev.mc.mmdlib`
with compatibility for the old API.

## Identity and toolchains

The release coordinate is `zone.moddev.mc.mmdlib:MMDLib:1.0.0.112021`.
The old Maven group was `com.mcmoddev`; consumers adopting this candidate must
change their Maven coordinate. New Java consumers use `zone.moddev.mc.mmdlib`.
Generated compiler exports and a narrow Forge loading adapter preserve
`com.mcmoddev.lib` API consumers. The `mmdlib` mod ID, configuration and registry
names remain unchanged. A normalized inventory of 431 upstream production
files guards the relocation, version metadata and dual-namespace plugin
discovery scope in `verifySourceCompatibility`. It also includes the reviewed
comment and Javadoc corrections, verified to leave executable Java tokens
unchanged. A separate frozen inventory
guards all 500 old class APIs and public/protected signatures.

See [namespace compatibility](NAMESPACE-COMPATIBILITY.md) for the relinking
contract, compiler exports, reflective crafting factory bridges and acceptance
consumer compiled against the original published library.

| Component | Pinned version |
| --- | --- |
| Minecraft / compile Forge | 1.12.2 / 14.23.5.2847 |
| Compatibility runtime Forge | 14.23.5.2859 |
| Mappings | stable_39 for 1.12 |
| ForgeGradle / Renamer | 7.0.34 / 1.1.5 |
| Gradle wrapper | 9.6.1, distribution SHA-256 pinned |
| Gradle JVM | Temurin 17.0.1+12 |
| Mavenizer JVM | Temurin 25.0.3+9 |
| Compiler, Javadoc, tests and Minecraft JVM | Temurin 8.0.502+7 |

The historical gameplay coremod classes remain dormant. The release manifest's
`FMLCorePlugin` points only to
`zone.moddev.mc.mmdlib.compat.LegacyNamespaceBootstrap`; it installs the API
namespace translator before dependent mods link. Optional integrations remain
compile-only and are not embedded or required in a standalone runtime. The
VeinMiner API source already owned by this upstream tree is retained.

## Dependency recovery

`gradle/legacy-dependencies.json` records the 13 optional compile APIs and four
acceptance fixtures with exact versions, download URLs and SHA-256 checksums.
The staging helper verifies original bytes before placing them in an isolated
Maven mirror. Renamer maps those original jars for compilation; runtime tests
use the originals. Dependency declarations do not resolve transitive artifacts
from retired repositories.

The IC2 compile fixture is the original full `2.8.73-ex112` jar from the official
IC2 Maven repository. It replaces the missing historical API-only download
without enabling IC2 at runtime. Base Metals `2.6.0.112021`, OreSpawn
`4.1.0.112021`, published MMDLib `1.0.0-rc2.36`, and CoFHWorld `1.4.0.1` are
qualification fixtures only. No Base Metals checkout or dependency declaration
is changed by this migration.

## Release artifacts and gates

The build emits four deterministic artifacts:

- `MMDLib-1.0.0.112021.jar`: production classes remapped to SRG names.
- `MMDLib-1.0.0.112021-deobf.jar`: mapped development classes.
- `MMDLib-1.0.0.112021-sources.jar`: library source.
- `MMDLib-1.0.0.112021-javadoc.jar`: Java 8 Javadoc.

Main and development jars include old compiler API exports and the exact alias
inventory. Sources and Javadocs describe the canonical implementation.
`build/release/SHA256SUMS` covers all four. Audits require Java 8 bytecode,
consistent manifests and `mcmod.info`, the chosen Maven coordinate, the Forge
2847 compile target, no optional dependency or probe classes, and no local paths
or volatile timestamps in archive text. Archive entry order and line endings
are normalized. API documentation was reviewed alongside the comments, including
parameter names, implementation notes and references to moved classes.

CI validates wrapper integrity, a cold Forge bootstrap and offline rebuild,
existing tests, dependency/resource/source/artifact audits, reproducibility,
Eclipse launches and packaged-server profiles. CodeQL uses explicit uncached
compilation. Action references are pinned to complete commit hashes.

The manual release workflow checks the exact branch commit's required checks,
validates the version and any existing tag, then prepares an immutable audited
bundle. Publication needs explicit manual confirmation in the upstream
repository, Maven credentials and a CurseForge token. It uploads that bundle
to Maven and CurseForge project `261744`, then creates the GitHub Release.
A pushed tag does not publish by itself. This local receipt does not establish
hosted CI, CodeQL analysis or publication results; GitHub reports those for the
commit checked by each workflow.

## Runtime qualification

Packaged server probes create and reload disposable worlds with a 240-second
bound per phase. The library is checked on both Forge 2847 and 2859; integration profiles
use Forge 2859. Client probes use the production Forge 2859 launcher and the
audited SRG candidate, create an integrated world, verify loaded mod IDs and
the library version, save the world and exit within four minutes. Every profile
also runs a consumer compiled independently against old published MMDLib and
requires a passing legacy API marker. Shared enums and singletons, subclassing,
interfaces, lambdas, old plugin annotations, event callbacks and reflection-based
condition factory construction are exercised.

| Profile | Composition |
| --- | --- |
| Library | MMDLib alone |
| Base Metals | MMDLib, Base Metals and OreSpawn |
| Tinkers | Base Metals profile, Mantle, Tinkers' Construct, Construct's Armory |
| Machines | Base Metals profile, Mekanism, Tesla, Redstone Flux, CoFHCore, CoFHWorld, CraftTweaker |
| IC2 | Standalone library with IC2; client also has JEI and Hwyla |
| Thaumcraft | Base Metals profile, Thaumcraft and Baubles |
| Combined client integrations | All recovered APIs except IC2, plus Base Metals/OreSpawn/CoFHWorld |

IC2 `2.8.73-ex112` combined with the full Machines stack fails during
initialization with a bronze-recipe conflict involving `OreDict:ingotBronze`
and `ic2:dust#bronze`. The identical full stack fails with the original published
MMDLib `1.0.0-rc2.36`, establishing that this predates the build migration.
IC2 is qualified separately. This migration intentionally preserves the
existing integration behavior.

The production client launcher also avoids the MCP development environment's
access-transformer seam with the packaged OreSpawn fixture. See
[`gradle/README.md`](../gradle/README.md) for repeatable commands and required
runtime, client assets and toolchain inputs.

## Local qualification receipt

Qualified locally on Windows on 2 October 2026 with the pinned toolchains.

| Gate | Result |
| --- | --- |
| JSON parsing, `processResources`, `compileJava` | Pass |
| Existing and namespace tests, `check`, `build`, Javadoc | Pass; 39 tests, zero failures, errors or skips |
| Dependency, source compatibility, 500-class legacy ABI and release artifact audits | Pass |
| Old source/compiler compatibility | Pass against both the historical published library and new compiler exports |
| Eclipse generation and production classpath isolation | Pass |
| Maven POM generation and prepared-bundle checksum verification | Pass |
| Independent empty Gradle/project cache build, uncached tasks | Pass before the documentation review; 5 minutes 8 seconds including wrapper startup |
| Cold-cache candidate rebuilt offline without build cache | Pass before the documentation review; all four checksums matched the cold and workspace builds |
| Final clean offline builds after the comment review | Pass; checks, artifacts and Eclipse setup rerun |
| Final reproducibility | All four SHA-256 sums identical across two clean builds |
| Dedicated-server fresh/reload worlds | Pass for Library (2847), LibraryCompat (2859), Base Metals, Tinkers, Machines, IC2 and Thaumcraft |
| Production client integrated worlds on Forge 2859 | Pass for Library, Base Metals, combined integrations without IC2, and IC2 |
| Legacy binary runtime compatibility | Pass in every client/server profile, including singleton/enum identity and old plugin/event callbacks |
| Workflow lint and shell syntax | Pass for all five workflows and shell helpers |
| Comment review | Identical executable tokens in 438 Java files; identical bytecode in all 1,005 packaged classes after removing debug metadata, with unchanged packaged resources |
| Hosted CI, CodeQL analysis and publication | Not established by local checks; publication requires its own manual workflow |

The main jar used for the final packaged runtime checks is identified below;
its checksum also matches both final clean builds.
Java 8 class version 52 and SRG remapping are verified by the artifact audit.
The existing source produces optional-API deprecation warnings. Javadoc
generation completes without warnings after the documentation corrections;
the review preserves executable Java code and public API signatures.
The build also emits Gradle deprecation notices about a future Gradle 10
migration.

| Artifact | SHA-256 |
| --- | --- |
| Main | `CE2F6970A2AAF2A5156E2F1134A6E2C1AC1ABBB77EB35FD5FC526CB047E7E562` |
| Sources | `76A03EDCF0853BCE576CFB261BEEE542E4EFC0534E7CBC41AD0E16DAD41DEEC5` |
| Javadoc | `C76D50CB937AC037249DC71843D967C4A7B94D8223BA05A15B6775F637268265` |
| Development | `B96CCA655FAE9ABDE94EBE2B02FB2E59EE8A77345BFF548B838417AAE0044736` |

Build artifacts are generated under `build/libs/`, with checksums in
`build/release/SHA256SUMS`. Unit test
reports are under `build/reports/tests/`; packaged acceptance markers and
disposable worlds are kept in their profile directories under `build/`.
These outputs, local assistant notes and IDE files are ignored. Test sources,
build helpers and frozen verification inputs are tracked so a fresh clone can
repeat the checks.

The empty-cache build verified that the build-only API generator is included
in a fresh checkout. The ignore rule excludes root `/build/` output, allowing
the Java package named `build` under `src/buildSupport` to remain tracked.
The final comment review checked all 438 Java files for identical executable
tokens and refreshed only the 198 affected production-comment hashes. The
431-file production inventory, resource hashes and 500-class legacy ABI
inventory remain intact.

The commands and prerequisites in the [build guide](../gradle/README.md) are
portable. This migration does not change the Base Metals checkout or publish
a release; merging the build changes and running the guarded release workflow
are separate steps.
