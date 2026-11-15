package zone.moddev.mc.lib.gui.client.recipe;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.client.Drawer;
import zone.moddev.mc.lib.gui.client.IconSprite;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.drawer.DrawerDefinition;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.SideDrawerLayout;

/**
 * Native-scale paged recipe drawer; has no JEI dependency or machine-specific
 * policy.
 */
public final class RecipeDrawer extends Drawer {
    private final RecipeCatalog catalog;
    private boolean open;
    private String groupKey = "";
    private int page;
    private RecipeCatalog.Group group;
    private List<RecipeCatalog.Group> groups = List.of();
    private Rect body = new Rect(0, 0, 0, 0);
    private int rowHeight = 24, pageSize = 1;

    public RecipeDrawer(MachineScreen screen, DrawerDefinition definition,
        RecipeCatalog catalog) {
        super(screen, definition, IconSprite.gui(
            ResourceLocation.fromNamespaceAndPath("mmdlib", "icon_recipes")),
            0x96b9a5);
        this.catalog = Objects.requireNonNull(catalog);
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
    public Selection selection() {
        return new Selection(groupKey, page);
    }

    public boolean restore(Selection selection) {
        if (screen.drawers().pending(edge()))
            return false;
        groupKey = selection.key().isEmpty()
            ? catalog.initialGroup()
            : selection.key();
        page = selection.offset();
        openLocal();
        open = true;
        layout();
        return true;
    }

    private void layout() {
        groups = catalog.groups();
        group = groups.stream().filter(g -> g.key().equals(groupKey))
            .findFirst().orElse(groups.isEmpty() ? null : groups.getFirst());
        String next = group == null ? "" : group.key();
        if (!next.equals(groupKey))
            page = 0;
        groupKey = next;
        int width = 160;
        rowHeight = 24;
        if (group != null)
            for (var choice : group.choices()) {
                width = Math.max(width, choice.presentation().width());
                rowHeight = Math.max(rowHeight, choice.presentation().height());
            }
        body = zone.moddev.mc.lib.gui.layout.RecipeDrawerLayout.body(edge(),
            tabBounds(), screen.panelBounds(), screen.width,
            screen.drawerBottom(), width, rowHeight);
        pageSize = zone.moddev.mc.lib.gui.layout.RecipeDrawerLayout
            .pageSize(body, rowHeight);
        page = Math.clamp(page, 0, pages() - 1);
    }

    private int pages() {
        return Math.max(1,
            Math.ceilDiv(group == null ? 0 : group.choices().size(), pageSize));
    }
    public Rect bodyBounds() {
        layout();
        return body;
    }
    public List<RecipeCatalog.Choice> visibleRecipes() {
        layout();
        if (group == null)
            return List.of();
        int start = page * pageSize;
        return group.choices().subList(start,
            Math.min(group.choices().size(), start + pageSize));
    }

    private Rect previousGroup() {
        return new Rect(body.x() + 5, body.y() + 4, 16, 18);
    }
    private Rect nextGroup() {
        return new Rect(body.right() - 21, body.y() + 4, 16, 18);
    }
    private Rect previousPage() {
        return new Rect(body.x() + 5, body.bottom() - 20, 16, 16);
    }
    private Rect nextPage() {
        return new Rect(body.right() - 21, body.bottom() - 20, 16, 16);
    }
    private Rect content() {
        return zone.moddev.mc.lib.gui.layout.RecipeDrawerLayout.content(body);
    }

    public void renderForeground(GuiGraphics g, int mx, int my) {
        if (!open)
            return;
        var choices = visibleRecipes();
        drawFrame(g, tabBounds(), body, true);
        var font = Minecraft.getInstance().font;
        g.enableScissor(body.x() + 4, body.y() + 4, body.right() - 4,
            body.bottom() - 4);
        try {
            if (group != null) {
                g.renderItem(group.icon(), body.x() + 24, body.y() + 5);
                g.drawString(font,
                    font.plainSubstrByWidth(group.title().getString(),
                        Math.max(0, body.width() - 68)),
                    body.x() + 44, body.y() + 9, 0xff263e35, false);
            }
            arrow(g, previousGroup(), "<", groups.size() > 1);
            arrow(g, nextGroup(), ">", groups.size() > 1);
            arrow(g, previousPage(), "<", page > 0);
            arrow(g, nextPage(), ">", page + 1 < pages());
            var label = Component.translatable("screen.mmdlib.recipes.page",
                page + 1, pages());
            g.drawString(font, label,
                body.x() + (body.width() - font.width(label)) / 2,
                body.bottom() - 16, 0xff263e35, false);
            var content = content();
            g.enableScissor(content.x(), content.y(), content.right(),
                content.bottom());
            try {
                if (choices.isEmpty())
                    g.drawString(font,
                        Component.translatable("screen.mmdlib.recipes.empty"),
                        content.x(), content.y(), 0xff555555, false);
                for (int i = 0; i < choices.size(); i++) {
                    var row = new Rect(content.x(), content.y() + i * rowHeight,
                        content.width(), rowHeight);
                    if (row.contains(mx, my))
                        g.fill(row.x(), row.y(), row.right(), row.bottom(),
                            0x3058bd98);
                    g.pose().pushPose();
                    try {
                        g.pose().translate(row.x(), row.y(), 0);
                        choices.get(i).presentation().draw(g);
                    } finally {
                        g.pose().popPose();
                    }
                }
            } finally {
                g.disableScissor();
            }
        } finally {
            g.disableScissor();
        }
    }

    private static void arrow(GuiGraphics g, Rect r, String text,
        boolean enabled) {
        g.drawString(Minecraft.getInstance().font, text, r.x() + 5, r.y() + 4,
            enabled ? 0xff263e35 : 0xff999999, false);
    }

    public List<Rect> exclusions() {
        if (!open)
            return List.of(tabBounds());
        layout();
        return List.of(tabBounds(), body,
            SideDrawerLayout.rail(tabBounds(), body));
    }
    public boolean contains(double x, double y) {
        if (!open)
            return tabContains(x, y);
        layout();
        return tabContains(x, y) || body.contains(x, y)
            || SideDrawerLayout.rail(tabBounds(), body).contains(x, y)
                && !otherTab(x, y);
    }
    public boolean covers(double x, double y) {
        if (!open)
            return false;
        layout();
        return body.contains(x, y)
            || SideDrawerLayout.rail(tabBounds(), body).contains(x, y);
    }
    public boolean click(double x, double y, int button) {
        if (!contains(x, y))
            return false;
        if (button != 0)
            return true;
        if (tabContains(x, y)) {
            if (open)
                close();
            else
                restore(selection());
        } else if (open) {
            if (previousGroup().contains(x, y))
                changeGroup(-1);
            else if (nextGroup().contains(x, y))
                changeGroup(1);
            else if (previousPage().contains(x, y))
                page = Math.max(0, page - 1);
            else if (nextPage().contains(x, y))
                page = Math.min(pages() - 1, page + 1);
            else if (content().contains(x, y)) {
                var choices = visibleRecipes();
                int row = (int) (y - content().y()) / rowHeight;
                if (row < choices.size()
                    && catalog.select(group, choices.get(row)))
                    close();
            }
        }
        remember(open, selection());
        return true;
    }
    private void changeGroup(int delta) {
        if (groups.isEmpty())
            return;
        groupKey = groups
            .get(Math.floorMod(groups.indexOf(group) + delta, groups.size()))
            .key();
        page = 0;
    }
    public boolean scroll(double x, double y, double amount) {
        if (!open)
            return false;
        layout();
        if (!open || !body.contains(x, y))
            return false;
        page = Math.clamp(page + (amount > 0 ? -1 : amount < 0 ? 1 : 0), 0,
            pages() - 1);
        remember(true, selection());
        return true;
    }
    public void tooltip(GuiGraphics g, int x, int y) {
        if (open)
            layout();
        List<Component> lines = List.of();
        if (tabContains(x, y))
            lines = List
                .of(Component.translatable("screen.mmdlib.recipes.title"));
        else if (open && content().contains(x, y)) {
            var choices = visibleRecipes();
            int row = (y - content().y()) / rowHeight;
            if (row < choices.size())
                lines = choices.get(row).presentation().tooltip(
                    x - content().x(), y - content().y() - row * rowHeight);
        } else if (open
            && (previousGroup().contains(x, y) || nextGroup().contains(x, y))) {
            lines = List
                .of(Component.translatable("screen.mmdlib.recipes.group"));
        }
        if (!lines.isEmpty())
            g.renderComponentTooltip(Minecraft.getInstance().font, lines, x, y);
    }
}
