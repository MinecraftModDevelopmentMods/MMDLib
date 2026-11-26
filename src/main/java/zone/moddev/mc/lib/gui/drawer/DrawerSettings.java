package zone.moddev.mc.lib.gui.drawer;

import zone.moddev.mc.lib.gui.model.Edge;

/** Optional overrides for a machine feature's drawer placement. */
public record DrawerSettings(boolean enabled, Edge edge,
    DrawerPlacement.Anchor anchor, Integer order) {
    public static final DrawerSettings DEFAULT = new DrawerSettings(true, null,
        null, null);
    public static final DrawerSettings HIDDEN = new DrawerSettings(false, null,
        null, null);

    DrawerDefinition apply(DrawerDefinition definition) {
        if (edge == null && anchor == null && order == null)
            return definition;
        var placement = definition.placement();
        return new DrawerDefinition(definition.type(), definition.id(),
            new DrawerPlacement(edge == null ? placement.edge() : edge,
                anchor == null ? placement.anchor() : anchor,
                order == null ? placement.order() : order, placement.width(),
                placement.height(), placement.overlap(), placement.gap()),
            definition.element(), definition.toolbarWidth(),
            definition.interactive());
    }
}
