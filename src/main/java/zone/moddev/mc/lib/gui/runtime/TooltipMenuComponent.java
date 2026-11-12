package zone.moddev.mc.lib.gui.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.runtime.component.ComponentSnapshot;
import zone.moddev.mc.lib.gui.runtime.component.ComponentSync;
import zone.moddev.mc.lib.gui.runtime.component.MenuComponentRegistry;
import zone.moddev.mc.lib.gui.runtime.component.MenuSnapshot;
import zone.moddev.mc.lib.gui.runtime.component.ServerTooltips;

/**
 * Per-viewer transport for element-owned server tooltip rows; no presentation
 * policy.
 */
public final class TooltipMenuComponent
    implements MenuSnapshot, ServerTooltips {
    public static final int MAX_ELEMENTS = 32767, MAX_ROWS = 4096;
    public static final ComponentSync<Map<Integer, List<Component>>> ROWS = new ComponentSync<>(
        ResourceLocation.parse("mmdlib:tooltips"), 2,
        StreamCodec.of((b, values) -> {
            b.writeVarInt(values.size());
            values.forEach((index, rows) -> {
                b.writeVarInt(index);
                b.writeVarInt(rows.size());
                rows.forEach(
                    row -> ComponentSerialization.STREAM_CODEC.encode(b, row));
            });
        }, b -> {
            int count = b.readVarInt();
            if (count < 0 || count > MAX_ELEMENTS)
                throw new IllegalArgumentException("Too many tooltip entries");
            var result = new TreeMap<Integer, List<Component>>();
            for (int i = 0; i < count; i++) {
                int index = b.readVarInt(), size = b.readVarInt();
                if (index < 0 || index >= MAX_ELEMENTS || size < 0
                    || size > MAX_ROWS || result.containsKey(index))
                    throw new IllegalArgumentException("Invalid tooltip entry");
                var rows = new ArrayList<Component>();
                for (int n = 0; n < size; n++)
                    rows.add(ComponentSerialization.STREAM_CODEC.decode(b));
                result.put(index, List.copyOf(rows));
            }
            return Collections.unmodifiableMap(result);
        }), 65536, Map::of,
        values -> values.size() <= MAX_ELEMENTS && values.entrySet().stream()
            .allMatch(e -> e.getKey() >= 0 && e.getKey() < MAX_ELEMENTS
                && e.getValue().size() <= MAX_ROWS));

    private final MenuComponentRegistry.Context context;
    private List<Element> elements;
    private final Map<Element, Integer> indexes = new IdentityHashMap<>();

    private List<Element> elements() {
        if (elements == null) {
            var result = new ArrayList<Element>();
            for (var element : context.menu().elements)
                append(element, result);
            for (var component : context.menu().components().entries())
                for (var root : component.presentationRoots())
                    append(root, result);
            for (var entry : context.menu().nativeSlots().entries())
                if (entry.element() != null)
                    append(entry.element(), result);
            elements = List.copyOf(result);
        }
        return elements;
    }

    private void append(Element element, List<Element> result) {
        if (indexes.containsKey(element))
            return;
        if (result.size() >= MAX_ELEMENTS)
            throw new IllegalArgumentException("Too many tooltip elements");
        indexes.put(element, result.size());
        result.add(element);
        for (var child : element.children())
            append(child, result);
    }

    public TooltipMenuComponent(MenuComponentRegistry.Context context) {
        this.context = context;
    }

    /**
     * Samples each element independently; parents may explicitly use their rows
     * in child hooks.
     */
    public Map<Integer, List<Component>> sample() {
        if (context.client() || context.host() == null)
            return Map.of();
        var rows = new TreeMap<Integer, List<Component>>();
        var elements = elements();
        for (int i = 0; i < elements.size(); i++) {
            var lines = elements.get(i).tooltip(context.host(),
                context.menu().getCarried().copy());
            if (!lines.isEmpty())
                rows.put(i, List.copyOf(lines));
        }
        return Collections.unmodifiableMap(rows);
    }

    public void snapshot(ComponentSnapshot values) {
        values.put(ROWS, sample());
    }

    public List<Component> lines(Element element) {
        elements();
        return context.menu().components().state(this, ROWS)
            .getOrDefault(indexes.getOrDefault(element, -1), List.of()).stream()
            .map(Component::copy).map(Component.class::cast).toList();
    }
}
