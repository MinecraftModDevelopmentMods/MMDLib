package zone.moddev.mc.lib.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import java.util.List;
import java.util.Objects;

/** Call-scoped context; tool targets can be blocks, any entity (including dropped items), or air; harvests carry block drops. */
public final class MaterialEventContexts {
    private MaterialEventContexts() {}
    public record WeaponUse(Player player, LivingEntity target) {
        public WeaponUse { Objects.requireNonNull(player); Objects.requireNonNull(target); }
    }
    public record ToolUse(Player player, ToolTarget target) {
        public ToolUse { Objects.requireNonNull(player); Objects.requireNonNull(target); }
    }
    public record ToolHarvest(Player player, Harvest harvest) {
        public ToolHarvest { Objects.requireNonNull(player); Objects.requireNonNull(harvest); }
    }
    /**
     * A block broken by a player's tool, server side only. Drops is NeoForge's live, mutable drop list:
     * handlers may replace, remove, or add entities before they are spawned. Tool is the breaking stack.
     */
    public record Harvest(ServerLevel level, BlockPos position, BlockState state, ItemStack tool, List<ItemEntity> drops) {
        public Harvest {
            Objects.requireNonNull(level); position = Objects.requireNonNull(position).immutable();
            Objects.requireNonNull(state); Objects.requireNonNull(tool); Objects.requireNonNull(drops);
        }
    }
    /** Server side, before damage mitigation; the container may change the amount or reduction stages. */
    public record ProjectileDamage(Projectile projectile, LivingEntity target, ItemStack ammo, DamageContainer damage) {
        public ProjectileDamage {
            Objects.requireNonNull(projectile); Objects.requireNonNull(target); Objects.requireNonNull(ammo); Objects.requireNonNull(damage);
        }
    }
    /** Server side, after damage was dealt. */
    public record ProjectileHit(Projectile projectile, LivingEntity target, ItemStack ammo) {
        public ProjectileHit { Objects.requireNonNull(projectile); Objects.requireNonNull(target); Objects.requireNonNull(ammo); }
    }
    public record BlockClick(Player player, BlockUse use) {
        public BlockClick { Objects.requireNonNull(player); Objects.requireNonNull(use); }
    }
    /**
     * A right-click on a block, keyed by the block's own definition. Call-scoped like the other contexts.
     * A handler that acts on the click calls {@link #consume()} on both sides, so the held item is not
     * also used (e.g. placed against the block); the bridge then cancels the interaction as handled.
     */
    public static final class BlockUse {
        private final Level level; private final BlockPos position; private final BlockState state;
        private final Direction face; private final InteractionHand hand; private final ItemStack stack;
        private boolean consumed;
        public BlockUse(Level level, BlockPos position, BlockState state, Direction face, InteractionHand hand, ItemStack stack) {
            this.level = Objects.requireNonNull(level); this.position = Objects.requireNonNull(position).immutable();
            this.state = Objects.requireNonNull(state); this.face = Objects.requireNonNull(face);
            this.hand = Objects.requireNonNull(hand); this.stack = Objects.requireNonNull(stack);
        }
        public Level level() { return level; }
        public BlockPos position() { return position; }
        public BlockState state() { return state; }
        public Direction face() { return face; }
        public InteractionHand hand() { return hand; }
        public ItemStack stack() { return stack; }
        public void consume() { consumed = true; }
        public boolean consumed() { return consumed; }
    }
    public sealed interface ToolTarget permits BlockTarget, EntityTarget, AirTarget {
        InteractionHand hand();
        ItemStack stack();
    }
    public record BlockTarget(Level level, BlockPos position, BlockState state, Direction face,
            InteractionHand hand, ItemStack stack) implements ToolTarget {
        public BlockTarget {
            Objects.requireNonNull(level); position = Objects.requireNonNull(position).immutable();
            Objects.requireNonNull(state); Objects.requireNonNull(face); Objects.requireNonNull(hand); Objects.requireNonNull(stack);
        }
    }
    public record EntityTarget(Entity entity, InteractionHand hand, ItemStack stack) implements ToolTarget {
        public EntityTarget { Objects.requireNonNull(entity); Objects.requireNonNull(hand); Objects.requireNonNull(stack); }
    }
    public record AirTarget(InteractionHand hand, ItemStack stack) implements ToolTarget {
        public AirTarget { Objects.requireNonNull(hand); Objects.requireNonNull(stack); }
    }
}
