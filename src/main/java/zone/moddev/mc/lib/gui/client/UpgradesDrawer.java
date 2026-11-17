package zone.moddev.mc.lib.gui.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.drawer.DrawerDefinition;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.SideDrawerLayout;
import zone.moddev.mc.lib.gui.model.containers.UpgradesDrawerElement;

/** Upgrade inventory drawer using ordinary native slots and element actions. */
public class UpgradesDrawer extends Drawer {
    private final UpgradesDrawerElement element;
    public UpgradesDrawer(MachineScreen screen, DrawerDefinition definition) {
        super(screen, definition,
            IconSprite.gui(ResourceLocation.parse("mmdlib:icon_upgrades")),
            0xf0cd82);
        element = (UpgradesDrawerElement) definition.element().orElseThrow();
    }
    public boolean open() {
        return screen.getMenu().elementStates().get(element,
            UpgradesDrawerElement.OPEN);
    }
    public boolean pending() {
        return screen.getMenu().elementPending(element);
    }
    private boolean setOpen(boolean value) {
        return !pending() && screen.submitElementAction(element,
            UpgradesDrawerElement.SET_OPEN, value);
    }
    public void close() {
        if (open())
            setOpen(false);
    }
    public void closeLocal() {
    }
    public boolean restore(Selection selection) {
        if (pending())
            return false;
        if (!open()) {
            setOpen(true);
            return false;
        }
        activated();
        return true;
    }
    public Rect bodyBounds() {
        var size = element
            .bodySize(text -> net.minecraft.client.Minecraft.getInstance().font
                .width(ScreenText.component(text)));
        return contentBounds(size[0], size[1],
            SideDrawerLayout.HeightLimit.SCREEN);
    }
    public record Presentation(Rect body, int colour) {
    }
    @Override
    public <T> java.util.Optional<T> presentation(Class<T> type) {
        return type == Presentation.class
            ? java.util.Optional
                .of(type.cast(new Presentation(bodyBounds(), colour())))
            : super.presentation(type);
    }
    public boolean contains(double x, double y) {
        return tabContains(x, y)
            || open() && (bodyBounds().contains(x, y) || SideDrawerLayout
                .rail(allocatedTabBounds(), bodyBounds()).contains(x, y));
    }
    public List<Rect> exclusions() {
        return open()
            ? List.of(tabBounds(), bodyBounds(),
                SideDrawerLayout.rail(allocatedTabBounds(), bodyBounds()))
            : List.of(tabBounds());
    }
    public boolean click(double x, double y, int button) {
        if (button != 0 || !tabContains(x, y))
            return false;
        boolean next = !open();
        if (setOpen(next)) {
            if (next)
                activated();
            remember(next, selection());
        }
        return true;
    }
    public void tooltip(GuiGraphics graphics, int x, int y) {
        if (tabContains(x, y))
            graphics.renderTooltip(
                net.minecraft.client.Minecraft.getInstance().font,
                Component.translatable("screen.mmdlib.upgrades.title"), x, y);
    }
    /**
     * Draw before child slot frames; the ordinary menu draws their synchronized
     * contents.
     */
    public static void render(UpgradesDrawerElement element,
        ElementRenderers.Context context) {
        var tab = context.bounds();
        context.access().drawerPresentation(element, Presentation.class)
            .ifPresent(p -> {
                if (context.access().state().get(element,
                    UpgradesDrawerElement.OPEN))
                    SideDrawerRenderer.open(context.graphics(), tab, p.body(),
                        zone.moddev.mc.lib.gui.model.Edge.RIGHT, p.colour());
                else
                    SideDrawerRenderer.tab(context.graphics(), tab, p.colour());
            });
    }
}
