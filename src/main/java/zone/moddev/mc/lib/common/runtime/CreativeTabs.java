package zone.moddev.mc.lib.common.runtime;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;

import zone.moddev.mc.lib.common.types.CreativeTabDefinition.*;
import zone.moddev.mc.lib.common.types.CreativeTabDefinition;

/**
 * Turns {@link CreativeTabDefinition}s into creative tabs. Contents are
 * resolved when the game fills the tab, so tags and every registrar's materials
 * are complete by then.
 */
final class CreativeTabs {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CreativeTabs() {
    }

    /** The tab {@code id}, as {@code definition} describes it. */
    static CreativeModeTab build(ResourceLocation id,
        CreativeTabDefinition definition) {
        var title = definition.titleFallback()
            .map(fallback -> Component
                .translatableWithFallback(definition.titleKey(id), fallback))
            .orElseGet(() -> Component.translatable(definition.titleKey(id)));
        var builder = CreativeModeTab.builder().title(title)
            .icon(() -> new ItemStack(icon(id, definition)))
            .displayItems((parameters,
                output) -> items(definition, parameters.enabledFeatures())
                    .forEach(item -> output.accept(new ItemStack(item))));
        if (!definition.before().isEmpty())
            builder.withTabsBefore(keys(definition.before()));
        if (!definition.after().isEmpty())
            builder.withTabsAfter(keys(definition.after()));
        if (definition.searchBar())
            builder.withSearchBar();
        return builder.build();
    }

    /**
     * The tab's items in order: each once, where it first appears, and only
     * those enabled.
     */
    static List<Item> items(CreativeTabDefinition definition,
        FeatureFlagSet enabled) {
        var items = new LinkedHashSet<Item>();
        for (var entry : definition.contents())
            switch (entry) {
                case ItemEntry item ->
                    items.add(BuiltInRegistries.ITEM.get(item.item()));
                case TagEntry tag -> BuiltInRegistries.ITEM
                    .getTagOrEmpty(TagKey.create(Registries.ITEM, tag.tag()))
                    .forEach(holder -> items.add(holder.value()));
                case MaterialEntry material ->
                    items.addAll(materialItems(material.material()));
            }
        return items.stream().filter(item -> item.isEnabled(enabled)).toList();
    }

    /**
     * Fails for an item or material the tab names that does not exist, naming
     * the tab; warns about a {@code before}/{@code after} tab that is not
     * registered, since it may belong to a mod that is not installed. Run once
     * registries are complete.
     *
     * @throws IllegalArgumentException
     *             for an unknown item or material
     */
    static void verify(ResourceLocation id, CreativeTabDefinition definition) {
        definition.icon().ifPresent(icon -> requireItem(id, icon));
        for (var entry : definition.contents()) {
            if (entry instanceof ItemEntry item)
                requireItem(id, item.item());
            else if (entry instanceof MaterialEntry material
                && materialItems(material.material()).isEmpty())
                throw new IllegalArgumentException("Creative tab " + id
                    + " names material " + material.material()
                    + ", which has no registered items");
        }
        for (var tab : concat(definition.before(), definition.after()))
            if (!BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(tab))
                LOGGER.warn(
                    "Creative tab {} is placed next to {}, which is"
                        + " not registered; that placement is ignored",
                    id, tab);
    }

    private static Item icon(ResourceLocation id,
        CreativeTabDefinition definition) {
        if (definition.icon().isPresent())
            return BuiltInRegistries.ITEM.get(definition.icon().get());
        for (var entry : definition.contents()) {
            if (entry instanceof ItemEntry item)
                return BuiltInRegistries.ITEM.get(item.item());
            if (entry instanceof MaterialEntry material) {
                var items = materialItems(material.material());
                if (!items.isEmpty())
                    return items.getFirst();
            }
        }
        throw new IllegalStateException("Creative tab " + id + " has no icon");
    }

    /** Every registered item of {@code material}, across all registrars. */
    private static List<Item> materialItems(ResourceLocation material) {
        return RuntimeContentRegistrar.instances().stream()
            .flatMap(registrar -> registrar.itemMaterials().entrySet().stream()
                .filter(entry -> entry.getValue().name()
                    .equals(Optional.of(material)))
                .map(entry -> (Item) registrar.items().get(entry.getKey())
                    .get()))
            .toList();
    }

    private static void requireItem(ResourceLocation tab,
        ResourceLocation item) {
        if (!BuiltInRegistries.ITEM.containsKey(item))
            throw new IllegalArgumentException(
                "Creative tab " + tab + " names unknown item " + item);
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<CreativeModeTab>[] keys(
        List<ResourceLocation> tabs) {
        return tabs.stream()
            .map(tab -> ResourceKey.create(Registries.CREATIVE_MODE_TAB, tab))
            .toArray(ResourceKey[]::new);
    }

    private static List<ResourceLocation> concat(List<ResourceLocation> a,
        List<ResourceLocation> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
