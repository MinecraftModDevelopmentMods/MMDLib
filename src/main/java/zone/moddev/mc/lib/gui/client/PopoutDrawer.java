package zone.moddev.mc.lib.gui.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.SideDrawerLayout;
import zone.moddev.mc.lib.gui.model.Edge;
import zone.moddev.mc.lib.gui.model.elements.PopoutElement;

/** XML information, warning and error drawers, including text layout, scrolling and input. */
public final class PopoutDrawer extends Drawer {
    private final PopoutElement element;
    private boolean open;
    private int scroll;

    public PopoutDrawer(
        MachineScreen screen, zone.moddev.mc.lib.gui.drawer.DrawerDefinition definition) {
        this(screen, definition, (PopoutElement) definition.element().orElseThrow());
    }

    private PopoutDrawer(
        MachineScreen screen,
        zone.moddev.mc.lib.gui.drawer.DrawerDefinition definition,
        PopoutElement element) {
        super(screen, definition, IconSprite.gui(element.iconOrDefault()), element.colour());
        this.element = element;
    }

    public boolean available() {
        return screen.visible(element);
    }

    public boolean open() {
        return open;
    }

    public void close() {
        open = false;
    }

    public void closeLocal() {
        close();
    }

    public Rect tabBounds() {
        return screen.bounds(element);
    }

    public boolean restore(Selection selection) {
        if (!available())
            return true;
        openLocal();
        open = true;
        return true;
    }

    // ElementRenderers retains control of the rendering passes and mod overrides.
    public void renderTab(GuiGraphics graphics) {
    }

    private int bodyWidth() {
        var tab = tabBounds();
        int room = edge() == Edge.LEFT ? tab.x() - 4 : screen.width - tab.right() - 4;
        return Math.max(0, Math.min(150, room));
    }

    private List<FormattedCharSequence> lines() {
        var font = Minecraft.getInstance().font;
        var content = element.content(screen.getMenu().value(element, false), screen::text);
        var result = new ArrayList<FormattedCharSequence>();
        int width = Math.max(1, bodyWidth() - 12);
        for (var paragraph : content) {
            if (paragraph.getString().isEmpty())
                result.add(FormattedCharSequence.EMPTY);
            else
                result.addAll(font.split(paragraph, width));
        }
        return result;
    }

    private Rect body() {
        var tab = tabBounds();
        int width = bodyWidth();
        int top = SideDrawerLayout.top(screen.panelBounds());
        int height = Math.max(tab.bottom() - top, lines().size() * 10 + 12);
        return SideDrawerLayout.body(
            screen.panelBounds(),
            screen.drawerBottom(),
            edge() == Edge.LEFT ? tab.x() - width : tab.right(),
            top,
            width,
            height);
    }

    public <T> Optional<T> presentation(Class<T> type) {
        return type == PopoutRenderer.Presentation.class
            ? presentation().map(type::cast)
            : Optional.empty();
    }

    private Optional<PopoutRenderer.Presentation> presentation() {
        if (!open || !available())
            return Optional.empty();
        var body = body();
        var lines = lines();
        scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() * 10 - (body.height() - 12)));
        return Optional.of(new PopoutRenderer.Presentation(body, lines, scroll));
    }

    public List<Rect> exclusions() {
        if (!available())
            return List.of();
        return open
            ? List.of(body(), SideDrawerLayout.rail(tabBounds(), body()))
            : List.of(tabBounds());
    }

    public boolean contains(double x, double y) {
        return available()
            && (tabContains(x, y)
                || open
                    && (body().contains(x, y)
                        || SideDrawerLayout.rail(tabBounds(), body()).contains(x, y)
                            && !otherTab(x, y)));
    }

    public boolean click(double x, double y, int button) {
        if (button < 0 || button > 1 || !contains(x, y))
            return false;
        if (tabContains(x, y)) {
            if (open)
                close();
            else {
                openLocal();
                open = true;
            }
            remember(open, selection());
        }
        return true;
    }

    public boolean scroll(double x, double y, double amount) {
        if (!open || !available() || !body().contains(x, y))
            return false;
        scroll += (int) (-amount * 10);
        return true;
    }

    static void drawTab(PopoutElement element, ElementRenderers.Context context) {
        var tab = context.bounds();
        SideDrawerRenderer.tab(context.graphics(), tab, element.colour());
        IconSprite.gui(element.iconOrDefault()).draw(context.graphics(), tab.x() + 3, tab.y() + 3);
    }

    static void drawBody(
        PopoutElement element,
        ElementRenderers.Context context,
        PopoutRenderer.Presentation presentation) {
        var g = context.graphics();
        var body = presentation.body();
        var tab = context.bounds();
        SideDrawerRenderer.open(g, tab, body, element.edge(), element.colour());
        IconSprite.gui(element.iconOrDefault()).draw(g, tab.x() + 3, tab.y() + 3);
        g.enableScissor(body.x() + 5, body.y() + 5, body.right() - 5, body.bottom() - 5);
        try {
            for (int i = 0; i < presentation.lines().size(); i++)
                g.drawString(
                    context.access().font(),
                    presentation.lines().get(i),
                    body.x() + 6,
                    body.y() + 6 + i * 10 - presentation.scroll(),
                    0xff303030,
                    false);
        } finally {
            g.disableScissor();
        }
    }
}
