package com.mcmoddev.lib.container.gui;

import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import com.mcmoddev.lib.container.IWidgetContainer;
import com.mcmoddev.lib.container.MMDContainer;
import com.mcmoddev.lib.container.widget.IProxiedWidget;
import com.mcmoddev.lib.container.widget.IWidget;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Holds all the information about a MMD GUI.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class GuiContext {
    private final EntityPlayer player;
    private final MMDContainer container;
    private final IWidgetContainer provider;

    @SideOnly(Side.CLIENT)
    private MMDGuiContainer guiContainer = null; // too lazy to find a way to make this final

    /**
     * Initializes a new instance of GuiContext.
     * @param player The player this gui belongs to.
     * @param container The container of this gui.
     * @param holder The main widget provider.
     */
    public GuiContext(final EntityPlayer player, final MMDContainer container, final IWidgetContainer holder) {
        this.player = player;
        this.container = container;
        this.provider = holder;
    }

    /**
     * Initializes a new instance of GuiContext.
     * @param player The player this gui belongs to.
     * @param container The container of this gui.
     * @param guiContainer The gui container of this gui.
     * @param holder The main widget provider.
     * @implNote Only exists on client side.
     */
    @SideOnly(Side.CLIENT)
    public GuiContext(final EntityPlayer player, final MMDContainer container, final MMDGuiContainer guiContainer, final IWidgetContainer holder) {
        this(player, container, holder);
        this.guiContainer = guiContainer;
    }

    /**
     * Gets the player that owns this gui.
     * @return The player that owns this gui.
     */
    public EntityPlayer getPlayer() {
        return this.player;
    }

    /**
     * Gets this gui's {@link MMDContainer container}.
     * @return This gui's {@link MMDContainer container}.
     */
    public MMDContainer getContainer() {
        return this.container;
    }

    /**
     * Gets this gui's {@link MMDGuiContainer gui container}.
     * @return This gui's {@link MMDGuiContainer gui container}.
     */
    @Nullable
    @SideOnly(Side.CLIENT)
    public MMDGuiContainer getGuiContainer() {
        return this.guiContainer;
    }

    /**
     * Gets the main widget provider of this gui.
     * @return The main widget provider of this gui.
     */
    public IWidgetContainer getWidgetProvider() {
        return this.provider;
    }

    /**
     * Gets all the widgets contained by this gui.
     * @return A list of all the widgets contained by this gui.
     */
    public List<IWidget> getWidgets() {
        return this.container.getWidgets()
            .stream()
            .map(w -> (w instanceof IProxiedWidget) ? ((IProxiedWidget)w).getOriginalWidget() : w)
            .collect(Collectors.toList());
    }

    /**
     * Finds a widget with a specified key.
     * @param key Key of the widget to find.
     * @return The widget that matched the specified key. Or null if such a widget was not found.
     */
    @Nullable
    public IWidget findWidgetByKey(final String key) {
        return this.findWidgetByKey(key, true);
    }

    /**
     * Finds a widget with a specified key.
     * @param key Key of the widget to find.
     * @param handleProxies True if {@link IProxiedWidget proxied widgets} should return the original widgets or not.
     *                      False otherwise.
     * @return The widget that matched the specified key. Or null if such a widget was not found.
     */
    @Nullable
    public IWidget findWidgetByKey(final String key, final boolean handleProxies) {
        final IWidget widget = this.container.findWidgetByKey(key);
        if (handleProxies && (widget instanceof IProxiedWidget)) {
            return ((IProxiedWidget) widget).getOriginalWidget();
        } else {
            return widget;
        }
    }
}
