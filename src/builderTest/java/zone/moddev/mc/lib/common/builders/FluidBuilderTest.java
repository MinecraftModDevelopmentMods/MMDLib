package zone.moddev.mc.lib.common.builders;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.common.codecs.FluidTemplate;
import zone.moddev.mc.lib.common.parsers.FluidParser;
import java.util.HashMap;
import java.util.Optional;

public final class FluidBuilderTest {
    public static void main(String[] args) {
        var root = FluidBuilder.create();
        require(root == FluidBuilder.INSTANCE);
        var template = root.setName("mmdlib:molten").setDensity(7000).setViscosity(4000).setTemperature(1200);
        var copper = template.forMaterial("test:copper").build();
        var tin = template.forMaterial("test:tin").build();
        require(root.getTemplateName() == null && template.getMaterialName() == null);
        require(copper.sourceId().equals(ResourceLocation.parse("test:copper_molten")));
        require(copper.flowingId().equals(ResourceLocation.parse("test:flowing_copper_molten")));
        require(copper.typeId().equals(copper.sourceId()) && copper.blockId().equals(copper.sourceId()));
        require(copper.bucket().name().equals(ResourceLocation.parse("test:copper_molten_bucket")));
        require(tin.bucket().name().equals(ResourceLocation.parse("test:tin_molten_bucket")));
        require(copper.bucket().maxStackSize() == 1 && copper.block().item() == null);
        require(copper.typeProperties().density() == 7000 && copper.typeProperties().temperature() == 1200);
        var events = new HashMap<String, ResourceLocation>(); events.put("contact", ResourceLocation.parse("test:burn"));
        var changed = template.setLightLevel(12).setDensity(-100).setMotionScale(.03).setCanSwim(false).setCanDrown(false)
                .setCanExtinguish(true).setSupportsBoating(true).setCanHydrate(true).setSlopeFindDistance(2)
                .setLevelDecreasePerBlock(2).setTickRate(20).setExplosionResistance(50).setCanConvertToSource(true)
                .setTintColor(0xFFAA4400).setTextures(ResourceLocation.parse("test:still"), ResourceLocation.parse("test:flow"))
                .setOverlay(Optional.of(ResourceLocation.parse("test:overlay"))).setEventMap(events);
        events.clear();
        var built = changed.forMaterial("test:copper").build();
        require(built.events().size() == 1 && built.typeProperties().density() == -100 && built.typeProperties().lightLevel() == 12);
        require(!built.typeProperties().canSwim() && !built.typeProperties().canDrown() && built.typeProperties().canHydrate());
        require(built.flowProperties().tickRate() == 20 && built.flowProperties().canConvertToSource());
        require(built.appearance().tint() == 0xFFAA4400 && built.appearance().overlay().isPresent());
        require(copper.typeProperties().lightLevel() == 0 && copper.typeProperties().canSwim());
        var item = ItemBuilder.create().setName("test:bucket_template").setFireResistant(true);
        var block = BlockBuilder.create().setName("test:block_template").setLightLevel(4);
        var paired = template.setBucketTemplate(item).setBlockTemplate(block).forMaterial("test:copper").build();
        require(paired.bucket().fireResistant() && paired.block().properties().lightLevel() == 4);
        require(item.getMaterialName() == null && block.getMaterialName() == null);
        var disabled = template.setHasBlock(false).setHasBucket(false).forMaterial("test:copper").build();
        require(disabled.block() == null && disabled.bucket() == null && disabled.blockId() == null);
        expectFailure(root::build); expectFailure(template::build);
        expectFailure(() -> template.setViscosity(-1)); expectFailure(() -> template.setTemperature(-1));
        expectFailure(() -> template.setMotionScale(Double.NaN)); expectFailure(() -> template.setLightLevel(16));
        expectFailure(() -> template.setTickRate(0)); expectFailure(() -> template.setLevelDecreasePerBlock(9));
        var json = JsonParser.parseString("""
            {"type_properties":{"temperature":1100,"density":7000,"light_level":8},
             "flow_properties":{"tick_rate":20},"appearance":{"tint":-1},
             "bucket":{"fire_resistant":true},"block":{"properties":{"light_level":4}}}
            """).getAsJsonObject();
        var decoded = FluidTemplate.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        require(FluidTemplate.CODEC.parse(JsonOps.INSTANCE, FluidTemplate.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow())
                .getOrThrow().equals(decoded));
        require(FluidTemplate.CODEC.parse(NbtOps.INSTANCE, FluidTemplate.CODEC.encodeStart(NbtOps.INSTANCE, decoded).getOrThrow())
                .getOrThrow().equals(decoded));
        var parsed = new FluidParser(ResourceLocation.parse("mmdlib:molten"), json).parse().forMaterial("test:copper").build();
        require(parsed.typeProperties().temperature() == 1100 && parsed.bucket().fireResistant());
        for (String invalid : new String[] {"{\"type_properties\":{\"viscosity\":-1}}", "{\"type_properties\":{\"light_level\":16}}",
                "{\"flow_properties\":{\"tick_rate\":0}}", "{\"has_block\":false,\"block\":{}}",
                "{\"has_bucket\":false,\"bucket\":{}}", "{\"block\":{\"item\":{}}}"})
            require(FluidTemplate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid)).error().isPresent());
        System.out.println("Fluid builder, family IDs, optional block/bucket binding, validation, and JSON/NBT checks passed");
    }
    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Fluid builder assertion failed");
    }
    private static void expectFailure(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException ex) { return; }
        throw new AssertionError("Expected invalid fluid template to fail");
    }
}
