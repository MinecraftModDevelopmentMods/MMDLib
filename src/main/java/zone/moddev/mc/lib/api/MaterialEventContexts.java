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
 * The context objects passed to the built-in events in {@link MaterialEventTypes}. Each is
 * call-scoped: valid only during the dispatch that created it, so handlers must not keep them. A
 * tool target can be a block, any entity (dropped items included) or the air; a harvest carries the
 * block's drops.
 */
public final class MaterialEventContexts {
  private MaterialEventContexts() {}

  /** Context of {@code weapon-hit}: who hit what. */
  public record WeaponUse(Player player, LivingEntity target) {
    /** Requires every component. */
    public WeaponUse {
      Objects.requireNonNull(player);
      Objects.requireNonNull(target);
    }
  }

  /**
   * Context of {@code tool-left-click} and {@code tool-right-click}: who used the tool, and on
   * what.
   */
  public record ToolUse(Player player, ToolTarget target) {
    /** Requires every component. */
    public ToolUse {
      Objects.requireNonNull(player);
      Objects.requireNonNull(target);
    }
  }

  /** Context of {@code tool-harvest}: who broke the block, and the harvest itself. */
  public record ToolHarvest(Player player, Harvest harvest) {
    /** Requires every component. */
    public ToolHarvest {
      Objects.requireNonNull(player);
      Objects.requireNonNull(harvest);
    }
  }

  /**
   * A block broken by a player's tool, server side only. Drops is NeoForge's live, mutable drop
   * list: handlers may replace, remove, or add entities before they are spawned. Tool is the
   * breaking stack.
   */
  public record Harvest(
      ServerLevel level,
      BlockPos position,
      BlockState state,
      ItemStack tool,
      List<ItemEntity> drops) {
    /** Requires every component; stores an immutable copy of {@code position}. */
    public Harvest {
      Objects.requireNonNull(level);
      position = Objects.requireNonNull(position).immutable();
      Objects.requireNonNull(state);
      Objects.requireNonNull(tool);
      Objects.requireNonNull(drops);
    }
  }

  /**
   * Server side, before damage mitigation; the container may change the amount or reduction stages.
   */
  public record ProjectileDamage(
      Projectile projectile, LivingEntity target, ItemStack ammo, DamageContainer damage) {
    /** Requires every component. */
    public ProjectileDamage {
      Objects.requireNonNull(projectile);
      Objects.requireNonNull(target);
      Objects.requireNonNull(ammo);
      Objects.requireNonNull(damage);
    }
  }

  /** Server side, after damage was dealt. */
  public record ProjectileHit(Projectile projectile, LivingEntity target, ItemStack ammo) {
    /** Requires every component. */
    public ProjectileHit {
      Objects.requireNonNull(projectile);
      Objects.requireNonNull(target);
      Objects.requireNonNull(ammo);
    }
  }

  /**
   * The redstone power a block receives changed, server side only. Previous and power are 0-15 (the
   * strongest neighbouring signal); fired once per change, not for every neighbour update.
   */
  public record RedstoneChange(
      ServerLevel level, BlockPos position, BlockState state, int previous, int power) {
    /**
     * Requires every object component; stores an immutable copy of {@code position}.
     *
     * @throws IllegalArgumentException if either power level is outside 0-15
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
  }

  /** Context of {@code block-right-click}: who clicked, and the click itself. */
  public record BlockClick(Player player, BlockUse use) {
    /** Requires every component. */
    public BlockClick {
      Objects.requireNonNull(player);
      Objects.requireNonNull(use);
    }
  }

  /**
   * A right-click on a block, keyed by the block's own definition. Call-scoped like the other
   * contexts. A handler that acts on the click calls {@link #consume()} on both sides, so the held
   * item is not also used (e.g. placed against the block); the bridge then cancels the interaction
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

    /** Creates the context for one click; the position is copied to an immutable one. */
    public BlockUse(
        Level level,
        BlockPos position,
        BlockState state,
        Direction face,
        InteractionHand hand,
        ItemStack stack) {
      this.level = Objects.requireNonNull(level);
      this.position = Objects.requireNonNull(position).immutable();
      this.state = Objects.requireNonNull(state);
      this.face = Objects.requireNonNull(face);
      this.hand = Objects.requireNonNull(hand);
      this.stack = Objects.requireNonNull(stack);
    }

    /** The level the block is in; check {@code isClientSide()} before changing the world. */
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

    /** Marks the click as handled, so the held item is not also used. Call it on both sides. */
    public void consume() {
      consumed = true;
    }

    /** Whether a handler called {@link #consume()}. */
    public boolean consumed() {
      return consumed;
    }
  }

  /** What a tool was used on: a block, an entity or the air. */
  public sealed interface ToolTarget permits BlockTarget, EntityTarget, AirTarget {
    /** The hand holding the tool. */
    InteractionHand hand();

    /** The tool stack. */
    ItemStack stack();
  }

  /** A tool used on a block, from the clicked face. */
  public record BlockTarget(
      Level level,
      BlockPos position,
      BlockState state,
      Direction face,
      InteractionHand hand,
      ItemStack stack)
      implements ToolTarget {
    /** Requires every component; stores an immutable copy of {@code position}. */
    public BlockTarget {
      Objects.requireNonNull(level);
      position = Objects.requireNonNull(position).immutable();
      Objects.requireNonNull(state);
      Objects.requireNonNull(face);
      Objects.requireNonNull(hand);
      Objects.requireNonNull(stack);
    }
  }

  /** A tool used on an entity, including dropped items and other non-living entities. */
  public record EntityTarget(Entity entity, InteractionHand hand, ItemStack stack)
      implements ToolTarget {
    /** Requires every component. */
    public EntityTarget {
      Objects.requireNonNull(entity);
      Objects.requireNonNull(hand);
      Objects.requireNonNull(stack);
    }
  }

  /** A tool used on nothing (the air). */
  public record AirTarget(InteractionHand hand, ItemStack stack) implements ToolTarget {
    /** Requires every component. */
    public AirTarget {
      Objects.requireNonNull(hand);
      Objects.requireNonNull(stack);
    }
  }
}
