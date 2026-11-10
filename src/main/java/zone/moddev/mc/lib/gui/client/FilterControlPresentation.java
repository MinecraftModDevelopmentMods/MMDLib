package zone.moddev.mc.lib.gui.client;

/** Read-only drawing of an editor's local text or compatibility-widget state. */
interface FilterControlPresentation {
    void render(ElementRenderers.Context context);
}
