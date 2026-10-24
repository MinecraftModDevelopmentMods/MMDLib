package zone.moddev.mc.lib.common.builders;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.common.types.MaterialPart;
import zone.moddev.mc.lib.common.types.MaterialParts;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import java.util.Set;

public final class MaterialPartsTest {
    public static void main(String[] args) {
        require(MaterialPart.values().length == 55);
        for (var part : MaterialPart.values()) {
            var encoded = MaterialPart.CODEC.encodeStart(JsonOps.INSTANCE, part).getOrThrow();
            require(MaterialPart.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow() == part);
        }
        require(MaterialPart.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"powder\"")).getOrThrow() == MaterialPart.POWDER);
        require(MaterialPart.POWDER.id().equals("dust"));
        var selection = MaterialParts.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        require(selection.parts().size() == 55 && selection.hasFluid());
        require(MaterialParts.CODEC.parse(NbtOps.INSTANCE, MaterialParts.CODEC.encodeStart(NbtOps.INSTANCE, selection).getOrThrow())
                .getOrThrow().equals(selection));
        var planner = new MaterialContentPlanner();
        var copperId = ResourceLocation.parse("test:copper");
        var without = planner.plan(copperId, selection, mod -> false);
        var with = planner.plan(copperId, selection, mod -> mod.equals("mekanism"));
        for (var part : MaterialPart.values()) {
            boolean optional = part.requiredMod().isPresent();
            if (part.kind() == MaterialPart.Kind.ITEM) {
                require(with.items().containsKey(part));
                require(without.items().containsKey(part) != optional);
            } else {
                require(with.blocks().containsKey(part));
                require(without.blocks().containsKey(part) != optional);
            }
        }
        require(without.fluid() != null && with.fluid() != null);
        require(without.entities().size() == 2);
        for (var part : Set.of(MaterialPart.ARROW, MaterialPart.BOLT)) {
            require(without.items().get(part).name().equals(without.entities().get(part).name()));
            require(without.entities().get(part).factory().equals(part.templateId()));
        }
        for (var part : Set.of(MaterialPart.ANVIL, MaterialPart.DOOR, MaterialPart.SLAB))
            require(without.items().get(part).equals(without.blocks().get(part).item()));
        require(!without.items().containsKey(MaterialPart.DOUBLE_SLAB));
        require(without.blocks().containsKey(MaterialPart.ORE) && without.blocks().containsKey(MaterialPart.ENDORE)
                && without.blocks().containsKey(MaterialPart.NETHERORE));
        require(!without.blocks().containsKey(MaterialPart.MEK_CRYSTAL));
        var restricted = MaterialParts.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"parts\":[\"arrow\",\"crystal\"],\"has_fluid\":false}")).getOrThrow();
        var plan = planner.plan(copperId, restricted, mod -> false);
        require(plan.items().size() == 1 && plan.entities().size() == 1 && plan.blocks().isEmpty() && plan.fluid() == null);
        require(planner.plan(copperId, new MaterialParts(Set.of(), true), mod -> false).fluid() != null);
        require(planner.plan(copperId, new MaterialParts(Set.of(), false), mod -> false).fluid() == null);
        var customized = planner.withItemTemplate(MaterialPart.ARROW, ItemBuilder.create().setMaxStackSize(16))
                .withBlockTemplate(MaterialPart.BLOCK, BlockBuilder.create().setStrength(8, 16))
                .withEntityTemplate(MaterialPart.BOLT, EntityBuilder.create().setFactory("test:bolt").setDimensions(.25F, .25F))
                .withFluidTemplate(FluidBuilder.create().setName("mmdlib:molten").setTemperature(1400));
        var customPlan = customized.plan(copperId, selection, mod -> false);
        require(customPlan.items().get(MaterialPart.ARROW).maxStackSize() == 16);
        require(without.items().get(MaterialPart.ARROW).maxStackSize() == 64);
        require(customPlan.blocks().get(MaterialPart.BLOCK).properties().hardness() == 8);
        require(customPlan.entities().get(MaterialPart.BOLT).factory().equals(ResourceLocation.parse("test:bolt")));
        require(customPlan.fluid().typeProperties().temperature() == 1400);
        var tin = customized.plan(ResourceLocation.parse("test:tin"), selection, mod -> false);
        require(tin.items().get(MaterialPart.ARROW).name().equals(ResourceLocation.parse("test:tin_arrow")));
        require(customPlan.items().get(MaterialPart.ARROW).name().equals(ResourceLocation.parse("test:copper_arrow")));
        require(new MMDMaterial(restricted).planContent(copperId, planner, mod -> false).fluid() == null);
        for (String invalid : new String[] {"{\"parts\":[\"unknown\"]}", "{\"parts\":[\"powder\",\"dust\"]}", "{\"has_fluid\":\"bad\"}"})
            require(MaterialParts.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid)).error().isPresent());
        System.out.println("55 material parts, optional Mekanism gating, projectile companions, and default fluid planning passed");
    }
    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Material part assertion failed");
    }
}
