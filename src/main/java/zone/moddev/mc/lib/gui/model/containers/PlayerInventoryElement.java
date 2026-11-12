package zone.moddev.mc.lib.gui.model.containers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.layout.ElementLayouts;
import zone.moddev.mc.lib.gui.layout.LayoutContext;
import zone.moddev.mc.lib.gui.layout.LayoutContribution;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;

/** {@code player-inventory}: places the player inventory explicitly. */
public class PlayerInventoryElement extends PlayerSlotsElement {
    public PlayerInventoryElement(Common common) {
        this(common,
            zone.moddev.mc.lib.gui.model.elements.PlayerSlotElement::player);
    }

    /**
     * Use custom slot children while retaining this section's standard layout.
     */
    public PlayerInventoryElement(Common common,
        java.util.function.IntFunction<? extends zone.moddev.mc.lib.gui.model.elements.SlotElement> factory) {
        super(common, java.util.stream.IntStream.range(0, 36)
            .map(i -> (i + 9) % 36).boxed().toList(), factory);
    }

    public String part() {
        return "storage";
    }

    public static final int WIDTH = 184, HEIGHT = 94;

    public int[] measure(TextMeasure text) {
        return new int[]{text instanceof LayoutContext context
            ? context.minimumWidth(this, 162)
            : 162, HEIGHT};
    }

    public Rect defaultBounds(Rect inventory) {
        return new Rect(inventory.x(), inventory.y(), 162, HEIGHT);
    }

    protected Rect position(int index) {
        return new Rect(index % 9 * 18,
            index < 9 ? 76 : 18 + (index / 9 - 1) * 18, 18, 18);
    }

    /**
     * Default sections absent from the declared tree; these are ordinary model
     * elements.
     */
    public static List<Element> defaults(List<Element> body,
        List<Element> tabs) {
        var parts = new HashSet<String>();
        java.util.function.Consumer<Element> collect = new java.util.function.Consumer<>() {
            public void accept(Element element) {
                if (element instanceof PlayerSlotsElement player)
                    parts.add(player.part());
                element.children().forEach(this);
            }
        };
        body.forEach(collect);
        tabs.forEach(collect);
        var defaults = new ArrayList<Element>();
        if (!parts.contains("storage"))
            defaults.add(new PlayerInventoryElement(Common.empty()));
        if (!parts.contains("armour"))
            defaults.add(new PlayerArmourElement(Common.empty()));
        if (!parts.contains("offhand"))
            defaults.add(new PlayerOffhandElement(Common.empty()));
        return List.copyOf(defaults);
    }

    /**
     * Vanilla storage, hotbar and equipment coordinates belong to this
     * container.
     */
    public static Rect slot(Rect bounds, int index) {
        if (index < 0 || index > 40)
            throw new IllegalArgumentException("Player slot: " + index);
        int x = index < 36 ? index % 9 * 18 : 166;
        int y = index < 36
            ? (index < 9 ? 76 : 18 + (index / 9 - 1) * 18)
            : index == 40 ? 76 : (39 - index) * 18;
        return new Rect(bounds.x() + x, bounds.y() + y, 18, 18);
    }

    public static final ResourceLocation INVENTORY = ResourceLocation
        .parse("mmdlib:player_inventory");
    public static final ResourceLocation TOOLBAR = ResourceLocation
        .parse("mmdlib:inventory_toolbar");

    @Override
    public Optional<LayoutContribution> layoutContribution(
        LayoutContribution.Context context) {
        return Optional.of(new Placement(this, context));
    }

    /**
     * Inventory composition and toolbar policy; ScreenLayout only places the
     * measured regions.
     */
    public static class Placement implements LayoutContribution {
        private final PlayerInventoryElement explicit;
        private final InventoryControlsElement controls;
        private final TextMeasure text;
        private final int toolbarWidth;
        private final boolean implicit;
        private final Screen screen;
        private Rect inventory, toolbar, originalAnchor;

        public Placement(PlayerInventoryElement element,
            LayoutContribution.Context context) {
            this.screen = context.screen();
            this.text = context.text();
            this.toolbarWidth = context.drawers().stream().mapToInt(
                zone.moddev.mc.lib.gui.drawer.DrawerDefinition::toolbarWidth)
                .max().orElse(0);
            explicit = element;
            controls = screen.body().stream()
                .filter(InventoryControlsElement.class::isInstance)
                .map(InventoryControlsElement.class::cast).findFirst()
                .orElse(null);
            implicit = explicit != null && screen.implicitElements().stream()
                .anyMatch(e -> e == explicit);
            context.text().reserveWidth(explicit, footerWidth());
        }

        public List<Element> claimedRoots() {
            return controls == null ? List.of() : List.of(controls);
        }

        private int footerWidth() {
            return screen.implicitElements().stream()
                .filter(PlayerSlotsElement.class::isInstance)
                .map(PlayerSlotsElement.class::cast)
                .mapToInt(part -> part
                    .defaultBounds(new Rect(0, 0, 162, HEIGHT)).right())
                .max().orElse(162);
        }

        public Optional<LayoutContribution.Size> footerSize() {
            return implicit
                ? Optional.of(new LayoutContribution.Size(
                    Math.max(162, footerWidth()), HEIGHT))
                : Optional.empty();
        }

        public void footer(Rect bounds) {
            inventory = bounds;
        }

        public void finish(Panel panel) {
            if (explicit != null && !implicit) {
                inventory = panel.bounds(explicit);
                originalAnchor = inventory;
            }
            if (inventory == null)
                return;
            if (toolbarWidth == 0 && controls == null) {
                placeImplicit(panel);
                originalAnchor = panel.bounds(explicit);
                return;
            }
            int start = inventory.y();
            int[] size = controls == null
                ? new int[]{0, 0}
                : ElementLayouts.size(controls, text);
            int rowHeight = Math.max(18,
                controls == null ? 0 : size[1] + ScreenLayout.GAP);
            int storageRight = slot(inventory, 17).right();
            int colourWidth = storageRight - inventory.x()
                - (size[0] == 0 ? 0 : size[0] + ScreenLayout.GAP);
            if (colourWidth < toolbarWidth)
                throw new IllegalArgumentException(
                    "Inventory controls leave too little room for colour selectors");
            int extra = Math.max(0, rowHeight - 18);
            panel.shiftBelow(start, extra);
            toolbar = new Rect(inventory.x(), start, colourWidth,
                rowHeight - ScreenLayout.GAP);
            if (controls != null)
                panel.place(controls, new Rect(storageRight - size[0], start,
                    size[0], rowHeight - ScreenLayout.GAP));
            inventory = inventory.offset(0, extra);
            panel.grow(extra);
            placeImplicit(panel);
            originalAnchor = panel.bounds(explicit);
        }

        public void beforeOffsets(Panel panel) {
            // Other plans may have shifted all sections already; only authored
            // offsets need
            // propagation.
            var placed = panel.bounds(explicit);
            int dx = placed.x() - originalAnchor.x(),
                dy = placed.y() - originalAnchor.y();
            inventory = inventory.offset(dx, dy);
            if (toolbar != null)
                toolbar = toolbar.offset(dx, dy);
            originalAnchor = placed;
        }

        /**
         * Called after generic element offsets, so native slots follow an
         * explicit container's offsets.
         */
        public void resolveOffsets(Panel panel) {
            if (inventory == null)
                return;
            var placed = panel.bounds(explicit);
            int dx = placed.x() - originalAnchor.x(),
                dy = placed.y() - originalAnchor.y();
            inventory = inventory.offset(dx, dy);
            if (toolbar != null)
                toolbar = toolbar.offset(dx, dy);
            if (controls != null)
                panel.shift(controls, dx, dy);
            for (var element : screen.implicitElements())
                if (element instanceof PlayerSlotsElement
                    && element != explicit)
                    panel.shift(element, dx, dy);
        }

        private void placeImplicit(Panel panel) {
            for (var element : screen.implicitElements())
                if (element instanceof PlayerSlotsElement part)
                    panel.place(element, part.defaultBounds(inventory));
        }

        public Map<ResourceLocation, Rect> regions() {
            var result = new HashMap<ResourceLocation, Rect>();
            if (inventory != null)
                result.put(INVENTORY, inventory);
            if (toolbar != null)
                result.put(TOOLBAR, toolbar);
            return Map.copyOf(result);
        }

        public Optional<Rect> inventory() {
            return Optional.ofNullable(inventory);
        }

        public Optional<Rect> toolbar() {
            return Optional.ofNullable(toolbar);
        }
    }
}
