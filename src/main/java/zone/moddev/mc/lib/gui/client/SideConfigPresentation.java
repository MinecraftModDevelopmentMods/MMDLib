package zone.moddev.mc.lib.gui.client;

/** Read-only presentation of an inline side configuration control. */
public interface SideConfigPresentation {
    int selection();

    int sideMode(int group, int face);
}
