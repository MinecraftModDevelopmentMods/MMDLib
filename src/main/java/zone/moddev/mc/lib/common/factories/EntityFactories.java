package zone.moddev.mc.lib.common.factories;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import zone.moddev.mc.lib.common.types.MMDEntity;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Code-owned factory catalog. Java implementations register under the IDs referenced by templates. */
public final class EntityFactories {
    private final ConcurrentHashMap<ResourceLocation, EntityFactory<?, ?>> factories = new ConcurrentHashMap<>();

    public <E extends Entity, S> void register(ResourceLocation id, EntityFactory<E, S> factory) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(factory.entityClass(), "entityClass");
        Objects.requireNonNull(factory.settingsCodec(), "settingsCodec");
        if (factories.putIfAbsent(id, factory) != null)
            throw new IllegalArgumentException("Entity factory already registered: " + id);
    }

    public <E extends Entity> Prepared<E> prepare(MMDEntity definition, Class<E> expectedClass) {
        return prepare(definition, expectedClass, JsonOps.INSTANCE);
    }

    /** Pass RegistryOps when a factory's settings codec needs registry-backed values. */
    public <E extends Entity> Prepared<E> prepare(MMDEntity definition, Class<E> expectedClass, DynamicOps<JsonElement> ops) {
        var factory = factories.get(definition.factory());
        if (factory == null) throw new IllegalArgumentException("Unknown entity factory: " + definition.factory());
        return prepareCaptured(definition, checked(factory, expectedClass), ops);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Entity> EntityFactory<E, ?> checked(EntityFactory<?, ?> factory, Class<E> expectedClass) {
        if (!Objects.requireNonNull(expectedClass).equals(factory.entityClass()))
            throw new IllegalArgumentException("Entity factory produces " + factory.entityClass().getName()
                    + ", requested " + expectedClass.getName());
        return (EntityFactory<E, ?>) factory;
    }

    private static <E extends Entity, S> Prepared<E> prepareCaptured(MMDEntity definition, EntityFactory<E, S> factory,
                                                                   DynamicOps<JsonElement> ops) {
        S settings = definition.factoryData().decode(factory.settingsCodec(), ops).getOrThrow(message ->
                new IllegalArgumentException(definition.name() + " factory " + definition.factory() + ": " + message));
        Class<E> entityClass = factory.entityClass();
        EntityType.EntityFactory<E> constructor = (type, level) -> {
            E entity = entityClass.cast(Objects.requireNonNull(factory.create(type, level, definition, settings),
                    "Entity factory returned null"));
            if (entity.getType() != type || entity.level() != level)
                throw new IllegalStateException("Entity factory must use the supplied type and level: " + definition.factory());
            return entity;
        };
        return new Prepared<>(definition, entityClass, constructor, builder -> factory.configureType(builder, definition, settings));
    }

    /** Settings are validated now; buildType is deferred until the consuming mod's registry supplier runs. */
    public static final class Prepared<E extends Entity> {
        private final MMDEntity definition;
        private final Class<E> entityClass;
        private final EntityType.EntityFactory<E> constructor;
        private final Consumer<EntityType.Builder<E>> configure;
        private Prepared(MMDEntity definition, Class<E> entityClass, EntityType.EntityFactory<E> constructor,
                         Consumer<EntityType.Builder<E>> configure) {
            this.definition = definition;
            this.entityClass = entityClass;
            this.constructor = constructor;
            this.configure = configure;
        }
        public MMDEntity definition() { return definition; }
        public Class<E> entityClass() { return entityClass; }
        public EntityType<E> buildType() {
            var builder = EntityType.Builder.of(constructor, definition.category())
                    .sized(definition.width(), definition.height()).clientTrackingRange(definition.trackingRange())
                    .updateInterval(definition.updateInterval());
            if (!definition.save()) builder.noSave();
            if (!definition.summon()) builder.noSummon();
            if (definition.fireImmune()) builder.fireImmune();
            configure.accept(builder);
            return builder.build(definition.name().toString());
        }
    }
}
