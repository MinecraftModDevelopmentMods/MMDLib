package zone.moddev.mc.lib.gui.client;

/** Read-only presentation of an inline side configuration control. */
public interface SideConfigPresentation {
    int selection();

    default boolean faceAvailable(int group, int face) {
        return true;
    }

    int sideMode(int group, int face);
}
