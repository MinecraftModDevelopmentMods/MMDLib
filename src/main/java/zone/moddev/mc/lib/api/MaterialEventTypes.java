package zone.moddev.mc.lib.api;

import java.lang.invoke.MethodType;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.common.damagesource.DamageContainer;

import zone.moddev.mc.lib.api.MaterialEventContexts.*;

/**
 * The built-in event contracts. Each is installed in every {@link MaterialEvents} with the method
 * signature listed in {@code docs/method-events.md}. MMDLib's {@code ItemEventBridge} fires them
 * from NeoForge events; this class itself installs no listeners. Mods can register further
 * contracts with {@link MaterialEvents#registerEvent}.
 */
public final class MaterialEventTypes {
  private MaterialEventTypes() {}

  /**
   * Each tick, for items worn or held by a player; handler {@code (Player)}. Runs once per bound
   * callback per tick, on both sides.
   */
  public static final MaterialEvent<Player> PLAYER_TICK =
      new MaterialEvent<>("player-tick", Player.class);

  /** A player hits a living entity with a bound item; handler {@code (Player, LivingEntity)}. */
  public static final MaterialEvent<WeaponUse> WEAPON_HIT =
      new MaterialEvent<>("weapon-hit", WeaponUse.class);

  /**
   * A player left-clicks a block, an entity or the air with a bound item; handler {@code (Player,
   * ToolTarget)}.
   */
  public static final MaterialEvent<ToolUse> TOOL_LEFT_CLICK =
      new MaterialEvent<>("tool-left-click", ToolUse.class);

  /**
   * A player right-clicks a block, an entity or the air with a bound item; handler {@code (Player,
   * ToolTarget)}.
   */
  public static final MaterialEvent<ToolUse> TOOL_RIGHT_CLICK =
      new MaterialEvent<>("tool-right-click", ToolUse.class);

  /**
   * A player breaks a block with a bound tool, server side; handler {@code (Player, Harvest)},
   * whose drop list may be changed.
   */
  public static final MaterialEvent<ToolHarvest> TOOL_HARVEST =
      new MaterialEvent<>("tool-harvest", ToolHarvest.class);

  /**
   * A bound projectile is about to damage a living entity, server side, before armor; handler
   * {@code (Projectile, LivingEntity, ItemStack, DamageContainer)}.
   */
  public static final MaterialEvent<ProjectileDamage> PROJECTILE_DAMAGE =
      new MaterialEvent<>("projectile-damage", ProjectileDamage.class);

  /**
   * A player right-clicks a bound block, keyed by the clicked block's definition, before the held
   * item's {@code tool-right-click}; handler {@code (Player, BlockUse)}.
   */
  public static final MaterialEvent<BlockClick> BLOCK_RIGHT_CLICK =
      new MaterialEvent<>("block-right-click", BlockClick.class);

  /**
   * A bound projectile has damaged a living entity, server side; handler {@code (Projectile,
   * LivingEntity, ItemStack)}.
   */
  public static final MaterialEvent<ProjectileHit> PROJECTILE_HIT =
      new MaterialEvent<>("projectile-hit", ProjectileHit.class);

  /**
   * The redstone power a bound block receives has changed, server side; fired by the block itself;
   * handler {@code (RedstoneChange)}.
   */
  public static final MaterialEvent<RedstoneChange> REDSTONE_CHANGED =
      new MaterialEvent<>("redstone-changed", RedstoneChange.class);

  public static final MaterialEvent<zone.moddev.mc.lib.gui.runtime.ScreenHover> GUI_HOVER =
      new MaterialEvent<>("gui-hover", zone.moddev.mc.lib.gui.runtime.ScreenHover.class);

  public static final MaterialEvent<zone.moddev.mc.lib.gui.runtime.ScreenClick> GUI_CLICK =
      new MaterialEvent<>("gui-click", zone.moddev.mc.lib.gui.runtime.ScreenClick.class);

  static void install(MaterialEvents events) {
    events.registerEvent(GUI_CLICK.name(), GUI_CLICK.contextType());
    events.registerEvent(GUI_HOVER.name(), GUI_HOVER.contextType());
    events.registerEvent(
        PLAYER_TICK.name(),
        Player.class,
        MethodType.methodType(void.class, Player.class),
        (method, player) -> {
          method.invokeExact(player);
        });
    events.registerEvent(
        WEAPON_HIT.name(),
        WeaponUse.class,
        MethodType.methodType(void.class, Player.class, LivingEntity.class),
        (method, context) -> {
          method.invokeExact(context.player(), context.target());
        });
    events.registerEvent(
        TOOL_LEFT_CLICK.name(),
        ToolUse.class,
        MethodType.methodType(void.class, Player.class, ToolTarget.class),
        (method, context) -> {
          method.invokeExact(context.player(), context.target());
        });
    events.registerEvent(
        TOOL_RIGHT_CLICK.name(),
        ToolUse.class,
        MethodType.methodType(void.class, Player.class, ToolTarget.class),
        (method, context) -> {
          method.invokeExact(context.player(), context.target());
        });
    events.registerEvent(
        TOOL_HARVEST.name(),
        ToolHarvest.class,
        MethodType.methodType(void.class, Player.class, Harvest.class),
        (method, context) -> {
          method.invokeExact(context.player(), context.harvest());
        });
    events.registerEvent(
        BLOCK_RIGHT_CLICK.name(),
        BlockClick.class,
        MethodType.methodType(void.class, Player.class, BlockUse.class),
        (method, context) -> {
          method.invokeExact(context.player(), context.use());
        });
    events.registerEvent(
        PROJECTILE_DAMAGE.name(),
        ProjectileDamage.class,
        MethodType.methodType(
            void.class,
            Projectile.class,
            LivingEntity.class,
            ItemStack.class,
            DamageContainer.class),
        (method, context) -> {
          method.invokeExact(
              context.projectile(), context.target(), context.ammo(), context.damage());
        });
    events.registerEvent(
        PROJECTILE_HIT.name(),
        ProjectileHit.class,
        MethodType.methodType(void.class, Projectile.class, LivingEntity.class, ItemStack.class),
        (method, context) -> {
          method.invokeExact(context.projectile(), context.target(), context.ammo());
        });
    events.registerEvent(
        REDSTONE_CHANGED.name(),
        RedstoneChange.class,
        MethodType.methodType(void.class, RedstoneChange.class),
        (method, context) -> {
          method.invokeExact(context);
        });
  }
}
