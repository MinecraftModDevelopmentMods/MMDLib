package zone.moddev.mc.lib.common.types;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Material-bound block definition, resolved by a content factory after material loading. */
public record MMDBlock(ResourceLocation name, ResourceLocation template, ResourceLocation material,
        ResourceLocation parent, String type, Properties properties, Set<ResourceLocation> tags,
        Map<String, ResourceLocation> events, MMDItem item) {
    public MMDBlock {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(properties, "properties");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("Block type must not be blank");
        tags = Set.copyOf(tags);
        events = Map.copyOf(events);
        events.keySet().forEach(event -> {
            if (event.isBlank()) throw new IllegalArgumentException("Event name must not be blank");
        });
        if (item != null && (!item.name().equals(name) || !item.material().equals(material)))
            throw new IllegalArgumentException("Block item must share its block's name and material");
    }

    /** Sound and map-color names are unresolved factory keys, not eager Minecraft objects. */
    public record Properties(float hardness, float blastResistance, float friction, float speedFactor,
            float jumpFactor, int lightLevel, boolean requiresCorrectTool, boolean collision,
            boolean occlusion, boolean randomTicks, boolean replaceable, String sound, String mapColor) {
        public static final Properties DEFAULT = new Properties(1.5F, 6F, 0.6F, 1F, 1F,
                0, false, true, true, false, false, "stone", "stone");
        public Properties {
            if (!Float.isFinite(hardness) || (hardness < 0 && hardness != -1))
                throw new IllegalArgumentException("Hardness must be nonnegative, or -1 for unbreakable");
            nonnegative(blastResistance, "blast resistance");
            nonnegative(friction, "friction");
            nonnegative(speedFactor, "speed factor");
            nonnegative(jumpFactor, "jump factor");
            if (lightLevel < 0 || lightLevel > 15) throw new IllegalArgumentException("Light must be in 0..15");
            if (sound == null || sound.isBlank() || mapColor == null || mapColor.isBlank())
                throw new IllegalArgumentException("Sound and map color keys must not be blank");
        }
        private static void nonnegative(float value, String name) {
            if (!Float.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid " + name);
        }
        private static final Codec<Properties> FIELDS = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("hardness", DEFAULT.hardness).forGetter(Properties::hardness),
                Codec.FLOAT.optionalFieldOf("blast_resistance", DEFAULT.blastResistance).forGetter(Properties::blastResistance),
                Codec.FLOAT.optionalFieldOf("friction", DEFAULT.friction).forGetter(Properties::friction),
                Codec.FLOAT.optionalFieldOf("speed_factor", DEFAULT.speedFactor).forGetter(Properties::speedFactor),
                Codec.FLOAT.optionalFieldOf("jump_factor", DEFAULT.jumpFactor).forGetter(Properties::jumpFactor),
                Codec.intRange(0, 15).optionalFieldOf("light_level", 0).forGetter(Properties::lightLevel),
                Codec.BOOL.optionalFieldOf("requires_correct_tool", false).forGetter(Properties::requiresCorrectTool),
                Codec.BOOL.optionalFieldOf("collision", true).forGetter(Properties::collision),
                Codec.BOOL.optionalFieldOf("occlusion", true).forGetter(Properties::occlusion),
                Codec.BOOL.optionalFieldOf("random_ticks", false).forGetter(Properties::randomTicks),
                Codec.BOOL.optionalFieldOf("replaceable", false).forGetter(Properties::replaceable),
                Codec.STRING.optionalFieldOf("sound", "stone").forGetter(Properties::sound),
                Codec.STRING.optionalFieldOf("map_color", "stone").forGetter(Properties::mapColor)
        ).apply(i, Properties::new));
        // Validate before invoking the record constructor so invalid JSON produces DataResult errors.
        public static final Codec<Properties> CODEC = Codec.PASSTHROUGH.flatXmap(dynamic -> {
            try { return FIELDS.parse(dynamic); }
            catch (IllegalArgumentException ex) { return com.mojang.serialization.DataResult.error(ex::getMessage); }
        }, value -> FIELDS.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, value)
                .map(json -> new com.mojang.serialization.Dynamic<>(com.mojang.serialization.JsonOps.INSTANCE, json)));
    }
}
