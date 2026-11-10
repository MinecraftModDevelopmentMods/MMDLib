package zone.moddev.mc.lib.gui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A container supplying the named inventory for descendant slots. Nearest scope
 * wins.
 */
public interface InventoryScope extends Element {
    /** Stable storage name, shared by server and client definitions. */
    String inventory();

    /**
     * Nearest enclosing scope, through any ordinary layout wrappers; uses
     * element identity.
     */
    static Optional<InventoryScope> owner(Screen screen, Element element) {
        var path = new ArrayList<Element>();
        var roots = new ArrayList<Element>(screen.body());
        roots.addAll(screen.tabs());
        roots.addAll(screen.implicitElements());
        for (var root : roots) {
            if (!path(root, element, path))
                continue;
            for (int i = path.size() - 2; i >= 0; i--)
                if (path.get(i) instanceof InventoryScope scope)
                    return Optional.of(scope);
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** Unscoped slots keep the legacy combined inventory name. */
    static String name(Screen screen, Element element) {
        return owner(screen, element).map(InventoryScope::inventory)
            .orElse("items");
    }

    private static boolean path(Element current, Element target,
        List<Element> path) {
        path.add(current);
        if (current == target)
            return true;
        for (var child : current.children())
            if (path(child, target, path))
                return true;
        path.removeLast();
        return false;
    }
}
