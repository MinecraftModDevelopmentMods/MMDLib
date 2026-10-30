package zone.moddev.mc.lib.gui.runtime;

import net.minecraft.world.entity.player.Player;

import zone.moddev.mc.lib.gui.model.Element;

/** Validated GUI action, passed to a gui-click extension handler. */
public record ScreenClick(
    Player player,
    MachineMenu menu,
    Element element,
    int button,
    boolean shift,
    boolean control,
    boolean alt,
    int option) {}
