package zone.moddev.mc.lib.gui.client;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import zone.moddev.mc.lib.gui.layout.AutomationLayout;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.SideDrawerLayout;
import zone.moddev.mc.lib.gui.runtime.StatsPage;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;

/** Read-only statistics; unit selection and scrolling are entirely local. */
public final class StatsDrawer extends Drawer {
    private boolean open;
    private int scroll;
    private ResourceLocation selected;
    private StatsPage page;
    private boolean handled;
    private Rect tab, body;
    private List<FormattedCharSequence> lines = List.of();
    private List<Component> cachedText = List.of();
    private int cachedWidth = -1;
    private net.minecraft.client.gui.Font cachedFont;
    private net.minecraft.locale.Language cachedLanguage;
    private static final int COLOUR = 0xa7cdd6;
    private static final float SCALE = .75f;

    public StatsDrawer(MachineScreen screen,
        zone.moddev.mc.lib.gui.drawer.DrawerDefinition definition) {
        super(screen, definition, (g, x, y) -> {
            for (int i = 0; i < 3; i++)
                g.fill(x + 2 + i * 5, y + 10 - i * 4, x + 5 + i * 5, y + 15,
                    0xff30545e);
        }, COLOUR);
    }

    public boolean open() {
        return open;
    }

    public Rect tabBounds() {
        layout();
        return tab;
    }

    public void close() {
        open = false;
    }

    public void closeLocal() {
        close();
    }

    public void resized() {
        handled = false;
    }

    public boolean restore(Selection selection) {
        return show();
    }

    private Component text(String key) {
        return Component.translatable("screen.mmdlib.stats." + key);
    }

    /**
     * Stable selected page identity, retained when the host reorders its
     * timescales.
     */
    public Optional<ResourceLocation> selectedPage() {
        layout();
        return Optional.ofNullable(selected);
    }

    private List<StatsPage> pages() {
        return screen.getMenu().components()
            .require(BuiltinMenuComponents.STATS).pages();
    }

    private void updatePage() {
        var pages = pages();
        page = pages.stream().filter(p -> p.id().equals(selected)).findFirst()
            .orElse(pages.isEmpty() ? null : pages.getFirst());
        var next = page == null ? null : page.id();
        if (!Objects.equals(selected, next))
            scroll = 0;
        selected = next;
    }

    void layout() {
        updatePage();
        var panel = screen.panelBounds();
        tab = super.tabBounds();
        if (!open)
            return;
        int width = Math.max(0,
            Math.min(160, SideDrawerLayout.room(edge(), tab, screen.width)));
        var font = Minecraft.getInstance().font;
        var text = page == null ? List.of(text("empty")) : page.lines();
        var language = net.minecraft.locale.Language.getInstance();
        if (!text.equals(cachedText) || width != cachedWidth
            || font != cachedFont || language != cachedLanguage) {
            cachedText = text;
            cachedWidth = width;
            cachedFont = font;
            cachedLanguage = language;
            lines = text.stream()
                .flatMap(c -> font
                    .split(c, Math.max(1, (int) ((width - 12) / SCALE)))
                    .stream())
                .toList();
        }
        body = SideDrawerLayout.body(panel, screen.drawerBottom(),
            SideDrawerLayout.bodyX(edge(), tab, width),
            panel.y() + AutomationLayout.TOP, width, 28 + lines.size() * 9,
            SideDrawerLayout.HeightLimit.SCREEN);
        scroll = Math.clamp(scroll, 0,
            Math.max(0, lines.size() * 9 - Math.max(0, body.height() - 28)));
    }

    boolean show() {
        if (screen.drawers().pending(edge()))
            return false;
        openLocal();
        open = true;
        return true;
    }

    public void render(GuiGraphics g, int mx, int my) {
        layout();
        drawFrame(g, tab, body, open);
        if (!open || body.width() < 12 || body.height() < 12)
            return;
        var font = Minecraft.getInstance().font;
        g.enableScissor(body.x() + 5, body.y() + 4, body.right() - 5,
            body.bottom() - 4);
        var r = unitBounds();
        g.enableScissor(body.x() + 5, body.y() + 4,
            Math.max(body.x() + 5, page == null ? body.right() - 5 : r.x() - 4),
            body.y() + 19);
        g.drawString(font, text("title"), body.x() + 6, body.y() + 6,
            0xff263e45, false);
        g.disableScissor();
        if (page != null) {
            boolean hover = r.contains(mx, my);
            g.fill(r.x(), r.y(), r.right(), r.bottom(), 0xff486e78);
            g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1,
                hover ? 0xffc2e3e9 : 0xffa7cdd6);
            g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 2, 0xffd6edf1);
            var label = page == null ? Component.empty() : page.label();
            g.pose().pushPose();
            g.pose().translate(
                r.x() + Math.max(3,
                    (r.width() - 10 - font.width(label) * SCALE) / 2),
                r.y() + 4, 0);
            g.pose().scale(SCALE, SCALE, 1);
            g.drawString(font, label, 0, 0, 0xff263e45, false);
            g.pose().popPose();
            int arrowX = r.right() - 6, arrowY = r.y() + 6;
            g.fill(arrowX - 1, arrowY - 2, arrowX, arrowY + 3, 0xff41616b);
            g.fill(arrowX, arrowY - 1, arrowX + 1, arrowY + 2, 0xff41616b);
            g.fill(arrowX + 1, arrowY, arrowX + 2, arrowY + 1, 0xff41616b);
        }
        g.enableScissor(body.x() + 5,
            Math.min(body.bottom() - 4, body.y() + 24), body.right() - 5,
            body.bottom() - 4);
        g.pose().pushPose();
        g.pose().translate(body.x() + 6, body.y() + 24 - scroll, 0);
        g.pose().scale(SCALE, SCALE, 1);
        for (int i = 0; i < lines.size(); i++)
            g.drawString(font, lines.get(i), 0, i * 12, 0xff263e45, false);
        g.pose().popPose();
        g.disableScissor();
        g.disableScissor();
    }

    private Rect unitBounds() {
        int labelWidth = page == null
            ? 0
            : (int) Math
                .ceil(Minecraft.getInstance().font.width(page.label()) * SCALE);
        int width = Math.min(Math.max(64, labelWidth + 14),
            Math.max(0, body.width() - 12));
        return new Rect(body.right() - 6 - width, body.y() + 4, width, 14);
    }

    public boolean tabContains(double x, double y) {
        layout();
        return tab.contains(x, y);
    }

    public void tooltip(GuiGraphics g, int x, int y) {
        if (tab.contains(x, y)
            || open && page != null && unitBounds().contains(x, y))
            g.renderComponentTooltip(Minecraft.getInstance().font,
                List.of(text("title"), text("cycle_tip"), text("tip")), x, y);
    }

    public boolean contains(double x, double y) {
        layout();
        return tab.contains(x, y) || open && (body.contains(x, y)
            || SideDrawerLayout.rail(tab, body).contains(x, y)
                && !otherTab(x, y));
    }

    public boolean click(double x, double y, int button) {
        if (!contains(x, y)) {
            if ((button == 0 || button == 1) && otherTab(x, y))
                open = false;
            return false;
        }
        handled = true;
        if (button != 0 && button != 1)
            return true;
        if (tab.contains(x, y)) {
            if (open)
                open = false;
            else
                show();
            remember(open, selection());
        } else if (open && unitBounds().contains(x, y)) {
            var pages = pages();
            if (pages.isEmpty())
                return true;
            int index = 0;
            for (int i = 0; i < pages.size(); i++)
                if (pages.get(i).id().equals(selected))
                    index = i;
            selected = pages
                .get(
                    Math.floorMod(index + (button == 0 ? 1 : -1), pages.size()))
                .id();
            updatePage();
            scroll = 0;
        }
        return true;
    }

    public boolean release(double x, double y, int button) {
        boolean result = handled;
        handled = false;
        return result;
    }

    public boolean drag(double x, double y, int button, double dx, double dy) {
        return handled;
    }

    public boolean key(int key, int scan, int modifiers) {
        if (key != 256 || !open)
            return false;
        open = false;
        remember(open, selection());
        return true;
    }

    public boolean scroll(double x, double y, double amount) {
        if (!open || !contains(x, y))
            return false;
        scroll += amount < 0 ? 18 : -18;
        return true;
    }

    public List<Rect> exclusions() {
        layout();
        return open
            ? List.of(tab, body, SideDrawerLayout.rail(tab, body))
            : List.of(tab);
    }
}
