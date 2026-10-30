package zone.moddev.mc.lib.smoke;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;

import net.neoforged.neoforge.fluids.FluidStack;

import io.netty.buffer.Unpooled;

import zone.moddev.mc.lib.api.*;
import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.load.*;
import zone.moddev.mc.lib.gui.runtime.*;
import zone.moddev.mc.lib.smoke.MachineSmoke.Counter;

/** Game-runtime coverage, exercised by the isolated GUI client smoke run. */
public final class GuiRuntimeChecks {
  public static final ResourceLocation ID = ResourceLocation.parse("mmdlib_smoke:counter");

  public static void install() {
    MaterialExtensions.GLOBAL
        .events()
        .registerCallback(
            ResourceLocation.parse("mmdlib_smoke:click"),
            MaterialEventTypes.GUI_CLICK,
            c -> ((Counter) c.menu().blockEntity).clicks++);
    String xml =
        """
<screen xmlns="https://moddev.zone/mmdlib/screen" title="test.title" title-fallback="GUI runtime smoke"
        block-entity="zone.moddev.mc.lib.smoke.MachineSmoke$Counter">
  <vbox padding="7" spacing="4">
    <context id="main">
      <view id="machine">
        <hbox spacing="6">
          <group id="input" label="test.input" label-fallback="Input"><slot id="input_slot" index="0"/></group>
          <gauge id="energy" orientation="vertical" length="72" color="#C0392B" value="#getGuiEnergy" max="#getGuiCapacity" tooltip="test.energy" tooltip-fallback="Energy: {value} / {max}"/>
          <progress id="progress" value="#getGuiEnergy" max="#getGuiCapacity" jei-recipes="mmdlib:crushing"/>
          <gauge id="water" orientation="vertical" length="72" value="#getGuiWater" max="#getGuiWaterCapacity"/>
          <slot id="ghost" index="2" kind="ghost"/>
        </hbox>
        <meter orientation="horizontal" thickness="5" length="100" color="#3F76E4" value="#getGuiEnergy" max="#getGuiCapacity"/>
        <checkbox text="test.check" text-fallback="Automatic" value="#isGuiEnabled" on-click="mmdlib_smoke:click"/>
        <radio-group id="radio" value="#getGuiMode" on-click="mmdlib_smoke:click"><radio text="test.a" text-fallback="First"/><radio text="test.b" text-fallback="Second"/></radio-group>
        <label text="test.label" text-fallback="Server-bound values"/>
      </view>
      <view id="sides" title="test.sides" title-fallback="Side configuration">
        <side-config id="config" groups="input water energy"/>
        <slot index="1"/>
      </view>
    </context>
    <hbox spacing="4"><button id="navigate" shows="main:sides" text="test.nav" text-fallback="Sides" tooltip="test.nav.tip" tooltip-fallback="Choose resource faces"/><spacer/><button id="action" text="test.action" text-fallback="Action" tooltip="test.action.tip" tooltip-fallback="Server checked action" on-click="mmdlib_smoke:click"/></hbox>
  </vbox>
  <side-tab edge="right"><slot index="3" kind="upgrade" icon="mmdlib:upgrade_speed"/></side-tab>
  <popout id="info" edge="left" kind="info" text="test.info" text-fallback="*Information* about this machine.\n\nA second paragraph."/>
  <popout edge="left" kind="warning" value="#getGuiWarnings"/>
  <popout edge="right" kind="error" value="#getGuiError"/>
</screen>
""";
    try {
      var screen =
          ScreenReader.read(
              ID,
              "gui-runtime-smoke.xml",
              new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      var bound = ScreenBinder.bind(screen, GuiRuntimeChecks.class.getClassLoader());
      var all = new HashMap<>(Screens.all());
      all.put(ID, bound);
      Screens.install(all);
      ScreenRuntime.validate();
    } catch (java.io.IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public static void check(ServerPlayer player, Counter entity) {
    var menu = new MachineMenu(73, player.getInventory(), entity);
    var previous = player.containerMenu;
    player.containerMenu = menu;
    try {
      require(menu.stillValid(player), "Menu unexpectedly invalid");
      require(
          !menu.slots.get(1).isActive()
              && !menu.slots.get(1).mayPlace(new ItemStack(Items.IRON_INGOT)),
          "Hidden slot accepts items");
      entity.items.setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 3));
      menu.clicked(1, 0, ClickType.PICKUP, player);
      require(
          entity.items.getStackInSlot(1).getCount() == 3 && menu.getCarried().isEmpty(),
          "Hidden pickup");
      require(menu.quickMoveStack(player, 1).isEmpty(), "Hidden quick move");
      int action = index(menu, "action"),
          nav = index(menu, "navigate"),
          config = index(menu, "config"),
          ghost = index(menu, "ghost");
      menu.action(player, new ScreenNetwork.Action(74, action, 0, 0, 0, 0));
      // Direct dispatch also checks the sender's current menu; packet/menu ID is checked by the
      // button channel.
      int clicks = entity.clicks;
      menu.clickMenuButton(player, new ScreenNetwork.Action(73, action, 0, 0, 7, 0).pack());
      require(entity.clicks == clicks + 1, "Action handler not called");
      menu.clickMenuButton(player, new ScreenNetwork.Action(73, action, 0, 0, 0, 1).pack());
      menu.action(player, new ScreenNetwork.Action(73, -1, 0, 0, 0, 0));
      require(entity.clicks == clicks + 1, "Malformed action executed");
      int before =
          entity
              .sides
              .get("input")
              .get(zone.moddev.mc.lib.api.capabilities.RelativeFace.FRONT)
              .ordinal();
      menu.action(player, new ScreenNetwork.Action(73, config, 3, 0, 0, 0));
      require(
          entity
                  .sides
                  .get("input")
                  .get(zone.moddev.mc.lib.api.capabilities.RelativeFace.FRONT)
                  .ordinal()
              == before,
          "Hidden side action");
      require(menu.ghost(player, ghost, new ItemStack(Items.DIAMOND, 64)), "Ghost filter rejected");
      require(entity.ghost.getCount() == 1 && entity.ghost.is(Items.DIAMOND), "Ghost filter count");
      menu.clickMenuButton(player, new ScreenNetwork.Action(73, nav, 1, 0, 0, 0).pack());
      require(menu.slots.get(1).isActive() && !menu.slots.get(0).isActive(), "Context visibility");
      require(!menu.ghost(player, ghost, new ItemStack(Items.STONE)), "Hidden ghost changed");
      menu.clickMenuButton(player, new ScreenNetwork.Action(73, config, 3, 0, 0, 0).pack());
      require(
          entity
                  .sides
                  .get("input")
                  .get(zone.moddev.mc.lib.api.capabilities.RelativeFace.FRONT)
                  .ordinal()
              == (before + 1) % 4,
          "Side config unchanged");
      int calls = entity.guiReads;
      menu.broadcastChanges();
      var secondMenu = new MachineMenu(74, player.getInventory(), entity);
      secondMenu.broadcastChanges();
      require(entity.guiReads == calls + 1, "Getters recomputed for each viewer");
      var first = new HashMap<Integer, Object>();
      for (int i = 0; i < menu.elements.size(); i++) {
        var e = menu.elements.get(i);
        if (menu.bound.values().containsKey(e)) first.put(i * 2, menu.value(e, false));
        if (menu.bound.maxima().containsKey(e)) first.put(i * 2 + 1, menu.value(e, true));
      }
      var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
      try {
        ScreenNetwork.Values.CODEC.encode(wire, new ScreenNetwork.Values(73, first));
        var decoded = ScreenNetwork.Values.CODEC.decode(wire);
        first.forEach(
            (k, v) ->
                require(ScreenValues.equal(v, decoded.delta().get(k)), "Runtime wire value " + k));
      } finally {
        wire.release();
      }
      var nested = Component.literal("nested");
      var rich = Component.empty().append(nested);
      var textCopy = ScreenValues.snapshot(rich, player.registryAccess());
      nested.append(" changed");
      require(!rich.equals(textCopy), "Nested text snapshot aliases machine");
      var fluid = entity.getGuiWater();
      var snapshot = (FluidStack) ScreenValues.snapshot(fluid, player.registryAccess());
      fluid.setAmount(10);
      require(snapshot.getAmount() == 500, "Fluid snapshot aliases machine");
      player.containerMenu = previous;
      menu.clickMenuButton(player, new ScreenNetwork.Action(73, action, 0, 0, 0, 0).pack());
      require(entity.clicks == clicks + 1, "Stale menu action executed");
      double x = player.getX(), y = player.getY(), z = player.getZ();
      player.setPos(x + 100, y, z);
      require(!menu.stillValid(player), "Out-of-reach menu valid");
      player.setPos(x, y, z);
      zone.moddev.mc.lib.MMDLib.LOGGER.info(
          "MMDLIB_GUI_RUNTIME_PASS: hidden slots, actions, ghost filters, side configuration,"
              + " reach, shared getter cache, fluid/text/BigInteger packets");
    } finally {
      player.containerMenu = previous;
    }
  }

  private static int index(MachineMenu menu, String id) {
    return menu.elements.indexOf(menu.bound.screen().find(id).orElseThrow());
  }

  private static void require(boolean condition, String label) {
    if (!condition) throw new AssertionError(label);
  }
}
