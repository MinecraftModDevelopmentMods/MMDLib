package zone.moddev.mc.lib.gui.runtime.network;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.runtime.*;

/** Built-in contributors use the same registration path as consumer mods. */
public final class BuiltinNetworks {
  private BuiltinNetworks() {}

  static void register(NetworkRegistry registry) {
    registry.register(
        ResourceLocation.parse("mmdlib:screen"), ScreenNetwork.VERSION, ScreenNetwork::register);
    registry.register(
        ResourceLocation.parse("mmdlib:automation"),
        AutomationNetwork.VERSION,
        AutomationNetwork::register);
    registry.register(
        ResourceLocation.parse("mmdlib:redstone"),
        RedstoneNetwork.VERSION,
        RedstoneNetwork::register);
    registry.register(
        ResourceLocation.parse("mmdlib:container_drawer"),
        ContainerDrawerNetwork.VERSION,
        ContainerDrawerNetwork::register);
  }
}
