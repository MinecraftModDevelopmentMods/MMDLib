package zone.moddev.mc.lib.gui.runtime;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import zone.moddev.mc.lib.gui.layout.Rect;

/**
 * Provider-owned, bounded layout and gesture settings. Override settings to supply a mod policy.
 */
public class ContainerPolicy {
    public enum Gesture {
        PICKUP(ContainerDrawerMenu.PICKUP), QUICK_TAKE(ContainerDrawerMenu.QUICK_TAKE), QUICK_STORE(
            ContainerDrawerMenu.QUICK_STORE), DISTRIBUTE(ContainerDrawerMenu.DISTRIBUTE), COLLECT(
                ContainerDrawerMenu.COLLECT), SWAP(ContainerDrawerMenu.SWAP);
        private final int requestKind;

        Gesture(int requestKind) {
            this.requestKind = requestKind;
        }
    }

    public record Layout(
        int width,
        int columns,
        int maxRows,
        int cellX,
        int cellY,
        int strideX,
        int strideY,
        int cellSize,
        int footer) {
        public Layout {
            if (width < 32
                || width > 512
                || columns < 1
                || columns > ContainerDrawerMenu.MAX_PAGE
                || maxRows < 1
                || maxRows * (long) columns > ContainerDrawerMenu.MAX_PAGE
                || cellX < 0
                || cellY < 18
                || cellY > 512
                || strideX < 1
                || strideX > 512
                || strideY < 1
                || strideY > 512
                || cellSize < 4
                || cellSize > 64
                || cellSize > strideX
                || cellSize > strideY
                || footer < 18
                || footer > 512
                || cellX + (long) (columns - 1) * strideX + cellSize > width)
                throw new IllegalArgumentException("Invalid container layout");
        }

        public Rect cell(int index) {
            return new Rect(
                cellX + index % columns * strideX, cellY + index / columns * strideY, cellSize,
                cellSize);
        }
    }

    public record Settings(
        Layout layout,
        int initialPageSize,
        int pageAlignment,
        Set<Gesture> gestures,
        boolean protectSource) {
        public Settings {
            Objects.requireNonNull(layout);
            gestures = Set.copyOf(gestures);
            if (pageAlignment < 1
                || pageAlignment > ContainerDrawerMenu.MAX_PAGE
                || initialPageSize < 1
                || initialPageSize > ContainerDrawerMenu.MAX_PAGE
                || initialPageSize > layout.columns() * layout.maxRows()
                || initialPageSize % pageAlignment != 0
                || layout.columns() % pageAlignment != 0)
                throw new IllegalArgumentException("Invalid container page policy");
        }

        public boolean allows(Gesture gesture) {
            return gestures.contains(gesture);
        }

        public boolean allowsRequest(int kind) {
            for (var gesture : Gesture.values())
                if (gesture.requestKind == kind)
                    return allows(gesture);
            return true;
        }

        public boolean acceptsPage(int offset, int size) {
            return offset >= 0
                && offset % pageAlignment == 0
                && size >= 1
                && size <= ContainerDrawerMenu.MAX_PAGE
                && size <= layout.columns() * layout.maxRows()
                && size % pageAlignment == 0;
        }
    }

    public static final ContainerPolicy STORAGE = new ContainerPolicy(
        new Settings(
            new Layout(120, 6, 9, 6, 22, 18, 18, 18, 22),
            27,
            1,
            EnumSet.allOf(Gesture.class),
            true));
    public static final ContainerPolicy EQUIPMENT = new ContainerPolicy(
        new Settings(
            new Layout(120, 2, 27, 76, 34, 20, 22, 18, 22),
            20,
            2,
            EnumSet.of(Gesture.PICKUP, Gesture.QUICK_TAKE, Gesture.QUICK_STORE),
            false));

    /**
     * Provider-authored instructions; sampled with settings, copied and synchronized as
     * presentation.
     */
    public List<net.minecraft.network.chat.Component> instructions(Settings settings) {
        return defaultInstructions(settings);
    }

    public static List<net.minecraft.network.chat.Component> defaultInstructions(
        Settings settings) {
        var rows = new ArrayList<net.minecraft.network.chat.Component>();
        if (settings.allows(Gesture.DISTRIBUTE))
            rows.add(
                net.minecraft.network.chat.Component
                    .translatable("screen.mmdlib.container.distribute"));
        if (settings.allows(Gesture.COLLECT))
            rows.add(
                net.minecraft.network.chat.Component
                    .translatable("screen.mmdlib.container.collect"));
        if (settings.allows(Gesture.SWAP))
            rows.add(
                net.minecraft.network.chat.Component.translatable("screen.mmdlib.container.swap"));
        return List.copyOf(rows);
    }

    private final Settings settings;

    public ContainerPolicy() {
        this(STORAGE.settings());
    }

    public ContainerPolicy(Settings settings) {
        this.settings = Objects.requireNonNull(settings);
    }

    /** Sampled on the server and synchronized. Changes invalidate in-flight gestures. */
    public Settings settings() {
        return settings;
    }
}
