package zone.moddev.mc.lib.common.builders;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import zone.moddev.mc.lib.common.codecs.EntityTemplate;
import zone.moddev.mc.lib.common.factories.EntityFactories;
import zone.moddev.mc.lib.common.factories.EntityFactory;
import zone.moddev.mc.lib.common.parsers.EntityParser;
import zone.moddev.mc.lib.common.types.FactoryData;
import zone.moddev.mc.lib.common.types.MMDEntity;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class EntityBuilderTest {
    public static void main(String[] args) {
        var root = EntityBuilder.create();
        require(root == EntityBuilder.INSTANCE);
        var settings = JsonParser.parseString("{\"power\":2}").getAsJsonObject();
        var events = new HashMap<String, ResourceLocation>();
        events.put("hit", ResourceLocation.parse("test:hit"));
        var template = root.setName("mmdlib:projectile").setFactory("test:projectile")
                .setFactoryData(new FactoryData(settings)).setEventMap(events);
        settings.addProperty("power", -1); events.clear();
        var copper = template.forMaterial("test:copper").build();
        var tin = template.forMaterial("test:tin").build();
        require(root.getTemplateName() == null && template.getMaterialName() == null);
        require(copper.name().equals(ResourceLocation.parse("test:copper_projectile")));
        require(tin.name().equals(ResourceLocation.parse("test:tin_projectile")));
        require(copper.events().size() == 1);
        copper.factoryData().json().getAsJsonObject().addProperty("power", -2);
        require(copper.factoryData().json().getAsJsonObject().get("power").getAsInt() == 2);
        var changed = template.setDimensions(1, 2).setTrackingRange(8).setUpdateInterval(10)
                .setSave(false).setSummon(false).setFireImmune(true).forMaterial("test:copper").build();
        require(changed.width() == 1 && changed.height() == 2 && changed.trackingRange() == 8);
        require(!changed.save() && !changed.summon() && changed.fireImmune());
        require(copper.width() == .5F && copper.save());
        expectFailure(root::build);
        expectFailure(template::build);
        expectFailure(() -> template.setDimensions(-1, 1));
        expectFailure(() -> template.setDimensions(Float.NaN, 1));
        expectFailure(() -> template.setTrackingRange(-1));
        expectFailure(() -> template.setUpdateInterval(0));
        var json = JsonParser.parseString("{\"factory\":\"test:projectile\",\"factory_data\":{\"power\":2}}").getAsJsonObject();
        var decoded = EntityTemplate.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        require(EntityTemplate.CODEC.parse(JsonOps.INSTANCE, EntityTemplate.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow())
                .getOrThrow().equals(decoded));
        require(EntityTemplate.CODEC.parse(NbtOps.INSTANCE, EntityTemplate.CODEC.encodeStart(NbtOps.INSTANCE, decoded).getOrThrow())
                .getOrThrow().equals(decoded));
        require(new EntityParser(ResourceLocation.parse("mmdlib:projectile"), json).parse()
                .forMaterial("test:copper").build().name().equals(copper.name()));
        for (String invalid : new String[] {"{}", "{\"factory\":\"test:a\",\"width\":-1}",
                "{\"factory\":\"test:a\",\"category\":\"invalid\"}", "{\"factory\":\"test:a\",\"update_interval\":0}"})
            require(EntityTemplate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid)).error().isPresent());
        EntityFactories factories = new EntityFactories();
        AtomicInteger instances = new AtomicInteger();
        EntityFactory<Entity, Integer> factory = new EntityFactory<>() {
            public Class<Entity> entityClass() { return Entity.class; }
            public Codec<Integer> settingsCodec() { return Codec.intRange(1, 10).fieldOf("power").codec(); }
            public Entity create(EntityType<Entity> type, Level level, MMDEntity definition, Integer power) {
                instances.incrementAndGet();
                throw new AssertionError("Live construction must be deferred until type spawning");
            }
        };
        var id = ResourceLocation.parse("test:projectile");
        factories.register(id, factory);
        var prepared = factories.prepare(copper, Entity.class);
        require(prepared.definition().equals(copper) && prepared.entityClass() == Entity.class && instances.get() == 0);
        expectFailure(() -> factories.register(id, factory));
        expectFailure(() -> factories.prepare(copper, LivingEntity.class));
        expectFailure(() -> new EntityFactories().prepare(copper, Entity.class));
        var badSettings = template.setFactoryData(new FactoryData(JsonParser.parseString("{\"power\":0}")))
                .forMaterial("test:copper").build();
        expectFailure(() -> factories.prepare(badSettings, Entity.class));
        System.out.println("Entity templates, typed factory resolution, settings validation, and JSON/NBT checks passed");
    }
    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Entity template assertion failed");
    }
    private static void expectFailure(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException ex) { return; }
        throw new AssertionError("Expected entity configuration to fail");
    }
}
