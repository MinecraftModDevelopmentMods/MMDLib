package zone.moddev.mc.lib.gui.layout;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;

/** Orders opaque group IDs by their first associated element, then by declaration order. */
public final class GroupOrder {
    private GroupOrder() {
    }

    public static <K> List<K> of(
        Screen screen, List<K> groups, Function<Element, ? extends Collection<K>> associations) {
        var declared = new LinkedHashSet<>(groups);
        if (declared.size() != groups.size() || declared.contains(null))
            throw new IllegalArgumentException("Group IDs must be unique and non-null");
        var order = new LinkedHashSet<K>();
        for (var element : screen.elements())
            for (var id : associations.apply(element))
                if (declared.contains(id))
                    order.add(id);
        order.addAll(declared);
        return List.copyOf(order);
    }
}
