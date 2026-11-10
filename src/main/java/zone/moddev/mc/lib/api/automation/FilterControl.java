package zone.moddev.mc.lib.api.automation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.layout.FlowLayout;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.model.elements.GhostSlotElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;

/**
 * An open element contract for item-provided editors. Each control validates
 * its own edits.
 */
public interface FilterControl extends Element {
    String id();

    default Common common() {
        return new Common(Optional.of(id()), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Common.Clicks.NONE,
            Optional.empty(), Optional.empty(), List.of(), 0, 0, 0);
    }

    default int[] measure(TextMeasure text) {
        return new int[]{18, 18};
    }

    default boolean stretchX() {
        return true;
    }

    /**
     * Server-only candidate data, after the host has authorized the attachment
     * and revision.
     */
    record Edit(InventoryFilter provider, ItemStack filter,
        CompoundTag configuration, int index, String value, ItemStack cursor,
        HolderLookup.Provider registries) {
    }

    /**
     * Refuse edits unless this control explicitly validates and applies the
     * proposed value.
     */
    default boolean edit(Edit edit) {
        return false;
    }

    class Toggle implements FilterControl {
        private final String id;
        private final ResourceLocation icon;
        private final ResourceLocation selectedIcon;
        private final Component tooltip;
        private final Component selectedTooltip;

        public Toggle(String id, ResourceLocation icon,
            ResourceLocation selectedIcon, Component tooltip,
            Component selectedTooltip) {
            this.id = id;
            this.icon = icon;
            this.selectedIcon = selectedIcon;
            this.tooltip = tooltip;
            this.selectedTooltip = selectedTooltip;
        }

        public Toggle(String id, ResourceLocation icon, Component tooltip) {
            this(id, icon, icon, tooltip, tooltip);
        }

        public int[] measure(TextMeasure text) {
            return new int[]{14, 14};
        }

        public boolean stretchX() {
            return false;
        }

        public boolean edit(Edit edit) {
            edit.configuration().putBoolean(id,
                !edit.configuration().getBoolean(id));
            return true;
        }

        public String id() {
            return id;
        }

        public ResourceLocation icon() {
            return icon;
        }

        public ResourceLocation selectedIcon() {
            return selectedIcon;
        }

        public Component tooltip() {
            return tooltip;
        }

        public Component selectedTooltip() {
            return selectedTooltip;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object)
                return true;
            if (object == null || getClass() != object.getClass())
                return false;
            var other = (Toggle) object;
            return java.util.Objects.equals(id, other.id)
                && java.util.Objects.equals(icon, other.icon)
                && java.util.Objects.equals(selectedIcon, other.selectedIcon)
                && java.util.Objects.equals(tooltip, other.tooltip)
                && java.util.Objects.equals(selectedTooltip,
                    other.selectedTooltip);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(id);
            hash = 31 * hash + java.util.Objects.hashCode(icon);
            hash = 31 * hash + java.util.Objects.hashCode(selectedIcon);
            hash = 31 * hash + java.util.Objects.hashCode(tooltip);
            hash = 31 * hash + java.util.Objects.hashCode(selectedTooltip);
            return hash;
        }

        @Override
        public String toString() {
            return "Toggle[id=" + id + ", icon=" + icon + ", selectedIcon="
                + selectedIcon + ", tooltip=" + tooltip + ", selectedTooltip="
                + selectedTooltip + "]";
        }
    }

    /**
     * Compatibility element for a registered filter widget; it uses shared
     * behavior/render routing.
     */
    class Custom implements FilterControl {
        private final String id;
        private final FilterWidgetType<?> type;
        private final int height;

        public Custom(String id, FilterWidgetType<?> type, int height) {
            if (height < 1 || height > 256)
                throw new IllegalArgumentException("Custom control height");
            Objects.requireNonNull(type);

            this.id = id;
            this.type = type;
            this.height = height;
        }

        public int[] measure(TextMeasure text) {
            return new int[]{18, height};
        }

        public boolean edit(Edit edit) {
            return type.dispatch(edit.configuration(), edit.value());
        }

        public String id() {
            return id;
        }

        public FilterWidgetType<?> type() {
            return type;
        }

        public int height() {
            return height;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object)
                return true;
            if (object == null || getClass() != object.getClass())
                return false;
            var other = (Custom) object;
            return java.util.Objects.equals(id, other.id)
                && java.util.Objects.equals(type, other.type)
                && java.util.Objects.equals(height, other.height);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(id);
            hash = 31 * hash + java.util.Objects.hashCode(type);
            hash = 31 * hash + java.util.Objects.hashCode(height);
            return hash;
        }

        @Override
        public String toString() {
            return "Custom[id=" + id + ", type=" + type + ", height=" + height
                + "]";
        }
    }

    class Text implements FilterControl {
        private final String id;
        private final int maxLength;
        private final Component tooltip;

        public Text(String id, int maxLength, Component tooltip) {
            if (maxLength < 1 || maxLength > 256)
                throw new IllegalArgumentException("Text limit");

            this.id = id;
            this.maxLength = maxLength;
            this.tooltip = tooltip;
        }

        public boolean edit(Edit edit) {
            if (edit.value().length() > maxLength
                || edit.value().codePoints().anyMatch(Character::isISOControl))
                return false;
            edit.configuration().putString(id, edit.value());
            return true;
        }

        public String id() {
            return id;
        }

        public int maxLength() {
            return maxLength;
        }

        public Component tooltip() {
            return tooltip;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object)
                return true;
            if (object == null || getClass() != object.getClass())
                return false;
            var other = (Text) object;
            return java.util.Objects.equals(id, other.id)
                && java.util.Objects.equals(maxLength, other.maxLength)
                && java.util.Objects.equals(tooltip, other.tooltip);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(id);
            hash = 31 * hash + java.util.Objects.hashCode(maxLength);
            hash = 31 * hash + java.util.Objects.hashCode(tooltip);
            return hash;
        }

        @Override
        public String toString() {
            return "Text[id=" + id + ", maxLength=" + maxLength + ", tooltip="
                + tooltip + "]";
        }
    }

    class Number implements FilterControl {
        private final String id;
        private final int min;
        private final int max;
        private final int step;
        private final Component tooltip;

        public Number(String id, int min, int max, int step,
            Component tooltip) {
            if (min > max || step < 1)
                throw new IllegalArgumentException("Number bounds");

            this.id = id;
            this.min = min;
            this.max = max;
            this.step = step;
            this.tooltip = tooltip;
        }

        public boolean edit(Edit edit) {
            int proposed;
            try {
                proposed = Integer.parseInt(edit.value());
            } catch (NumberFormatException invalid) {
                return false;
            }
            if (proposed < min || proposed > max)
                return false;
            edit.configuration().putInt(id, proposed);
            return true;
        }

        public String id() {
            return id;
        }

        public int min() {
            return min;
        }

        public int max() {
            return max;
        }

        public int step() {
            return step;
        }

        public Component tooltip() {
            return tooltip;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object)
                return true;
            if (object == null || getClass() != object.getClass())
                return false;
            var other = (Number) object;
            return java.util.Objects.equals(id, other.id)
                && java.util.Objects.equals(min, other.min)
                && java.util.Objects.equals(max, other.max)
                && java.util.Objects.equals(step, other.step)
                && java.util.Objects.equals(tooltip, other.tooltip);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(id);
            hash = 31 * hash + java.util.Objects.hashCode(min);
            hash = 31 * hash + java.util.Objects.hashCode(max);
            hash = 31 * hash + java.util.Objects.hashCode(step);
            hash = 31 * hash + java.util.Objects.hashCode(tooltip);
            return hash;
        }

        @Override
        public String toString() {
            return "Number[id=" + id + ", min=" + min + ", max=" + max
                + ", step=" + step + ", tooltip=" + tooltip + "]";
        }
    }

    class Samples implements FilterControl {
        private static final SlotElement DEFAULT_SLOT = new GhostSlotElement(
            Common.empty(), 0, SlotElement.Appearance.standard(SlotSize.NORMAL,
                Optional.empty(), Optional.empty()));
        private final String id;
        private final int count;
        private final ResourceType<?> resource;

        public Samples(String id, int count, ResourceType<?> resource) {
            if (count < 1 || count > 256)
                throw new IllegalArgumentException("Sample limit");

            this.id = id;
            this.count = count;
            this.resource = resource;
        }

        public int[] measure(TextMeasure text) {
            return measure(text, cellSize());
        }

        public int[] measure(TextMeasure text, int width) {
            int height = 0;
            var bounds = new Rect(0, 0, width, 0);
            for (int i = 0; i < count(); i++)
                height = Math.max(height, sampleBounds(bounds, i).bottom());
            return new int[]{width, height};
        }

        /**
         * Grid pitch in pixels; override together with slot appearance for
         * larger or spaced cells.
         */
        public int cellSize() {
            return 18;
        }

        /**
         * Slot presentation only: these cells do not create native inventory
         * slots or separate actions.
         */
        public SlotElement sampleSlot(int index) {
            return DEFAULT_SLOT;
        }

        /**
         * Absolute cell bounds, shared by measurement, drawing, tooltips and
         * click targeting.
         */
        public Rect sampleBounds(Rect bounds, int index) {
            int size = cellSize();
            if (size < 1)
                throw new IllegalArgumentException(
                    "Sample cell size must be positive");
            int columns = Math.max(1, bounds.width() / size);
            return new Rect(bounds.x() + index % columns * size,
                bounds.y() + index / columns * size, size, size);
        }

        /**
         * Last matching cell wins; unoccupied space and points outside the
         * control do not hit.
         */
        public int sampleIndex(Rect bounds, double x, double y) {
            if (!bounds.contains(x, y))
                return -1;
            for (int i = count() - 1; i >= 0; i--)
                if (sampleBounds(bounds, i).contains(x, y))
                    return i;
            return -1;
        }

        /**
         * Selects the displayed stack from read-only synchronized
         * configuration; mods may override.
         */
        public ItemStack sampleDisplay(CompoundTag configuration, int index) {
            var key = ResourceLocation.tryParse(
                configuration.getList(id(), Tag.TAG_STRING).getString(index));
            if (key == null)
                return ItemStack.EMPTY;
            if (resource() == ResourceType.ITEMS)
                return new ItemStack(BuiltInRegistries.ITEM.get(key));
            if (resource() == ResourceType.FLUIDS)
                return new ItemStack(
                    BuiltInRegistries.FLUID.get(key).getBucket());
            return ItemStack.EMPTY;
        }

        /**
         * Tooltip rows for one cell; the default follows the element's
         * displayed stack.
         */
        public List<Component> sampleTooltip(CompoundTag configuration,
            int index) {
            var item = sampleDisplay(configuration, index);
            return List.of(item.isEmpty()
                ? Component.translatable("screen.mmdlib.automation.sample")
                : item.getHoverName());
        }

        public boolean edit(Edit edit) {
            if (!edit.value().equals("sample") && !edit.value().equals("clear"))
                return false;
            if (edit.index() < 0 || edit.index() >= count
                || resource != edit.provider().resource(edit.filter()))
                return false;
            var entries = edit.configuration().getList(id, Tag.TAG_STRING)
                .copy();
            while (entries.size() < count)
                entries.add(StringTag.valueOf(""));
            String sample = edit.value().equals("clear")
                ? ""
                : edit.provider().sample(edit.filter(), edit.cursor(),
                    edit.registries());
            if (!edit.value().equals("clear") && sample.isEmpty())
                return false;
            entries.set(edit.index(), StringTag.valueOf(sample));
            edit.configuration().put(id, entries);
            return true;
        }

        public String id() {
            return id;
        }

        public int count() {
            return count;
        }

        public ResourceType<?> resource() {
            return resource;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object)
                return true;
            if (object == null || getClass() != object.getClass())
                return false;
            var other = (Samples) object;
            return java.util.Objects.equals(id, other.id)
                && java.util.Objects.equals(count, other.count)
                && java.util.Objects.equals(resource, other.resource);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(id);
            hash = 31 * hash + java.util.Objects.hashCode(count);
            hash = 31 * hash + java.util.Objects.hashCode(resource);
            return hash;
        }

        @Override
        public String toString() {
            return "Samples[id=" + id + ", count=" + count + ", resource="
                + resource + "]";
        }
    }

    /**
     * A wrapping row or stack; ordinary elements may be composed alongside
     * filter controls.
     */
    class Box implements FilterControl {
        private final String id;
        private final boolean horizontal;
        private final List<Element> children;

        public Box(String id, boolean horizontal, List<Element> children) {
            children = List.copyOf(children);

            this.id = id;
            this.horizontal = horizontal;
            this.children = children;
        }

        public Box(String id, boolean horizontal,
            Collection<? extends Element> children) {
            this(id, horizontal, List.copyOf(children));
        }

        public int[] measure(TextMeasure text) {
            return measure(text, 18);
        }

        public int[] measure(TextMeasure text, int width) {
            return new int[]{width,
                FlowLayout.of(children, horizontal, width, 2, text).height()};
        }

        public Map<Element, Rect> childLayout(Rect bounds, TextMeasure text) {
            return FlowLayout.of(children, horizontal, bounds.width(), 2, text)
                .children();
        }

        public String id() {
            return id;
        }

        public boolean horizontal() {
            return horizontal;
        }

        public List<Element> children() {
            return children;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object)
                return true;
            if (object == null || getClass() != object.getClass())
                return false;
            var other = (Box) object;
            return java.util.Objects.equals(id, other.id)
                && java.util.Objects.equals(horizontal, other.horizontal)
                && java.util.Objects.equals(children, other.children);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(id);
            hash = 31 * hash + java.util.Objects.hashCode(horizontal);
            hash = 31 * hash + java.util.Objects.hashCode(children);
            return hash;
        }

        @Override
        public String toString() {
            return "Box[id=" + id + ", horizontal=" + horizontal + ", children="
                + children + "]";
        }
    }

    static List<FilterControl> flatten(FilterControl root) {
        var result = new ArrayList<FilterControl>();
        collect(root, result, new HashSet<>(),
            Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        return List.copyOf(result);
    }

    private static void collect(Element element, List<FilterControl> result,
        Set<String> ids, Set<Element> seen, int depth) {
        if (depth > 8 || seen.size() >= 128 || !seen.add(element))
            throw new IllegalArgumentException("Editor too large or cyclic");
        if (element instanceof FilterControl control) {
            if (!control.id().matches("[a-z0-9_.-]{1,64}")
                || !ids.add(control.id()))
                throw new IllegalArgumentException(
                    "Invalid/duplicate editor control: " + control.id());
            result.add(control);
        }
        for (var child : element.children())
            collect(child, result, ids, seen, depth + 1);
    }
}
