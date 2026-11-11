package zone.moddev.mc.lib.smoke;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.common.runtime.RuntimeContentRegistrar;

/**
 * The test resources' {@code mmdlib/creative_tab/test/workshop.json}: the iron
 * material, a stick, the {@code #c:gears} tag (holding the iron gear, already
 * in) and the stick again.
 */
final class CreativeTabSmoke {
    private CreativeTabSmoke() {
    }

    static void workshop(ServerLevel level) {
        var id = ResourceLocation.parse("mmdlib:test/workshop");
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
        require(tab != null, "the tab is registered");
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
            level.enabledFeatures(), false, level.registryAccess()));
        var shown = tab.getDisplayItems().stream().map(ItemStack::getItem)
            .toList();
        var iron = ironItems();
        require(
            iron.contains(BuiltInRegistries.ITEM
                .get(ResourceLocation.parse("mmdlib:iron_molten_bucket"))),
            "the material's items include its bucket");
        require(shown.subList(0, iron.size()).equals(iron),
            "the material's items come first, in order: shown " + shown
                + ", expected " + iron);
        require(
            shown.size() == iron.size() + 1 && shown.getLast() == Items.STICK,
            "then the stick, once; the tag's gear is not repeated");
        require(tab.getIconItem().getItem() == iron.getFirst(),
            "the icon is the first material item");
        require(tab.hasSearchBar(), "the tab has a search bar");
        require(
            tab.getDisplayName()
                .getContents() instanceof TranslatableContents title
                && title.getKey().equals("itemGroup.mmdlib.test.workshop")
                && "MMDLib Test Workshop".equals(title.getFallback()),
            "the default title key, with the fallback");
        require(MMDLib.creativeTabs().containsKey(id), "the tab is listed");
    }

    /** Every item registered for the iron material, by ID. */
    private static List<Item> ironItems() {
        var iron = ResourceLocation.parse("mmdlib:iron");
        return RuntimeContentRegistrar.instances().stream()
            .flatMap(registrar -> registrar.itemMaterials().entrySet().stream()
                .filter(
                    entry -> entry.getValue().name().orElseThrow().equals(iron))
                .map(entry -> BuiltInRegistries.ITEM.get(entry.getKey())))
            .toList();
    }

    private static void require(boolean condition, String what) {
        if (!condition)
            throw new AssertionError("Creative tab smoke failed: " + what);
    }
}
