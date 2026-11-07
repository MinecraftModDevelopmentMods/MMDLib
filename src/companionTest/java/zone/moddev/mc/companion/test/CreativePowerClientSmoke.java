package zone.moddev.mc.companion.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.layout.AutomationLayout;

/** Generator side settings round trip through the actual drawer and server. */
final class CreativePowerClientSmoke {
  private static int step;

  static boolean run(MachineScreen screen) {
    var menu = screen.getMenu();
    var automation = menu.automation;
    if (automation == null) throw new AssertionError("Generator must expose its energy drawer");
    if (automation.pending()) return false;
    int top = RelativeFace.TOP.ordinal();
    switch (step) {
      case 0 -> {
        var left = automation.inventory.group(0).world(RelativeFace.LEFT);
        if (left != net.minecraft.core.Direction.WEST
            || !menu.blockEntity
                .getLevel()
                .getBlockState(menu.blockEntity.getBlockPos().relative(left))
                .is(zone.moddev.mc.companion.CompanionMod.CRUSHER.block.get()))
          throw new AssertionError(
              "Generator drawer places the western crusher on its left when facing south");
        if (!menu.slots.isEmpty()
            || automation.addonsEnabled()
            || screen.layout.inventory().isPresent()
            || screen.layout.inventoryColors().isPresent()
            || !menu.containerDrawer.state().icons().isEmpty()
            || !menu.curiosDrawer.state().icons().isEmpty())
          throw new AssertionError("Generator has no inventory, addon slots or equipment drawers");
        if (menu.stats(0).size() != 3
            || menu.stats(0).stream().anyMatch(c -> c.getString().contains("RF used")))
          throw new AssertionError("Generator statistics only show output and output limit");
        if (menu.stats(2).stream().noneMatch(c -> c.getString().contains("1,200,000 RF/min")))
          throw new AssertionError("Generator maximum output follows the minute timescale");
        var panel = screen.panelBounds();
        click(screen, panel.right() + 5, panel.y() + AutomationLayout.TOP + 5);
      }
      case 1 -> {
        if (!automation.open()) throw new AssertionError("Generator side tab opens drawer");
        var drawer =
            screen.exclusions().stream()
                .filter(r -> r.x() >= screen.panelBounds().right() && r.width() > 22)
                .findFirst()
                .orElseThrow();
        if (drawer.height()
            != AutomationLayout.face(RelativeFace.BOTTOM.ordinal()).bottom()
                + AutomationLayout.PADDING)
          throw new AssertionError("Side-only drawer ends below the faces without addon space");
        var face = AutomationLayout.face(top).offset(drawer.x(), drawer.y());
        click(screen, face.x() + 8, face.y() + 8);
      }
      case 2 -> {
        if (automation.mode(0, top) != IoMode.DISABLED)
          throw new AssertionError("Generator top face disabled through client/server action");
        var info = screen.bounds(menu.bound.screen().find("info").orElseThrow());
        click(screen, info.x() + 5, info.bottom() + 7);
      }
      case 3 -> {
        var panel = screen.panelBounds();
        int x = panel.x() - AutomationLayout.RAIL - 35;
        int y = panel.y() + AutomationLayout.TOP + 10;
        click(screen, x, y);
        click(screen, x, y);
      }
      case 4 ->
          Screenshot.grab(
              Minecraft.getInstance().gameDirectory,
              "creative-power-sides-stats.png",
              Minecraft.getInstance().getMainRenderTarget(),
              m -> {});
      case 5 -> automation.send("face", 0, -1, IoMode.DISABLED.name(), top, "");
      case 6 -> {
        if (automation.mode(0, top) != IoMode.OUTPUT)
          throw new AssertionError("Generator top face re-enabled");
        screen.keyPressed(256, 0, 0);
        var info = screen.bounds(menu.bound.screen().find("info").orElseThrow());
        click(screen, info.x() + 5, info.y() + 5);
      }
      case 7 ->
          Screenshot.grab(
              Minecraft.getInstance().gameDirectory,
              "creative-power-info.png",
              Minecraft.getInstance().getMainRenderTarget(),
              m -> {});
      case 8 -> {
        var info = screen.bounds(menu.bound.screen().find("info").orElseThrow());
        click(screen, info.x() + 5, info.y() + 5);
        automation.send("close", 0, -1, "", 0, "");
      }
      default -> {
        return true;
      }
    }
    step++;
    return false;
  }

  private static void click(MachineScreen screen, int x, int y) {
    screen.mouseClicked(x, y, 0);
    screen.mouseReleased(x, y, 0);
  }
}
