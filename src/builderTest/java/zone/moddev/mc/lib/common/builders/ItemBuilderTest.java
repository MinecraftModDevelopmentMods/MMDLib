package zone.moddev.mc.lib.common.builders;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.util.StackContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class ItemBuilderTest {
    public static void main(String[] args) {
        require(ItemBuilder.create() == ItemBuilder.INSTANCE);
        var root = ItemBuilder.INSTANCE;
        var template = root.setName("mmdlib:ingot");
        require(root.getTemplateName() == null && root.getMaterialName() == null);
        require(template != root && template.getRegistryName() == null);
        var copper = template.forMaterial("basemetals:copper");
        var iron = template.forMaterial("minecraft:iron");
        require(copper.build().name().equals(ResourceLocation.parse("basemetals:copper_ingot")));
        require(iron.build().name().equals(ResourceLocation.parse("minecraft:iron_ingot")));
        require(template.getMaterialName() == null);
        var tool = copper.setMaxDamage(250);
        require(tool.build().maxStackSize() == 1 && tool.build().maxDamage() == 250);
        require(copper.build().maxStackSize() == 64 && copper.build().maxDamage() == 0);
        require(tool.build() != tool.build());
        var parent = ResourceLocation.parse("mmdlib:base_item");
        var finish = ResourceLocation.parse("test:finish");
        var configured = copper.setParent(parent).setType("tool").setFood(ResourceLocation.parse("test:food"))
                .setContainerItem(ResourceLocation.parse("test:container")).setUseTime(12)
                .setUseAnim(net.minecraft.world.item.UseAnim.EAT).setUseFinishMode(finish)
                .setColorHandler("test:tint").build();
        require(configured.parent().equals(parent) && configured.type().equals("tool"));
        require(configured.food().equals(ResourceLocation.parse("test:food")));
        require(configured.containerItem().equals(ResourceLocation.parse("test:container")));
        require(configured.useTime() == 12 && configured.useAnim() == net.minecraft.world.item.UseAnim.EAT);
        require(configured.useFinishMode().equals(finish) && configured.colorHandler().equals("test:tint"));
        require(template.buildFor(ResourceLocation.parse("test:tin")).name().equals(ResourceLocation.parse("test:tin_ingot")));
        var fireproof = copper.setFireResistant(true);
        require(fireproof.build().fireResistant() && !copper.build().fireResistant());
        var lore = new ArrayList<>(List.of("Copper"));
        var events = new HashMap<String, ResourceLocation>();
        events.put("use", ResourceLocation.parse("test:use"));
        String[] actions = {"axe_strip"};
        String[] tabs = {"ingredients"};
        var stack = new StackContext(null).withCount(2).withTag(new CompoundTag());
        stack.tag.putString("name", "before");
        var data = copper.setLore(lore).setEventMap(events).setToolActions(actions)
                .withCreativeMenuStack(stack, tabs);
        lore.clear(); events.clear(); actions[0] = "changed"; tabs[0] = "changed";
        stack.count = 7; stack.tag.putString("name", "after");
        var built = data.build();
        require(built.lore().equals(List.of("Copper")) && built.events().size() == 1);
        require(built.toolActions().contains("axe_strip"));
        var creative = built.creativeStacks().get(0);
        require(creative.count() == 2 && creative.tabs().equals(List.of("ingredients")));
        require(creative.tag().getString("name").equals("before"));
        creative.tag().putString("name", "mutated");
        require(creative.tag().getString("name").equals("before"));
        expectFailure(root::build);
        expectFailure(template::build);
        expectFailure(() -> tool.setMaxStackSize(64).build());
        expectFailure(() -> copper.setMaxStackSize(0));
        expectFailure(() -> copper.setMaxDamage(-1));
        expectFailure(() -> copper.setUseTime(-1));
        System.out.println("Immutable material item template checks passed");
    }
    private static void require(boolean value) {
        if (!value) throw new AssertionError("ItemBuilder assertion failed");
    }
    private static void expectFailure(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected invalid builder state to fail");
    }
}
