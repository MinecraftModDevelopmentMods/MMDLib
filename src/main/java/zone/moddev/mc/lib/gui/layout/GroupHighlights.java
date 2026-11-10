package zone.moddev.mc.lib.gui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.ElementGroup;
import zone.moddev.mc.lib.gui.model.Screen;

/**
 * Shared highlight selection. Elements choose groups; selected containers may represent
 * descendants.
 */
public final class GroupHighlights {
    private GroupHighlights() {
    }

    private record Claim(Element element, String group) {
    }

    public static Map<String, List<Element>> of(Screen screen, List<ElementGroup> groups) {
        return of(screen, groups, element -> true);
    }

    /**
     * Eligibility limits targets, not traversal. An ineligible container cannot suppress
     * descendants.
     */
    public static Map<String, List<Element>> of(
        Screen screen, List<ElementGroup> groups, Predicate<Element> eligible) {
        groups = List.copyOf(groups);
        var result = new LinkedHashMap<String, List<Element>>();
        for (var group : groups)
            result.putIfAbsent(group.id(), new ArrayList<>());
        var roots = new ArrayList<Element>(screen.body());
        roots.addAll(screen.tabs());
        roots.addAll(screen.implicitElements());
        for (var root : roots)
            collect(root, screen, groups, eligible, new ArrayList<>(), result);
        result.replaceAll((id, targets) -> List.copyOf(targets));
        return Collections.unmodifiableMap(result);
    }

    private static void collect(
        Element element,
        Screen screen,
        List<ElementGroup> groups,
        Predicate<Element> eligible,
        List<Claim> ancestors,
        Map<String, List<Element>> result) {
        int count = ancestors.size();
        if (eligible.test(element)) {
            for (var group : new LinkedHashSet<>(element.highlightedGroups(screen, groups))) {
                if (!result.containsKey(group))
                    continue;
                boolean represented = ancestors.stream()
                    .anyMatch(
                        claim -> claim.group().equals(group)
                            && claim
                                .element()
                                .replacesChildHighlight(screen, element, group, groups));
                if (!represented) {
                    result.get(group).add(element);
                    ancestors.add(new Claim(element, group));
                }
            }
        }
        for (var child : element.children())
            collect(child, screen, groups, eligible, ancestors, result);
        ancestors.subList(count, ancestors.size()).clear();
    }
}
