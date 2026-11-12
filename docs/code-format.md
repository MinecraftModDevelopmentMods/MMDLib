# Code format

Java follows the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
with these overrides:

- **Indentation: 4 spaces for both blocks and continuations.** Wrapped expressions and array
  initializers add one four-space level, not eight spaces.
- **Lines aim for 80 columns.** Parameter, argument and record component lists are packed up to
  the limit and wrapped only where needed, never one per line; breaks that fit are rejoined.
- **Up to 120 columns for what can't be wrapped**, such as a long string constant. Past 120,
  `./gradlew lineLimit` (part of `check`) warns with the file and line. It never fails the build:
  some lines read worse split than long.
- **Wildcard imports are allowed**, static or not; in a mod they usually mean less code.
- **Keep methods short**: aim for 25 to 30 lines. This is a review guideline; no tool enforces it.
- The other Google conventions (brace style, one statement per line, naming, `@Override` always,
  ...) still apply.
- **Imports are split into blocks, one per second-level package, separated by a single blank
  line.** Order:
  1. All static imports, as one block.
  2. `java.*` blocks, then `javax.*` blocks.
  3. Third-party blocks in ASCII order of their second-level prefix (`com.google`, `com.mojang`,
     `mezz.jei`, `net.minecraft`, `net.neoforged`, `org.slf4j`, ...).
  4. MMDLib's own imports (`zone.moddev.*`, the library and the companion) last, as one block.

  Within a block, imports are ASCII-sorted, so a wildcard comes before that package's single-type
  imports. Example:

  ```java
  import java.util.List;
  import java.util.Map;

  import com.mojang.logging.LogUtils;
  import com.mojang.serialization.Codec;

  import net.minecraft.core.BlockPos;
  import net.minecraft.world.level.Level;

  import net.neoforged.bus.api.IEventBus;

  import org.slf4j.Logger;

  import zone.moddev.mc.companion.CompanionMod;
  import zone.moddev.mc.lib.MMDLib;
  ```

## Changing the style

Day to day, formatting only touches the files a change edits (see the ratchet below); don't
reformat files a change doesn't otherwise touch. When the style itself changes, reformat the whole
tree once, as described under Tools.

## Tools

[Spotless](https://github.com/diffplug/spotless) does the formatting:

- `./gradlew spotlessApply` (or `./gradlew format`) formats files.
- `./gradlew spotlessCheck` reports files that aren't formatted. It is part of `check` and `build`,
  so CI fails on them.

Both are **ratcheted** from `origin/master-2.0-1.21.1-neo`: they only look at files that differ
from that branch, which is how older files are left alone until edited. Keep that branch fetched;
CI checks out with full history for this reason.

After changing the style, `./gradlew spotlessApply -PformatAll` drops the ratchet and formats the
whole tree. Commit that on its own and add the commit to `.git-blame-ignore-revs`, so `git blame`
skips it.

The Java formatter is Eclipse JDT with the `MMDLib` profile in `gradle/java-formatter.xml`. Imports
are regrouped afterwards by a custom step, `gradle/import-blocks.gradle`, since Spotless's own
`importOrder` can't express rule 3. The step refuses, with the line number, input it can't rewrite
safely: comments between imports, and more than one import or a trailing comment on a line. Its
fixtures in `gradle/import-blocks/` run as `testImportBlocks`, part of
`check`.

### VS Code

The same profile formats in VS Code. `.vscode/` is not committed, so add this to your
`.vscode/settings.json`:

```json
"java.format.settings.url": "gradle/java-formatter.xml",
"java.format.settings.profile": "MMDLib",
"[java]": {
    "editor.rulers": [80, 120],
    "editor.tabSize": 4,
    "editor.insertSpaces": true
}
```

VS Code's formatter doesn't apply the import-block step; run `./gradlew format` before committing.
