package zone.moddev.mc.lib.gui.client;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;
import zone.moddev.mc.lib.gui.sync.ElementStateView;

/** Client presentation of a data-defined machine; getters are never invoked here. */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private long animationStart = -1;
    private double elapsedTicks;
    public ScreenLayout layout;
    private final Drawers drawers;
    private final NativeSlotRenderer nativeSlots = new NativeSlotRenderer();
    private ElementInput<Element, ElementBehavior> elementInput;
    private boolean behaviorsRemoved;
    private int tooltipMouseX, tooltipMouseY;
    private final Map<Element, Element> renderParents = new IdentityHashMap<>();
    private final ElementRenderAccess renderAccess = new ElementRenderAccess() {
        public net.minecraft.client.gui.Font font() {
            return MachineScreen.this.font;
        }

        public Rect viewport() {
            return new Rect(0, 0, width, height);
        }

        public Optional<Element> find(String id) {
            return menu.bound.screen().find(id);
        }

        public Optional<Element> parent(Element element) {
            return Optional.ofNullable(renderParents.get(element));
        }

        public Object value(Element element, boolean maximum) {
            return menu.value(element, maximum);
        }

        public Component text(Text text) {
            return MachineScreen.this.text(text);
        }

        public List<Component> serverTooltip(Element element) {
            return menu.components()
                .requireCapability(zone.moddev.mc.lib.gui.runtime.component.ServerTooltips.class)
                .lines(element);
        }

        public Rect bounds(Element element) {
            return MachineScreen.this.bounds(element);
        }

        public boolean visible(Element element) {
            return MachineScreen.this.visible(element);
        }

        public ElementStateView state() {
            return menu.elementStates();
        }

        public <T> Optional<T> capability(Element element, Class<T> type) {
            return BehaviorCapabilities.find(elementInput.instance(element), type);
        }

        public ItemStack contents(SlotElement element) {
            return menu.slot(element).getItem().copy();
        }

        public ItemStack hint(SlotElement element) {
            return menu.slotHint(element).copy();
        }

        public <T> Optional<T> drawerPresentation(Element element, Class<T> type) {
            return drawers.presentation(element, type);
        }
    };

    /** Optional integration entry point, installed only by JEI. */
    public static java.util.function.BiPredicate<MachineScreen, Element> recipes = (s, e) -> false;

    /** Optional overlays can reserve space for a bottom toolbar without moving the main panel. */
    public static java.util.function.IntSupplier drawerBottomInset = () -> 4;

    public int drawerBottom() {
        return Math.max(0, height - Math.max(4, drawerBottomInset.getAsInt()));
    }

    /**
     * Installed only by JEI: whether JEI draws the tooltip of an element's recipe area, in which
     * case the screen leaves it to JEI (which asks for {@link #tooltipLines}) instead of drawing
     * its own.
     */
    public static java.util.function.BiPredicate<MachineScreen, Element> recipeTooltip = (s,
        e) -> false;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        ElementRenderers.GLOBAL.validate(menu.bound.screen());
        for (var parent : menu.elements)
            for (var child : parent.children())
                renderParents.put(child, parent);
        layout = menu.layout();
        imageWidth = layout.panel().width();
        imageHeight = layout.panel().height();
        elementInput = createBehaviors();
        drawers = Drawers.create(this);
    }

    /** Client drawer extension point; drawers own their rendering and interactions. */
    public Drawers drawers() {
        return drawers;
    }

    /** Read-only presentation services shared by renderers, drawers and optional integrations. */
    public ElementRenderAccess presentation() {
        return renderAccess;
    }

    private ElementInput<Element, ElementBehavior> createBehaviors() {
        return new ElementInput<>(
            menu.elements,
            element -> ElementBehaviors.GLOBAL.create(
                new ElementAccess() {
                    public Element element() {
                        return element;
                    }

                    public Rect bounds() {
                        return MachineScreen.this.bounds(element);
                    }

                    public Object value() {
                        return menu.value(element, false);
                    }

                    public Object maximum() {
                        return menu.value(element, true);
                    }

                    public ElementRenderAccess presentation() {
                        return renderAccess;
                    }

                    public List<Element> elements() {
                        return List.copyOf(menu.elements);
                    }

                    public ResourceLocation screenId() {
                        return menu.bound.screen().id();
                    }

                    public boolean pending(Element target) {
                        return menu.elementPending(target);
                    }

                    public <T> boolean submit(Element target, ElementAction<T> action, T value) {
                        return submitElementAction(target, action, value);
                    }

                    public void navigate(String context, String view) {
                        menu.navigate(context, view);
                    }

                    public ItemStack carried() {
                        return menu.getCarried().copy();
                    }

                    public zone.moddev.mc.lib.machine.inventory.SlotPlan reservation(
                        SlotElement slot) {
                        return menu.reservationPlan(menu.slotIndex(slot));
                    }

                    public boolean showRecipes() {
                        return recipes.test(MachineScreen.this, element);
                    }
                }),
            this::visible,
            this::bounds,
            (x, y) -> renderAccess.viewport().contains(x, y),
            (element, entered) -> ElementHover.dispatch(menu, element, entered));
    }

    @EventBusSubscriber(modid = "mmdlib", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterMenuScreensEvent event) {
            ScreenRuntime.textMeasure = text -> net.minecraft.client.Minecraft.getInstance().font
                .width(ScreenText.component(text));
            event.register(ScreenRuntime.MENUS.get(), MachineScreen::new);
        }
    }

    @EventBusSubscriber(modid = "mmdlib", value = Dist.CLIENT)
    public static final class PendingActions {
        @SubscribeEvent
        public static void logout(
            net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            DrawerMemory.disconnected();
        }

        @SubscribeEvent
        public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof MachineMenu menu) {
                menu.tickClientActions();
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        drawers.resized();
        // Recipe browsers can return to the same screen object after removed() was called.
        if (behaviorsRemoved) {
            elementInput = createBehaviors();
            behaviorsRemoved = false;
        }
        elementInput.resized();
        elementInput.initialized();
    }

    public Component text(Text text) {
        return ScreenText.resolve(text, menu.bound.screen().source());
    }

    public Rect bounds(Element e) {
        updateLayout();
        return layout.bounds(e).offset(leftPos, topPos);
    }

    public Rect panelBounds() {
        updateLayout();
        return layout.panel().offset(leftPos, topPos);
    }

    private void updateLayout() {
        if (layout == menu.layout())
            return;
        layout = menu.layout();
        imageWidth = layout.panel().width();
        imageHeight = layout.panel().height();
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;
        hoveredSlot = null;
        quickCraftSlots.clear();
        isQuickCrafting = false;
        elementInput.resized();
        drawers.resized();
    }

    public int guiLeft() {
        return leftPos;
    }

    public int guiTop() {
        return topPos;
    }

    public boolean visible(Element e) {
        return menu.visible(e);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int x, int y) {
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        long tick = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        if (animationStart < 0)
            animationStart = tick;
        elapsedTicks = Math.max(0, tick - animationStart + partial);
        ElementDrawing.sprite(g, "panel", layout.panel().offset(leftPos, topPos));
        g.drawString(
            font, text(menu.bound.screen().title()), leftPos + 7, topPos + 6, 0xff404040, false);
        renderElements(ElementRenderers.Pass.BACKGROUND, g, partial, mx, my);
        renderElements(ElementRenderers.Pass.OVERLAY, g, partial, mx, my);
        drawers.render(g, mx, my);
    }

    private ElementRenderers.Context renderContext(
        GuiGraphics g, Element e, Rect bounds, int mx, int my, float partial) {
        return new ElementRenderers.Context(
            g,
            e,
            bounds,
            renderAccess.value(e, false),
            renderAccess.value(e, true),
            mx,
            my,
            partial,
            elapsedTicks,
            renderAccess);
    }

    private void draw(GuiGraphics g, Element e, Rect bounds, int mx, int my, float partial) {
        ElementRenderers.GLOBAL.render(renderContext(g, e, bounds, mx, my, partial));
    }

    private void renderElements(
        ElementRenderers.Pass pass, GuiGraphics g, float partial, int mx, int my) {
        for (var e : menu.elements)
            if (visible(e))
                ElementRenderers.GLOBAL.render(pass,
                    renderContext(g, e, bounds(e), mx, my, partial));
    }

    public List<Rect> exclusions() {
        var regions = new java.util.ArrayList<>(drawers.exclusions());
        var overlay = elementInput.overlayOwner();
        if (overlay != null)
            elementInput.instance(overlay).overlayBounds().ifPresent(regions::add);
        return List.copyOf(regions);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        tooltipMouseX = mx;
        tooltipMouseY = my;
        updateLayout();
        updateBehaviors();
        elementInput.hover(mx, my);
        super.render(g, mx, my, partial);
        ElementHover.preview(
            elementInput.hovered(), renderAccess, e -> draw(g, e, bounds(e), mx, my, partial));
        renderElements(ElementRenderers.Pass.FOREGROUND, g, partial, mx, my);
        drawers.renderTabs(g);
        renderElements(ElementRenderers.Pass.TOP, g, partial, mx, my);
        if (elementInput.overlayOwner() == null)
            renderTooltip(g, mx, my);
        if (elementInput.hovered() != null)
            tooltip(g, elementInput.hovered(), mx, my);
        if (elementInput.overlayOwner() == null)
            drawers.tooltip(g, mx, my);
    }

    private void tooltip(GuiGraphics g, Element e, int mx, int my) {
        if (!e.common().jeiRecipes().isEmpty() && recipeTooltip.test(this, e))
            return;
        if (nativeSlots.usesItemTooltip(menu.nativeSlots(), hoveredSlot, e))
            return;
        var lines = tooltipLines(e, mx, my);
        if (!lines.isEmpty())
            g.renderComponentTooltip(font, lines, mx, my);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var vanilla = super.getTooltipFromContainerItem(stack);
        return drawers
            .nativeTooltip(hoveredSlot, vanilla, tooltipMouseX, tooltipMouseY)
            .orElseGet(
                () -> nativeSlots.itemTooltip(
                    menu.nativeSlots(),
                    hoveredSlot,
                    elementInput.hovered(),
                    vanilla,
                    element -> tooltipLines(element, tooltipMouseX, tooltipMouseY)));
    }

    /** The tooltip lines of {@code e} with the mouse at {@code mx, my}; empty for none. */
    public List<Component> tooltipLines(Element e, int mx, int my) {
        return ElementTooltips.collect(
            e,
            renderAccess,
            elementInput::instance,
            mx,
            my,
            hasShiftDown(),
            hasControlDown(),
            hasAltDown());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        updateLayout();
        updateBehaviors();
        if (elementInput.overlayOwner() != null)
            return elementInput.click(mx, my, button);
        if (drawers.click(mx, my, button)) {
            elementInput.clearFocus();
            return true;
        }
        updateBehaviors();
        return elementInput.click(mx, my, button) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        drawers.userInput();
        // A lost drawer acknowledgment must never trap the player in the screen.
        if (drawers.pending()
            && (key == 256
                || net.minecraft.client.Minecraft.getInstance().options.keyInventory
                    .matches(key, scan))) {
            onClose();
            return true;
        }
        if (drawers.key(key, scan, modifiers))
            return true;
        updateBehaviors();
        if (elementInput.keyPressed(key, scan, modifiers))
            return true;
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void removed() {
        drawers.removed();
        elementInput.removed();
        behaviorsRemoved = true;
        super.removed();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        updateBehaviors();
        if (elementInput.overlayOwner() != null)
            return elementInput.scroll(mx, my, dx, dy);
        if (drawers.scroll(mx, my, dy))
            return true;
        updateBehaviors();
        return elementInput.scroll(mx, my, dx, dy) || super.mouseScrolled(mx, my, dx, dy);
    }

    /** Shared typed submission for element behaviors and external UI integrations such as JEI. */
    public <T> boolean submitElementAction(Element target, ElementAction<T> action, T value) {
        var packet = menu.prepareElementAction(target, action, value);
        packet.ifPresent(PacketDistributor::sendToServer);
        return packet.isPresent();
    }

    private void updateBehaviors() {
        updateLayout();
        elementInput.update();
        for (var element : menu.elements) {
            var result = menu.takeElementResult(element);
            if (result != null)
                elementInput.instance(element).actionResult(result.accepted());
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        drawers.tick();
        updateBehaviors();
        elementInput.tick();
    }

    @Override
    public void mouseMoved(double x, double y) {
        drawers.mouseMoved(x, y);
        updateBehaviors();
        elementInput.mouseMoved(x, y);
        super.mouseMoved(x, y);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (drawers.drag(x, y, button, dx, dy))
            return true;
        updateBehaviors();
        return elementInput.drag(x, y, button, dx, dy) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (drawers.release(x, y, button))
            return true;
        updateBehaviors();
        return elementInput.release(x, y, button) || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (drawers.character(character, modifiers))
            return true;
        updateBehaviors();
        return elementInput.charTyped(character, modifiers)
            || super.charTyped(character, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (drawers.keyReleased(key, scan, modifiers))
            return true;
        updateBehaviors();
        return elementInput.keyReleased(key, scan, modifiers)
            || super.keyReleased(key, scan, modifiers);
    }

    @Override
    protected boolean hasClickedOutside(double x, double y, int left, int top, int button) {
        return !drawers.contains(x, y) && super.hasClickedOutside(x, y, left, top, button);
    }

    @Override
    protected void renderSlotHighlight(
        GuiGraphics graphics,
        net.minecraft.world.inventory.Slot slot,
        int mouseX,
        int mouseY,
        float partialTick) {
        nativeSlots.highlight(
            graphics,
            slot,
            menu.nativeSlots().entry(slot.index),
            getSlotColor(slot.index),
            () -> super.renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick));
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, net.minecraft.world.inventory.Slot slot) {
        nativeSlots.render(
            graphics,
            slot,
            menu.nativeSlots().entry(slot.index),
            () -> super.renderSlot(graphics, slot));
    }

    @Override
    protected void renderSlotContents(
        GuiGraphics graphics,
        ItemStack displayed,
        net.minecraft.world.inventory.Slot slot,
        @org.jetbrains.annotations.Nullable String countString) {
        nativeSlots.contents(
            graphics,
            font,
            slot,
            menu.nativeSlots().entry(slot.index),
            displayed,
            countString,
            count -> super.renderSlotContents(graphics, displayed, slot, count));
    }

    /** Native slot hit testing for overlay hosts, in screen coordinates. */
    public net.minecraft.world.inventory.Slot nativeSlotAt(double x, double y) {
        return nativeSlotAt(x, y, true);
    }

    /** Include inactive geometry only when blocking pending inventory gestures. */
    public net.minecraft.world.inventory.Slot nativeSlotAt(double x, double y, boolean activeOnly) {
        updateLayout();
        return nativeSlots.hit(menu.nativeSlots(), x - leftPos, y - topPos, activeOnly);
    }

    @Override
    protected boolean isHovering(int x, int y, int w, int h, double mx, double my) {
        var bounds = nativeSlots.hitBounds(menu.nativeSlots(), x, y, w, h);
        return super.isHovering(bounds.x(), bounds.y(), bounds.width(), bounds.height(), mx, my);
    }
}
