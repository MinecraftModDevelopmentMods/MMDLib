package zone.moddev.mc.lib.gui.client;

import zone.moddev.mc.lib.gui.model.containers.NavigableElement;

/** Remembered selection lifecycle shared by built-in and mod navigation destinations. */
public class NavigableBehavior extends DefaultElementBehavior {
    private final NavigableElement destination;
    private boolean initialized;

    public NavigableBehavior(NavigableElement destination, ElementAccess access) {
        super(access);
        this.destination = destination;
    }

    public void initialized() {
        if (initialized)
            return;
        initialized = true;
        NavigationMemory.restore(access, destination);
    }

    public void actionResult(boolean accepted) {
        super.actionResult(accepted);
        NavigationMemory.remember(access, destination);
    }
}
