package zone.moddev.mc.lib.gui.layout;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.drawer.DrawerDefinition;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;

/**
 * A fresh per-layout plan supplied by an element. Contributions run in screen traversal order; each
 * may take ownership of body roots, append a footer and publish named regions. No registration is
 * required. Element definitions must not retain the mutable plan.
 */
public interface LayoutContribution {
    record Context(Screen screen, LayoutContext text, List<DrawerDefinition> drawers) {
        public Context {
            drawers = List.copyOf(drawers);
        }
    }

    record Size(int width, int height) {
        public Size {
            if (width < 0 || height < 0)
                throw new IllegalArgumentException("Negative footer size");
        }
    }

    /** Body roots this plan places itself; duplicate claims are rejected by identity. */
    default List<Element> claimedRoots() {
        return List.of();
    }

    /** Additional centred footer space, measured before the main body is placed. */
    default Optional<Size> footerSize() {
        return Optional.empty();
    }

    /** Receives the allocated footer rectangle before finish. */
    default void footer(Rect bounds) {
    }

    /** Places owned elements and adjusts the panel before tabs and authored offsets are applied. */
    default void finish(Panel panel) {
    }

    /** Observes final placement after every plan finishes, before authored offsets are applied. */
    default void beforeOffsets(Panel panel) {
    }

    /** Resolves associations after authored offsets; changes must not resize the panel here. */
    default void resolveOffsets(Panel panel) {
    }

    /** Final named regions for feature consumers; duplicate keys are rejected. */
    default Map<ResourceLocation, Rect> regions() {
        return Map.of();
    }

    interface Panel {
        Rect bounds(Element element);

        void place(Element element, Rect bounds);

        void shift(Element element, int dx, int dy);

        /** Shifts placed roots and pending footer allocations at or below y. */
        void shiftBelow(int y, int amount);

        void grow(int amount);
    }
}
