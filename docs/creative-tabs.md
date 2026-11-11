# Creative tabs

A mod using MMDLib can describe its creative tabs in JSON, next to its material and
other definitions. Each file in `mmdlib/creative_tab/` becomes one tab:

```text
src/main/resources/mmdlib/
  material/copper.json
  creative_tab/tools.json        -> tab example:tools
  creative_tab/blocks/deco.json  -> tab example:blocks/deco
```

The tab's ID is the mod's ID and the file's path below `creative_tab/`, without
`.json`, as for every other [definition file](definition-loading.md).

## The file

```json
{
  "title_fallback": "Example Tools",
  "icon": "example:copper_hammer",
  "contents": [
    { "material": "example:copper" },
    "example:copper_hammer",
    "#c:gears"
  ],
  "after": ["minecraft:tools_and_utilities"],
  "search_bar": true
}
```

Only `contents` is required.

| Field | Meaning |
|---|---|
| `contents` | What the tab shows, in order. Each entry is one of: an item ID (`"example:copper_hammer"`); an item tag, with `#` (`"#c:gears"`); or `{"material": "example:copper"}`, every registered item of that material. |
| `title` | The title's translation key. Defaults to `itemGroup.<mod>.<path>`, with `/` in the path written as `.`: `itemGroup.example.tools`, `itemGroup.example.blocks.deco`. |
| `title_fallback` | The title shown when no language file has the key, so a tab reads properly before it is translated. |
| `icon` | The item shown on the tab. Defaults to the first item or material entry in `contents`; a tab whose contents are all tags needs one. |
| `before`, `after` | Tab IDs this tab is placed before or after, such as `"minecraft:combat"`. |
| `search_bar` | `true` gives the tab a search bar. |

An item appears once, where it is first listed: a tag or material that includes an
item already shown does not repeat it. Items whose feature flags are disabled are
left out.

## When it is checked

Files are read at startup, with the other definitions; a malformed file, or a tab
of only tags without an `icon`, stops loading with the file named. Once every
registry is complete, an `icon` or item entry naming an unknown item, or a
material with no registered items, also stops loading, naming the tab. A `before`
or `after` tab that is not registered is only logged as a warning, since it may
belong to a mod that is not installed; that placement is then ignored.

Tags are filled in when the game builds the tab, so tags from datapacks work.

## Registering the tabs

MMDLib registers the tabs in its own namespace. As with materials, your mod
registers its own, through its `RuntimeContentRegistrar`. `MMDLib.creativeTabs()`
lists every tab file; queue yours once loading is done:

```java
MMDLib.creativeTabs().forEach((id, tab) -> {
    if (id.getNamespace().equals("example"))
        registrar.queueTab(id, tab);
});
```

`queueTab` returns the tab's `DeferredHolder`. It only takes IDs in the
registrar's namespace, and not `<mod>:materials`, which is the registrar's own tab.

## Building a tab in code

`CreativeTabDefinition.builder()` makes the same definition in Java; each method
matches a JSON field:

```java
registrar.queueTab(ResourceLocation.parse("example:tools"),
    CreativeTabDefinition.builder()
        .title("itemGroup.example.tools", "Example Tools")
        .icon(ResourceLocation.parse("example:copper_hammer"))
        .material(ResourceLocation.parse("example:copper"))
        .item(ResourceLocation.parse("example:copper_hammer"))
        .tag(ResourceLocation.parse("c:gears"))
        .after(ResourceLocation.parse("minecraft:tools_and_utilities"))
        .searchBar()
        .build());
```

## The materials tab

Every registrar also has its automatic `<mod>:materials` tab, holding every item it
registers; its title is the translation key `itemGroup.<mod>.materials`. It is
unchanged by tab files, so an item listed in your own tab also appears there.
