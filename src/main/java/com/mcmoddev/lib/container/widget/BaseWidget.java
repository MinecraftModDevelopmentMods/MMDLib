package com.mcmoddev.lib.container.widget;

public abstract class BaseWidget implements IWidget {
    private final String key;
    private final boolean canBeDirty;
    private boolean dirty = false;

    protected BaseWidget(String key) {
        this(key, true);
    }

    protected BaseWidget(String key, boolean canBeDirty) {
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

    protected void setDirty() {
        this.dirty = true;
    }

    //#endregion
}
