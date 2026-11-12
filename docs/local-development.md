# Using MMDLib from a local Maven repository

MMDLib can publish the working checkout to a shared folder without pushing or
waiting for CI. Configure the folder and development version in the Gradle user
home `gradle.properties` (normally `%USERPROFILE%\.gradle\gradle.properties` on
Windows, or `$GRADLE_USER_HOME/gradle.properties` when customized):

```properties
mmdlibLocalRepo=Z:/maven
mmdlibLocalVersion=2.0.0.121012-development
```

Publish locally with:

```powershell
.\gradlew.bat publishAllPublicationsToLocalRepository
```

This task publishes the main jar, sources, Javadoc and Maven metadata. It does not
publish to the remote repository. Without `mmdlibLocalRepo`, the local destination
remains `mcmodsrepo/` in this checkout. Without a local version override, the
development version remains `${mod_version}-development`. The `TAG` environment
variable always takes precedence for release builds.

Consumer projects can read the same two properties to select the local repository
and dependency `zone.moddev.mc.lib:mmdlib:<mmdlibLocalVersion>`. When local settings
are absent, use `https://maven.moddev.zone/releases` and the consumer's pinned release version.
Declare the selected repository as exclusive for the MMDLib module so a missing
local artifact cannot silently fall back to a remote build. Mark the local
dependency as changing and set its changing-module cache lifetime to zero, since
successive local publishes reuse the same version.

Republish after editing MMDLib, then rebuild or restart the consumer's development
game. The source checkout can contain uncommitted changes. A Maven dependency does
not automatically rebuild the source checkout.

`-PmmdlibUseLocal=false` disables the local overrides in MMDLib; consumers using
this convention can use the same flag to verify their pinned remote dependency.
Keep machine-local settings outside project repositories. Release tags and remote
publishing credentials continue to come from CI.
