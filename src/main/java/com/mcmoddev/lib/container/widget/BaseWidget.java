package com.mcmoddev.lib.container.widget;

/**
 * Base implementation of an {@link IWidget}.
 */
public abstract class BaseWidget implements IWidget {
    private final String key;
    private final boolean canBeDirty;
    private boolean dirty = false;

    /**
     * Initializes a new instance of BaseWidget.
     * @param key The key that uniquely identified this widget.
     * @implNote This will create an widget that can never become dirty. Only use this for widgets that only container static data.
     */
    protected BaseWidget(final String key) {
        this(key, true);
    }

    /**
     * Initializes a new instance of BaseWidget.
     * @param key The key that uniquely identified this widget.
     * @param canBeDirty Specified if this widgets can get dirty or it only contains static data.
     */
    protected BaseWidget(final String key, final boolean canBeDirty) {
        this.key = key;
        this.canBeDirty = canBeDirty;
        if (this.canBeDirty) {
            // this is initially to make sure initial data goes to client side too
            this.setDirty();
        }
    }

    @Override
    public String getKey() {
        return this.key;
    }

    //#region DIRTY HANDLING

    @Override
    public boolean isDirty() {
        return this.canBeDirty && this.dirty;
    }

    @Override
    public void resetDirtyFlag() {
        this.dirty = false;
    }

    /**
     * Marks this widget as being dirty.
     */
    protected void setDirty() {
        this.dirty = true;
    }

    //#endregion
}
