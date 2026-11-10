package zone.moddev.mc.lib.gui.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.client.gui.GuiGraphics;

import zone.moddev.mc.lib.gui.drawer.DrawerPlacement;
import zone.moddev.mc.lib.gui.layout.DrawerTabs;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Edge;
import zone.moddev.mc.lib.gui.model.Element;

/**
 * Generic dispatch, edge arbitration and connection-scoped preferences for
 * drawers.
 */
public final class Drawers {
    private final List<Drawer> entries = new ArrayList<>();
    private final DrawerMemory memory = new DrawerMemory();
    private final MachineScreen screen;
    private final Map<Element, Drawer> elements = new IdentityHashMap<>();

    private Drawers(MachineScreen screen) {
        this.screen = screen;
    }

    static Drawers create(MachineScreen screen) {
        var result = new Drawers(screen);
        var definitions = screen.layout.drawers();
        DrawerFactories.GLOBAL.validate(definitions);
        for (var definition : definitions)
            if (definition.interactive()) {
                var drawer = DrawerFactories.GLOBAL.create(screen, definition);
                result.entries.add(drawer);
                definition.element()
                    .ifPresent(element -> result.elements.put(element, drawer));
            }
        return result;
    }

    public List<Drawer> entries() {
        return List.copyOf(entries);
    }

    public <T> Optional<T> presentation(Element element, Class<T> type) {
        var drawer = elements.get(element);
        return drawer == null ? Optional.empty() : drawer.presentation(type);
    }

    public Rect tabBounds(Drawer drawer) {
        if (!entries.contains(drawer))
            throw new IllegalArgumentException("Drawer is not on this screen");
        var definition = drawer.definition();
        var tabs = DrawerTabs.place(screen.layout.drawers(),
            screen.panelBounds(), screen.drawerBottom(),
            candidate -> candidate.placement()
                .anchor() == DrawerPlacement.Anchor.TOP
                || !candidate.interactive()
                || entries.stream()
                    .anyMatch(d -> d.id().equals(candidate.id())
                        && d.edge() == candidate.placement().edge()
                        && d.available()));
        // An unavailable drawer can still compute content bounds while waiting
        // for a snapshot.
        var bounds = tabs.get(definition);
        return bounds != null
            ? bounds
            : DrawerTabs.place(List.of(definition), screen.panelBounds(),
                screen.drawerBottom(), d -> true).get(definition);
    }

    public boolean pending() {
        return entries.stream().anyMatch(Drawer::pending);
    }

    public boolean pending(Edge edge) {
        return entries.stream().anyMatch(d -> d.edge() == edge && d.pending());
    }

    void activated(Drawer drawer) {
        entries.stream().filter(d -> d != drawer && d.edge() == drawer.edge())
            .forEach(Drawer::closeLocal);
    }

    void openLocal(Drawer drawer) {
        entries.stream().filter(d -> d != drawer && d.edge() == drawer.edge())
            .forEach(Drawer::close);
    }

    void remember(Drawer drawer, boolean open, Drawer.Selection selection) {
        memory.intent(drawer, open, selection);
    }

    public boolean tabContains(double x, double y) {
        return entries.stream().anyMatch(d -> d.tabContains(x, y));
    }

    boolean otherTab(Drawer drawer, double x, double y) {
        return entries.stream().anyMatch(d -> d != drawer
            && d.edge() == drawer.edge() && d.tabContains(x, y));
    }

    public void render(GuiGraphics graphics, int x, int y) {
        entries.forEach(d -> d.render(graphics, x, y));
    }

    /**
     * Navigation tabs stay visible above every drawer body, including XML
     * foreground popouts.
     */
    public void renderTabs(GuiGraphics graphics) {
        entries.forEach(d -> d.renderTab(graphics));
    }

    public void tooltip(GuiGraphics graphics, int x, int y) {
        entries.forEach(d -> d.tooltip(graphics, x, y));
    }

    public Optional<List<net.minecraft.network.chat.Component>> nativeTooltip(
        net.minecraft.world.inventory.Slot slot,
        List<net.minecraft.network.chat.Component> vanilla, int x, int y) {
        for (var drawer : entries.reversed()) {
            var result = drawer.nativeTooltip(slot, vanilla, x, y);
            if (result.isPresent())
                return result;
        }
        return Optional.empty();
    }

    public List<Rect> exclusions() {
        var result = new ArrayList<Rect>();
        entries.forEach(drawer -> result.addAll(drawer.exclusions()));
        for (var definition : screen.layout.drawers())
            if (!definition.interactive())
                definition.element().filter(screen::visible)
                    .ifPresent(element -> result.add(screen.bounds(element)));
        return List.copyOf(result);
    }

    public boolean contains(double x, double y) {
        return entries.stream().anyMatch(d -> d.contains(x, y));
    }

    private boolean dispatch(Predicate<Drawer> input) {
        return entries.stream().anyMatch(input);
    }

    public boolean click(double x, double y, int button) {
        memory.userInput();
        return dispatch(d -> !otherTab(d, x, y) && d.click(x, y, button));
    }

    public void userInput() {
        memory.userInput();
    }

    public boolean key(int key, int scan, int modifiers) {
        return dispatch(d -> d.key(key, scan, modifiers));
    }

    public boolean keyReleased(int key, int scan, int modifiers) {
        return dispatch(d -> d.keyReleased(key, scan, modifiers));
    }

    public boolean character(char character, int modifiers) {
        return dispatch(d -> d.character(character, modifiers));
    }

    public boolean scroll(double x, double y, double amount) {
        return dispatch(d -> d.scroll(x, y, amount));
    }

    public boolean drag(double x, double y, int button, double dx, double dy) {
        return dispatch(d -> d.drag(x, y, button, dx, dy));
    }

    public boolean release(double x, double y, int button) {
        boolean handled = false;
        for (var drawer : entries)
            handled |= drawer.release(x, y, button);
        return handled;
    }

    public void mouseMoved(double x, double y) {
        entries.forEach(d -> d.mouseMoved(x, y));
    }

    public void resized() {
        entries.forEach(Drawer::resized);
    }

    public void tick() {
        memory.restore(entries);
        entries.forEach(Drawer::tick);
    }

    public void removed() {
        memory.capture(entries);
        entries.forEach(Drawer::removed);
    }
}
