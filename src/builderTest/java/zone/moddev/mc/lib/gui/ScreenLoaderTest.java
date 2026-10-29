package zone.moddev.mc.lib.gui;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.FluidStack;

import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.BoundValue;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;

public final class ScreenLoaderTest {
  /** Stands in for a mod's machine; only its getters matter here. */
  public static class Press extends BlockEntity {
    public Press(BlockEntityType<?> type, BlockPos pos, BlockState state) {
      super(type, pos, state);
    }

    public long getEnergy() {
      return 0;
    }

    public long getEnergyCapacity() {
      return 0;
    }

    public int getProgress() {
      return 0;
    }

    public int getProgressMax() {
      return 0;
    }

    public FluidStack getWaterTank() {
      return null;
    }

    public int getWaterCapacity() {
      return 0;
    }

    public BigInteger getThroughput() {
      return BigInteger.ZERO;
    }

    public double getMaxThroughput() {
      return 0;
    }

    public boolean isAutoEject() {
      return false;
    }

    public int getRedstoneMode() {
      return 0;
    }

    public String getName() {
      return "";
    }
  }

  /** A shared static getter, as {@code Class#method}. */
  public static final class Helpers {
    public static int temperature(BlockEntity entity) {
      return 300;
    }
  }

  private static final String HEAD =
      "<screen xmlns=\"https://moddev.zone/mmdlib/screen\" title=\"screen.example.press\""
          + " block-entity=\"zone.moddev.mc.lib.gui.ScreenLoaderTest$Press\">";

  private static final String EXAMPLE =
      """
<screen xmlns="https://moddev.zone/mmdlib/screen"
        title="screen.example.material_press"
        block-entity="zone.moddev.mc.lib.gui.ScreenLoaderTest$Press">
  <vbox padding="7" spacing="4">
    <context id="main">
      <view id="machine">
        <hbox spacing="4" align="center">
          <gauge id="energy" orientation="vertical" length="fill" color="#C0392B"
                 value="#getEnergy" max="#getEnergyCapacity"
                 tooltip="screen.example.energy" tooltip-shift="screen.example.energy.detail"/>
          <group id="input" label="screen.example.input" tint="#E1E9FF" padding="8">
            <slot index="0" tooltip="screen.example.input_slot"/>
          </group>
          <progress value="#getProgress" max="#getProgressMax" jei-recipes="mmdlib:crushing"/>
          <group id="output" label="screen.example.output" tint="#FFE8D6">
            <vbox spacing="4" align="center">
              <slot index="1" size="large"/>
              <slot index="2"/>
            </vbox>
          </group>
          <gauge id="water" orientation="vertical" length="3.5s" value="#getWaterTank" max="#getWaterCapacity"/>
        </hbox>
      </view>
      <view id="sides" title="screen.mmdlib.side_config">
        <side-config groups="input output water energy"/>
        <radio-group label="screen.mmdlib.redstone" value="#getRedstoneMode" on-click="mmdlib:set_redstone_mode">
          <radio text="screen.mmdlib.redstone.ignore"/>
          <radio text="screen.mmdlib.redstone.high"/>
          <radio text="screen.mmdlib.redstone.low"/>
        </radio-group>
      </view>
    </context>
    <hbox spacing="5" align="center">
      <meter orientation="horizontal" thickness="5" length="60" color="#3F76E4"
             value="#getThroughput" max="#getMaxThroughput"/>
      <spacer/>
      <button icon="mmdlib:icon_sides" shows="main:sides" tooltip="screen.mmdlib.side_config.show"/>
      <button text="screen.example.eject" on-click="example:eject_outputs"
              on-right-click="example:eject_one" on-shift-click="example:eject_all"
              tooltip="screen.example.eject.tooltip"/>
      <checkbox text="screen.example.auto_eject" value="#isAutoEject" on-click="example:toggle_auto_eject"/>
      <label text="screen.example.temperature" value="zone.moddev.mc.lib.gui.ScreenLoaderTest$Helpers#temperature"/>
    </hbox>
  </vbox>
  <side-tab edge="right">
    <vbox spacing="2">
      <slot index="3" kind="upgrade" icon="mmdlib:upgrade_speed"/>
      <slot index="4" kind="upgrade" icon="mmdlib:upgrade_energy"/>
    </vbox>
  </side-tab>
  <popout kind="info" text="screen.example.help" text-fallback="How the press works"/>
</screen>
""";

  public static void main(String[] args) throws Throwable {
    var bound = bind(EXAMPLE);
    var screen = bound.screen();
    require(screen.playerInventory());
    require(screen.body().size() == 1 && screen.tabs().size() == 2);
    var water = (Element.Gauge) screen.find("water").orElseThrow();
    require(bound.values().get(water).kind() == BoundValue.Kind.FLUID);
    var meter =
        screen.elements().stream()
            .filter(Element.Meter.class::isInstance)
            .findFirst()
            .orElseThrow();
    require(bound.values().get(meter).kind() == BoundValue.Kind.WHOLE);
    require(bound.maxima().get(meter).kind() == BoundValue.Kind.DECIMAL);
    var energy = screen.find("energy").orElseThrow();
    require(((Element.Gauge) energy).color().orElseThrow() == 0xFFC0392B);
    var context = (Element.Context) screen.find("main").orElseThrow();
    require(context.defaultView().common().id().orElseThrow().equals("machine"));
    var label =
        screen.elements().stream()
            .filter(Element.Label.class::isInstance)
            .findFirst()
            .orElseThrow();
    require(bound.values().get(label).read(null).equals(300));
    var popout = (Element.Popout) screen.tabs().get(1);
    require(popout.iconOrDefault().toString().equals("mmdlib:icon_info"));
    require(popout.text().orElseThrow().fallback().orElseThrow().equals("How the press works"));
    require(screen.elements().stream().filter(Element.Slot.class::isInstance).count() == 5);
    require(BoundValue.whole(7L).equals(BigInteger.valueOf(7)));

    checkLayout(screen);

    // Hardened parsing: no DOCTYPE, no entities.
    fails(
        "DOCTYPE",
        "<?xml version=\"1.0\"?><!DOCTYPE screen [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
            + HEAD
            + "<label text=\"&x;\"/></screen>");
    // Schema: structure, names and formats.
    fails("namespace", "<screen title=\"a\" block-entity=\"a.B\"><spacer/></screen>");
    fails("unknown element", HEAD + "<widget/></screen>");
    fails("unknown attribute", HEAD + "<label text=\"a\" colour=\"red\"/></screen>");
    fails("inline text", HEAD + "<label text=\"a\">Hello</label></screen>");
    fails("bad color", HEAD + "<group tint=\"red\"><spacer/></group></screen>");
    fails(
        "bad meter thickness",
        HEAD
            + "<meter length=\"20\" thickness=\"7\" color=\"#FFFFFF\" value=\"#getProgress\""
            + " max=\"#getProgressMax\"/></screen>");
    fails(
        "one radio",
        HEAD
            + "<radio-group value=\"#getRedstoneMode\"><radio text=\"a\"/></radio-group></screen>");
    fails("empty screen", HEAD + "</screen>");
    // Checks beyond the schema.
    fails("button tooltip", HEAD + "<button text=\"a\"/></screen>");
    fails("button face", HEAD + "<button tooltip=\"a\"/></screen>");
    fails(
        "duplicate id", HEAD + "<label id=\"a\" text=\"a\"/><label id=\"a\" text=\"b\"/></screen>");
    fails("duplicate slot", HEAD + "<slot index=\"1\"/><slot index=\"1\"/></screen>");
    fails("shows", HEAD + "<button text=\"a\" tooltip=\"a\" shows=\"main:nope\"/></screen>");
    fails("side-config", HEAD + "<side-config groups=\"nope\"/></screen>");
    fails("fallback without key", HEAD + "<group label-fallback=\"x\"><vbox/></group></screen>");
    fails("hover-shows", HEAD + "<label text=\"a\" hover-shows=\"nope\"/></screen>");
    fails("spacer outside box", HEAD + "<group><spacer/></group></screen>");
    fails("info text", HEAD + "<label text=\"a\"/><popout kind=\"info\"/></screen>");
    fails("warning value", HEAD + "<label text=\"a\"/><popout kind=\"warning\"/></screen>");
    fails("max without value", HEAD + "<label text=\"a\" max=\"#getProgressMax\"/></screen>");
    fails("gauge max", HEAD + "<gauge length=\"20\" value=\"#getProgress\"/></screen>");
    fails("two inventories", HEAD + "<player-inventory/><player-inventory/></screen>");
    // Sizes: slot units, fill placement, ranges.
    fails(
        "vertical fill in a column",
        HEAD
            + "<vbox><gauge length=\"fill\" value=\"#getProgress\""
            + " max=\"#getProgressMax\"/></vbox></screen>");
    fails(
        "horizontal fill in a row",
        HEAD
            + "<hbox><meter orientation=\"horizontal\" length=\"fill\" color=\"#FFFFFF\""
            + " value=\"#getProgress\" max=\"#getProgressMax\"/></hbox></screen>");
    fails(
        "gauge too short",
        HEAD + "<gauge length=\"7\" value=\"#getProgress\" max=\"#getProgressMax\"/></screen>");
    fails("size too large", HEAD + "<vbox padding=\"999999999999s\"/></screen>");
    fails("offset too far", HEAD + "<label text=\"a\" offset-x=\"4s\"/></screen>");
    fails("bad unit", HEAD + "<vbox padding=\"3px\"/></screen>");
    var units =
        bind(HEAD
                + "<vbox padding=\"0.5s\" spacing=\"1.25s\"><label text=\"a\""
                + " offset-x=\"-1s\"/></vbox></screen>")
            .screen();
    var unitBox = (Element.Box) units.body().getFirst();
    require(unitBox.padding() == 9 && unitBox.spacing() == 23);
    require(unitBox.children().getFirst().common().offsetX() == -18);
    // Getter binding.
    fails(
        "missing getter",
        HEAD + "<progress value=\"#getNothing\" max=\"#getProgressMax\"/></screen>");
    fails("wrong kind", HEAD + "<checkbox text=\"a\" value=\"#getProgress\"/></screen>");
    fails("string value", HEAD + "<label text=\"a\" value=\"#getName\"/></screen>");
    fails(
        "fluid in colored gauge",
        HEAD
            + "<gauge length=\"20\" color=\"#FFFFFF\" value=\"#getWaterTank\""
            + " max=\"#getWaterCapacity\"/></screen>");
    fails("missing class", HEAD.replace("$Press", "$Nope") + "<label text=\"a\"/></screen>");
    fails(
        "not a block entity", HEAD.replace("$Press", "$Helpers") + "<label text=\"a\"/></screen>");
    fails("missing static", HEAD + "<label text=\"a\" value=\"java.lang.Math#nope\"/></screen>");
    System.out.println(
        "Screen schema, hardened parsing, model, reference checks, getter binding and layout"
            + " passed");
  }

  private static void checkLayout(Screen screen) throws Exception {
    // Six pixels per character stands in for the font.
    var layout = ScreenLayout.of(screen, text -> text.key().length() * 6);
    var panel = layout.panel();
    require(panel.width() >= 176);
    var inventory = layout.inventory().orElseThrow();
    require(inventory.x() == (panel.width() - 162) / 2 && inventory.bottom() == panel.height() - 7);
    // A spacer pushes the rest of the bottom row against the root vbox's 7 px padding.
    var label =
        screen.elements().stream()
            .filter(Element.Label.class::isInstance)
            .findFirst()
            .orElseThrow();
    require(layout.bounds(label).right() == panel.width() - 7);
    // Every view of a context area sits at the same place, sized to the largest.
    var context = (Element.Context) screen.find("main").orElseThrow();
    var machine = layout.bounds(context.views().get(0));
    var sides = layout.bounds(context.views().get(1));
    require(machine.equals(sides) && machine.equals(layout.bounds(context)));
    // The upgrade tab overlaps the panel's border; its slots start inside the panel's right edge.
    var tab = layout.bounds(screen.tabs().get(0));
    require(tab.x() == panel.width() - 4);
    var upgrade =
        screen.elements().stream()
            .filter(e -> e instanceof Element.Slot slot && slot.index() == 3)
            .findFirst()
            .orElseThrow();
    require(layout.bounds(upgrade).x() == panel.width() + 4);
    require(ScreenLayout.itemPosition(layout.bounds(upgrade))[0] == panel.width() + 5);
    var large =
        screen.elements().stream()
            .filter(e -> e instanceof Element.Slot slot && slot.index() == 1)
            .findFirst()
            .orElseThrow();
    require(layout.bounds(large).width() == 26);
    // Slot units: 3.5 slots is 63 px. A fill gauge is as tall as its row.
    var water = screen.find("water").orElseThrow();
    require(((Element.Gauge) water).length() == 63 && layout.bounds(water).height() == 63);
    var energy = screen.find("energy").orElseThrow();
    require(((Element.Gauge) energy).fills());
    var row = (Element.Box) context.views().get(0).children().getFirst();
    require(layout.bounds(energy).height() == layout.bounds(row).height());
    // The info pop-out defaults to the left edge, outside the panel.
    require(layout.bounds(screen.tabs().get(1)).right() == 0);
    // An offset moves an element and everything inside it.
    var nudged = bind(HEAD + "<group offset-x=\"3\"><slot index=\"0\"/></group></screen>").screen();
    var nudgedLayout = ScreenLayout.of(nudged, TextMeasure.NONE);
    var group = nudged.body().getFirst();
    require(nudgedLayout.bounds(group).x() == 3);
    require(nudgedLayout.bounds(group.children().getFirst()).x() == 3 + 4);
    // Tabs that do not fit their edge are refused.
    var crowded =
        bind(HEAD.replace(">", " inventory=\"none\">")
                + "<label text=\"a\"/><popout kind=\"info\" text=\"a\"/>"
                + "<popout kind=\"info\" text=\"b\"/></screen>")
            .screen();
    try {
      ScreenLayout.of(crowded, TextMeasure.NONE);
      throw new AssertionError("Crowded tabs accepted");
    } catch (IllegalArgumentException expected) {
      require(expected.getMessage().contains("left edge"));
    }
  }

  private static ScreenBinder.Bound bind(String xml) throws Exception {
    Screen screen =
        ScreenReader.read(
            ResourceLocation.parse("example:press"),
            "press.xml",
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    return ScreenBinder.bind(screen, ScreenLoaderTest.class.getClassLoader());
  }

  private static void fails(String what, String xml) {
    try {
      bind(xml);
    } catch (ScreenException expected) {
      if (!expected.getMessage().startsWith("press.xml"))
        throw new AssertionError(
            what + ": message does not name the file: " + expected.getMessage());
      return;
    } catch (Exception other) {
      throw new AssertionError(what + ": wrong exception", other);
    }
    throw new AssertionError(what + ": accepted an invalid screen");
  }

  private static void require(boolean condition) {
    if (!condition) throw new AssertionError("Screen loader check failed");
  }
}
