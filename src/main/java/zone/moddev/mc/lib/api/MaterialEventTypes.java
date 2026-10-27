package zone.moddev.mc.lib.api;

import java.lang.invoke.MethodType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import zone.moddev.mc.lib.api.MaterialEventContexts.*;

/** Default event contracts. Mods can register additional typed method signatures. No bus listeners are installed here. */
public final class MaterialEventTypes {
    private MaterialEventTypes() {}
    public static final MaterialEvent<Player> PLAYER_TICK = new MaterialEvent<>("player-tick", Player.class);
    public static final MaterialEvent<WeaponUse> WEAPON_HIT = new MaterialEvent<>("weapon-hit", WeaponUse.class);
    public static final MaterialEvent<ToolUse> TOOL_LEFT_CLICK = new MaterialEvent<>("tool-left-click", ToolUse.class);
    public static final MaterialEvent<ToolUse> TOOL_RIGHT_CLICK = new MaterialEvent<>("tool-right-click", ToolUse.class);
    public static final MaterialEvent<ToolHarvest> TOOL_HARVEST = new MaterialEvent<>("tool-harvest", ToolHarvest.class);
    public static final MaterialEvent<ProjectileDamage> PROJECTILE_DAMAGE = new MaterialEvent<>("projectile-damage", ProjectileDamage.class);
    public static final MaterialEvent<ProjectileHit> PROJECTILE_HIT = new MaterialEvent<>("projectile-hit", ProjectileHit.class);
    static void install(MaterialEvents events) {
        events.registerEvent(PLAYER_TICK.name(), Player.class, MethodType.methodType(void.class, Player.class),
                (method, player) -> { method.invokeExact(player); });
        events.registerEvent(WEAPON_HIT.name(), WeaponUse.class, MethodType.methodType(void.class, Player.class, LivingEntity.class),
                (method, context) -> { method.invokeExact(context.player(), context.target()); });
        events.registerEvent(TOOL_LEFT_CLICK.name(), ToolUse.class, MethodType.methodType(void.class, Player.class, ToolTarget.class),
                (method, context) -> { method.invokeExact(context.player(), context.target()); });
        events.registerEvent(TOOL_RIGHT_CLICK.name(), ToolUse.class, MethodType.methodType(void.class, Player.class, ToolTarget.class),
                (method, context) -> { method.invokeExact(context.player(), context.target()); });
        events.registerEvent(TOOL_HARVEST.name(), ToolHarvest.class, MethodType.methodType(void.class, Player.class, Harvest.class),
                (method, context) -> { method.invokeExact(context.player(), context.harvest()); });
        events.registerEvent(PROJECTILE_DAMAGE.name(), ProjectileDamage.class, MethodType.methodType(void.class,
                Projectile.class, LivingEntity.class, ItemStack.class, DamageContainer.class), (method, context) -> {
                    method.invokeExact(context.projectile(), context.target(), context.ammo(), context.damage()); });
        events.registerEvent(PROJECTILE_HIT.name(), ProjectileHit.class, MethodType.methodType(void.class,
                Projectile.class, LivingEntity.class, ItemStack.class),
                (method, context) -> { method.invokeExact(context.projectile(), context.target(), context.ammo()); });
    }
}
