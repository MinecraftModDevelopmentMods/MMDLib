package zone.moddev.mc.lib.gui.client;

import java.util.*;

import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.runtime.AutomationMenu;

/**
 * Compact inventory-first drawer. Its selector belongs inside the body, not the navigation rail.
 */
public final class AutomationDrawer {
  public static final int WIDTH = 112, RAIL = 22;
  private static final int[][] FACES = {{1, 1}, {2, 2}, {0, 1}, {2, 1}, {1, 0}, {1, 2}};
  private final MachineScreen screen;
  private final AutomationMenu model;
  private final Minecraft mc = Minecraft.getInstance();
  private int verticalOffset, horizontalOffset, editorScroll, keyboardGroup;
  private String editorKey = "";
  private FilterControl root;
  private FilterPanelLayout panelLayout;
  private List<FilterControl> controls = List.of();
  private final Map<String, EditBox> textFields = new HashMap<>();
  private final Map<String, FilterWidgets.Instance> customWidgets = new HashMap<>();
  private FilterWidgets.Instance focusedCustom;
  private Component tooltip;
  private Rect body, tab, selector, horizontal, extension;
  private final Map<String, Rect> controlBounds = new HashMap<>();
  private int lastSelected = -1;

  public AutomationDrawer(MachineScreen screen) {
    this.screen = screen;
    model = screen.getMenu().automation;
  }

  private Rect panel() {
    return screen.panelBounds();
  }

  private Rect face(int index) {
    return new Rect(
        body.x() + 36 + FACES[index][0] * 18, body.y() + 7 + FACES[index][1] * 18, 18, 18);
  }

  private Rect gear(int column) {
    return new Rect(body.x() + 30 + column * 20, body.y() + 115, 10, 10);
  }

  private Rect module(int column) {
    return new Rect(body.x() + 26 + column * 20, body.y() + 74, 18, 18);
  }

  private Rect filter(int column) {
    return new Rect(body.x() + 28 + column * 20, body.y() + 96, 14, 14);
  }

  private int count() {
    return model.inventory.groups().size();
  }

  public void layout() {
    Rect panel = panel();
    tab = new Rect(panel.right(), panel.y(), RAIL, 24);
    body = new Rect(tab.right(), panel.y(), WIDTH, Math.max(132, panel.height()));
    int shown = Math.max(1, (body.height() - 16) / 16);
    selector = new Rect(body.x() + 5, body.y() + 8, 14, shown * 16);
    var inventory = screen.layout.inventory().orElse(null);
    horizontal =
        inventory == null
            ? null
            : new Rect(
                panel.x() + inventory.x(), panel.y() + inventory.y() - 18, inventory.width(), 16);
    if (lastSelected != model.selected()) {
      int hSlots = horizontal == null ? 1 : Math.max(1, (horizontal.width() - 20) / 18);
      if (model.selected() < verticalOffset || model.selected() >= verticalOffset + shown)
        verticalOffset = Math.max(0, model.selected() - shown + 1);
      if (model.selected() < horizontalOffset || model.selected() >= horizontalOffset + hSlots)
        horizontalOffset = Math.max(0, model.selected() - hSlots + 1);
      lastSelected = model.selected();
      keyboardGroup = model.selected();
    }
    verticalOffset = Math.clamp(verticalOffset, 0, Math.max(0, count() - shown));
    syncEditor();
    int desired = panelLayout == null ? 0 : panelLayout.height() + 20;
    int room = Math.max(0, screen.height - body.bottom() - 4);
    extension =
        model.open() && model.editor() >= 0
            ? new Rect(body.x(), body.bottom(), body.width(), Math.min(desired, room))
            : null;
    controlBounds.clear();
    if (extension != null && panelLayout != null) {
      int viewport = Math.max(0, extension.height() - 18);
      editorScroll = Math.clamp(editorScroll, 0, Math.max(0, panelLayout.height() - viewport));
      panelLayout
          .controls()
          .forEach(
              (id, r) ->
                  controlBounds.put(
                      id, r.offset(extension.x() + 6, extension.y() + 16 - editorScroll)));
      textFields.forEach(
          (id, field) -> {
            Rect r = controlBounds.get(id);
            if (r != null) {
              field.setX(r.x());
              field.setY(r.y());
              field.setWidth(Math.max(12, r.width() - 12));
              if (r.bottom() <= extension.y() + 15 || r.y() >= extension.bottom() - 3)
                field.setFocused(false);
            }
          });
    }
  }

  private void syncEditor() {
    int c = model.editor(), g = model.selected();
    var stack = c < 0 ? ItemStack.EMPTY : model.filter(g, c);
    var provider = stack.getCapability(AddonCapabilities.FILTER);
    String key =
        !model.open() || provider == null
            ? ""
            : g
                + "/"
                + c
                + "/"
                + model.generation(g, c)
                + "/"
                + provider.editorType(stack)
                + "/"
                + model.revision(g, c);
    if (key.equals(editorKey)) return;
    boolean sameAttachment =
        !key.isEmpty()
            && !editorKey.isEmpty()
            && key.substring(0, key.lastIndexOf('/'))
                .equals(editorKey.substring(0, editorKey.lastIndexOf('/')));
    if (sameAttachment && root != null) {
      try {
        if (root.equals(provider.controls(stack))) {
          editorKey = key;
          var config = provider.configuration(stack);
          textFields.forEach(
              (id, field) -> {
                if (!field.isFocused()) field.setValue(config.getString(id));
              });
          return;
        }
      } catch (RuntimeException invalid) {
        /* Rebuild below to display the unavailable state. */
      }
    }
    if (!sameAttachment) editorScroll = 0;
    closeEditorInput();
    customWidgets.values().forEach(FilterWidgets.Instance::removed);
    customWidgets.clear();
    editorKey = key;
    root = null;
    panelLayout = null;
    controls = List.of();
    for (var field : textFields.values()) field.setFocused(false);
    textFields.clear();
    if (key.isEmpty()) return;
    try {
      root = provider.controls(stack);
      controls = FilterControl.flatten(root);
      panelLayout = FilterPanelLayout.of(root, WIDTH - 12);
      var config = provider.configuration(stack);
      for (var control : controls)
        if (control instanceof FilterControl.Text text) {
          var field = new EditBox(mc.font, 0, 0, 80, 16, text.tooltip());
          field.setMaxLength(text.maxLength());
          field.setValue(config.getString(text.id()));
          textFields.put(text.id(), field);
        } else if (control instanceof FilterControl.Custom custom) {
          var instance =
              FilterWidgets.create(
                  custom,
                  () -> controlBounds.get(custom.id()),
                  () -> provider.configuration(model.filter(g, c)).copy(),
                  model::pending,
                  value -> edit(custom.id(), 0, value));
          if (instance != null) customWidgets.put(custom.id(), instance);
        }
    } catch (RuntimeException invalid) {
      root = null;
      controls = List.of();
      panelLayout = null;
      zone.moddev.mc.lib.MMDLib.LOGGER.warn("Invalid filter editor", invalid);
    }
  }

  private static void sprite(GuiGraphics g, String name, Rect r) {
    g.blitSprite(ResourceLocation.parse("mmdlib:" + name), r.x(), r.y(), r.width(), r.height());
  }

  private static void tint(GuiGraphics g, String name, Rect r, int colour) {
    g.setColor(
        ((colour >> 16) & 255) / 255f, ((colour >> 8) & 255) / 255f, (colour & 255) / 255f, 1);
    sprite(g, name, r);
    g.setColor(1, 1, 1, 1);
  }

  private void icon(GuiGraphics g, String name, Rect r) {
    sprite(g, name, r);
  }

  private void button(GuiGraphics g, Rect r, boolean selected) {
    sprite(g, "slot", r);
    if (selected) g.renderOutline(r.x(), r.y(), r.width(), r.height(), 0xfff5da72);
  }

  private void swatch(GuiGraphics g, Rect r, int group, int mx, int my) {
    button(g, r, model.selected() == group && model.open());
    g.fill(
        r.x() + 2,
        r.y() + 2,
        r.right() - 2,
        r.bottom() - 2,
        0xff000000 | model.inventory.group(group).colour());
    if (r.contains(mx, my)) {
      tooltip = model.inventory.group(group).name();
      highlight(g, group);
    }
  }

  public void drawBackground(GuiGraphics g, int mx, int my) {
    layout();
    tooltip = null;
    tint(g, "drawer_tab", tab, 0x819bc2);
    icon(g, "icon_sides", new Rect(tab.x() + 3, tab.y() + 3, 16, 16));
    for (int index = 0; index < count(); index++) {
      var group = model.inventory.group(index);
      for (String id : group.highlights())
        screen
            .getMenu()
            .bound
            .screen()
            .find(id)
            .ifPresent(
                e -> {
                  if (screen.visible(e)) {
                    var r = screen.bounds(e);
                    g.renderOutline(
                        r.x(), r.y(), r.width(), r.height(), 0xff000000 | group.colour());
                  }
                });
    }
    if (tab.contains(mx, my)) tooltip = Component.translatable("screen.mmdlib.automation.open");
    if (horizontal != null) {
      int capacity = Math.max(1, (horizontal.width() - 20) / 18);
      horizontalOffset = Math.clamp(horizontalOffset, 0, Math.max(0, count() - capacity));
      for (int i = 0; i < capacity && horizontalOffset + i < count(); i++)
        swatch(
            g,
            new Rect(horizontal.x() + 10 + i * 18, horizontal.y() + 1, 14, 14),
            horizontalOffset + i,
            mx,
            my);
      if (count() > capacity) {
        arrow(g, new Rect(horizontal.x(), horizontal.y() + 3, 8, 10), false, false);
        arrow(g, new Rect(horizontal.right() - 8, horizontal.y() + 3, 8, 10), true, false);
      }
    }
    if (!model.open()) return;
    tint(g, "drawer_panel", body, 0x819bc2);
    g.fill(tab.x() + 2, tab.bottom(), tab.right() - 1, body.bottom(), 0xff425878);
    g.fill(body.x() + 22, body.y() + 5, body.x() + 23, body.bottom() - 5, 0xff526788);
    int shown = selector.height() / 16;
    for (int i = 0; i < shown && verticalOffset + i < count(); i++)
      swatch(g, new Rect(selector.x(), selector.y() + i * 16, 14, 14), verticalOffset + i, mx, my);
    if (count() > shown) {
      arrow(g, new Rect(selector.x() + 3, body.y() + 1, 8, 6), false, true);
      arrow(g, new Rect(selector.x() + 3, body.bottom() - 7, 8, 6), true, true);
    }
    for (int f = 0; f < 6; f++) drawFace(g, f, mx, my);
    for (int c = 0; c < 4; c++) {
      sprite(g, "slot", module(c));
      if (module(c).contains(mx, my) && model.addon(model.selected(), c).isEmpty())
        tooltip =
            Component.translatable("screen.mmdlib.automation.addon")
                .append(": ")
                .append(model.inventory.group(model.selected()).name());
      if (model.supportsFilter(model.selected(), c)
          || !model.filter(model.selected(), c).isEmpty()) {
        var f = filter(c);
        g.fill(f.x() + 6, module(c).bottom(), f.x() + 7, f.y(), 0xff425878);
        sprite(g, "slot", f);
        if (!model.filter(model.selected(), c).isEmpty()) {
          var gear = gear(c);
          button(g, gear, model.editor() == c);
          gear(g, gear);
          if (gear.contains(mx, my))
            tooltip = Component.translatable("screen.mmdlib.automation.filter");
        }
      }
    }
    if (model.open()) highlight(g, model.selected());
    drawEditor(g, mx, my);
  }

  private void highlight(GuiGraphics g, int group) {
    var selected = model.inventory.group(group);
    for (String id : selected.highlights())
      screen
          .getMenu()
          .bound
          .screen()
          .find(id)
          .ifPresent(
              e -> {
                if (screen.getMenu().visible(e)) {
                  Rect r = screen.bounds(e);
                  g.renderOutline(
                      r.x() - 1,
                      r.y() - 1,
                      r.width() + 2,
                      r.height() + 2,
                      0xff000000 | selected.colour());
                }
              });
  }

  private void drawFace(GuiGraphics g, int index, int mx, int my) {
    var r = face(index);
    sprite(g, "slot", r);
    var group = model.inventory.group(model.selected());
    var relative = RelativeFace.values()[index];
    var direction = group.world(relative);
    var level = screen.getMenu().blockEntity.getLevel();
    var pos = screen.getMenu().blockEntity.getBlockPos().relative(direction);
    Component neighbour = Component.translatable("screen.mmdlib.automation.unloaded");
    if (level != null && level.hasChunkAt(pos)) {
      var state = level.getBlockState(pos);
      neighbour =
          state.isAir()
              ? Component.translatable("screen.mmdlib.automation.empty")
              : state.getBlock().getName();
      if (!state.isAir()) {
        g.pose().pushPose();
        try {
          g.flush();
          g.pose().translate(r.x() + 9, r.y() + 9, 150);
          g.pose().scale(10, -10, 10);
          g.pose().mulPose(Axis.XP.rotationDegrees(30));
          g.pose().mulPose(Axis.YP.rotationDegrees(225));
          g.pose().translate(-.5, -.5, -.5);
          mc.getBlockRenderer()
              .renderSingleBlock(
                  state,
                  g.pose(),
                  g.bufferSource(),
                  LightTexture.FULL_BRIGHT,
                  OverlayTexture.NO_OVERLAY);
          g.flush();
        } catch (RuntimeException unsupported) {
          // A missing special renderer must not break inventory interaction.
        } finally {
          g.pose().popPose();
        }
      }
    }
    var mode = model.mode(model.selected(), index);
    int x = r.right() - 8, y = r.bottom() - 8;
    g.fill(x, y, r.right() - 1, r.bottom() - 1, 0xee263440);
    if (mode.canInsert()) {
      int ax = x + (mode.canExtract() ? 1 : 2);
      g.fill(ax + 1, y + 1, ax + 2, y + 5, 0xff85c5ff);
      g.fill(ax, y + 3, ax + 3, y + 4, 0xff85c5ff);
    }
    if (mode.canExtract()) {
      int ax = x + (mode.canInsert() ? 4 : 2);
      g.fill(ax + 1, y + 2, ax + 2, y + 6, 0xffffbb73);
      g.fill(ax, y + 3, ax + 3, y + 4, 0xffffbb73);
    }
    if (!mode.canInsert() && !mode.canExtract()) {
      g.fill(x + 2, y + 3, x + 5, y + 4, 0xffaaaaaa);
    }
    if (r.contains(mx, my))
      tooltip =
          Component.translatable("screen.mmdlib.face." + relative.getSerializedName())
              .append(" · ")
              .append(neighbour)
              .append(" · ")
              .append(Component.translatable("screen.mmdlib.mode." + mode.getSerializedName()));
  }

  private static void gear(GuiGraphics g, Rect r) {
    int x = r.x() + 2, y = r.y() + 2;
    g.fill(x + 2, y, x + 4, y + 6, 0xff303844);
    g.fill(x, y + 2, x + 6, y + 4, 0xff303844);
    g.fill(x + 1, y + 1, x + 5, y + 5, 0xff303844);
    g.fill(x + 2, y + 2, x + 4, y + 4, 0xffadc2dd);
  }

  private static void arrow(GuiGraphics g, Rect r, boolean forward, boolean vertical) {
    for (int i = 0; i < 3; i++) {
      int d = forward ? i : 2 - i;
      if (vertical) g.fill(r.x() + i, r.y() + d, r.right() - i, r.y() + d + 1, 0xffeeeeee);
      else g.fill(r.x() + d, r.y() + i, r.x() + d + 1, r.bottom() - i, 0xffeeeeee);
    }
  }

  private void drawEditor(GuiGraphics g, int mx, int my) {
    if (extension == null || extension.height() < 18) return;
    tint(g, "drawer_panel", extension, 0x819bc2);
    Rect collapse = new Rect(extension.right() - 15, extension.y() + 3, 10, 10);
    button(g, collapse, false);
    arrow(g, collapse, false, true);
    if (collapse.contains(mx, my))
      tooltip = Component.translatable("screen.mmdlib.automation.collapse");
    int c = model.editor();
    var stack = model.filter(model.selected(), c);
    var provider = stack.getCapability(AddonCapabilities.FILTER);
    if (provider == null || panelLayout == null) {
      g.drawString(mc.font, "?", extension.x() + 6, extension.y() + 4, 0xffffff);
      return;
    }
    g.pose().pushPose();
    g.pose().translate(extension.x() + 5, extension.y() + 2, 0);
    g.pose().scale(.7f, .7f, 1);
    g.renderItem(stack, 0, 0);
    g.pose().popPose();
    g.fill(
        extension.x() + 20,
        extension.y() + 8,
        extension.right() - 19,
        extension.y() + 9,
        0xff526788);
    int viewport = extension.height() - 18;
    if (panelLayout.height() > viewport && viewport > 0) {
      int track = extension.height() - 20,
          thumb = Math.max(6, track * viewport / panelLayout.height());
      int y =
          extension.y()
              + 16
              + (track - thumb) * editorScroll / Math.max(1, panelLayout.height() - viewport);
      g.fill(
          extension.right() - 4,
          extension.y() + 16,
          extension.right() - 2,
          extension.bottom() - 4,
          0xff526788);
      g.fill(extension.right() - 4, y, extension.right() - 2, y + thumb, 0xffd2dfef);
    }
    var config = provider.configuration(stack);
    g.enableScissor(
        extension.x() + 4, extension.y() + 15, extension.right() - 4, extension.bottom() - 3);
    try {
      for (var control : controls) {
        Rect r = controlBounds.get(control.id());
        if (r == null || r.bottom() <= extension.y() + 15 || r.y() >= extension.bottom() - 3)
          continue;
        switch (control) {
          case FilterControl.Custom custom -> {
            var instance = customWidgets.get(custom.id());
            if (instance != null) {
              g.enableScissor(
                  r.x(),
                  Math.max(r.y(), extension.y() + 15),
                  r.right(),
                  Math.min(r.bottom(), extension.bottom() - 3));
              try {
                instance.render(g, r, config.copy(), mx, my);
              } finally {
                g.disableScissor();
              }
              if (r.contains(mx, my)) tooltip = instance.tooltip();
            } else g.drawString(mc.font, "?", r.x() + 2, r.y() + 2, 0xffffff);
          }
          case FilterControl.Toggle toggle -> {
            button(g, r, config.getBoolean(toggle.id()));
            g.blitSprite(toggle.icon(), r.x() + 2, r.y() + 2, 10, 10);
            if (r.contains(mx, my)) tooltip = toggle.tooltip();
          }
          case FilterControl.Samples samples -> {
            var entries = config.getList(samples.id(), net.minecraft.nbt.Tag.TAG_STRING);
            int columns = Math.max(1, r.width() / 18);
            for (int i = 0; i < samples.count(); i++) {
              Rect cell = new Rect(r.x() + (i % columns) * 18, r.y() + (i / columns) * 18, 18, 18);
              sprite(g, "slot", cell);
              var id = ResourceLocation.tryParse(entries.getString(i));
              if (id != null) {
                ItemStack display = ItemStack.EMPTY;
                if (samples.resource() == ResourceType.ITEMS)
                  display = new ItemStack(BuiltInRegistries.ITEM.get(id));
                else if (samples.resource() == ResourceType.FLUIDS)
                  display = new ItemStack(BuiltInRegistries.FLUID.get(id).getBucket());
                if (!display.isEmpty()) {
                  g.renderItem(display, cell.x() + 1, cell.y() + 1);
                  if (cell.contains(mx, my)) tooltip = display.getHoverName();
                }
              }
              if (cell.contains(mx, my) && tooltip == null)
                tooltip = Component.translatable("screen.mmdlib.automation.sample");
            }
          }
          case FilterControl.Text text -> {
            var field = textFields.get(text.id());
            if (field != null) field.render(g, mx, my, 0);
            var submit = new Rect(r.right() - 10, r.y() + 3, 8, 10);
            button(g, submit, false);
            arrow(g, submit, true, false);
            if (r.contains(mx, my)) tooltip = text.tooltip();
          }
          case FilterControl.Number number -> {
            button(g, r, false);
            g.drawCenteredString(
                mc.font,
                Integer.toString(config.getInt(number.id())),
                r.x() + r.width() / 2,
                r.y() + 5,
                0xffffff);
            arrow(g, new Rect(r.x() + 2, r.y() + 4, 6, 8), false, false);
            arrow(g, new Rect(r.right() - 8, r.y() + 4, 6, 8), true, false);
            if (r.contains(mx, my)) tooltip = number.tooltip();
          }
          default -> {}
        }
      }
    } finally {
      g.disableScissor();
    }
  }

  public void tooltip(GuiGraphics g, int mx, int my) {
    if (tooltip != null) g.renderTooltip(mc.font, tooltip, mx, my);
  }

  public List<Rect> exclusions() {
    layout();
    var result = new ArrayList<Rect>();
    result.add(tab);
    if (model.open()) {
      result.add(body);
      if (extension != null) result.add(extension);
    }
    return result;
  }

  public boolean contains(double x, double y) {
    layout();
    return tab.contains(x, y)
        || model.open() && (body.contains(x, y) || extension != null && extension.contains(x, y));
  }

  /** Returns true only for controls/background. Real item slots must reach vanilla handling. */
  public boolean click(double x, double y, int button) {
    layout();
    selectorFocused = false;
    if (button > 1) return false;
    if (tab.contains(x, y)) {
      model.send(model.open() ? "close" : "select", model.selected(), -1, "", 0, "");
      return true;
    }
    if (horizontal != null && horizontal.contains(x, y)) {
      int capacity = Math.max(1, (horizontal.width() - 20) / 18);
      if (x < horizontal.x() + 10) horizontalOffset = Math.max(0, horizontalOffset - 1);
      else if (x >= horizontal.right() - 10)
        horizontalOffset = Math.min(Math.max(0, count() - capacity), horizontalOffset + 1);
      else {
        int index = horizontalOffset + ((int) x - horizontal.x() - 10) / 18;
        if (index < count()) model.send("select", index, -1, "", 0, "");
      }
      return true;
    }
    if (!model.open()) return false;
    if (!body.contains(x, y) && (extension == null || !extension.contains(x, y)))
      closeEditorInput();
    if (x >= body.x() + 2 && x < body.x() + 22 && y >= body.y() && y < body.bottom()) {
      int shown = selector.height() / 16;
      if (y < selector.y()) verticalOffset = Math.max(0, verticalOffset - 1);
      else if (y >= selector.bottom())
        verticalOffset = Math.min(Math.max(0, count() - shown), verticalOffset + 1);
      else {
        int index = verticalOffset + ((int) y - selector.y()) / 16;
        if (index < count()) model.send("select", index, -1, "", 0, "");
      }
      return true;
    }
    for (int f = 0; f < 6; f++)
      if (face(f).contains(x, y)) {
        model.send(
            "face",
            model.selected(),
            -1,
            model.mode(model.selected(), f).name(),
            f,
            button == 1 ? "reverse" : "");
        return true;
      }
    for (int c = 0; c < 4; c++) {
      if (model.supportsFilter(model.selected(), c)
          && !model.filter(model.selected(), c).isEmpty()
          && gear(c).contains(x, y)) {
        model.send("editor", model.selected(), c, "", 0, "");
        return true;
      }
      if (module(c).contains(x, y)
          || (model.supportsFilter(model.selected(), c)
                  || !model.filter(model.selected(), c).isEmpty())
              && filter(c).contains(x, y)) return model.pending();
    }
    if (extension != null && extension.contains(x, y)) {
      if (y < extension.y() + 15) {
        model.send("collapse", model.selected(), -1, "", 0, "");
        return true;
      }
      if (y >= extension.bottom() - 3) return true;
      var stack = model.filter(model.selected(), model.editor());
      var provider = stack.getCapability(AddonCapabilities.FILTER);
      for (var control : controls) {
        Rect r = controlBounds.get(control.id());
        if (r == null || !r.contains(x, y) || provider == null) continue;
        switch (control) {
          case FilterControl.Custom custom -> {
            closeEditorInput();
            focusedCustom = customWidgets.get(custom.id());
            if (focusedCustom != null) {
              focusedCustom.focus(true);
              focusedCustom.click(x, y, button);
            }
          }
          case FilterControl.Toggle toggle -> edit(toggle.id(), 0, "");
          case FilterControl.Samples samples -> {
            int index =
                ((int) y - r.y()) / 18 * Math.max(1, r.width() / 18) + ((int) x - r.x()) / 18;
            if (index < samples.count())
              edit(samples.id(), index, button == 1 ? "clear" : "sample");
          }
          case FilterControl.Text text -> {
            var field = textFields.get(text.id());
            if (x >= r.right() - 12) {
              edit(text.id(), 0, field.getValue());
              field.setFocused(false);
            } else {
              textFields.values().forEach(f -> f.setFocused(false));
              field.setFocused(true);
              field.mouseClicked(x, y, button);
            }
          }
          case FilterControl.Number number -> {
            long current = provider.configuration(stack).getInt(number.id());
            long next = current + (x < r.x() + r.width() / 2 ? -number.step() : number.step());
            edit(number.id(), 0, Long.toString(Math.clamp(next, number.min(), number.max())));
          }
          default -> {}
        }
        return true;
      }
      return true;
    }
    if (body.contains(x, y)) {
      textFields.values().forEach(f -> f.setFocused(false));
      return true;
    }
    return false;
  }

  private void edit(String id, int entry, String value) {
    model.send("edit", model.selected(), model.editor(), id, entry, value);
  }

  public boolean scroll(double x, double y, double amount) {
    layout();
    if (model.open() && x >= body.x() && x < body.x() + 24 && y >= body.y() && y < body.bottom()) {
      verticalOffset += (int) -amount;
      return true;
    }
    if (extension != null && extension.contains(x, y)) {
      for (var entry : customWidgets.entrySet())
        if (controlBounds.get(entry.getKey()).contains(x, y)
            && entry.getValue().scroll(x, y, amount)) return true;
      if (focusedCustom != null) focusedCustom.cancel();
      editorScroll += (int) (-amount * 18);
      return true;
    }
    if (horizontal != null && horizontal.contains(x, y)) {
      horizontalOffset += (int) -amount;
      return true;
    }
    return false;
  }

  public boolean key(int key, int scan, int modifiers) {
    if (focusedCustom != null && focusedCustom.key(key, scan, modifiers)) return true;
    for (var entry : textFields.entrySet())
      if (entry.getValue().isFocused()) {
        if (key == 256) {
          entry.getValue().setFocused(false);
          return true;
        }
        if (key == 257 || key == 335) {
          edit(entry.getKey(), 0, entry.getValue().getValue());
          return true;
        }
        entry.getValue().keyPressed(key, scan, modifiers);
        return true;
      }
    if (model.open() && key == 256) {
      model.send(model.editor() >= 0 ? "collapse" : "close", model.selected(), -1, "", 0, "");
      return true;
    }
    if (key == 295) { // F6 moves to the inventory selector; arrows move between inventories.
      closeEditorInput();
      keyboardGroup = model.selected();
      selectorFocused = true;
      narrateSelection();
      return true;
    }
    if (selectorFocused && (key == 264 || key == 265 || key == 262 || key == 263)) {
      keyboardGroup = Math.floorMod(keyboardGroup + (key == 264 || key == 262 ? 1 : -1), count());
      model.send("select", keyboardGroup, -1, "", 0, "");
      narrateSelection();
      return true;
    }
    if (selectorFocused && (key == 257 || key == 335)) {
      model.send("select", keyboardGroup, -1, "", 0, "");
      return true;
    }
    return false;
  }

  public boolean character(char c, int modifiers) {
    if (focusedCustom != null && focusedCustom.character(c, modifiers)) return true;
    for (var field : textFields.values())
      if (field.isFocused()) {
        field.charTyped(c, modifiers);
        return true;
      }
    return false;
  }

  public boolean drag(double x, double y, int button, double dx, double dy) {
    return focusedCustom != null && focusedCustom.drag(x, y, button, dx, dy);
  }

  public boolean release(double x, double y, int button) {
    return focusedCustom != null && focusedCustom.release(x, y, button);
  }

  public void closeEditorInput() {
    textFields.values().forEach(f -> f.setFocused(false));
    if (focusedCustom != null) {
      focusedCustom.cancel();
      focusedCustom.focus(false);
      focusedCustom = null;
    }
  }

  private boolean selectorFocused;

  private void narrateSelection() {
    mc.getNarrator().sayNow(model.inventory.group(keyboardGroup).name());
  }

  public void removed() {
    closeEditorInput();
    customWidgets.values().forEach(FilterWidgets.Instance::removed);
    customWidgets.clear();
    editorKey = "";
  }
}
