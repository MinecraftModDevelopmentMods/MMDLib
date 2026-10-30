package zone.moddev.mc.lib.gui.runtime;

import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

import zone.moddev.mc.lib.gui.Screens;

/** Registration and explicit block-right-click opening. */
public final class ScreenRuntime {
  private static final DeferredRegister<MenuType<?>> REGISTER =
      DeferredRegister.create(Registries.MENU, "mmdlib");
  public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> MENUS =
      REGISTER.register("machine", () -> IMenuTypeExtension.create(MachineMenu::new));
  public static zone.moddev.mc.lib.gui.layout.TextMeasure textMeasure =
      text -> text.key().length() * 6;

  private ScreenRuntime() {}

  public static void register(IEventBus bus) {
    REGISTER.register(bus);
    bus.addListener(ScreenNetwork::register);
  }

  /** Checks extension contracts and limits before any screen can be opened. */
  public static void validate() {
    var callbacks = zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL.events().callbacks();
    for (var bound : Screens.all().values()) {
      var screen = bound.screen();
      if (screen.elements().size() > 4096)
        throw new zone.moddev.mc.lib.gui.load.ScreenException(
            screen.source(), 0, "At most 4096 elements are supported");
      for (var e : screen.elements()) {
        if (e instanceof zone.moddev.mc.lib.gui.model.Element.Slot slot && slot.index() > 4095)
          throw new zone.moddev.mc.lib.gui.load.ScreenException(
              screen.source(), e.common().line(), "Slot index exceeds 4095");
        if (e instanceof zone.moddev.mc.lib.gui.model.Element.SideConfig config
            && config.groups().size() > 682)
          throw new zone.moddev.mc.lib.gui.load.ScreenException(
              screen.source(), e.common().line(), "At most 682 side groups are supported");
        var c = e.common();
        var clicks = c.clicks();
        for (var id :
            java.util.stream.Stream.of(
                    clicks.click(),
                    clicks.rightClick(),
                    clicks.shiftClick(),
                    clicks.shiftRightClick())
                .flatMap(java.util.Optional::stream)
                .toList())
          requireCallback(
              callbacks, id, zone.moddev.mc.lib.api.MaterialEventTypes.GUI_CLICK, screen, e);
        c.onHover()
            .ifPresent(
                id ->
                    requireCallback(
                        callbacks,
                        id,
                        zone.moddev.mc.lib.api.MaterialEventTypes.GUI_HOVER,
                        screen,
                        e));
      }
    }
  }

  private static void requireCallback(
      java.util.Map<
              net.minecraft.resources.ResourceLocation,
              zone.moddev.mc.lib.common.types.MMDLibEventCallback<?>>
          callbacks,
      net.minecraft.resources.ResourceLocation id,
      zone.moddev.mc.lib.api.MaterialEvent<?> event,
      zone.moddev.mc.lib.gui.model.Screen screen,
      zone.moddev.mc.lib.gui.model.Element element) {
    var callback = callbacks.get(id);
    if (callback == null || !callback.event().equals(event))
      throw new zone.moddev.mc.lib.gui.load.ScreenException(
          screen.source(), element.common().line(), id + " must be a " + event.name() + " handler");
  }

  public static String fingerprint(zone.moddev.mc.lib.gui.model.Screen screen) {
    String model =
        screen.id()
            + "|"
            + screen.title()
            + "|"
            + screen.blockEntity()
            + "|"
            + screen.playerInventory()
            + "|"
            + screen.body()
            + "|"
            + screen.tabs();
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(model.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  public static boolean open(Player player, BlockEntity entity) {
    if (entity == null) return false;
    var bound = Screens.get(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()));
    if (bound.isEmpty() || !bound.get().blockEntityClass().isInstance(entity)) return false;
    if (player instanceof ServerPlayer server) {
      var text = bound.get().screen().title();
      Component title =
          text.fallback()
              .map(f -> Component.translatableWithFallback(text.key(), f))
              .orElseGet(() -> Component.translatable(text.key()));
      server.openMenu(
          new SimpleMenuProvider(
              (id, inventory, p) -> new MachineMenu(id, inventory, entity), title),
          buf -> {
            buf.writeBlockPos(entity.getBlockPos());
            buf.writeUtf(fingerprint(bound.get().screen()));
          });
    }
    return true;
  }
}
