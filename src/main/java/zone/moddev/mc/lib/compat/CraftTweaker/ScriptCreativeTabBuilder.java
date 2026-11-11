package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.types.CreativeTabDefinition.*;
import zone.moddev.mc.lib.common.types.CreativeTabDefinition;

/**
 * Builds a creative tab from a {@code #loader mmdlib} script, or changes one
 * declared in an {@code mmdlib/creative_tab/} file:
 *
 * <pre>{@code
 * var tools = new CreativeTabBuilder("tools");
 * tools.titleFallback = "Tools";
 * tools.addMaterials("mmdlib:copper").addItems("minecraft:stick");
 * tools.addTags("c:gears");
 * tools.build();
 *
 * var workshop = CreativeTabBuilder.edit("example:workshop");
 * workshop.addItems("minecraft:apple").removeTags("c:gears");
 * workshop.build();
 * }</pre>
 *
 * <p>
 * A new tab is {@code mmdlib:<name>}. An edited file tab keeps its ID and
 * namespace, and replaces the file's version. Each property matches a field of
 * the tab file; see {@code docs/creative-tabs.md}.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.CreativeTabBuilder")
public final class ScriptCreativeTabBuilder {
    private final ResourceLocation id;
    private String title, titleFallback;
    private ResourceLocation icon;
    private final List<Entry> contents = new ArrayList<>();
    private List<ResourceLocation> before = List.of(), after = List.of();
    private boolean searchBar;

    /**
     * Starts a new tab named {@code name}, a path such as {@code "tools"}.
     *
     * @throws IllegalArgumentException
     *             if a file already declares the tab; use {@link #edit} to
     *             change it
     */
    @ZenCodeType.Constructor
    public ScriptCreativeTabBuilder(String name) {
        id = ScriptDefinitionProperties.mmdlibId(name, "creative tab");
        if (ScriptDefinitions.creativeTabFiles().containsKey(id))
            throw new IllegalArgumentException("Creative tab " + id
                + " is declared in a file; use CreativeTabBuilder.edit(\"" + id
                + "\") to change it");
    }

    private ScriptCreativeTabBuilder(ResourceLocation id,
        CreativeTabDefinition from) {
        this.id = id;
        title = from.title().orElse(null);
        titleFallback = from.titleFallback().orElse(null);
        icon = from.icon().orElse(null);
        contents.addAll(from.contents());
        before = from.before();
        after = from.after();
        searchBar = from.searchBar();
    }

    /**
     * Starts from the tab {@code id} declared in an
     * {@code mmdlib/creative_tab/} file, in any namespace; building it replaces
     * the file's version.
     *
     * @throws IllegalArgumentException
     *             if no file declares it
     */
    @ZenCodeType.Method
    public static ScriptCreativeTabBuilder edit(String id) {
        var location = ResourceLocation.parse(id);
        var file = ScriptDefinitions.creativeTabFiles().get(location);
        if (file == null)
            throw new IllegalArgumentException(
                "No mmdlib/creative_tab file declares " + id);
        return new ScriptCreativeTabBuilder(location, file);
    }

    /** Whether an {@code mmdlib/creative_tab/} file declares {@code id}. */
    @ZenCodeType.Method
    public static boolean exists(String id) {
        return ScriptDefinitions.creativeTabFiles()
            .containsKey(ResourceLocation.parse(id));
    }

    /** The IDs of every tab declared in {@code mmdlib/creative_tab/} files. */
    @ZenCodeType.Method
    public static String[] declared() {
        return ScriptDefinitions.creativeTabFiles().keySet().stream()
            .map(ResourceLocation::toString).sorted().toArray(String[]::new);
    }

    /** The tab's ID, such as {@code mmdlib:tools}. */
    @ZenCodeType.Getter("name")
    public String getName() {
        return id.toString();
    }

    /** The title's translation key; empty for the default. */
    @ZenCodeType.Getter("title")
    public String getTitle() {
        return title == null ? "" : title;
    }

    @ZenCodeType.Setter("title")
    public void setTitle(String value) {
        title = value.isEmpty() ? null : value;
    }

    /** The title shown when no language file has the key; empty for none. */
    @ZenCodeType.Getter("titleFallback")
    public String getTitleFallback() {
        return titleFallback == null ? "" : titleFallback;
    }

    @ZenCodeType.Setter("titleFallback")
    public void setTitleFallback(String value) {
        titleFallback = value.isEmpty() ? null : value;
    }

    /** The icon item's ID; empty for the first item or material entry. */
    @ZenCodeType.Getter("icon")
    public String getIcon() {
        return icon == null ? "" : icon.toString();
    }

    @ZenCodeType.Setter("icon")
    public void setIcon(String value) {
        icon = value.isEmpty() ? null : ResourceLocation.parse(value);
    }

    /** Whether the tab has a search bar. */
    @ZenCodeType.Getter("searchBar")
    public boolean getSearchBar() {
        return searchBar;
    }

    @ZenCodeType.Setter("searchBar")
    public void setSearchBar(boolean value) {
        searchBar = value;
    }

    /** Tabs this one is placed before. */
    @ZenCodeType.Getter("before")
    public String[] getBefore() {
        return strings(before);
    }

    @ZenCodeType.Setter("before")
    public void setBefore(String[] tabs) {
        before = ids(tabs);
    }

    /** Tabs this one is placed after. */
    @ZenCodeType.Getter("after")
    public String[] getAfter() {
        return strings(after);
    }

    @ZenCodeType.Setter("after")
    public void setAfter(String[] tabs) {
        after = ids(tabs);
    }

    /**
     * The contents, in order, written as in a tab file: an item as
     * {@code "minecraft:stick"}, a tag as {@code "#c:gears"}, a material as
     * {@code "material=mmdlib:copper"}.
     */
    @ZenCodeType.Getter("contents")
    public String[] getContents() {
        return contents.stream().map(entry -> switch (entry) {
            case ItemEntry item -> item.item().toString();
            case TagEntry tag -> "#" + tag.tag();
            case MaterialEntry material -> "material=" + material.material();
        }).toArray(String[]::new);
    }

    /** Adds items, at the end. */
    @ZenCodeType.Method
    public ScriptCreativeTabBuilder addItems(String... items) {
        for (var item : items)
            contents.add(new ItemEntry(ResourceLocation.parse(item)));
        return this;
    }

    /**
     * Adds every item in each item tag, at the end; with or without the
     * {@code #}.
     */
    @ZenCodeType.Method
    public ScriptCreativeTabBuilder addTags(String... tags) {
        for (var tag : tags)
            contents.add(new TagEntry(tag(tag)));
        return this;
    }

    /** Adds every registered item of each material, at the end. */
    @ZenCodeType.Method
    public ScriptCreativeTabBuilder addMaterials(String... materials) {
        for (var material : materials)
            contents.add(new MaterialEntry(ResourceLocation.parse(material)));
        return this;
    }

    /** Removes these item entries; tags and materials are not affected. */
    @ZenCodeType.Method
    public ScriptCreativeTabBuilder removeItems(String... items) {
        var gone = Arrays.stream(items).map(ResourceLocation::parse).toList();
        contents.removeIf(entry -> entry instanceof ItemEntry item
            && gone.contains(item.item()));
        return this;
    }

    /** Removes these tag entries. */
    @ZenCodeType.Method
    public ScriptCreativeTabBuilder removeTags(String... tags) {
        var gone = Arrays.stream(tags).map(ScriptCreativeTabBuilder::tag)
            .toList();
        contents.removeIf(
            entry -> entry instanceof TagEntry tag && gone.contains(tag.tag()));
        return this;
    }

    /** Removes these material entries. */
    @ZenCodeType.Method
    public ScriptCreativeTabBuilder removeMaterials(String... materials) {
        var gone = Arrays.stream(materials).map(ResourceLocation::parse)
            .toList();
        contents.removeIf(entry -> entry instanceof MaterialEntry material
            && gone.contains(material.material()));
        return this;
    }

    /** Removes every entry. */
    @ZenCodeType.Method
    public ScriptCreativeTabBuilder clearContents() {
        contents.clear();
        return this;
    }

    /**
     * Finishes the tab and queues it for registration, replacing a file's
     * version of it.
     *
     * @throws IllegalArgumentException
     *             if the tab is invalid, say all tags and no icon
     * @throws IllegalStateException
     *             if a script already built it
     */
    @ZenCodeType.Method
    public void build() {
        ScriptDefinitions.add(id,
            new CreativeTabDefinition(Optional.ofNullable(title),
                Optional.ofNullable(titleFallback), Optional.ofNullable(icon),
                contents, before, after, searchBar));
    }

    private static ResourceLocation tag(String text) {
        return ResourceLocation
            .parse(text.startsWith("#") ? text.substring(1) : text);
    }

    private static List<ResourceLocation> ids(String[] values) {
        return Arrays.stream(values).map(ResourceLocation::parse).toList();
    }

    private static String[] strings(List<ResourceLocation> values) {
        return values.stream().map(ResourceLocation::toString)
            .toArray(String[]::new);
    }
}
