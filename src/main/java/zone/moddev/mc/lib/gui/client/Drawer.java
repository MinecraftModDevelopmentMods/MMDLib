package zone.moddev.mc.lib.gui.client;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.gui.GuiGraphics;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Edge;

/**
 * Client-owned drawer behavior. Subclasses supply their appearance, geometry, content, input and
 * preference restoration. The screen dispatches these hooks without knowing the drawer's type.
 */
public abstract class Drawer {
    protected final MachineScreen screen;
    private final String id;
    private final Edge edge;
    private final IconSprite iconSprite;
    private final int colour;
    private zone.moddev.mc.lib.gui.drawer.DrawerDefinition definition;

    protected Drawer(
        MachineScreen screen,
        zone.moddev.mc.lib.gui.drawer.DrawerDefinition definition,
        IconSprite iconSprite,
        int colour) {
        this(screen, definition.id(), definition.placement().edge(), iconSprite, colour);
    }

    final void attach(zone.moddev.mc.lib.gui.drawer.DrawerDefinition definition) {
        if (this.definition != null
            || !id.equals(definition.id())
            || edge != definition.placement().edge())
            throw new IllegalArgumentException(
                "Drawer instance does not match its registered identity: " + definition.id());
        this.definition = definition;
    }

    public final zone.moddev.mc.lib.gui.drawer.DrawerDefinition definition() {
        return Objects.requireNonNull(definition, "Drawer was not created by a registered factory");
    }

    private Drawer(MachineScreen screen, String id, Edge edge, IconSprite iconSprite, int colour) {
        this.screen = Objects.requireNonNull(screen);
        this.id = Objects.requireNonNull(id);
        this.edge = Objects.requireNonNull(edge);
        this.iconSprite = Objects.requireNonNull(iconSprite);
        this.colour = colour;
    }

    public final String id() {
        return id;
    }

    public final Edge edge() {
        return edge;
    }

    public IconSprite iconSprite() {
        return iconSprite;
    }

    public int colour() {
        return colour;
    }

    /** Stable, drawer-defined preference data; never a server authority or inventory snapshot. */
    public record Selection(String key, int offset) {
        public Selection {
            Objects.requireNonNull(key);
        }

        public Selection() {
            this("", 0);
        }
    }

    public boolean available() {
        return true;
    }

    public boolean pending() {
        return false;
    }

    public abstract boolean open();

    public Rect tabBounds() {
        return screen.drawers().tabBounds(this);
    }

    /** Optional read-only presentation for an element renderer associated with this drawer. */
    public <T> java.util.Optional<T> presentation(Class<T> type) {
        return java.util.Optional.empty();
    }

    public abstract void close();

    /** Close purely local presentation when a server-owned drawer is opened. */
    public void closeLocal() {
    }

    /** Return false to retry after a server snapshot/acknowledgment arrives. */
    public boolean restore(Selection selection) {
        return true;
    }

    public Selection selection() {
        return new Selection();
    }

    protected final void remember(boolean open, Selection selection) {
        screen.drawers().remember(this, open, selection);
    }

    protected final void activated() {
        screen.drawers().activated(this);
    }

    protected final void openLocal() {
        screen.drawers().openLocal(this);
    }

    protected final boolean otherTab(double x, double y) {
        return screen.drawers().otherTab(this, x, y);
    }

    public boolean tabContains(double x, double y) {
        return available() && tabBounds().contains(x, y);
    }

    protected final void drawFrame(GuiGraphics graphics, Rect tab, Rect body, boolean expanded) {
        if (expanded)
            SideDrawerRenderer.open(graphics, tab, body, edge, colour());
        else
            SideDrawerRenderer.tab(graphics, tab, colour());
        drawIcon(graphics, tab);
    }

    protected void drawIcon(GuiGraphics graphics, Rect tab) {
        iconSprite().draw(graphics, tab.x() + 3, tab.y() + 4);
    }

    public void renderTab(GuiGraphics graphics) {
        if (!available())
            return;
        var tab = tabBounds();
        SideDrawerRenderer.tab(graphics, tab, colour());
        drawIcon(graphics, tab);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    public void tooltip(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    /**
     * Optional native tooltip ownership for this drawer; empty leaves the screen bridge in charge.
     */
    public java.util.Optional<List<net.minecraft.network.chat.Component>> nativeTooltip(
        net.minecraft.world.inventory.Slot slot,
        List<net.minecraft.network.chat.Component> vanilla,
        int mouseX,
        int mouseY) {
        return java.util.Optional.empty();
    }

    public List<Rect> exclusions() {
        return available() ? List.of(tabBounds()) : List.of();
    }

    public boolean contains(double x, double y) {
        return tabContains(x, y);
    }

    public boolean click(double x, double y, int button) {
        return false;
    }

    public boolean key(int key, int scan, int modifiers) {
        return false;
    }

    public boolean keyReleased(int key, int scan, int modifiers) {
        return false;
    }

    public boolean character(char character, int modifiers) {
        return false;
    }

    public boolean scroll(double x, double y, double amount) {
        return false;
    }

    public boolean drag(double x, double y, int button, double dx, double dy) {
        return false;
    }

    public boolean release(double x, double y, int button) {
        return false;
    }

    public void mouseMoved(double x, double y) {
    }

    public void resized() {
    }

    public void tick() {
    }

    public void removed() {
        resized();
    }
}
