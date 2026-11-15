package zone.moddev.mc.lib.gui.client;

/**
 * Behavior service for opening a declared drawer without exposing the screen.
 */
@FunctionalInterface
public interface DrawerOpener {
    boolean open(String id);
}
