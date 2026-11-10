package zone.moddev.mc.companion.test;

import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import zone.moddev.mc.companion.ProcessorEntity;
import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.client.*;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.containers.BoxElement;
import zone.moddev.mc.lib.gui.model.elements.*;

/**
 * Subclasses inherit production renderers, snapshots and tank actions without new registrations.
 */
final class ElementInheritanceClientSmoke {
  static int energyRenders, progressRenders, tankFills, gaugeFills, stage, attempts;
  static volatile boolean prepared;

  static class Tank extends FluidTankElement {
    Tank() {
      super(
          ElementInheritanceClientSmoke.common("inherited_tank", false),
          0,
          Orientation.VERTICAL,
          36);
    }

    public GaugeFill fill(Object value) {
      tankFills++;
      return GaugeFill.solid(0xbb6699);
    }

    public Component displayText(Object value, boolean detailed) {
      return Component.literal("tank " + super.displayText(value, detailed).getString());
    }

    public void appendTooltip(TooltipContext context, List<Component> rows) {
      super.appendTooltip(context, rows);
      rows.add(displayText(context.value(), context.shiftDown()));
    }
  }

  static class Energy extends EnergyGaugeElement {
    Energy() {
      super(
          ElementInheritanceClientSmoke.common("inherited_energy", false),
          Orientation.VERTICAL,
          36,
          0xff44aa77);
    }

    public double fillRatio(Object value, Object maximum) {
      energyRenders++;
      return .25;
    }

    public Component displayText(Object value, boolean detailed) {
      return Component.literal("custom " + super.displayText(value, detailed).getString());
    }
  }

  static class Progress extends ProgressElement {
    Progress() {
      super(
          ElementInheritanceClientSmoke.common("inherited_progress", true),
          Optional.empty(),
          ProgressDirection.RIGHT);
    }

    public double fillRatio(Object value, Object maximum) {
      progressRenders++;
      return .75;
    }
  }

  static class Gauge extends GaugeElement {
    Gauge() {
      super(
          ElementInheritanceClientSmoke.common("inherited_gauge", true),
          Orientation.HORIZONTAL,
          36,
          Optional.empty());
    }

    public GaugeFill fill(Object value) {
      if (value != null && !(value instanceof Number))
        throw new AssertionError("Expected numeric gauge snapshot");
      gaugeFills++;
      return GaugeFill.fluid(
          new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), 0xff55aaff);
    }

    public double fillRatio(Object value, Object maximum) {
      return .5;
    }
  }

  static class Row extends BoxElement {
    Row() {
      super(
          Common.empty(),
          false,
          0,
          8,
          Align.START,
          List.of(new Tank(), new Energy(), new Progress(), new Gauge()));
    }
  }

  private static Common common(String id, boolean progress) {
    var c = Common.empty();
    return new Common(
        Optional.of(id),
        progress ? Optional.of(ValueRef.parse("#getProgress")) : Optional.empty(),
        progress ? Optional.of(ValueRef.parse("#getTime")) : Optional.empty(),
        c.tooltip(),
        c.tooltipShift(),
        c.clicks(),
        c.onHover(),
        c.hoverShows(),
        c.jeiRecipes(),
        0,
        0,
        0);
  }

  static void install() {
    var id = ResourceLocation.parse("mmdlib_companion:crusher");
    var old = Screens.get(id).orElseThrow().screen();
    var body = new ArrayList<>(old.body());
    body.add(new Row());
    var model =
        new Screen(
            old.id(),
            old.source(),
            old.title(),
            old.blockEntity(),
            old.playerInventory(),
            body,
            old.tabs());
    var screens = new HashMap<>(Screens.all());
    screens.put(id, ScreenBinder.bind(model, ElementInheritanceClientSmoke.class.getClassLoader()));
    Screens.install(screens);
  }

  static boolean run(MachineScreen screen) {
    var menu = screen.getMenu();
    var tank = menu.bound.screen().find("inherited_tank").orElseThrow();
    if (!(menu.value(tank, false) instanceof FluidStack fluid)) return false;
    if (++attempts > 40)
      throw new AssertionError(
          "Inherited tank interaction stuck at "
              + stage
              + ": "
              + fluid
              + ", cursor="
              + menu.getCarried());
    if (stage == 0) {
      if (tankFills == 0 || gaugeFills == 0)
        throw new AssertionError("Inherited renderer bypassed gauge fill selection");
      if (!GaugeFill.fluid(FluidStack.EMPTY).equals(GaugeFill.solid(0x777777)))
        throw new AssertionError("Empty fluid must select the default solid fill");
      var original = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 750);
      var selected = GaugeFill.fluid(original);
      original.setAmount(1);
      var returned = selected.fluid().orElseThrow();
      if (returned.getAmount() != 750) throw new AssertionError("Fill retained mutable input");
      returned.setAmount(2);
      if (selected.fluid().orElseThrow().getAmount() != 750)
        throw new AssertionError("Fill exposed mutable contents");
      var defaultGauge =
          new GaugeElement(Common.empty(), Orientation.VERTICAL, 36, Optional.empty());
      if (defaultGauge.fill(original).fluid().isEmpty())
        throw new AssertionError("Default fluid fill lost");
      if (new GaugeElement(Common.empty(), Orientation.VERTICAL, 36, Optional.of(0x123456))
          .fill(original)
          .fluid()
          .isPresent()) throw new AssertionError("Configured solid must override fluid");
      if (energyRenders == 0 || progressRenders == 0)
        throw new AssertionError("Inherited renderer bypassed element fillRatio");
      if (screen.tooltipLines(tank, 0, 0).stream()
          .noneMatch(row -> row.getString().equals("tank " + fluid.getAmount())))
        throw new AssertionError("Tank subclass display hook not composed");
      var energy = menu.bound.screen().find("inherited_energy").orElseThrow();
      if (screen.tooltipLines(energy, 0, 0).stream()
          .noneMatch(row -> row.getString().startsWith("custom ")))
        throw new AssertionError("Inherited energy tooltip bypassed displayText");
      var mc = Minecraft.getInstance();
      Screenshot.grab(
          mc.gameDirectory, "inherited-elements.png", mc.getMainRenderTarget(), ignored -> {});
      mc.getSingleplayerServer()
          .execute(
              () -> {
                try {
                  var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                  var machine =
                      (ProcessorEntity)
                          ((zone.moddev.mc.lib.gui.runtime.MachineMenu) player.containerMenu)
                              .blockEntity;
                  machine.lanes.forEach(lane -> lane.reset());
                  for (int i = 0; i < machine.items.getSlots(); i++)
                    machine.items.setStackInSlot(i, ItemStack.EMPTY);
                  machine.energy.setStored(0);
                  machine.fluids.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
                  player.getAbilities().instabuild = false;
                  player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
                  player.containerMenu.broadcastChanges();
                  prepared = true;
                } catch (Throwable failure) {
                  CompanionClientSmoke.fail(failure);
                }
              });
      stage = 1;
      return false;
    }
    if (!prepared) return false;
    var r = screen.bounds(tank);
    if (stage == 1) {
      if (!fluid.isEmpty() || !menu.getCarried().is(Items.WATER_BUCKET)) return false;
      screen.mouseClicked(r.x() + 8, r.y() + 8, 0);
      screen.mouseReleased(r.x() + 8, r.y() + 8, 0);
      stage = 2;
      return false;
    }
    if (stage == 2) {
      if (fluid.getAmount() != 1000 || !menu.getCarried().is(Items.BUCKET)) return false;
      screen.mouseClicked(r.x() + 8, r.y() + 8, 1);
      screen.mouseReleased(r.x() + 8, r.y() + 8, 1);
      stage = 3;
      return false;
    }
    return fluid.isEmpty() && menu.getCarried().is(Items.WATER_BUCKET);
  }
}
