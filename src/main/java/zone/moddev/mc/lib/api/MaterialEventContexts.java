package zone.moddev.mc.lib.api;

import java.util.List;
import java.util.Objects;

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

/**
 * The context objects passed to the built-in events in
 * {@link MaterialEventTypes}. Each is call-scoped: valid only during the
 * dispatch that created it, so handlers must not keep them. A tool target can
 * be a block, any entity (dropped items included) or the air; a harvest carries
 * the block's drops.
 */
public final class MaterialEventContexts {
    private MaterialEventContexts() {
    }

    /** Context of {@code weapon-hit}: who hit what. */
    public record WeaponUse(Player player, LivingEntity target) {
        /** Requires every component. */
        public WeaponUse {
            Objects.requireNonNull(player);
            Objects.requireNonNull(target);
        }
    }

    /**
     * Context of {@code tool-left-click} and {@code tool-right-click}: who used
     * the tool, and on what.
     */
    public record ToolUse(Player player, ToolTarget target) {
        /** Requires every component. */
        public ToolUse {
            Objects.requireNonNull(player);
            Objects.requireNonNull(target);
        }
    }

    /**
     * Context of {@code tool-harvest}: who broke the block, and the harvest
     * itself.
     */
    public record ToolHarvest(Player player, Harvest harvest) {
        /** Requires every component. */
        public ToolHarvest {
            Objects.requireNonNull(player);
            Objects.requireNonNull(harvest);
        }
    }

    /**
     * A block broken by a player's tool, server side only. Drops is NeoForge's
     * live, mutable drop list: handlers may replace, remove, or add entities
     * before they are spawned. Tool is the breaking stack.
     */
    public record Harvest(ServerLevel level, BlockPos position,
        BlockState state, ItemStack tool, List<ItemEntity> drops) {
        /**
         * Requires every component; stores an immutable copy of
         * {@code position}.
         */
        public Harvest {
            Objects.requireNonNull(level);
            position = Objects.requireNonNull(position).immutable();
            Objects.requireNonNull(state);
            Objects.requireNonNull(tool);
            Objects.requireNonNull(drops);
        }
    }

    /**
     * Server side, before damage mitigation; the container may change the
     * amount or reduction stages.
     */
    public record ProjectileDamage(Projectile projectile, LivingEntity target,
        ItemStack ammo, DamageContainer damage) {
        /** Requires every component. */
        public ProjectileDamage {
            Objects.requireNonNull(projectile);
            Objects.requireNonNull(target);
            Objects.requireNonNull(ammo);
            Objects.requireNonNull(damage);
        }
    }

    /** Server side, after damage was dealt. */
    public record ProjectileHit(Projectile projectile, LivingEntity target,
        ItemStack ammo) {
        /** Requires every component. */
        public ProjectileHit {
            Objects.requireNonNull(projectile);
            Objects.requireNonNull(target);
            Objects.requireNonNull(ammo);
        }
    }

    /**
     * The redstone power a block receives changed, server side only. Previous
     * and power are 0-15 (the strongest neighbouring signal); fired once per
     * change, not for every neighbour update.
     */
    public record RedstoneChange(ServerLevel level, BlockPos position,
        BlockState state, int previous, int power) {
        /**
         * Requires every object component; stores an immutable copy of
         * {@code position}.
         *
         * @throws IllegalArgumentException if either power level is outside
         *             0-15
         */
        public RedstoneChange {
            Objects.requireNonNull(level);
            position = Objects.requireNonNull(position).immutable();
            Objects.requireNonNull(state);
            if (previous < 0 || previous > 15 || power < 0 || power > 15)
                throw new IllegalArgumentException("Redstone power is 0-15");
        }

        /** Whether the block now receives any power. */
        public boolean powered() {
            return power > 0;
        }

        /**
         * The power arriving at the block's {@code face} (a world direction)
         * right now.
         */
        public int signal(net.minecraft.core.Direction face) {
            return signal(level, position, face);
        }

        /**
         * The power arriving at {@code face} (a world direction) of the block
         * at {@code pos}, as {@link net.minecraft.world.level.Level#getSignal}
         * reports it from that neighbor.
         */
        public static int signal(net.minecraft.world.level.Level level,
            BlockPos pos, net.minecraft.core.Direction face) {
            return level.getSignal(pos.relative(face), face);
        }
    }

    /**
     * Context of {@code block-right-click}: who clicked, and the click itself.
     */
    public record BlockClick(Player player, BlockUse use) {
        /** Requires every component. */
        public BlockClick {
            Objects.requireNonNull(player);
            Objects.requireNonNull(use);
        }
    }

    /**
     * A right-click on a block, keyed by the block's own definition.
     * Call-scoped like the other contexts. A handler that acts on the click
     * calls {@link #consume()} on both sides, so the held item is not also used
     * (e.g. placed against the block); the bridge then cancels the interaction
     * as handled.
     */
    public static final class BlockUse {
        private final Level level;
        private final BlockPos position;
        private final BlockState state;
        private final Direction face;
        private final InteractionHand hand;
        private final ItemStack stack;
        private boolean consumed;

        /**
         * Creates the context for one click; the position is copied to an
         * immutable one.
         */
        public BlockUse(Level level, BlockPos position, BlockState state,
            Direction face, InteractionHand hand, ItemStack stack) {
            this.level = Objects.requireNonNull(level);
            this.position = Objects.requireNonNull(position).immutable();
            this.state = Objects.requireNonNull(state);
            this.face = Objects.requireNonNull(face);
            this.hand = Objects.requireNonNull(hand);
            this.stack = Objects.requireNonNull(stack);
        }

        /**
         * The level the block is in; check {@code isClientSide()} before
         * changing the world.
         */
        public Level level() {
            return level;
        }

        /** The clicked block's position. */
        public BlockPos position() {
            return position;
        }

        /** The clicked block's state at the time of the click. */
        public BlockState state() {
            return state;
        }

        /** The face that was clicked. */
        public Direction face() {
            return face;
        }

        /** The hand used. */
        public InteractionHand hand() {
            return hand;
        }

        /** The item in that hand, possibly empty. */
        public ItemStack stack() {
            return stack;
        }

        /**
         * Marks the click as handled, so the held item is not also used. Call
         * it on both sides.
         */
        public void consume() {
            consumed = true;
        }

        /** Whether a handler called {@link #consume()}. */
        public boolean consumed() {
            return consumed;
        }
    }

    /** What a tool was used on: a block, an entity or the air. */
    public sealed interface ToolTarget
        permits BlockTarget, EntityTarget, AirTarget {
        /** The hand holding the tool. */
        InteractionHand hand();

        /** The tool stack. */
        ItemStack stack();
    }

    /** A tool used on a block, from the clicked face. */
    public record BlockTarget(Level level, BlockPos position, BlockState state,
        Direction face, InteractionHand hand, ItemStack stack)
        implements ToolTarget {
        /**
         * Requires every component; stores an immutable copy of
         * {@code position}.
         */
        public BlockTarget {
            Objects.requireNonNull(level);
            position = Objects.requireNonNull(position).immutable();
            Objects.requireNonNull(state);
            Objects.requireNonNull(face);
            Objects.requireNonNull(hand);
            Objects.requireNonNull(stack);
        }
    }

    /**
     * A tool used on an entity, including dropped items and other non-living
     * entities.
     */
    public record EntityTarget(Entity entity, InteractionHand hand,
        ItemStack stack) implements ToolTarget {
        /** Requires every component. */
        public EntityTarget {
            Objects.requireNonNull(entity);
            Objects.requireNonNull(hand);
            Objects.requireNonNull(stack);
        }
    }

    /** A tool used on nothing (the air). */
    public record AirTarget(InteractionHand hand, ItemStack stack)
        implements ToolTarget {
        /** Requires every component. */
        public AirTarget {
            Objects.requireNonNull(hand);
            Objects.requireNonNull(stack);
        }
    }

    /**
     * Which part of using an item (eating, drawing a bow, ...) an
     * {@link ItemUse} reports.
     */
    public enum UsePhase {
        /**
         * The entity starts using the item; the duration can be changed, or the
         * use cancelled.
         */
        START,
        /** Each tick of use; the use can be cancelled. */
        TICK,
        /** The entity let go before the use finished, like releasing a bow. */
        STOP,
        /** The use finished; the stack left in hand can be replaced. */
        FINISH
    }

    /**
     * Context of the {@code item-use-*} events: an entity using a bound item
     * over time. Both sides. Results set here are written back to the NeoForge
     * event after the handler runs.
     */
    public static final class ItemUse {
        private final UsePhase phase;
        private final LivingEntity entity;
        private final ItemStack stack;
        private int duration;
        private ItemStack result;
        private boolean cancelled;

        /**
         * @param duration ticks of use: the total at {@link UsePhase#START},
         *            the ticks left otherwise
         * @param result the stack left in hand at {@link UsePhase#FINISH}, else
         *            the used stack
         */
        public ItemUse(UsePhase phase, LivingEntity entity, ItemStack stack,
            int duration, ItemStack result) {
            this.phase = Objects.requireNonNull(phase);
            this.entity = Objects.requireNonNull(entity);
            this.stack = Objects.requireNonNull(stack);
            this.duration = duration;
            this.result = Objects.requireNonNull(result);
        }

        public UsePhase phase() {
            return phase;
        }

        public LivingEntity entity() {
            return entity;
        }

        public ItemStack stack() {
            return stack;
        }

        /** The total ticks of use at start; the ticks left in later phases. */
        public int duration() {
            return duration;
        }

        /**
         * Changes the total ticks of use.
         *
         * @throws IllegalStateException outside {@link UsePhase#START}
         */
        public void setDuration(int duration) {
            if (phase != UsePhase.START)
                throw new IllegalStateException(
                    "The use duration can only change when use starts");
            this.duration = Math.max(0, duration);
        }

        /** The stack left in hand when use finishes. */
        public ItemStack result() {
            return result;
        }

        /**
         * Replaces the stack left in hand.
         *
         * @throws IllegalStateException outside {@link UsePhase#FINISH}
         */
        public void setResult(ItemStack result) {
            if (phase != UsePhase.FINISH)
                throw new IllegalStateException(
                    "The result can only be set when use finishes");
            this.result = Objects.requireNonNull(result);
        }

        /**
         * Stops the use.
         *
         * @throws IllegalStateException outside {@link UsePhase#START} and
         *             {@link UsePhase#TICK}
         */
        public void cancel() {
            if (phase != UsePhase.START && phase != UsePhase.TICK)
                throw new IllegalStateException(
                    "Only starting or ongoing use can be cancelled");
            cancelled = true;
        }

        public boolean cancelled() {
            return cancelled;
        }
    }

    /**
     * Context of {@code item-hit}: a melee hit with a bound item has dealt
     * {@code damage} to {@code
     * target}, after armor and other reductions. Server side.
     */
    public record ItemHit(LivingEntity attacker, LivingEntity target,
        ItemStack stack, float damage) {
        /** Requires every component. */
        public ItemHit {
            Objects.requireNonNull(attacker);
            Objects.requireNonNull(target);
            Objects.requireNonNull(stack);
        }
    }

    /**
     * Context of {@code item-break-block} (keyed by the held item) and
     * {@code block-broken} (keyed by the block): a player is about to break a
     * block. Server side, before the block is removed.
     */
    public static final class BlockBreak {
        // Blocks broken through mine() are not reported to item-break-block
        // again, so a 3x3 tool
        // does
        // not set off 3x3 breaks of its own.
        private static final ThreadLocal<Boolean> MINING = ThreadLocal
            .withInitial(() -> false);

        private final Player player;
        private final ServerLevel level;
        private final BlockPos position;
        private final BlockState state;
        private final ItemStack tool;
        private final Direction face;
        private boolean cancelled;

        /**
         * @param face the face being mined, or null when it is not known
         */
        public BlockBreak(Player player, ServerLevel level, BlockPos position,
            BlockState state, ItemStack tool, Direction face) {
            this.player = Objects.requireNonNull(player);
            this.level = Objects.requireNonNull(level);
            this.position = Objects.requireNonNull(position).immutable();
            this.state = Objects.requireNonNull(state);
            this.tool = Objects.requireNonNull(tool);
            this.face = face;
        }

        public Player player() {
            return player;
        }

        public ServerLevel level() {
            return level;
        }

        public BlockPos position() {
            return position;
        }

        public BlockState state() {
            return state;
        }

        /** The item in the player's main hand; empty for bare hands. */
        public ItemStack tool() {
            return tool;
        }

        /** The face being mined, or null when it is not known. */
        public Direction face() {
            return face;
        }

        /** Keeps the block: it is not broken and drops nothing. */
        public void cancel() {
            cancelled = true;
        }

        public boolean cancelled() {
            return cancelled;
        }

        /**
         * Breaks another block as this player would, with their tool: drops,
         * experience, tool wear and other mods' break checks all apply. The
         * block must be one the player could break; air, unbreakable blocks,
         * blocks the tool cannot harvest, and this break's own block (which is
         * already being broken) are skipped.
         *
         * @return whether the block was broken
         */
        public boolean mine(BlockPos other) {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer server)
                || other.equals(position))
                return false;
            var target = level.getBlockState(other);
            if (target.isAir() || target.getDestroySpeed(level, other) < 0
                || (target.requiresCorrectToolForDrops()
                    && !tool.isCorrectToolForDrops(target)))
                return false;
            boolean outer = MINING.get();
            MINING.set(true);
            try {
                return server.gameMode.destroyBlock(other);
            } finally {
                MINING.set(outer);
            }
        }

        /**
         * Whether a break now comes from {@link #mine}, inside another break's
         * handler.
         */
        public static boolean mining() {
            return MINING.get();
        }
    }

    /**
     * Context of {@code block-placed}, {@code block-stepped-on} and
     * {@code block-random-tick}: something happened to a bound block.
     * {@code entity} is the placer or the entity stepping on it, and null for
     * random ticks.
     */
    public record BlockAction(Level level, BlockPos position, BlockState state,
        Entity entity) {
        /** Requires the level, position and state. */
        public BlockAction {
            Objects.requireNonNull(level);
            position = Objects.requireNonNull(position).immutable();
            Objects.requireNonNull(state);
        }
    }

    /**
     * Context of {@code machine-cycle-start} and {@code machine-cycle-finish}:
     * an XML machine's processor started or finished a cycle, on the server.
     *
     * @param machine the machine's block entity
     * @param level its level
     * @param position its position
     * @param machineId the machine definition's ID
     * @param recipe the cycle's recipe
     * @param time the cycle's length, in ticks
     */
    public record MachineCycle(
        net.minecraft.world.level.block.entity.BlockEntity machine,
        ServerLevel level, BlockPos position,
        net.minecraft.resources.ResourceLocation machineId,
        net.minecraft.resources.ResourceLocation recipe, int time) {
        /** Requires every value. */
        public MachineCycle {
            Objects.requireNonNull(machine);
            Objects.requireNonNull(level);
            position = Objects.requireNonNull(position).immutable();
            Objects.requireNonNull(machineId);
            Objects.requireNonNull(recipe);
        }
    }
}
