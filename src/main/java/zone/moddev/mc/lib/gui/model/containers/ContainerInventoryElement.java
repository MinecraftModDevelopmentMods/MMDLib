package zone.moddev.mc.lib.gui.model.containers;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.elements.ContainerSlotElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.runtime.EquippedContainers;

/** Element-owned page presentation. Indexes retain their absolute addresses in the container. */
public class ContainerInventoryElement extends InventoryElement {
    public record Page(
        int offset,
        int width,
        int height,
        List<Rect> cells,
        List<EquippedContainers.SlotInfo> descriptions,
        EquippedContainers.Presentation presentation) {
        public Page {
            cells = List.copyOf(cells);
            descriptions = List.copyOf(descriptions);
            if (offset < 0
                || width < 0
                || height < 0
                || (!descriptions.isEmpty() && descriptions.size() != cells.size()))
                throw new IllegalArgumentException("Invalid container page");
        }

        public Optional<EquippedContainers.SlotInfo> description(int index) {
            int local = index - offset;
            return local >= 0 && local < descriptions.size()
                ? Optional.of(descriptions.get(local))
                : Optional.empty();
        }
    }

    private final Page page;

    public ContainerInventoryElement(Page page) {
        this(page, index -> new ContainerSlotElement(index, page.description(index)));
    }

    public ContainerInventoryElement(Page page, IntFunction<? extends SlotElement> factory) {
        super(Common.empty(), "container", 1, 0, slots(page, factory));
        this.page = page;
    }

    private static List<Element> slots(Page page, IntFunction<? extends SlotElement> factory) {
        var slots = new ArrayList<Element>();
        for (int i = 0; i < page.cells().size(); i++) {
            var slot = Objects.requireNonNull(factory.apply(page.offset() + i));
            if (slot.index() != page.offset() + i || slot.playerInventory() || slot.ghost())
                throw new IllegalArgumentException(
                    "Container cells must retain their real storage indexes");
            slots.add(slot);
        }
        return List.copyOf(slots);
    }

    /** Values for registered cell renderers and tooltip hooks; pending locks remain in the host. */
    public Object drawerValue(
        Element element,
        boolean maximum,
        net.minecraft.world.item.ItemStack contents,
        boolean selected) {
        if (maximum)
            return null;
        return element instanceof SlotElement
            ? new ContainerSlotElement.Display(contents, selected)
            : page.presentation().title();
    }

    public Page page() {
        return page;
    }

    public Rect cellBounds(SlotElement slot) {
        return page.cells().get(slot.index() - page.offset());
    }

    public List<net.minecraft.network.chat.Component> headings() {
        return page.presentation().headings();
    }

    public net.minecraft.network.chat.Component cellLabel(SlotElement slot) {
        return slot.cellLabel();
    }

    public Rect labelBounds(SlotElement slot) {
        var cell = cellBounds(slot);
        return new Rect(
            6, cell.y() + 5, Math.max(0, page.presentation().policy().layout().cellX() - 10), 9);
    }

    public Rect headingBounds(int index) {
        var layout = page.presentation().policy().layout();
        return new Rect(
            index == 0 ? 6 : layout.cellX() + (index - 1) * layout.strideX() + 4,
            layout.cellY() - 12,
            index == 0 ? Math.max(0, layout.cellX() - 10) : layout.strideX() - 2,
            9);
    }

    @Override
    public int[] measure(TextMeasure text) {
        return new int[]{page.width(), page.height()};
    }

    @Override
    public Map<Element, Rect> childLayout(Rect bounds, TextMeasure text) {
        var result = new IdentityHashMap<Element, Rect>();
        for (var slot : slots())
            result.put(slot, cellBounds(slot));
        return result;
    }
}
