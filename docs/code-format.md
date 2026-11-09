# Code format

Java follows the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
with these overrides:

- **Indentation: 4 spaces for both blocks and continuations.** Wrapped expressions and array
  initializers add one four-space level, not eight spaces. Existing explicit line breaks are kept
  where the formatter supports it. The 100-column limit and the other Google conventions (brace
  style, one statement per line, naming, `@Override` always, no wildcard imports, ...) still apply.
- **Imports are split into blocks, one per second-level package, separated by a single blank
  line.** Order:
  1. All static imports, as one block.
  2. `java.*` blocks, then `javax.*` blocks.
  3. Third-party blocks in ASCII order of their second-level prefix (`com.google`, `com.mojang`,
     `mezz.jei`, `net.minecraft`, `net.neoforged`, `org.slf4j`, ...).
  4. MMDLib's own imports (`zone.moddev.*`, the library and the companion) last, as one block.

  Within a block, imports are ASCII-sorted. Example:

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

## Code written before this style

Most of the code predates this style: it uses google-java-format's 2-space indentation. It is
**not** reformatted in bulk. A file is brought over to the new style when it is next edited, as
part of that change, and only then. Don't reformat files a change doesn't otherwise touch.

## Tools

[Spotless](https://github.com/diffplug/spotless) does the formatting:

- `./gradlew spotlessApply` (or `./gradlew format`) formats files.
- `./gradlew spotlessCheck` reports files that aren't formatted. It is part of `check` and `build`,
  so CI fails on them.

Both are **ratcheted** from `origin/master-2.0-1.21.1-neo`: they only look at files that differ
from that branch, which is how older files are left alone until edited. Keep that branch fetched;
CI checks out with full history for this reason.

The Java formatter is Eclipse JDT with the `MMDLib` profile in `gradle/java-formatter.xml`. Imports
are regrouped afterwards by a custom step, `gradle/import-blocks.gradle`, since Spotless's own
`importOrder` can't express rule 3. The step refuses, with the line number, input it can't rewrite
safely: comments between imports, more than one import or a trailing comment on a line, and
wildcard imports. Its fixtures in `gradle/import-blocks/` run as `testImportBlocks`, part of
`check`.

### VS Code

The same profile formats in VS Code. `.vscode/` is not committed, so add this to your
`.vscode/settings.json`:

```json
"java.format.settings.url": "gradle/java-formatter.xml",
"java.format.settings.profile": "MMDLib",
"[java]": {
    "editor.tabSize": 4,
    "editor.insertSpaces": true
}
```

VS Code's formatter doesn't apply the import-block step; run `./gradlew format` before committing.
