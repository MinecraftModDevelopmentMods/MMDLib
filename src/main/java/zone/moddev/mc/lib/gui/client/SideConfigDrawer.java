package zone.moddev.mc.lib.gui.client;

import static zone.moddev.mc.lib.api.automation.AutomationInventory.COLUMNS;

import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.model.ElementGroup;
import zone.moddev.mc.lib.gui.model.containers.PlayerInventoryElement;
import zone.moddev.mc.lib.gui.runtime.AutomationMenu;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;

/**
 * Compact inventory-first drawer. Its selector belongs inside the body, not the navigation rail.
 */
public final class SideConfigDrawer extends Drawer {
  private final NativeSlotRenderer nativeSlots = new NativeSlotRenderer();
  private ElementFragment slotFragment;
  private int slotGroup = -1;
  public static final int WIDTH = AutomationLayout.WIDTH, RAIL = AutomationLayout.RAIL;
  private final AutomationMenu model;
  private final Minecraft mc = Minecraft.getInstance();
  private final FaceConfigRenderer faces = new FaceConfigRenderer();
  private int verticalOffset, editorScroll, keyboardGroup;
  private final ColourButtonBar<Integer> colourBar;
  private final List<Integer> displayOrder;
  private final Map<String, List<zone.moddev.mc.lib.gui.model.Element>> groupHighlights;
  private String editorKey = "";
  private FilterControl root;
  private ElementFragment editor;
  private Component tooltip;
  private List<Component> faceTooltip = List.of();
  private Rect body, tab, selector, editorArea;
  private int lastSelected = -1;

  public SideConfigDrawer(
      MachineScreen screen, zone.moddev.mc.lib.gui.drawer.DrawerDefinition definition) {
    super(
        screen,
        definition,
        IconSprite.gui(ResourceLocation.parse("mmdlib:icon_side_config")),
        0x819bc2);
    model = screen.getMenu().components().get(BuiltinMenuComponents.AUTOMATION);
    var groups = model.inventory.groups();
    var associations =
        groups.stream()
            .map(
                group ->
                    new ElementGroup(
                        group.id(),
                        group.highlights(),
                        Optional.of(
                            new ElementGroup.Members(
                                group.type().id(), group.inventory(), group.members()))))
            .toList();
    var indices = new LinkedHashMap<String, Integer>();
    for (int i = 0; i < groups.size(); i++) indices.put(groups.get(i).id(), i);
    var definitionModel = screen.getMenu().bound.screen();
    groupHighlights = GroupHighlights.of(definitionModel, associations);
    displayOrder =
        GroupOrder.of(
                definitionModel,
                associations.stream().map(ElementGroup::id).toList(),
                element -> element.associatedGroups(definitionModel, associations))
            .stream()
            .map(indices::get)
            .toList();
    colourBar =
        new ColourButtonBar<>(
            displayOrder.stream()
                .map(index -> new ColourButtonBar.Entry<>(index, groups.get(index).colour()))
                .toList(),
            group -> send("select", group, -1, "", 0, ""));
  }

  private boolean send(
      String kind, int group, int column, String control, int entry, String value) {
    if (!model.send(kind, group, column, control, entry, value)) return false;
    if (kind.equals("close")) remember(false, selection());
    else if (kind.equals("select")) {
      activated();
      remember(true, new Selection(model.inventory.group(group).id(), 0));
    }
    return true;
  }

  private Rect panel() {
    return screen.panelBounds();
  }

  private Rect face(int index) {
    return AutomationLayout.face(index).offset(body.x(), body.y());
  }

  private Rect filterToggle(int column) {
    return model
        .inventoryView(model.selected())
        .filterToggleBounds(column)
        .offset(body.x(), body.y());
  }

  private Rect module(int column) {
    return model
        .inventoryView(model.selected())
        .drawerSlotBounds(column)
        .offset(body.x(), body.y());
  }

  private Rect filter(int column) {
    return model
        .inventoryView(model.selected())
        .drawerSlotBounds(COLUMNS + column)
        .offset(body.x(), body.y());
  }

  private int count() {
    return model.inventory.groups().size();
  }

  public void layout() {
    Rect panel = panel();
    tab = super.tabBounds();
    syncEditor();
    boolean hasFilterSlot = false, hasFilter = false;
    for (int c = 0; c < COLUMNS; c++) {
      boolean visible = model.slotVisible(model.selected(), COLUMNS + c);
      hasFilterSlot |= visible;
      hasFilter |= visible && !model.filter(model.selected(), c).isEmpty();
    }
    int contentHeight =
        model.addonsEnabled()
            ? model.inventoryView(model.selected()).contentHeight(hasFilterSlot, hasFilter)
            : AutomationLayout.face(RelativeFace.BOTTOM.ordinal()).bottom()
                + AutomationLayout.PADDING;
    int baseHeight =
        Math.max(
            contentHeight,
            16
                + Math.min(count(), AutomationLayout.SELECTOR_LIMIT)
                    * AutomationLayout.SELECTOR_PITCH);
    boolean editing = model.open() && model.editor() >= 0;
    int editorHeight =
        (editor == null ? 18 : editor.height()) + (2 * AutomationLayout.EDITOR_PADDING);
    int desiredHeight =
        editing
            ? Math.max(
                baseHeight, AutomationLayout.EDITOR_Y + editorHeight + AutomationLayout.PADDING)
            : baseHeight;
    body =
        SideDrawerLayout.body(
            panel,
            screen.drawerBottom(),
            SideDrawerLayout.bodyX(edge(), tab, WIDTH),
            tab.y(),
            WIDTH,
            desiredHeight,
            SideDrawerLayout.HeightLimit.SCREEN);
    model.position(body.x() - panel.x(), body.y() - panel.y());
    syncSlots();
    int shown =
        Math.max(
            1,
            Math.min(
                Math.min(count(), AutomationLayout.SELECTOR_LIMIT),
                (Math.min(baseHeight, body.height()) - 16) / AutomationLayout.SELECTOR_PITCH));
    selector = new Rect(body.x() + 5, body.y() + 8, 14, shown * AutomationLayout.SELECTOR_PITCH);
    colourBar.layout(
        screen
            .layout
            .region(PlayerInventoryElement.TOOLBAR)
            .map(r -> r.offset(panel.x(), panel.y()))
            .orElse(null),
        model.selected());
    if (lastSelected != model.selected()) {
      int position = displayOrder.indexOf(model.selected());
      if (position < verticalOffset || position >= verticalOffset + shown)
        verticalOffset = Math.max(0, position - shown + 1);
      lastSelected = model.selected();
      keyboardGroup = model.selected();
    }
    verticalOffset = Math.clamp(verticalOffset, 0, Math.max(0, count() - shown));
    editorArea =
        editing && body.height() > AutomationLayout.EDITOR_Y + AutomationLayout.PADDING
            ? new Rect(
                body.x() + AutomationLayout.EDITOR_X,
                body.y() + AutomationLayout.EDITOR_Y,
                WIDTH - AutomationLayout.EDITOR_X - AutomationLayout.PADDING,
                Math.min(
                    editorHeight,
                    body.height() - AutomationLayout.EDITOR_Y - AutomationLayout.PADDING))
            : null;
    if (editor != null) {
      if (editorArea == null) editor.place(0, 0, new Rect(0, 0, 0, 0));
      else {
        int viewport = Math.max(0, editorArea.height() - 2 * AutomationLayout.EDITOR_PADDING);
        editorScroll = Math.clamp(editorScroll, 0, Math.max(0, editor.height() - viewport));
        editor.place(
            editorArea.x() + 6,
            editorArea.y() + AutomationLayout.EDITOR_PADDING - editorScroll,
            new Rect(
                editorArea.x() + 4,
                editorArea.y() + AutomationLayout.EDITOR_PADDING,
                editorArea.width() - 8,
                viewport));
      }
    }
  }

  private void syncSlots() {
    if (!model.open() || !model.addonsEnabled()) {
      if (slotFragment != null) slotFragment.removed();
      slotFragment = null;
      slotGroup = -1;
      return;
    }
    int group = model.selected();
    if (slotFragment == null || slotGroup != group) {
      if (slotFragment != null) slotFragment.removed();
      var inventory = model.inventoryView(group);
      slotFragment =
          new ElementFragment(
              inventory,
              WIDTH,
              new ElementFragment.Host() {
                public ResourceLocation screenId() {
                  return screen.getMenu().bound.screen().id();
                }

                public boolean visible(zone.moddev.mc.lib.gui.model.Element element) {
                  return model.visible(group, element);
                }

                public Object value(zone.moddev.mc.lib.gui.model.Element element, boolean maximum) {
                  return model.drawerValue(group, element, maximum);
                }

                public ItemStack contents(zone.moddev.mc.lib.gui.model.elements.SlotElement slot) {
                  return screen.getMenu().slot(slot).getItem().copy();
                }

                public ItemStack hint(zone.moddev.mc.lib.gui.model.elements.SlotElement slot) {
                  return screen.getMenu().slotHint(slot);
                }

                public zone.moddev.mc.lib.machine.inventory.SlotPlan reservation(
                    zone.moddev.mc.lib.gui.model.elements.SlotElement slot) {
                  return screen.getMenu().reservationPlan(screen.getMenu().slotIndex(slot));
                }

                public List<Component> serverTooltip(zone.moddev.mc.lib.gui.model.Element element) {
                  return screen
                      .getMenu()
                      .components()
                      .requireCapability(
                          zone.moddev.mc.lib.gui.runtime.component.ServerTooltips.class)
                      .lines(element);
                }

                public ItemStack carried() {
                  return screen.getMenu().getCarried().copy();
                }
              });
      slotGroup = group;
    }
    slotFragment.place(body.x(), body.y(), body);
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
          return;
        }
      } catch (RuntimeException invalid) {
        /* Rebuild below to display the unavailable state. */
      }
    }
    if (!sameAttachment) editorScroll = 0;
    if (editor != null) editor.removed();
    editor = null;
    editorKey = key;
    root = null;
    if (key.isEmpty()) return;
    try {
      root = provider.controls(stack);
      var controls = FilterControl.flatten(root);
      FilterEdits edits =
          (control, entry, value) ->
              controls.stream().anyMatch(candidate -> candidate == control)
                  && entry >= 0
                  && entry < 256
                  && value.length() <= 256
                  && send("edit", g, c, control.id(), entry, value);
      editor =
          new ElementFragment(
              root,
              WIDTH - 40,
              new ElementFragment.Host() {
                public ResourceLocation screenId() {
                  return screen.getMenu().bound.screen().id();
                }

                public Object value(zone.moddev.mc.lib.gui.model.Element element, boolean maximum) {
                  return provider.configuration(model.filter(g, c)).copy();
                }

                public boolean pending(zone.moddev.mc.lib.gui.model.Element element) {
                  return model.pending();
                }

                public void hoverChanged(
                    zone.moddev.mc.lib.gui.model.Element element, boolean entered) {
                  ElementHover.dispatch(screen.getMenu(), element, entered);
                }

                public ItemStack carried() {
                  return screen.getMenu().getCarried().copy();
                }

                public <T> Optional<T> service(Class<T> type) {
                  return type.isInstance(edits) ? Optional.of(type.cast(edits)) : Optional.empty();
                }
              });
    } catch (RuntimeException invalid) {
      root = null;
      editor = null;
      zone.moddev.mc.lib.MMDLib.LOGGER.warn("Invalid filter editor", invalid);
    }
  }

  private void swatch(GuiGraphics g, Rect r, int group, int mx, int my) {
    boolean selected = model.selected() == group && model.open();
    g.fill(r.x(), r.y(), r.right(), r.bottom(), selected ? 0xffeeeeee : 0xff555555);
    int border = selected ? 2 : 1;
    g.fill(
        r.x() + border,
        r.y() + border,
        r.right() - border,
        r.bottom() - border,
        0xff000000 | model.inventory.group(group).colour());
    if (r.contains(mx, my)) {
      tooltip = model.inventory.group(group).name();
      highlight(g, group);
    }
  }

  public void render(GuiGraphics g, int mx, int my) {
    layout();
    tooltip = null;
    faceTooltip = List.of();
    drawFrame(g, tab, body, model.open());
    for (int index = 0; index < count(); index++) {
      var group = model.inventory.group(index);
      for (var element : groupHighlights.get(group.id()))
        screen
            .presentation()
            .highlight(g, element, ElementHighlight.decoration(0xff000000 | group.colour()));
    }
    if (tab.contains(mx, my)) tooltip = Component.translatable("screen.mmdlib.automation.open");
    int hoveredGroup = colourBar.render(g, mx, my, model.open()).orElse(-1);
    if (hoveredGroup >= 0) {
      tooltip = model.inventory.group(hoveredGroup).name();
      highlight(g, hoveredGroup);
    }
    if (!model.open()) return;
    g.fill(
        body.x() + AutomationLayout.SELECTOR_WIDTH,
        body.y() + 5,
        body.x() + AutomationLayout.SELECTOR_WIDTH + 1,
        body.bottom() - 5,
        0xff526788);
    int shown = selector.height() / AutomationLayout.SELECTOR_PITCH;
    for (int i = 0; i < shown && verticalOffset + i < count(); i++)
      swatch(
          g,
          new Rect(selector.x(), selector.y() + i * AutomationLayout.SELECTOR_PITCH, 14, 14),
          displayOrder.get(verticalOffset + i),
          mx,
          my);
    if (count() > shown) {
      arrow(g, new Rect(selector.x() + 3, body.y() + 1, 8, 6), false, true);
      arrow(g, new Rect(selector.x() + 3, selector.bottom() + 1, 8, 6), true, true);
    }
    for (int f = 0; f < 6; f++) drawFace(g, f, mx, my);
    for (int c = 0; model.addonsEnabled() && c < COLUMNS; c++) {
      if (model.slotVisible(model.selected(), COLUMNS + c)) {
        var f = filter(c);
        g.fill(f.x() + 6, module(c).bottom(), f.x() + 7, f.y(), 0xff425878);
        if (!model.filter(model.selected(), c).isEmpty()) {
          var toggle = filterToggle(c);
          boolean expanded = model.editor() == c;
          boolean hovered = toggle.contains(mx, my);
          g.fill(
              toggle.x(),
              toggle.y(),
              toggle.right(),
              toggle.bottom(),
              hovered ? 0xff7189aa : 0xff425878);
          g.renderOutline(
              toggle.x(),
              toggle.y(),
              toggle.width(),
              toggle.height(),
              hovered ? 0xffb4bac2 : 0xff858b94);
          arrow(
              g,
              new Rect(toggle.x() + (toggle.width() - 5) / 2, toggle.y() + 2, 5, 3),
              !expanded,
              true);
          if (hovered)
            tooltip =
                Component.translatable(
                    expanded
                        ? "screen.mmdlib.automation.collapse"
                        : "screen.mmdlib.automation.filter");
        }
      }
    }
    if (slotFragment != null) slotFragment.render(g, mx, my);
    if (model.open()) highlight(g, model.selected());
    drawEditor(g, mx, my);
  }

  private void highlight(GuiGraphics g, int group) {
    var selected = model.inventory.group(group);
    for (var element : groupHighlights.get(selected.id()))
      screen
          .presentation()
          .highlight(g, element, ElementHighlight.emphasis(0xff000000 | selected.colour()));
  }

  private void drawFace(GuiGraphics g, int index, int mx, int my) {
    var r = face(index);
    var mode = model.mode(model.selected(), index);
    var relative = RelativeFace.values()[index];
    var neighbour =
        faces.render(
            g,
            r,
            screen.getMenu().blockEntity,
            model.inventory.group(model.selected()).world(relative),
            mode.canInsert(),
            mode.canExtract());
    if (r.contains(mx, my))
      faceTooltip =
          List.of(
              Component.translatable("screen.mmdlib.face." + relative.getSerializedName()),
              neighbour,
              Component.translatable("screen.mmdlib.mode." + mode.getSerializedName()));
  }

  public Rect tabBounds() {
    layout();
    return tab;
  }

  public boolean open() {
    return model.open();
  }

  public boolean pending() {
    return model.pending();
  }

  public void close() {
    if (model.open() && !model.pending()) model.send("close", model.selected(), -1, "", 0, "");
  }

  public Selection selection() {
    return new Selection(model.inventory.group(model.selected()).id(), 0);
  }

  public boolean restore(Selection selection) {
    if (pending()) return false;
    int group = model.inventory.index(selection.key());
    if (!model.send("select", Math.max(0, group), -1, "", 0, "")) return false;
    activated();
    return true;
  }

  public void tick() {
    if (open() && !pending()) activated();
    layout();
    if (editor != null) editor.tick();
    if (slotFragment != null) slotFragment.tick();
  }

  public void resized() {
    closeEditorInput();
    if (slotFragment != null) slotFragment.cancelInteraction();
  }

  private static void arrow(GuiGraphics g, Rect r, boolean forward, boolean vertical) {
    for (int i = 0; i < 3; i++) {
      int d = forward ? i : 2 - i;
      if (vertical) g.fill(r.x() + i, r.y() + d, r.right() - i, r.y() + d + 1, 0xffeeeeee);
      else g.fill(r.x() + d, r.y() + i, r.x() + d + 1, r.bottom() - i, 0xffeeeeee);
    }
  }

  private void drawEditor(GuiGraphics g, int mx, int my) {
    if (editorArea == null || editorArea.height() < (2 * AutomationLayout.EDITOR_PADDING)) return;
    g.fill(editorArea.x(), editorArea.y(), editorArea.right(), editorArea.bottom(), 0xff7189aa);
    g.renderOutline(
        editorArea.x(), editorArea.y(), editorArea.width(), editorArea.height(), 0xff526788);
    if (editor == null) {
      g.drawString(mc.font, "?", editorArea.x() + 6, editorArea.y() + 4, 0xffffff);
      return;
    }
    int viewport = editorArea.height() - (2 * AutomationLayout.EDITOR_PADDING);
    if (editor.height() > viewport && viewport > 0) {
      int track = viewport, thumb = Math.max(6, track * viewport / editor.height());
      int y =
          editorArea.y()
              + AutomationLayout.EDITOR_PADDING
              + (track - thumb) * editorScroll / Math.max(1, editor.height() - viewport);
      g.fill(
          editorArea.right() - 4,
          editorArea.y() + AutomationLayout.EDITOR_PADDING,
          editorArea.right() - 2,
          editorArea.bottom() - 4,
          0xff526788);
      g.fill(editorArea.right() - 4, y, editorArea.right() - 2, y + thumb, 0xffd2dfef);
    }
    editor.render(g, mx, my);
    var lines = editor.tooltip(mx, my);
    if (!lines.isEmpty()) faceTooltip = lines;
  }

  public void tooltip(GuiGraphics g, int mx, int my) {
    if (!faceTooltip.isEmpty()) g.renderComponentTooltip(mc.font, faceTooltip, mx, my);
    else if (tooltip != null) g.renderTooltip(mc.font, tooltip, mx, my);
    else if (slotFragment != null) {
      var hit = slotAt(mx, my);
      if (hit != null && hit.hasItem()) return;
      var rows =
          hit == null
              ? slotFragment.tooltip(mx, my)
              : slotFragment.tooltip(model.slotElement(hit.index), mx, my);
      if (!rows.isEmpty()) g.renderComponentTooltip(mc.font, rows, mx, my);
    }
  }

  private net.minecraft.world.inventory.Slot slotAt(double x, double y) {
    if (slotFragment == null
        || !body.contains(x, y)
        || editorArea != null && editorArea.contains(x, y)) return null;
    var slot = screen.nativeSlotAt(x, y);
    if (slot == null) return null;
    var element = model.slotElement(slot.index);
    return element != null && model.visible(model.selected(), element) ? slot : null;
  }

  @Override
  public Optional<List<Component>> nativeTooltip(
      net.minecraft.world.inventory.Slot slot, List<Component> vanilla, int x, int y) {
    layout();
    if (slot == null || slotAt(x, y) != slot) return Optional.empty();
    var element = model.slotElement(slot.index);
    return Optional.of(
        nativeSlots.itemTooltip(
            screen.getMenu().nativeSlots(),
            slot,
            element,
            vanilla,
            e -> slotFragment.tooltip(e, x, y)));
  }

  public List<Rect> exclusions() {
    layout();
    var result = new ArrayList<Rect>();
    result.add(model.open() ? SideDrawerLayout.rail(tab, body) : tab);
    if (model.open()) {
      result.add(body);
    }
    return result;
  }

  public boolean contains(double x, double y) {
    layout();
    return tab.contains(x, y)
        || model.open() && (body.contains(x, y) || SideDrawerLayout.rail(tab, body).contains(x, y));
  }

  /** Returns true only for controls/background. Real item slots must reach vanilla handling. */
  public boolean click(double x, double y, int button) {
    layout();
    selectorFocused = false;
    if (button > 1) return false;
    if (tab.contains(x, y)) {
      send(model.open() ? "close" : "select", model.selected(), -1, "", 0, "");
      return true;
    }
    if (colourBar.click(x, y)) return true;
    if (!model.open()) return false;
    if (SideDrawerLayout.rail(tab, body).contains(x, y) && !screen.drawers().tabContains(x, y))
      return true;
    if (!body.contains(x, y)) closeEditorInput();
    if (x >= body.x() + 2
        && x < body.x() + AutomationLayout.SELECTOR_WIDTH
        && y >= body.y()
        && y < selector.bottom() + 8) {
      int shown = selector.height() / AutomationLayout.SELECTOR_PITCH;
      if (y < selector.y()) verticalOffset = Math.max(0, verticalOffset - 1);
      else if (y >= selector.bottom())
        verticalOffset = Math.min(Math.max(0, count() - shown), verticalOffset + 1);
      else {
        int index = verticalOffset + ((int) y - selector.y()) / AutomationLayout.SELECTOR_PITCH;
        if (index < count()) send("select", displayOrder.get(index), -1, "", 0, "");
      }
      return true;
    }
    for (int f = 0; f < 6; f++)
      if (face(f).contains(x, y)) {
        send(
            "face",
            model.selected(),
            -1,
            model.mode(model.selected(), f).name(),
            f,
            button == 1 ? "reverse" : "");
        return true;
      }
    for (int c = 0; c < COLUMNS; c++) {
      if (model.slotVisible(model.selected(), COLUMNS + c)
          && model.supportsFilter(model.selected(), c)
          && !model.filter(model.selected(), c).isEmpty()
          && filterToggle(c).contains(x, y)) {
        send("editor", model.selected(), c, "", 0, "");
        return true;
      }
    }
    if (editorArea != null && editorArea.contains(x, y)) {
      if (editor != null) editor.click(x, y, button);
      return true;
    }
    if (slotAt(x, y) != null) return false;
    closeEditorInput();
    if (body.contains(x, y)) return true;
    return false;
  }

  public boolean scroll(double x, double y, double amount) {
    layout();
    if (model.open()
        && x >= body.x()
        && x < body.x() + AutomationLayout.EDITOR_X
        && y >= body.y()
        && y < selector.bottom() + 8) {
      verticalOffset += (int) -amount;
      return true;
    }
    if (editorArea != null && editorArea.contains(x, y)) {
      if (editor != null && editor.scroll(x, y, amount)) return true;
      closeEditorInput();
      editorScroll += (int) (-amount * 18);
      return true;
    }
    if (colourBar.scroll(x, y, amount)) return true;
    return false;
  }

  public boolean key(int key, int scan, int modifiers) {
    layout();
    if (editor != null && editor.key(key, scan, modifiers)) return true;
    if (model.open() && key == 256) {
      send(model.editor() >= 0 ? "collapse" : "close", model.selected(), -1, "", 0, "");
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
      keyboardGroup =
          displayOrder.get(
              Math.floorMod(
                  displayOrder.indexOf(keyboardGroup) + (key == 264 || key == 262 ? 1 : -1),
                  count()));
      send("select", keyboardGroup, -1, "", 0, "");
      narrateSelection();
      return true;
    }
    if (selectorFocused && (key == 257 || key == 335)) {
      send("select", keyboardGroup, -1, "", 0, "");
      return true;
    }
    return false;
  }

  public boolean character(char c, int modifiers) {
    layout();
    return editor != null && editor.character(c, modifiers);
  }

  public boolean drag(double x, double y, int button, double dx, double dy) {
    layout();
    return editor != null && editor.drag(x, y, button, dx, dy);
  }

  public boolean release(double x, double y, int button) {
    layout();
    return editor != null && editor.release(x, y, button);
  }

  public void mouseMoved(double x, double y) {
    layout();
    if (editor != null) editor.mouseMoved(x, y);
    if (slotFragment != null) slotFragment.mouseMoved(x, y);
  }

  public void closeEditorInput() {
    if (editor != null) editor.cancelInteraction();
  }

  private boolean selectorFocused;

  private void narrateSelection() {
    mc.getNarrator().sayNow(model.inventory.group(keyboardGroup).name());
  }

  public void removed() {
    closeEditorInput();
    if (slotFragment != null) slotFragment.removed();
    slotFragment = null;
    slotGroup = -1;
    if (editor != null) editor.removed();
    editor = null;
    editorKey = "";
  }
}
