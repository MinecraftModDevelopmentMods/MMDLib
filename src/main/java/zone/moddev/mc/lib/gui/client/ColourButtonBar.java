package zone.moddev.mc.lib.gui.client;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphics;

import zone.moddev.mc.lib.gui.layout.Rect;

/**
 * Reusable selector for ordered opaque IDs and RGB colours, with bounded
 * overflow.
 */
public final class ColourButtonBar<K> {
    public record Entry<K>(K id, int colour) {
        public Entry {
            Objects.requireNonNull(id);
            if ((colour & 0xff000000) != 0)
                throw new IllegalArgumentException("Expected an RGB colour");
        }
    }

    private static final int SIZE = 14, PITCH = 18, ARROW = 10;
    private final List<Entry<K>> entries;
    private final List<K> order;
    private final Consumer<K> select;
    private Rect bounds;
    private int offset;
    private K selected;

    public ColourButtonBar(List<Entry<K>> entries, Consumer<K> select) {
        this.entries = List.copyOf(entries);
        order = this.entries.stream().map(Entry::id).toList();
        if (new HashSet<>(order).size() != order.size())
            throw new IllegalArgumentException("Button IDs must be unique");
        this.select = Objects.requireNonNull(select);
    }

    public List<K> order() {
        return order;
    }

    public void layout(Rect bounds, K selected) {
        boolean resized = !java.util.Objects.equals(this.bounds, bounds);
        this.bounds = bounds;
        if (bounds == null)
            return;
        int capacity = capacity();
        if (!Objects.equals(this.selected, selected) || resized) {
            int index = order.indexOf(selected);
            if (index < offset)
                offset = index;
            if (index >= offset + capacity)
                offset = index - capacity + 1;
        }
        this.selected = selected;
        clamp();
    }

    private boolean overflow() {
        return bounds.width() < order.size() * PITCH - (PITCH - SIZE);
    }

    private int capacity() {
        return Math.max(1,
            (bounds.width() - (overflow() ? 2 * ARROW : 0) + PITCH - SIZE)
                / PITCH);
    }

    private void clamp() {
        offset = Math.clamp(offset, 0, Math.max(0, order.size() - capacity()));
    }

    private Rect button(int visible) {
        return new Rect(bounds.x() + (overflow() ? ARROW : 0) + visible * PITCH,
            bounds.y() + (bounds.height() - SIZE) / 2, SIZE, SIZE);
    }

    /** Draws the bar and returns the hovered ID, if any. */
    public Optional<K> render(GuiGraphics graphics, int mouseX, int mouseY,
        boolean open) {
        if (bounds == null || entries.isEmpty())
            return Optional.empty();
        K hovered = null;
        for (int i = 0; i < capacity() && offset + i < order.size(); i++) {
            K group = order.get(offset + i);
            var rect = button(i);
            boolean active = open && Objects.equals(group, selected);
            int border = active ? 2 : 1;
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                active ? 0xffeeeeee : 0xff555555);
            graphics.fill(rect.x() + border, rect.y() + border,
                rect.right() - border, rect.bottom() - border,
                0xff000000 | entries.get(offset + i).colour());
            if (rect.contains(mouseX, mouseY))
                hovered = group;
        }
        if (overflow()) {
            arrow(graphics, bounds.x() + 2, offset > 0, false);
            arrow(graphics, bounds.right() - 7,
                offset + capacity() < order.size(), true);
        }
        return Optional.ofNullable(hovered);
    }

    private void arrow(GuiGraphics graphics, int x, boolean enabled,
        boolean right) {
        int y = bounds.y() + (bounds.height() - 5) / 2;
        for (int i = 0; i < 3; i++) {
            int dx = right ? i : 2 - i;
            graphics.fill(x + dx, y + i, x + dx + 1, y + 5 - i,
                enabled ? 0xffeeeeee : 0xff777777);
        }
    }

    public boolean click(double x, double y) {
        if (bounds == null || entries.isEmpty() || !bounds.contains(x, y))
            return false;
        if (overflow() && x < bounds.x() + ARROW)
            offset--;
        else if (overflow() && x >= bounds.right() - ARROW)
            offset++;
        else
            for (int i = 0; i < capacity() && offset + i < order.size(); i++)
                if (button(i).contains(x, y)) {
                    select.accept(order.get(offset + i));
                    break;
                }
        clamp();
        return true;
    }

    public boolean scroll(double x, double y, double amount) {
        if (bounds == null || entries.isEmpty() || !bounds.contains(x, y))
            return false;
        offset += (int) -amount;
        clamp();
        return true;
    }
}
