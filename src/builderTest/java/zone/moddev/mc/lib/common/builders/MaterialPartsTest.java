package zone.moddev.mc.lib.common.builders;

import java.util.Set;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.modules.ModuleCatalog;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import zone.moddev.mc.lib.common.types.MaterialPart;
import zone.moddev.mc.lib.common.types.MaterialParts;

public final class MaterialPartsTest {
  public static void main(String[] args) {
    require(ModuleCatalog.bundled().parts().entries().size() == 55);
    for (var part : ModuleCatalog.bundled().parts().entries().values()) {
      var encoded =
          ModuleCatalog.bundled().parts().codec().encodeStart(JsonOps.INSTANCE, part).getOrThrow();
      require(
          ModuleCatalog.bundled()
              .parts()
              .codec()
              .parse(JsonOps.INSTANCE, encoded)
              .getOrThrow()
              .equals(part));
    }
    require(
        ModuleCatalog.bundled()
            .parts()
            .codec()
            .parse(JsonOps.INSTANCE, JsonParser.parseString("\"powder\""))
            .getOrThrow()
            .equals(ModuleCatalog.bundled().parts().resolve("dust").getOrThrow()));
    require(ModuleCatalog.bundled().parts().resolve("dust").getOrThrow().id().equals("dust"));
    require(
        ModuleCatalog.bundled()
            .parts()
            .resolve("crystal")
            .getOrThrow()
            .equals(ModuleCatalog.bundled().parts().resolve("crystal").getOrThrow()));
    require(
        ModuleCatalog.bundled().parts().resolve("crystal").getOrThrow().kind()
            == MaterialPart.Kind.ITEM);
    require(
        ModuleCatalog.bundled()
            .parts()
            .codec()
            .parse(JsonOps.INSTANCE, JsonParser.parseString("\"mek_crystal\""))
            .getOrThrow()
            .equals(ModuleCatalog.bundled().parts().resolve("crystal").getOrThrow()));
    require(
        ModuleCatalog.bundled()
            .parts()
            .codec()
            .encodeStart(
                JsonOps.INSTANCE, ModuleCatalog.bundled().parts().resolve("crystal").getOrThrow())
            .getOrThrow()
            .getAsString()
            .equals("crystal"));
    for (var name : Set.of("helmet", "chestplate", "leggings", "boots")) {
      var part = ModuleCatalog.bundled().parts().resolve(name).getOrThrow();
      require(part.factory().equals(ResourceLocation.parse("mmdlib:armor")));
      require(
          part.factoryData()
                  .decode(net.minecraft.world.item.ArmorItem.Type.CODEC.fieldOf("slot").codec())
                  .getOrThrow()
              != null);
    }
    var selection =
        MaterialParts.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
    require(selection.parts().size() == 55 && selection.hasFluid());
    require(
        MaterialParts.CODEC
            .parse(
                NbtOps.INSTANCE,
                MaterialParts.CODEC.encodeStart(NbtOps.INSTANCE, selection).getOrThrow())
            .getOrThrow()
            .equals(selection));
    var planner = new MaterialContentPlanner();
    var copperId = ResourceLocation.parse("test:copper");
    var without = planner.plan(copperId, selection, mod -> false);
    var with = planner.plan(copperId, selection, mod -> mod.equals("mekanism"));
    for (var part : ModuleCatalog.bundled().parts().entries().values()) {
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
    for (var part :
        Set.of(
            ModuleCatalog.bundled().parts().resolve("arrow").getOrThrow(),
            ModuleCatalog.bundled().parts().resolve("bolt").getOrThrow())) {
      require(without.items().get(part).name().equals(without.entities().get(part).name()));
      require(without.entities().get(part).factory().equals(part.templateId()));
    }
    for (var part :
        Set.of(
            ModuleCatalog.bundled().parts().resolve("anvil").getOrThrow(),
            ModuleCatalog.bundled().parts().resolve("door").getOrThrow(),
            ModuleCatalog.bundled().parts().resolve("slab").getOrThrow()))
      require(without.items().get(part).equals(without.blocks().get(part).item()));
    require(
        !without
            .items()
            .containsKey(ModuleCatalog.bundled().parts().resolve("double_slab").getOrThrow()));
    require(
        without.blocks().containsKey(ModuleCatalog.bundled().parts().resolve("ore").getOrThrow())
            && without
                .blocks()
                .containsKey(ModuleCatalog.bundled().parts().resolve("endore").getOrThrow())
            && without
                .blocks()
                .containsKey(ModuleCatalog.bundled().parts().resolve("netherore").getOrThrow()));
    require(
        !without
            .blocks()
            .containsKey(ModuleCatalog.bundled().parts().resolve("crystal").getOrThrow()));
    var crystalOnly =
        MaterialParts.CODEC
            .parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("{\"parts\":[\"mek_crystal\"],\"has_fluid\":false}"))
            .getOrThrow();
    var crystalPlan = planner.plan(copperId, crystalOnly, mod -> mod.equals("mekanism"));
    require(
        crystalPlan.items().size() == 1
            && crystalPlan
                .items()
                .containsKey(ModuleCatalog.bundled().parts().resolve("crystal").getOrThrow()));
    require(crystalPlan.blocks().isEmpty());
    var noCrystal = planner.plan(copperId, crystalOnly, mod -> false);
    require(noCrystal.items().isEmpty() && noCrystal.blocks().isEmpty());
    require(
        MaterialParts.CODEC
            .parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("{\"parts\":[\"crystal\",\"mek_crystal\"]}"))
            .error()
            .isPresent());
    var restricted =
        MaterialParts.CODEC
            .parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("{\"parts\":[\"arrow\",\"crystal\"],\"has_fluid\":false}"))
            .getOrThrow();
    var plan = planner.plan(copperId, restricted, mod -> false);
    require(
        plan.items().size() == 1
            && plan.entities().size() == 1
            && plan.blocks().isEmpty()
            && plan.fluid() == null);
    require(
        planner.plan(copperId, new MaterialParts(Set.of(), true), mod -> false).fluid() != null);
    require(
        planner.plan(copperId, new MaterialParts(Set.of(), false), mod -> false).fluid() == null);
    var customized =
        planner
            .withItemTemplate(
                ModuleCatalog.bundled().parts().resolve("arrow").getOrThrow(),
                ItemBuilder.create().setMaxStackSize(16))
            .withBlockTemplate(
                ModuleCatalog.bundled().parts().resolve("block").getOrThrow(),
                BlockBuilder.create().setStrength(8, 16))
            .withEntityTemplate(
                ModuleCatalog.bundled().parts().resolve("bolt").getOrThrow(),
                EntityBuilder.create().setFactory("test:bolt").setDimensions(.25F, .25F))
            .withFluidTemplate(FluidBuilder.create().setName("mmdlib:molten").setTemperature(1400));
    var customPlan = customized.plan(copperId, selection, mod -> false);
    require(
        customPlan
                .items()
                .get(ModuleCatalog.bundled().parts().resolve("arrow").getOrThrow())
                .maxStackSize()
            == 16);
    require(
        without
                .items()
                .get(ModuleCatalog.bundled().parts().resolve("arrow").getOrThrow())
                .maxStackSize()
            == 64);
    require(
        customPlan
                .blocks()
                .get(ModuleCatalog.bundled().parts().resolve("block").getOrThrow())
                .properties()
                .hardness()
            == 8);
    require(
        customPlan
            .entities()
            .get(ModuleCatalog.bundled().parts().resolve("bolt").getOrThrow())
            .factory()
            .equals(ResourceLocation.parse("test:bolt")));
    require(customPlan.fluid().typeProperties().temperature() == 1400);
    var tin = customized.plan(ResourceLocation.parse("test:tin"), selection, mod -> false);
    require(
        tin.items()
            .get(ModuleCatalog.bundled().parts().resolve("arrow").getOrThrow())
            .name()
            .equals(ResourceLocation.parse("test:tin_arrow")));
    require(
        customPlan
            .items()
            .get(ModuleCatalog.bundled().parts().resolve("arrow").getOrThrow())
            .name()
            .equals(ResourceLocation.parse("test:copper_arrow")));
    require(
        new MMDMaterial(restricted).planContent(copperId, planner, mod -> false).fluid() == null);
    for (String invalid :
        new String[] {
          "{\"parts\":[\"unknown\"]}",
          "{\"parts\":[\"powder\",\"dust\"]}",
          "{\"has_fluid\":\"bad\"}"
        })
      require(
          MaterialParts.CODEC
              .parse(JsonOps.INSTANCE, JsonParser.parseString(invalid))
              .error()
              .isPresent());
    System.out.println(
        "55 canonical material parts, optional Mekanism gating, projectile companions, and default"
            + " fluid planning passed");
  }

  private static void require(boolean condition) {
    if (!condition) throw new AssertionError("Material part assertion failed");
  }
}
