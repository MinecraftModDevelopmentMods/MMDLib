# Creative tabs

A mod using MMDLib can describe its creative tabs in XML or JSON, next to its material and
other definitions. Each file in `mmdlib/creative_tab/` becomes one tab:

```text
src/main/resources/mmdlib/
  material/copper.json
  creative_tab/main.xml          -> tab example:main
  creative_tab/blocks/deco.json  -> tab example:blocks/deco
```

The tab's ID is the mod's ID and the file's path below `creative_tab/`, without
`.xml` or `.json`. Defining the same ID in both formats is an error.

## XML

```xml
<creative-tab xmlns="https://moddev.zone/mmdlib/creative-tab" version="1"
              title="itemGroup.example" title-fallback="Example Mod"
              icon="example:copper_hammer" default-for-mod="true"
              include-mod-items="true" search-bar="true">
  <exclude tag="example:internal_items"/>
  <after id="minecraft:tools_and_utilities"/>
</creative-tab>
```

The namespace and `version="1"` are required. Root attributes are `title`,
`title-fallback`, `icon`, `search-bar`, `default-for-mod` and `include-mod-items`;
boolean attributes default to `false`. Children are `item`, `tag`, `material`,
`before`, `after` and `exclude`. Entries use `id`; `exclude` requires exactly one
of `id` (an item) or `tag` (an item tag). Unknown attributes/elements are errors. DTDs and
external entities are not allowed. XML and JSON use the same validation and
runtime behavior.

`include-mod-items="true"` includes every registered item whose namespace matches
the tab's owning mod, including native Java items and generated block/material
items. No item list is needed. New items appear automatically after restarting.
Items are sorted by registry ID. Optional `item`, `tag` and `material` entries
come first, allowing favorites at the top without duplicates.

`exclude` accepts exact item IDs or item tags and wins over all inclusion sources, including
explicit entries and `default-for-mod` generated content. It affects this tab's
display and contribution to creative search; another tab may still list the item.
Unknown exclusions are harmless, allowing optional or retired internal items.
Supply an `icon` when there are no explicit item/material entries.

For `<exclude tag="example:internal_items"/>`, put the members in
`data/example/tags/item/internal_items.json`:

```json
{
  "replace": false,
  "values": ["example:internal_part", "example:legacy_alias"]
}
```

Use the bare tag ID in XML (no `#`). Tag files can include nested tags using
`"#example:other_internal_items"`. Exclusion tags are resolved whenever the tab
is rebuilt, using the current datapack tags; missing or empty tags exclude nothing.
Both shipped mod tabs use their own `internal_items` tag.

## JSON

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

`contents` may be omitted or empty only when `include_mod_items` or
`default_for_mod` is true. Otherwise an empty definition is rejected, including
one that accidentally uses `content` instead of `contents`. Without an explicit
item/material entry, an `icon` is required. The same rules apply to XML and Java
builders.

| Field | Meaning |
|---|---|
| `contents` | What the tab shows, in order. Each entry is one of: an item ID (`"example:copper_hammer"`); an item tag, with `#` (`"#c:gears"`); or `{"material": "example:copper"}`, every registered item of that material. |
| `title` | The title's translation key. Defaults to `itemGroup.<mod>.<path>`, with `/` in the path written as `.`: `itemGroup.example.tools`, `itemGroup.example.blocks.deco`. |
| `title_fallback` | The title shown when no language file has the key, so a tab reads properly before it is translated. |
| `icon` | The item shown on the tab. Defaults to the first item or material entry in `contents`; a tab whose contents are all tags needs one. |
| `before`, `after` | Tab IDs this tab is placed before or after, such as `"minecraft:combat"`. |
| `search_bar` | `true` gives the tab a search bar. |
| `default_for_mod` | `true` sends generated machines and registrar items in this tab's namespace here. At most one default per mod. XML spells this `default-for-mod`. |
| `include_mod_items` | `true` appends every enabled item registered in the owning namespace, sorted by ID. Defaults to `false`. XML spells this `include-mod-items`. |
| `exclude` | Item IDs to omit from all inclusion sources, e.g. `["example:internal_part"]`. Defaults to `[]`. XML uses `<exclude id="example:internal_part"/>`. |
| `exclude_tags` | Item tag IDs to omit, e.g. `["example:internal_items"]` (no `#`). Defaults to `[]`. XML uses `<exclude tag="example:internal_items"/>`. |

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

MMDLib automatically registers file-defined tabs for every owning mod. No Java
tab registration is needed. `MMDLib.creativeTabs()` lists the loaded definitions.
Existing consumers that queue file tabs through a `RuntimeContentRegistrar`
remain supported; a queued tab is not registered a second time.

Generated machines and material/standalone items join their owner's default tab,
after its explicit contents, without duplicates. Without an explicit default,
MMDLib selects a same-namespace Java or file tab: `main`, then `machines`, then
other names alphabetically, then `materials`, then `mmdlib_content`. A namespace
with generated items gets a fallback only
if it has no authored, queued or registered Java tab after its own tab registration
handlers run. Registrar namespaces retain `<mod>:materials` and the existing
`itemGroup.<mod>.materials` translation key; other namespaces get
`<mod>:mmdlib_content`. Both use the mod display name when their title is untranslated.
No unused placeholder tab is registered. Routing is selected once after registration;
Tag exclusions and feature flags are still evaluated on each contents rebuild.
There is no shared data-defined machines tab. MMDLib's Range, Gearbox and Capacitor Upgrades live in `mmdlib:main`.

Use `include-mod-items` to discover manually registered Java items too, and
exclude internal parts and compatibility aliases. Without that option, list
Java items explicitly or use the Java tab's display callback.

When replacing a Java tab with XML, remove the old registration for that ID.

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
        .defaultForMod()
        .includeModItems()
        .exclude(ResourceLocation.parse("example:internal_part"))
        .excludeTag(ResourceLocation.parse("example:internal_items"))
        .build());
```

`queueTab` returns the tab's `DeferredHolder`. It only takes IDs in the
registrar's namespace, and not `<mod>:materials`, which is reserved for its fallback.

## From CraftTweaker scripts

`#loader mmdlib` scripts can make tabs with `mods.mmdlib.CreativeTabBuilder`, and
change the tabs these files declare with `CreativeTabBuilder.edit(id)`; a changed
tab replaces the file's version. See [CraftTweaker definitions](crafttweaker.md#creative-tabs).
Code-built definition sources can do the same: `MaterialExtensions.creativeTabFiles()`
lists the file tabs while sources run, and a tab a source returns with a file tab's
ID replaces it.

## The materials tab

Every registrar retains `<mod>:materials` as a fallback, using translation key
`itemGroup.<mod>` and the mod's display name. It receives generated content only
when the mod has no preferred tab; otherwise it stays empty and hidden.
