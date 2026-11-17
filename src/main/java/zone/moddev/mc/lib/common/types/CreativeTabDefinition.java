package zone.moddev.mc.lib.common.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * A creative tab declared in {@code mmdlib/creative_tab/} as XML or JSON, or
 * built with {@link #builder()}: its title, icon, contents and placement.
 *
 * @param title the title's translation key; empty for
 *            {@code itemGroup.<namespace>.<path>}, with {@code /} as {@code .}
 * @param titleFallback the title shown when no language file has the key
 * @param icon the item shown on the tab; empty for the first item or material
 *            entry in {@code contents}
 * @param contents what the tab shows, in order
 * @param before tabs this one is placed before
 * @param after tabs this one is placed after
 * @param searchBar whether the tab has a search bar
 * @param defaultForMod whether generated content in this namespace joins this
 *            tab
 * @param includeModItems whether to append all registered items in this tab's
 *            namespace
 * @param exclusions item IDs omitted from explicit and automatic contents
 * @param excludedTags item tags omitted from explicit and automatic contents
 */
public record CreativeTabDefinition(Optional<String> title,
    Optional<String> titleFallback, Optional<ResourceLocation> icon,
    List<Entry> contents, List<ResourceLocation> before,
    List<ResourceLocation> after, boolean searchBar, boolean defaultForMod,
    boolean includeModItems, List<ResourceLocation> exclusions,
    List<ResourceLocation> excludedTags) {
    public CreativeTabDefinition(Optional<String> title,
        Optional<String> titleFallback, Optional<ResourceLocation> icon,
        List<Entry> contents, List<ResourceLocation> before,
        List<ResourceLocation> after, boolean searchBar) {
        this(title, titleFallback, icon, contents, before, after, searchBar,
            false, false, List.of(), List.of());
    }
    /** One line of a tab's {@code contents}. */
    public sealed interface Entry {
    }

    /** One item: {@code "example:hammer"}. */
    public record ItemEntry(ResourceLocation item) implements Entry {
    }

    /** Every item in an item tag: {@code "#c:ingots"}. */
    public record TagEntry(ResourceLocation tag) implements Entry {
    }

    /**
     * Every registered item of a material: {@code {"material":
     * "example:copper"}}.
     */
    public record MaterialEntry(ResourceLocation material) implements Entry {
    }

    private static final Codec<Entry> NAMED = Codec.STRING.comapFlatMap(
        text -> text.startsWith("#")
            ? ResourceLocation.read(text.substring(1)).map(TagEntry::new)
            : ResourceLocation.read(text).map(ItemEntry::new),
        entry -> entry instanceof TagEntry tag
            ? "#" + tag.tag()
            : ((ItemEntry) entry).item().toString());
    private static final Codec<MaterialEntry> MATERIAL = RecordCodecBuilder
        .create(i -> i
            .group(ResourceLocation.CODEC.fieldOf("material")
                .forGetter(MaterialEntry::material))
            .apply(i, MaterialEntry::new));

    /**
     * An entry: {@code "item:id"}, {@code "#tag:id"} or {@code {"material":
     * "material:id"}}.
     */
    public static final Codec<Entry> ENTRY = Codec.either(NAMED, MATERIAL).xmap(
        either -> either.map(e -> e, e -> e),
        entry -> entry instanceof MaterialEntry material
            ? Either.right(material)
            : Either.left(entry));

    public static final Codec<CreativeTabDefinition> CODEC = RecordCodecBuilder
        .create(i -> i.group(
            Codec.STRING.optionalFieldOf("title")
                .forGetter(CreativeTabDefinition::title),
            Codec.STRING.optionalFieldOf("title_fallback")
                .forGetter(CreativeTabDefinition::titleFallback),
            ResourceLocation.CODEC.optionalFieldOf("icon")
                .forGetter(CreativeTabDefinition::icon),
            ENTRY.listOf().optionalFieldOf("contents", List.of())
                .forGetter(CreativeTabDefinition::contents),
            ResourceLocation.CODEC.listOf().optionalFieldOf("before", List.of())
                .forGetter(CreativeTabDefinition::before),
            ResourceLocation.CODEC.listOf().optionalFieldOf("after", List.of())
                .forGetter(CreativeTabDefinition::after),
            Codec.BOOL.optionalFieldOf("search_bar", false)
                .forGetter(CreativeTabDefinition::searchBar),
            Codec.BOOL.optionalFieldOf("default_for_mod", false)
                .forGetter(CreativeTabDefinition::defaultForMod),
            Codec.BOOL.optionalFieldOf("include_mod_items", false)
                .forGetter(CreativeTabDefinition::includeModItems),
            ResourceLocation.CODEC.listOf()
                .optionalFieldOf("exclude", List.of())
                .forGetter(CreativeTabDefinition::exclusions),
            ResourceLocation.CODEC.listOf()
                .optionalFieldOf("exclude_tags", List.of())
                .forGetter(CreativeTabDefinition::excludedTags))
            .apply(i, CreativeTabDefinition::new));

    /**
     * Copies the lists.
     *
     * @throws IllegalArgumentException if contents are empty without automatic
     *             contents, or there is no {@code icon} and no item or material
     *             entry to take it from
     */
    public CreativeTabDefinition {
        Objects.requireNonNull(title);
        Objects.requireNonNull(titleFallback);
        Objects.requireNonNull(icon);
        contents = List.copyOf(contents);
        before = List.copyOf(before);
        after = List.copyOf(after);
        exclusions = List.copyOf(exclusions);
        excludedTags = List.copyOf(excludedTags);
        if (contents.isEmpty() && !defaultForMod && !includeModItems)
            throw new IllegalArgumentException(
                "A creative tab needs contents, include_mod_items or default_for_mod");
        check(icon, contents).getOrThrow(IllegalArgumentException::new);
    }

    private static DataResult<Boolean> check(Optional<ResourceLocation> icon,
        List<Entry> contents) {
        return icon.isPresent()
            || contents.stream().anyMatch(entry -> !(entry instanceof TagEntry))
                ? DataResult.success(true)
                : DataResult.error(() -> "A tab whose contents are all tags"
                    + " needs an icon");
    }

    /** The title's translation key for the tab {@code id}. */
    public String titleKey(ResourceLocation id) {
        return title.orElseGet(() -> "itemGroup." + id.getNamespace() + "."
            + id.getPath().replace('/', '.'));
    }

    /** A new, empty builder. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builds a {@link CreativeTabDefinition} in code; each method mirrors a
     * JSON field.
     */
    public static final class Builder {
        private String title, titleFallback;
        private ResourceLocation icon;
        private final List<Entry> contents = new ArrayList<>();
        private final List<ResourceLocation> before = new ArrayList<>(),
            after = new ArrayList<>();
        private boolean searchBar;
        private boolean defaultForMod;
        private boolean includeModItems;
        private final List<ResourceLocation> exclusions = new ArrayList<>();
        private final List<ResourceLocation> excludedTags = new ArrayList<>();

        private Builder() {
        }

        /** The title's translation key ({@code title}). */
        public Builder title(String key) {
            title = Objects.requireNonNull(key);
            return this;
        }

        /** The title's translation key and fallback text. */
        public Builder title(String key, String fallback) {
            titleFallback = Objects.requireNonNull(fallback);
            return title(key);
        }

        /** The icon item ({@code icon}). */
        public Builder icon(ResourceLocation item) {
            icon = Objects.requireNonNull(item);
            return this;
        }

        /** Adds one item. */
        public Builder item(ResourceLocation item) {
            contents.add(new ItemEntry(item));
            return this;
        }

        /** Adds every item in the item tag {@code tag}. */
        public Builder tag(ResourceLocation tag) {
            contents.add(new TagEntry(tag));
            return this;
        }

        /** Adds every registered item of {@code material}. */
        public Builder material(ResourceLocation material) {
            contents.add(new MaterialEntry(material));
            return this;
        }

        /** Places the tab before {@code tab}. */
        public Builder before(ResourceLocation tab) {
            before.add(Objects.requireNonNull(tab));
            return this;
        }

        /** Places the tab after {@code tab}. */
        public Builder after(ResourceLocation tab) {
            after.add(Objects.requireNonNull(tab));
            return this;
        }

        /** Gives the tab a search bar. */
        public Builder searchBar() {
            searchBar = true;
            return this;
        }

        /** Receives this mod's generated machine and material items. */
        public Builder defaultForMod() {
            defaultForMod = true;
            return this;
        }

        /** Appends all items registered in the tab's owning namespace. */
        public Builder includeModItems() {
            includeModItems = true;
            return this;
        }

        /** Omits an item from both explicit and automatic contents. */
        public Builder exclude(ResourceLocation item) {
            exclusions.add(Objects.requireNonNull(item));
            return this;
        }

        /**
         * Omits all members of an item tag, resolved when the tab is rebuilt.
         */
        public Builder excludeTag(ResourceLocation tag) {
            excludedTags.add(Objects.requireNonNull(tag));
            return this;
        }

        /**
         * The definition.
         *
         * @throws IllegalArgumentException as the {@link CreativeTabDefinition}
         *             constructor
         */
        public CreativeTabDefinition build() {
            return new CreativeTabDefinition(Optional.ofNullable(title),
                Optional.ofNullable(titleFallback), Optional.ofNullable(icon),
                contents, before, after, searchBar, defaultForMod,
                includeModItems, exclusions, excludedTags);
        }
    }
}
