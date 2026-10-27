package zone.moddev.mc.lib.common.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import zone.moddev.mc.lib.common.types.MaterialPart;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Select actual vanilla or BMe-derived stateful classes; never fall back for an unknown behavior. */
public final class StandardBlocks {
    private StandardBlocks() {}
    public static class Bookshelf extends Block {
        public Bookshelf(Properties properties) { super(properties); }
        @Override public float getEnchantPowerBonus(BlockState state, LevelReader level, BlockPos pos) { return 1; }
    }
    /**
     * Which entities press a plate, from the pressure-plate module's {@code factory_data.detects}:
     * {@code everything}, {@code mobs}, {@code players}, or an entity-type tag such as {@code #example:detectable}.
     * Absent, the plate follows its block set type like a vanilla plate.
     */
    public record Detects(Predicate<Entity> filter) {
        public static Optional<Detects> of(MaterialPart part) {
            var json = part.factoryData().json();
            if (!json.isJsonObject() || !json.getAsJsonObject().has("detects")) return Optional.empty();
            String value = json.getAsJsonObject().get("detects").getAsString();
            if (value.startsWith("#")) {
                var tag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(value.substring(1)));
                return Optional.of(new Detects(entity -> entity.getType().is(tag)));
            }
            return Optional.of(new Detects(switch (value) {
                case "everything" -> entity -> true;
                case "mobs" -> entity -> entity instanceof LivingEntity;
                case "players" -> entity -> entity instanceof Player;
                default -> throw new IllegalArgumentException("Unknown pressure plate detects value '" + value + "' in " + part
                        + "; expected everything, mobs, players, or #<entity type tag>");
            }));
        }
    }
    /** A pressure plate pressed only by entities its {@link Detects} filter accepts, e.g. a player-only plate. */
    public static class DetectorPressurePlate extends PressurePlateBlock {
        private final Detects detects;
        public DetectorPressurePlate(BlockSetType type, Detects detects, Properties properties) { super(type, properties); this.detects = detects; }
        @Override protected int getSignalStrength(Level level, BlockPos pos) {
            return level.getEntitiesOfClass(Entity.class, TOUCH_AABB.move(pos), entity -> !entity.isSpectator()
                    && !entity.isIgnoringBlockTriggers() && detects.filter().test(entity)).isEmpty() ? 0 : 15;
        }
    }
}
