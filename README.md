# MMDLib

MMDLib provides the shared Java APIs and helpers used by the Metals family of
Minecraft mods. This branch targets Minecraft 1.12.2 and Forge 14.23.5.2847.
The implementation uses `zone.moddev.mc.mmdlib`. A Forge compatibility adapter
supports existing mods compiled against `com.mcmoddev.lib`; the mod ID remains
`mmdlib`.

## Building

Use the checked-in Gradle wrapper. Gradle runs on Temurin 17.0.1+12,
ForgeGradle's Mavenizer uses Temurin 25.0.3+9, and production compilation and
Minecraft use Temurin 8.0.502+7. ForgeGradle is pinned to 7.0.34, Renamer to
1.1.5, and Gradle to 9.6.1. Mappings remain `stable_39`.

Set `JAVA_HOME` to the Java 17 installation. Install the three toolchains and
use one Gradle cache consistently in both the command line and Eclipse.
For explicit installations, pass
`-Dorg.gradle.java.installations.paths=<java17>,<java8>,<java25>`.

Recover the pinned legacy artifacts into an isolated mirror first. Python 3
downloads only the URLs recorded in the dependency manifest and verifies each
artifact's SHA-256 before staging it:

```text
python3 gradle/stage-legacy-dependencies.py /absolute/path/to/dependency-mirror
./gradlew check build javadoc verifyReleaseArtifacts writeReleaseChecksums -PlegacyDependencyVerificationRepository=/absolute/path/to/dependency-mirror
./gradlew prepareEclipse -PlegacyDependencyVerificationRepository=/absolute/path/to/dependency-mirror
```

On Windows, use `gradlew.bat` and the installed Python executable. Normal
builds require no release credentials and do not publish artifacts.

`build/libs/` contains the SRG-remapped main jar, a mapped `deobf`
development jar, sources, and Javadocs. `build/release/SHA256SUMS` records their
checksums. Archives use stable ordering, timestamps, and text line endings.

## Library compatibility

The build migration uses version `1.0.0.112021` and Maven coordinates
`zone.moddev.mc.mmdlib:MMDLib:1.0.0.112021`. The final component identifies
Minecraft 1.12.2 on Forge. Consumers using the former `com.mcmoddev` Maven
group must update their dependency coordinate when adopting this artifact;
new Java code should import `zone.moddev.mc.mmdlib`. Existing source can keep
its `com.mcmoddev.lib` imports through generated compiler API exports. At
runtime, the Forge adapter relinks old API references to the canonical classes,
preserving shared enums, events, integration singletons and registry state.
Saved mod, registry and configuration identities remain unchanged.

The compatibility inventory freezes all 500 legacy classes and their
public/protected signatures. `check` compiles a legacy consumer both against
the historical published library and against the new API exports; packaged
acceptance then runs that consumer alongside the modern implementation.
See [namespace compatibility](docs/NAMESPACE-COMPATIBILITY.md) for the adapter
contract and extension guidance.

Optional integration APIs remain compile-only. The build does not bundle
those mods or require them during normal MMDLib launches. Unused historical
dependency declarations and retired Maven/JCenter endpoints were removed;
the integration implementations themselves were preserved.

`gradle/legacy-dependencies.json` records exact dependency versions, original
download URLs, file IDs where applicable, and checksums. Base Metals and
OreSpawn entries are acceptance fixtures, not MMDLib runtime requirements.
The existing VeinMiner API source is retained as shipped by upstream.

The manifest installs only the namespace compatibility bootstrap. Historical
gameplay coremod transformers stay dormant. Release candidates are unsigned;
the upstream fingerprint warning can still appear and is not a loading failure.

## Verification and releases

GitHub Actions validates the wrapper, an empty-cache Forge bootstrap followed
by an offline rebuild, existing tests, release audits, deterministic rebuilds,
Eclipse launches, and packaged-server acceptance. CodeQL explicitly compiles
the production sources with build caching disabled.

Packaged server checks create and reload disposable worlds for MMDLib alone
on Forge 2847, and Base Metals plus optional integration profiles on Forge
2859. Client probes create a disposable integrated world and exit; their jars
are excluded from published artifacts. See the [build guide](gradle/README.md)
for the commands and the [migration receipt](docs/FG7-MIGRATION.md) for evidence
and known compatibility limits.

Local verification covers 39 tests, all 500 historical class APIs, Eclipse
setup, clean reproducible builds, and packaged client/server profiles. Hosted
CI and CodeQL results are reported separately by GitHub after the branch is
pushed. IC2 is tested separately from the full machine integration stack
because their existing bronze-recipe conflict also occurs with published
MMDLib.

Local assistant notes, IDE settings, generated reports, runtime worlds and
release output are ignored. Build helpers, automated test sources and frozen
compatibility inventories are part of the project.

The manual `Release MMDLib` workflow prepares an immutable candidate from
`master-1.12` after verifying successful checks on that exact commit. Live
publication requires `confirm_live_publication=true`, runs only in the upstream
repository, and requires `CURSEFORGE_TOKEN`, `MAVEN_UPLOAD_URL`,
`MAVEN_UPLOAD_USERNAME`, and `MAVEN_UPLOAD_PASSWORD`. The audited bundle is
published to Maven and CurseForge project 261744 before the GitHub Release.
Pushing a version tag only validates the candidate and records the manual
publication step.
