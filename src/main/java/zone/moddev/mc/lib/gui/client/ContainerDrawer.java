package zone.moddev.mc.lib.gui.client;

import java.util.LinkedHashSet;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.PacketDistributor;

import zone.moddev.mc.lib.gui.layout.ContainerDrawerLayout;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.containers.ContainerInventoryElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.runtime.ContainerDrawerMenu;
import zone.moddev.mc.lib.gui.runtime.EquippedContainers;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;

/** Item-rendered equipment tab and paged, server-synced storage cells. */
public class ContainerDrawer extends Drawer {
    private final ContainerDrawerMenu menu;
    private ContainerDrawerLayout layout;
    private ContainerInventoryElement inventory;
    private ElementFragment cells;
    private ContainerInventoryElement.Page cellPage;
    private String cellSource;
    private boolean handledPress;
    private int elementButton = -1;
    private long cellRevision = Long.MIN_VALUE;
    private int mouseX, mouseY;
    private final LinkedHashSet<Integer> dragSlots = new LinkedHashSet<>();
    private int dragButton = -1, firstSlot = -1, lastClickSlot = -1;
    private long dragRevision, lastClickTime;
    private ItemStack dragStack = ItemStack.EMPTY,
        lastClickStack = ItemStack.EMPTY;
    private Rect dragBody;
    private boolean collectQueued;
    private String lastSource = "";

    private int slotAt(double x, double y) {
        if (!bodyVisible() || !layout.body().contains(x, y))
            return -1;
        prepareCells();
        var hit = cells.hit(x, y);
        return hit instanceof SlotElement slot ? slot.index() : -1;
    }

    private boolean inputLocked() {
        return screen.drawers().pending(edge()) || inventoryPending();
    }

    private void discardCells() {
        if (cells != null)
            cells.removed();
        cells = null;
        inventory = null;
        cellPage = null;
        cellSource = null;
    }

    private boolean readyCells() {
        layout();
        if (!bodyVisible()) {
            discardCells();
            return false;
        }
        prepareCells();
        return true;
    }

    private boolean cellInputArea(double x, double y) {
        return layout.body().contains(x, y)
            && !layout.selection().contains(x, y)
            && !layout.previous().contains(x, y)
            && !layout.next().contains(x, y);
    }

    private boolean inventoryPending() {
        return screen.getMenu().components().pendingInventory();
    }

    private boolean inventoryTarget(double x, double y) {
        // Outside clicks can drop the carried stack. Settings inside the panel
        // remain usable.
        return !screen.drawers().tabContains(x, y) && screen
            .hasClickedOutside(x, y, screen.guiLeft(), screen.guiTop(), 0)
            || screen.nativeSlotAt(x, y, false) != null;
    }

    private int playerSlotAt(double x, double y) {
        return menu.targets().playerTarget(screen.nativeSlotAt(x, y));
    }

    void cancelGesture() {
        dragButton = -1;
        dragSlots.clear();
        dragStack = ItemStack.EMPTY;
        collectQueued = false;
        lastClickSlot = -1;
    }

    private boolean gestureValid() {
        return bodyVisible() && !inputLocked()
            && menu.state().revision() == dragRevision
            && layout.body().equals(dragBody)
            && ItemStack.matches(screen.getMenu().getCarried(), dragStack);
    }

    private void addDragSlot(int slot) {
        if (!menu.targets().contains(slot)
            || dragSlots.size() >= menu.targets().maxTargets())
            return;
        var stored = menu.targets().contents(slot);
        if ((stored.isEmpty()
            || ItemStack.isSameItemSameComponents(stored, dragStack))
            && stored.getCount() < dragStack.getMaxStackSize()
            && dragSlots.size() < dragStack.getCount())
            dragSlots.add(slot);
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
        if (!inputLocked()) {
            collectQueued = false;
            var state = menu.state();
            if (bodyVisible()
                && state.keys().get(state.selected()).equals(lastSource)
                && lastClickSlot >= state.offset()
                && lastClickSlot < state.offset() + state.contents().size()
                && ItemStack.isSameItemSameComponents(
                    screen.getMenu().getCarried(), lastClickStack))
                send(ContainerDrawerMenu.COLLECT, lastClickSlot,
                    state.pageSize());
        }
    }

    public ContainerDrawer(MachineScreen screen,
        zone.moddev.mc.lib.gui.drawer.DrawerDefinition definition,
        boolean equipment) {
        super(screen, definition, (graphics, x, y) -> {
        }, 0xc5aa7b);
        menu = equipment
            ? screen.getMenu().components().get(BuiltinMenuComponents.CURIOS)
            : screen.getMenu().components().get(BuiltinMenuComponents.BACKPACK);
    }

    public boolean available() {
        return !menu.state().icons().isEmpty();
    }

    private void layout() {
        int slots = available()
            ? menu.state().sizes().get(menu.state().selected())
            : 0;
        layout = ContainerDrawerLayout.of(screen.panelBounds(),
            screen.drawerBottom(), slots, super.tabBounds(),
            menu.policy().layout(), edge(), screen.width);
    }

    public ContainerDrawerLayout contentLayout() {
        layout();
        return layout;
    }

    private boolean bodyVisible() {
        return available() && menu.state().open() && layout.pageSize() > 0;
    }

    private void send(int kind, int argument, int size) {
        if (!menu.allows(kind))
            return;
        menu.request(kind, argument, size).ifPresent(request -> {
            var state = menu.state();
            if (!state.keys().isEmpty()) {
                if (kind == ContainerDrawerMenu.TOGGLE && state.open())
                    remember(false, selection());
                else if (kind == ContainerDrawerMenu.TOGGLE
                    || kind == ContainerDrawerMenu.SELECT
                    || kind == ContainerDrawerMenu.PAGE) {
                    int index = kind == ContainerDrawerMenu.SELECT
                        ? argument
                        : state.selected();
                    remember(true,
                        new Selection(state.keys().get(index),
                            kind == ContainerDrawerMenu.PAGE
                                ? argument
                                : state.offset()));
                }
            }
            PacketDistributor.sendToServer(request);
        });
    }

    public void close() {
        cancelGesture();
        if (cells != null)
            cells.cancelInteraction();
        if (menu.state().open() && !menu.pending())
            menu.request(ContainerDrawerMenu.TOGGLE, 0, menu.state().pageSize())
                .ifPresent(PacketDistributor::sendToServer);
    }

    public List<Rect> exclusions() {
        layout();
        if (!available())
            return List.of();
        return bodyVisible()
            ? List.of(layout.rail(), layout.body())
            : List.of(layout.tab());
    }

    public boolean contains(double x, double y) {
        layout();
        return available() && (layout.tab().contains(x, y) || bodyVisible()
            && (layout.body().contains(x, y) || layout.rail().contains(x, y)));
    }

    public boolean tabContains(double x, double y) {
        layout();
        return available() && layout.tab().contains(x, y);
    }

    protected EquippedContainers.Presentation presentation() {
        return menu.state().presentations().get(menu.state().selected());
    }

    /** Provider defaults may also be customized by a client drawer subclass. */
    protected List<Component> gestureInstructions() {
        return presentation().gestureRows();
    }

    private Component title() {
        return presentation().title();
    }

    @Override
    public int colour() {
        return available() ? presentation().colour() : super.colour();
    }

    @Override
    public IconSprite iconSprite() {
        if (!available())
            return super.iconSprite();
        return presentation().icon().map(IconSprite::atlas)
            .orElseGet(() -> IconSprite.item(() -> menu.state().icons()
                .get(menu.state().selected()).copy()));
    }

    /**
     * Called when source, page metadata or geometry changes. Override or supply
     * custom slot factories.
     */
    protected ContainerInventoryElement createInventory(
        ContainerInventoryElement.Page page) {
        return new ContainerInventoryElement(page);
    }

    private void prepareCells() {
        var state = menu.state();
        int count = Math.min(state.contents().size(), layout.pageSize());
        var bounds = new java.util.ArrayList<Rect>();
        for (int i = 0; i < count; i++)
            bounds.add(
                layout.cell(i).offset(-layout.body().x(), -layout.body().y()));
        var descriptions = state.descriptions().isEmpty()
            ? List.<EquippedContainers.SlotInfo>of()
            : state.descriptions().subList(0, count);
        var page = new ContainerInventoryElement.Page(state.offset(),
            layout.body().width(), layout.body().height(), bounds, descriptions,
            presentation());
        String source = state.keys().get(state.selected());
        if (!page.equals(cellPage) || !source.equals(cellSource)) {
            if (cells != null)
                cells.removed();
            cellPage = page;
            cellSource = source;
            inventory = java.util.Objects.requireNonNull(createInventory(page));
            cells = new ElementFragment(inventory, page.width(),
                new ElementFragment.Host() {
                    public ResourceLocation screenId() {
                        return ResourceLocation
                            .parse("mmdlib:container_drawer");
                    }

                    public ItemStack contents(SlotElement element) {
                        int local = element.index() - menu.state().offset();
                        return local >= 0
                            && local < menu.state().contents().size()
                                ? menu.state().contents().get(local).copy()
                                : ItemStack.EMPTY;
                    }

                    public Object value(Element element, boolean maximum) {
                        return inventory.drawerValue(element, maximum,
                            element instanceof SlotElement slot
                                ? contents(slot)
                                : ItemStack.EMPTY,
                            element instanceof SlotElement slot
                                && dragButton >= 0
                                && dragSlots.contains(slot.index()));
                    }

                    public Rect hitBounds(Element element, Rect frame) {
                        return element instanceof SlotElement slot
                            ? slot.cellHitBounds(frame)
                            : frame;
                    }

                    public boolean inputEnabled() {
                        return !inputLocked();
                    }

                    public boolean visible(Element element) {
                        return bodyVisible();
                    }

                    public boolean pending(Element element) {
                        return inputLocked();
                    }

                    public ItemStack carried() {
                        return screen.getMenu().getCarried().copy();
                    }
                });
        }
        if (cellRevision != state.revision()) {
            cells.cancelInteraction();
            cellRevision = state.revision();
        }
        cells.place(layout.body().x(), layout.body().y(), layout.body());
    }

    /**
     * Composed virtual-cell tooltip, including all parent rows after the native
     * item hook.
     */
    public List<Component> cellTooltip(int x, int y) {
        layout();
        int index = slotAt(x, y);
        if (index < 0)
            return List.of();
        var slot = inventory.slots().stream().filter(s -> s.index() == index)
            .findFirst().orElseThrow();
        var stack = menu.state().contents().get(index - menu.state().offset())
            .copy();
        var rows = cells.tooltip(slot, x, y);
        return stack.isEmpty()
            ? rows
            : NativeSlotRenderer.itemTooltip(slot, stack,
                Screen.getTooltipFromItem(Minecraft.getInstance(), stack),
                rows);
    }

    @Override
    public void removed() {
        discardCells();
        elementButton = -1;
        cancelGesture();
    }

    @Override
    public void tick() {
        if (readyCells())
            cells.tick();
    }

    public Rect tabBounds() {
        layout();
        return layout.tab();
    }

    public boolean open() {
        return menu.state().open();
    }

    public boolean pending() {
        return menu.pending();
    }

    public void resized() {
        if (cells != null)
            cells.cancelInteraction();
        cancelGesture();
    }

    public Selection selection() {
        var state = menu.state();
        return state.keys().isEmpty()
            ? new Selection()
            : new Selection(state.keys().get(state.selected()), state.offset());
    }

    public boolean restore(Selection selection) {
        if (!screen.getMenu().bound.screen().playerInventory())
            return true;
        var state = menu.state();
        if (state.keys().isEmpty() || pending())
            return false;
        int index = state.keys().indexOf(selection.key());
        if (index < 0)
            return true;
        if (!state.open())
            menu.request(ContainerDrawerMenu.TOGGLE, 0, state.pageSize())
                .ifPresent(PacketDistributor::sendToServer);
        else if (state.selected() != index)
            menu.request(ContainerDrawerMenu.SELECT, index, state.pageSize())
                .ifPresent(PacketDistributor::sendToServer);
        else {
            int offset = Math.min(selection.offset(),
                Math.max(0, state.sizes().get(index) - 1));
            offset = offset / state.pageSize() * state.pageSize();
            if (state.offset() != offset)
                menu.request(ContainerDrawerMenu.PAGE, offset, state.pageSize())
                    .ifPresent(PacketDistributor::sendToServer);
            activated();
            return true;
        }
        return false;
    }

    public void render(GuiGraphics g, int mx, int my) {
        layout();
        mouseX = mx;
        mouseY = my;
        if (!available()) {
            discardCells();
            return;
        }
        var state = menu.state();
        drawFrame(g, layout.tab(), layout.body(), bodyVisible());
        if (dragButton >= 0 && !gestureValid())
            cancelGesture();
        if (collectQueued)
            collect();
        if (!bodyVisible()) {
            discardCells();
            return;
        }
        if (layout.pageSize() != state.pageSize() && !menu.pending())
            send(ContainerDrawerMenu.PAGE, state.offset(), layout.pageSize());
        var font = Minecraft.getInstance().font;
        var body = layout.body();
        iconSprite().draw(g, body.x() + 4, body.y() + 3);
        if (body.width() > 45)
            g.drawString(font,
                font.plainSubstrByWidth(title().getString(), body.width() - 28),
                body.x() + 24, body.y() + 7, 0xff404040, false);
        prepareCells();
        cells.render(g, mx, my);
        var previous = layout.previous();
        var next = layout.next();
        g.drawString(font, "<", previous.x() + 3, previous.y() + 3,
            state.offset() > 0 ? 0xff404040 : 0xff999999, false);
        g.drawString(font, ">", next.x() + 3, next.y() + 3,
            state.offset() + state.pageSize() < state.sizes()
                .get(state.selected()) ? 0xff404040 : 0xff999999,
            false);
        if (body.width() >= 70) {
            String page = (state.offset() / state.pageSize() + 1) + "/"
                + ((state.sizes().get(state.selected()) + state.pageSize() - 1)
                    / state.pageSize());
            g.drawString(font, page,
                body.x() + (body.width() - font.width(page)) / 2,
                previous.y() + 3, 0xff404040, false);
        }
    }

    public void tooltip(GuiGraphics g, int mx, int my) {
        if (!contains(mx, my))
            return;
        var font = Minecraft.getInstance().font;
        var state = menu.state();
        if (layout.tab().contains(mx, my)) {
            var lines = new java.util.ArrayList<Component>();
            lines.add(title());
            lines.addAll(presentation().tabRows());
            if (state.open())
                lines.addAll(presentation().openRows());
            lines.addAll(gestureInstructions());
            if (layout.pageSize() == 0)
                lines.add(
                    Component.translatable("screen.mmdlib.container.room"));
            g.renderComponentTooltip(font, lines, mx, my);
        } else if (bodyVisible()) {
            int hit = slotAt(mx, my);
            if (hit >= 0) {
                var rows = cellTooltip(mx, my);
                var stack = state.contents().get(hit - state.offset()).copy();
                if (!rows.isEmpty())
                    g.renderTooltip(font, rows, stack.getTooltipImage(), stack,
                        mx, my);
                return;
            }
            if (!presentation().headerRows().isEmpty()
                && my < layout.body().y() + menu.policy().layout().cellY()) {
                var rows = new java.util.ArrayList<Component>();
                rows.add(title());
                rows.addAll(presentation().headerRows());
                g.renderComponentTooltip(font, rows, mx, my);
                return;
            }
            if (layout.selection().contains(mx, my))
                g.renderComponentTooltip(font,
                    List.of(state.icons().get(state.selected()).getHoverName(),
                        Component.translatable(state.icons().size() > 1
                            ? "screen.mmdlib.container.select"
                            : "screen.mmdlib.container.store")),
                    mx, my);
        }
    }

    public boolean click(double x, double y, int button) {
        layout();
        mouseX = (int) x;
        mouseY = (int) y;
        int hit = slotAt(x, y);
        long now = net.minecraft.Util.getMillis();
        if (readyCells() && !inputLocked() && dragButton < 0) {
            if (elementButton >= 0 || cellInputArea(x, y)) {
                if (cells.click(x, y, button)) {
                    cancelGesture();
                    if (elementButton < 0)
                        elementButton = button;
                    handledPress = true;
                    return true;
                }
            } else
                cells.cancelInteraction();
        }
        if (menu.allows(ContainerDrawerMenu.COLLECT) && button == 0 && hit >= 0
            && hit == lastClickSlot && now - lastClickTime < 250
            && !Screen.hasShiftDown()) {
            collectQueued = true;
            handledPress = true;
            collect();
            lastClickTime = 0;
            return true;
        }
        if (inputLocked()) {
            // Configuration requests do not touch vanilla slots. Only serialize
            // item-changing
            // gestures.
            boolean blocked = contains(x, y)
                || inventoryPending() && inventoryTarget(x, y);
            handledPress = blocked;
            return blocked;
        }
        if (!available())
            return false;
        var state = menu.state();
        if (layout.tab().contains(x, y)) {
            handledPress = true;
            if (button == 0) {
                if (!state.open())
                    activated();
                send(ContainerDrawerMenu.TOGGLE, 0, state.pageSize());
            }
            return true;
        }
        if (!bodyVisible())
            return false;
        if (menu.allows(ContainerDrawerMenu.DISTRIBUTE) && button >= 0
            && button <= 1 && !Screen.hasShiftDown()
            && !screen.getMenu().getCarried().isEmpty()) {
            // Let AbstractContainerScreen own complete gestures that start in
            // the normal inventory.
            // Intercepting just their press breaks vanilla quick-craft and
            // double-click tracking.
            if (hit >= 0) {
                beginDrag(hit, button);
                return true;
            }
        }
        if (layout.rail().contains(x, y)
            && !screen.drawers().tabContains(x, y)) {
            handledPress = true;
            return true;
        }
        if (bodyVisible() && layout.body().contains(x, y)) {
            handledPress = true;
            if (button < 0 || button > 1)
                return true;
            if (layout.pageSize() != state.pageSize())
                return true;
            if (layout.selection().contains(x, y)) {
                if (state.icons().size() > 1)
                    send(ContainerDrawerMenu.SELECT,
                        (state.selected() + 1) % state.icons().size(),
                        state.pageSize());
            } else if (layout.previous().contains(x, y))
                page(-1);
            else if (layout.next().contains(x, y))
                page(1);
            else
                for (int i = 0; i < Math.min(state.contents().size(),
                    layout.pageSize()); i++)
                    if (state.offset() + i == hit) {
                        if (menu.allows(ContainerDrawerMenu.COLLECT)
                            && button == 0 && !Screen.hasShiftDown()) {
                            lastClickSlot = state.offset() + i;
                            lastClickTime = now;
                            lastClickStack = state.contents().get(i).copy();
                            lastSource = state.keys().get(state.selected());
                        } else
                            lastClickSlot = -1;
                        send(
                            Screen.hasShiftDown()
                                ? ContainerDrawerMenu.QUICK_TAKE
                                : ContainerDrawerMenu.PICKUP,
                            (state.offset() + i) * 2 + button,
                            state.pageSize());
                        break;
                    }
            return true;
        }
        if (button == 0 && Screen.hasShiftDown()
            && menu.allows(ContainerDrawerMenu.QUICK_STORE)) {
            int slot = playerSlotAt(x, y);
            if (slot >= 0) {
                send(ContainerDrawerMenu.QUICK_STORE,
                    menu.targets().playerIndex(slot), state.pageSize());
                handledPress = true;
                return true;
            }
        }
        return false;
    }

    private void page(int direction) {
        var state = menu.state();
        int offset = Math.max(0, state.offset() + direction * state.pageSize());
        if (offset < state.sizes().get(state.selected())
            && offset != state.offset())
            send(ContainerDrawerMenu.PAGE, offset, state.pageSize());
    }

    public boolean scroll(double x, double y, double amount) {
        if (!contains(x, y) || !menu.state().open())
            return false;
        if (readyCells() && !inputLocked() && dragButton < 0
            && cells.scroll(x, y, amount))
            return true;
        if (!inputLocked() && amount != 0)
            page(amount < 0 ? 1 : -1);
        return true;
    }

    public boolean key(int key, int scan, int modifiers) {
        layout();
        if (key == 256 && dragButton >= 0) {
            cancelGesture();
            return true;
        }
        if (readyCells() && !inputLocked() && dragButton < 0
            && cells.key(key, scan, modifiers))
            return true;
        if (menu.allows(ContainerDrawerMenu.SWAP) && bodyVisible()
            && !inputLocked()) {
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                for (int hotbar = 0; hotbar < Minecraft
                    .getInstance().options.keyHotbarSlots.length; hotbar++)
                    if (Minecraft.getInstance().options.keyHotbarSlots[hotbar]
                        .matches(key, scan)) {
                        cancelGesture();
                        menu.request(ContainerDrawerMenu.SWAP, hotbar,
                            menu.state().pageSize(), List.of(slot))
                            .ifPresent(PacketDistributor::sendToServer);
                        return true;
                    }
            }
        }
        if (key == 256 && menu.state().open()) {
            send(ContainerDrawerMenu.TOGGLE, 0, menu.state().pageSize());
            return true;
        }
        if (key == 256)
            return false;
        if (inventoryPending()
            && (Minecraft.getInstance().options.keyDrop.matches(key, scan)
                || java.util.Arrays
                    .stream(Minecraft.getInstance().options.keyHotbarSlots)
                    .anyMatch(binding -> binding.matches(key, scan))))
            return true;
        return contains(mouseX, mouseY)
            && !Minecraft.getInstance().options.keyInventory.matches(key, scan);
    }

    @Override
    public boolean character(char character, int modifiers) {
        return readyCells() && !inputLocked() && dragButton < 0
            && cells.character(character, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        return readyCells() && !inputLocked() && dragButton < 0
            && cells.keyReleased(key, scan, modifiers);
    }

    public void mouseMoved(double x, double y) {
        mouseX = (int) x;
        mouseY = (int) y;
        if (readyCells() && !inputLocked() && dragButton < 0)
            cells.mouseMoved(x, y);
    }

    public boolean drag(double x, double y, int button, double dx, double dy) {
        layout();
        if (elementButton >= 0) {
            if (readyCells() && !inputLocked())
                cells.drag(x, y, button, dx, dy);
            return true;
        }
        if (dragButton >= 0) {
            if (!gestureValid() || button != dragButton)
                cancelGesture();
            else {
                int slot = slotAt(x, y);
                addDragSlot(slot >= 0 ? slot : playerSlotAt(x, y));
            }
            return true;
        }
        return handledPress;
    }

    public boolean release(double x, double y, int button) {
        if (elementButton >= 0) {
            if (readyCells() && !inputLocked())
                cells.release(x, y, button);
            if (button == elementButton) {
                elementButton = -1;
                handledPress = false;
            }
            return true;
        }
        boolean handled = handledPress;
        handledPress = false;
        layout();
        if (dragButton >= 0) {
            if (gestureValid() && button == dragButton) {
                if (dragSlots.size() > 1) {
                    menu.request(ContainerDrawerMenu.DISTRIBUTE, button,
                        menu.state().pageSize(), List.copyOf(dragSlots))
                        .ifPresent(PacketDistributor::sendToServer);
                } else if (slotAt(x, y) == firstSlot)
                    send(ContainerDrawerMenu.PICKUP, firstSlot * 2 + button,
                        menu.state().pageSize());
            }
            cancelGesture();
            return true;
        }
        return handled;
    }
}
