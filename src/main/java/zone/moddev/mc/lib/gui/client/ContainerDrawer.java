package zone.moddev.mc.lib.gui.client;

import java.util.List;
import java.util.LinkedHashSet;
import net.minecraft.world.item.ItemStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import zone.moddev.mc.lib.gui.layout.ContainerDrawerLayout;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.runtime.ContainerDrawerMenu;

/** Item-rendered equipment tab and paged, server-synced storage cells. */
final class ContainerDrawer {
  private final boolean equipment;
  private final int color;
  private final MachineScreen screen;
  private final ContainerDrawerMenu menu;
  private ContainerDrawerLayout layout;
  private boolean handledPress;
  private int mouseX, mouseY;
  private final LinkedHashSet<Integer> dragSlots = new LinkedHashSet<>();
  private int dragButton = -1, firstSlot = -1, lastClickSlot = -1;
  private long dragRevision, lastClickTime;
  private ItemStack dragStack = ItemStack.EMPTY, lastClickStack = ItemStack.EMPTY;
  private Rect dragBody;
  private boolean collectQueued;
  private String lastSource = "";

  private int slotAt(double x, double y) {
    if (!bodyVisible()) return -1;
    var state = menu.state();
    for (int i = 0; i < Math.min(state.contents().size(), layout.pageSize()); i++)
      if (layout.cell(i).contains(x, y)) return state.offset() + i;
    return -1;
  }

  private boolean inventoryPending() {
    return screen.getMenu().containerDrawer.pendingInventory()
        || screen.getMenu().curiosDrawer.pendingInventory();
  }

  private boolean inventoryTarget(double x, double y) {
    // Outside clicks can drop the carried stack. Settings inside the panel remain usable.
    return !screen.onDrawerTab(x, y)
            && screen.hasClickedOutside(x, y, screen.guiLeft(), screen.guiTop(), 0)
        || screen.getMenu().slots.stream().anyMatch(slot -> overSlot(slot, x, y));
  }

  private boolean overSlot(net.minecraft.world.inventory.Slot slot, double x, double y) {
    return x >= screen.guiLeft() + slot.x
        && x < screen.guiLeft() + slot.x + 16
        && y >= screen.guiTop() + slot.y
        && y < screen.guiTop() + slot.y + 16;
  }

  private int playerSlotAt(double x, double y) {
    for (var slot : screen.getMenu().slots)
      if (slot.container == Minecraft.getInstance().player.getInventory()
          && slot.getContainerSlot() < 36
          && overSlot(slot, x, y)) return ContainerDrawerMenu.MAX_SLOTS + slot.getContainerSlot();
    return -1;
  }

  void cancelGesture() {
    dragButton = -1;
    dragSlots.clear();
    dragStack = ItemStack.EMPTY;
    collectQueued = false;
    lastClickSlot = -1;
  }

  private boolean gestureValid() {
    return bodyVisible()
        && !menu.pending()
        && menu.state().revision() == dragRevision
        && layout.body().equals(dragBody)
        && ItemStack.matches(screen.getMenu().getCarried(), dragStack);
  }

  private void addDragSlot(int slot) {
    if (slot < 0 || dragSlots.size() >= ContainerDrawerMenu.MAX_PAGE + 36) return;
    var stored =
        slot >= ContainerDrawerMenu.MAX_SLOTS
            ? Minecraft.getInstance()
                .player
                .getInventory()
                .getItem(slot - ContainerDrawerMenu.MAX_SLOTS)
            : menu.state().contents().get(slot - menu.state().offset());
    if ((stored.isEmpty() || ItemStack.isSameItemSameComponents(stored, dragStack))
        && stored.getCount() < dragStack.getMaxStackSize()
        && dragSlots.size() < dragStack.getCount()) dragSlots.add(slot);
  }

  private void beginDrag(int slot, int button) {
    dragButton = button;
    firstSlot = slot;
    dragRevision = menu.state().revision();
    dragBody = layout.body();
    dragStack = screen.getMenu().getCarried().copy();
    dragSlots.clear();
    addDragSlot(slot);
    handledPress = true;
  }

  private void collect() {
    if (!menu.pending()) {
      collectQueued = false;
      var state = menu.state();
      if (bodyVisible()
          && state.keys().get(state.selected()).equals(lastSource)
          && lastClickSlot >= state.offset()
          && lastClickSlot < state.offset() + state.contents().size()
          && ItemStack.isSameItemSameComponents(screen.getMenu().getCarried(), lastClickStack))
        send(ContainerDrawerMenu.COLLECT, lastClickSlot, state.pageSize());
    }
  }

  ContainerDrawer(MachineScreen screen) {
    this(screen, false);
  }

  ContainerDrawer(MachineScreen screen, boolean equipment) {
    this.equipment = equipment;
    color = equipment ? 0xa6a0ca : 0xc5aa7b;
    this.screen = screen;
    menu = equipment ? screen.getMenu().curiosDrawer : screen.getMenu().containerDrawer;
  }

  private boolean available() {
    return !menu.state().icons().isEmpty();
  }

  private void layout() {
    int slots = available() ? menu.state().sizes().get(menu.state().selected()) : 0;
    layout =
        equipment
            ? ContainerDrawerLayout.equipment(
                screen.panelBounds(),
                screen.drawerBottom(),
                slots,
                !screen.getMenu().containerDrawer.state().icons().isEmpty())
            : ContainerDrawerLayout.of(screen.panelBounds(), screen.drawerBottom(), slots);
  }

  private boolean bodyVisible() {
    return available() && menu.state().open() && layout.pageSize() > 0;
  }

  private void send(int kind, int argument, int size) {
    menu.request(kind, argument, size)
        .ifPresent(
            request -> {
              screen.rememberContainerIntent(equipment, menu.state(), kind, argument);
              PacketDistributor.sendToServer(request);
            });
  }

  void close() {
    if (menu.state().open() && !menu.pending())
      send(ContainerDrawerMenu.TOGGLE, 0, menu.state().pageSize());
  }

  List<Rect> exclusions() {
    layout();
    if (!available()) return List.of();
    return bodyVisible() ? List.of(layout.rail(), layout.body()) : List.of(layout.tab());
  }

  boolean contains(double x, double y) {
    layout();
    return available()
        && (layout.tab().contains(x, y)
            || bodyVisible() && (layout.body().contains(x, y) || layout.rail().contains(x, y)));
  }

  boolean tabContains(double x, double y) {
    layout();
    return available() && layout.tab().contains(x, y);
  }

  private Component title() {
    return equipment
        ? Component.translatable("screen.mmdlib.curios.title")
        : menu.state().icons().get(menu.state().selected()).getHoverName();
  }

  void renderTab(GuiGraphics g) {
    layout();
    if (!available()) return;
    SideDrawerRenderer.tab(g, layout.tab(), color);
    if (equipment)
      emptyIcon(
          g,
          ResourceLocation.parse("curios:slot/empty_curio_slot"),
          layout.tab().x() + 3,
          layout.tab().y() + 4);
    else
      g.renderItem(
          menu.state().icons().get(menu.state().selected()),
          layout.tab().x() + 3,
          layout.tab().y() + 4);
  }

  private static void emptyIcon(GuiGraphics g, ResourceLocation icon, int x, int y) {
    g.blit(
        x,
        y,
        0,
        16,
        16,
        Minecraft.getInstance()
            .getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
            .apply(icon));
  }

  private static String slotName(zone.moddev.mc.lib.gui.runtime.EquippedContainers.SlotInfo info) {
    String key = "curios.identifier." + info.identifier();
    return (net.minecraft.client.resources.language.I18n.exists(key)
            ? Component.translatable(key).getString()
            : info.identifier())
        + (info.index() == 0 ? "" : " " + (info.index() + 1));
  }

  void render(GuiGraphics g, int mx, int my) {
    layout();
    mouseX = mx;
    mouseY = my;
    if (!available()) return;
    var state = menu.state();
    var icon = state.icons().get(state.selected());
    if (bodyVisible())
      SideDrawerRenderer.open(
          g, layout.tab(), layout.body(), zone.moddev.mc.lib.gui.model.Element.Edge.LEFT, color);
    renderTab(g);
    if (dragButton >= 0 && !gestureValid()) cancelGesture();
    if (collectQueued) collect();
    if (!bodyVisible()) return;
    if (layout.pageSize() != state.pageSize() && !menu.pending())
      send(ContainerDrawerMenu.PAGE, state.offset(), layout.pageSize());
    var font = Minecraft.getInstance().font;
    var body = layout.body();
    // The selected item itself is also the drawer header, including item tint/model components.
    if (equipment)
      emptyIcon(
          g, ResourceLocation.parse("curios:slot/empty_curio_slot"), body.x() + 4, body.y() + 3);
    else g.renderItem(icon, body.x() + 4, body.y() + 3);
    if (body.width() > 45)
      g.drawString(
          font,
          font.plainSubstrByWidth(title().getString(), body.width() - 28),
          body.x() + 24,
          body.y() + 7,
          0xff404040,
          false);
    if (equipment) {
      g.drawString(
          font,
          Component.translatable("screen.mmdlib.curios.equipment"),
          body.x() + 6,
          body.y() + 22,
          0xff404040,
          false);
      g.drawString(
          font,
          Component.translatable("screen.mmdlib.curios.functional.short"),
          body.right() - 40,
          body.y() + 22,
          0xff404040,
          false);
      g.drawString(
          font,
          Component.translatable("screen.mmdlib.curios.cosmetic.short"),
          body.right() - 20,
          body.y() + 22,
          0xff404040,
          false);
    }
    for (int i = 0; i < Math.min(state.contents().size(), layout.pageSize()); i++) {
      var cell = layout.cell(i);
      var info = i < state.descriptions().size() ? state.descriptions().get(i) : null;
      if (equipment && info == null) continue;
      if (equipment && i % 2 == 0)
        g.drawString(
            font,
            font.plainSubstrByWidth(slotName(info), body.width() - 54),
            body.x() + 6,
            cell.y() + 5,
            0xff404040,
            false);
      if (info != null && !info.available()) {
        g.drawString(font, "-", cell.x() + 6, cell.y() + 5, 0xff666666, false);
        continue;
      }
      g.blitSprite(ResourceLocation.parse("mmdlib:slot"), cell.x(), cell.y(), 18, 18);
      var stack = state.contents().get(i);
      if (stack.isEmpty() && info != null) emptyIcon(g, info.icon(), cell.x() + 1, cell.y() + 1);
      if (!stack.isEmpty()) {
        g.renderItem(stack, cell.x() + 1, cell.y() + 1);
        g.renderItemDecorations(font, stack, cell.x() + 1, cell.y() + 1);
      }
      // Generic insertion may route to another slot. Highlight targets without inventing counts.
      if (cell.contains(mx, my) || dragButton >= 0 && dragSlots.contains(state.offset() + i))
        g.fill(cell.x() + 1, cell.y() + 1, cell.right() - 1, cell.bottom() - 1, 0x80ffffff);
    }
    var previous = layout.previous();
    var next = layout.next();
    g.drawString(
        font,
        "<",
        previous.x() + 3,
        previous.y() + 3,
        state.offset() > 0 ? 0xff404040 : 0xff999999,
        false);
    g.drawString(
        font,
        ">",
        next.x() + 3,
        next.y() + 3,
        state.offset() + state.pageSize() < state.sizes().get(state.selected())
            ? 0xff404040
            : 0xff999999,
        false);
    if (body.width() >= 70) {
      String page =
          (state.offset() / state.pageSize() + 1)
              + "/"
              + ((state.sizes().get(state.selected()) + state.pageSize() - 1) / state.pageSize());
      g.drawString(
          font,
          page,
          body.x() + (body.width() - font.width(page)) / 2,
          previous.y() + 3,
          0xff404040,
          false);
    }
  }

  void tooltip(GuiGraphics g, int mx, int my) {
    if (!contains(mx, my)) return;
    var font = Minecraft.getInstance().font;
    var state = menu.state();
    if (layout.tab().contains(mx, my)) {
      var lines = new java.util.ArrayList<Component>();
      lines.add(title());
      lines.add(
          Component.translatable(
              equipment ? "screen.mmdlib.curios.toggle" : "screen.mmdlib.container.toggle"));
      if (state.open())
        lines.add(
            Component.translatable(
                equipment ? "screen.mmdlib.curios.store" : "screen.mmdlib.container.store"));
      if (!equipment) lines.add(Component.translatable("screen.mmdlib.container.gestures"));
      if (layout.pageSize() == 0) lines.add(Component.translatable("screen.mmdlib.container.room"));
      g.renderComponentTooltip(font, lines, mx, my);
    } else if (bodyVisible()) {
      if (equipment) {
        for (int i = 0; i < Math.min(state.descriptions().size(), layout.pageSize()); i++) {
          var info = state.descriptions().get(i);
          var cell = layout.cell(i);
          if (cell.contains(mx, my) && state.contents().get(i).isEmpty()) {
            g.renderComponentTooltip(
                font,
                List.of(
                    Component.literal(slotName(info)),
                    Component.translatable(
                        !info.available()
                            ? "screen.mmdlib.curios.unavailable"
                            : info.cosmetic()
                                ? "screen.mmdlib.curios.cosmetic"
                                : "screen.mmdlib.curios.functional")),
                mx,
                my);
            return;
          }
        }
        if (my < layout.body().y() + 34) {
          g.renderComponentTooltip(
              font,
              List.of(
                  title(),
                  Component.translatable("screen.mmdlib.curios.columns"),
                  Component.translatable("screen.mmdlib.curios.store")),
              mx,
              my);
          return;
        }
      }
      for (int i = 0; i < Math.min(state.contents().size(), layout.pageSize()); i++)
        if (layout.cell(i).contains(mx, my) && !state.contents().get(i).isEmpty()) {
          g.renderTooltip(font, state.contents().get(i), mx, my);
          return;
        }
      if (layout.selection().contains(mx, my))
        g.renderComponentTooltip(
            font,
            List.of(
                state.icons().get(state.selected()).getHoverName(),
                Component.translatable(
                    state.icons().size() > 1
                        ? "screen.mmdlib.container.select"
                        : "screen.mmdlib.container.store")),
            mx,
            my);
    }
  }

  boolean click(double x, double y, int button) {
    layout();
    mouseX = (int) x;
    mouseY = (int) y;
    int hit = slotAt(x, y);
    long now = net.minecraft.Util.getMillis();
    if (!equipment
        && button == 0
        && hit >= 0
        && hit == lastClickSlot
        && now - lastClickTime < 250
        && !Screen.hasShiftDown()) {
      collectQueued = true;
      handledPress = true;
      collect();
      lastClickTime = 0;
      return true;
    }
    if (screen.getMenu().containerDrawer.pending() || screen.getMenu().curiosDrawer.pending()) {
      // Configuration requests do not touch vanilla slots. Only serialize item-changing gestures.
      boolean blocked = contains(x, y) || inventoryPending() && inventoryTarget(x, y);
      handledPress = blocked;
      return blocked;
    }
    if (!available()) return false;
    var state = menu.state();
    if (layout.tab().contains(x, y)) {
      handledPress = true;
      if (button == 0) {
        if (!state.open()) screen.closeLeftDrawers();
        send(ContainerDrawerMenu.TOGGLE, 0, state.pageSize());
      }
      return true;
    }
    if (!bodyVisible()) return false;
    if (!equipment
        && button >= 0
        && button <= 1
        && !Screen.hasShiftDown()
        && !screen.getMenu().getCarried().isEmpty()) {
      // Let AbstractContainerScreen own complete gestures that start in the normal inventory.
      // Intercepting just their press breaks vanilla quick-craft and double-click tracking.
      if (hit >= 0) {
        beginDrag(hit, button);
        return true;
      }
    }
    if (layout.rail().contains(x, y) && !screen.onDrawerTab(x, y)) {
      handledPress = true;
      return true;
    }
    if (bodyVisible() && layout.body().contains(x, y)) {
      handledPress = true;
      if (button < 0 || button > 1) return true;
      if (layout.pageSize() != state.pageSize()) return true;
      if (layout.selection().contains(x, y)) {
        if (state.icons().size() > 1)
          send(
              ContainerDrawerMenu.SELECT,
              (state.selected() + 1) % state.icons().size(),
              state.pageSize());
      } else if (layout.previous().contains(x, y)) page(-1);
      else if (layout.next().contains(x, y)) page(1);
      else
        for (int i = 0; i < Math.min(state.contents().size(), layout.pageSize()); i++)
          if (layout.cell(i).contains(x, y)) {
            if (!equipment && button == 0 && !Screen.hasShiftDown()) {
              lastClickSlot = state.offset() + i;
              lastClickTime = now;
              lastClickStack = state.contents().get(i).copy();
              lastSource = state.keys().get(state.selected());
            } else lastClickSlot = -1;
            send(
                Screen.hasShiftDown() ? ContainerDrawerMenu.QUICK_TAKE : ContainerDrawerMenu.PICKUP,
                (state.offset() + i) * 2 + button,
                state.pageSize());
            break;
          }
      return true;
    }
    if (button == 0 && Screen.hasShiftDown()) {
      int slot = playerSlotAt(x, y);
      if (slot >= 0) {
        send(
            ContainerDrawerMenu.QUICK_STORE,
            slot - ContainerDrawerMenu.MAX_SLOTS,
            state.pageSize());
        handledPress = true;
        return true;
      }
    }
    return false;
  }

  private void page(int direction) {
    var state = menu.state();
    int offset = Math.max(0, state.offset() + direction * state.pageSize());
    if (offset < state.sizes().get(state.selected()) && offset != state.offset())
      send(ContainerDrawerMenu.PAGE, offset, state.pageSize());
  }

  boolean scroll(double x, double y, double amount) {
    if (!contains(x, y) || !menu.state().open()) return false;
    if (!menu.pending() && amount != 0) page(amount < 0 ? 1 : -1);
    return true;
  }

  boolean key(int key, int scan) {
    layout();
    if (key == 256 && dragButton >= 0) {
      cancelGesture();
      return true;
    }
    if (!equipment && bodyVisible() && !menu.pending()) {
      int slot = slotAt(mouseX, mouseY);
      if (slot >= 0) {
        for (int hotbar = 0; hotbar < 9; hotbar++)
          if (Minecraft.getInstance().options.keyHotbarSlots[hotbar].matches(key, scan)) {
            cancelGesture();
            menu.request(ContainerDrawerMenu.SWAP, hotbar, menu.state().pageSize(), List.of(slot))
                .ifPresent(PacketDistributor::sendToServer);
            return true;
          }
      }
    }
    if (key == 256 && menu.state().open()) {
      close();
      return true;
    }
    if (key == 256) return false;
    if (inventoryPending()
        && (Minecraft.getInstance().options.keyDrop.matches(key, scan)
            || java.util.Arrays.stream(Minecraft.getInstance().options.keyHotbarSlots)
                .anyMatch(binding -> binding.matches(key, scan)))) return true;
    return contains(mouseX, mouseY)
        && !Minecraft.getInstance().options.keyInventory.matches(key, scan);
  }

  void mouseMoved(double x, double y) {
    mouseX = (int) x;
    mouseY = (int) y;
  }

  boolean drag(double x, double y, int button) {
    layout();
    if (dragButton >= 0) {
      if (!gestureValid() || button != dragButton) cancelGesture();
      else {
        int slot = slotAt(x, y);
        addDragSlot(slot >= 0 ? slot : playerSlotAt(x, y));
      }
      return true;
    }
    return handledPress;
  }

  boolean release(double x, double y, int button) {
    boolean handled = handledPress;
    handledPress = false;
    layout();
    if (dragButton >= 0) {
      if (gestureValid() && button == dragButton) {
        if (dragSlots.size() > 1) {
          menu.request(
                  ContainerDrawerMenu.DISTRIBUTE,
                  button,
                  menu.state().pageSize(),
                  List.copyOf(dragSlots))
              .ifPresent(PacketDistributor::sendToServer);
        } else if (slotAt(x, y) == firstSlot)
          send(ContainerDrawerMenu.PICKUP, firstSlot * 2 + button, menu.state().pageSize());
      }
      cancelGesture();
      return true;
    }
    return handled;
  }
}
