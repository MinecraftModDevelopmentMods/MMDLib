package zone.moddev.mc.mmdlib.container.gui;

import java.util.List;
import javax.annotation.Nullable;
import zone.moddev.mc.mmdlib.container.gui.util.Padding;
import zone.moddev.mc.mmdlib.container.gui.util.Size2D;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Represents one of the parts that makes a {@link MMDGuiContainer}.
 */
public interface IWidgetGui {
	/**
	 * Gets the layer this widget renders in. This is just a helper for single layer widgets.
	 * One should only call {@link #rendersInLayer(GuiPieceLayer)}.
	 * @return The layer this widget renders in.
	 */
	default GuiPieceLayer getLayer() {
		return GuiPieceLayer.BACKGROUND;
	}

	/**
	 * Gets the list of layers this widget renders in.
	 * Mainly used for easier implementation of {@link #rendersInLayer(GuiPieceLayer)}
	 * Try not to call directly.
	 * @return The list of layers this widget renders in.
	 */
	default GuiPieceLayer[] getLayers() {
		return new GuiPieceLayer[] { this.getLayer() };
	}

	/**
	 * Tests if this widget is rendering in a certain layer or not.
	 * @param layer The layer one is interested in.
	 * @return True if this widget renders in the specified layer. False otherwise.
	 */
	default boolean rendersInLayer(final GuiPieceLayer layer) {
		for (final GuiPieceLayer pieceLayer:this.getLayers()) {
			if (pieceLayer == layer) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Gets the runtime computed size of this widget.
	 * @return The runtime computed size of this widget.
	 */
	Size2D getSize();

	/**
	 * Gets the padding that should be around this widget.
	 * The containing {@link IWidgetLayout layout} decides whether to apply it.
	 * @return The padding that should be around this widget.
	 */
	Padding getPadding();

	/**
	 * Sets the padding that should be around this widget.
	 * @param value The padding to be set.
	 * @return A reference to self.
	 */
	IWidgetGui setPadding(Padding value);

	/**
	 * Specifies if this widget captures mouse click for future mouse click move events.
	 * Implementation note: Mouse capture and drag dispatch are not implemented by the container.
	 * @return True if this widget captures mouse. False otherwise.
	 */
	default boolean capturesMouseOnClick() { return false; }

	/**
	 * Called when a mouse click happens within the bounds of this widget.
	 * @param container The container containing this widget.
	 * @param mouseX X coordinate of mouse click in local widget coordinates.
	 * @param mouseY Y coordinate of mouse click in local widget coordinates.
	 * @param mouseButton Mouse button being clicked.
	 * @return True if this widget reacted in any way to the mouse click. False otherwise.
	 */
	default boolean mouseClicked(final MMDGuiContainer container, final int mouseX, final int mouseY, final int mouseButton) { return false; }

	/**
	 * Called when a mouse drag occurs within the bounds of this widget.
	 * Implementation note: Mouse capture and drag dispatch are not implemented by the container.
	 * @param container The container containing this widget.
	 * @param mouseX X coordinate of mouse click in local widget coordinates.
	 * @param mouseY Y coordinate of mouse click in local widget coordinates.
	 * @param mouseButton Mouse button being clicked.
	 * @return True if this widget reacted in any way to the mouse click move. False otherwise.
	 */
	default boolean mouseClickMove(final MMDGuiContainer container, final int mouseX, final int mouseY, final int mouseButton) { return false; }

	/**
	 * Called when a mouse button release happens within the bounds of this widget.
	 * @param container The container containing this widget.
	 * @param mouseX X coordinate of the mouse cursor in local widget coordinates.
	 * @param mouseY Y coordinate of the mouse cursor in local widget coordinates.
	 * @param state The mouse button that was released.
	 * @return True if this widget reacted in any way to the mouse release event. False otherwise.
	 */
	default boolean mouseReleased(final MMDGuiContainer container, final int mouseX, final int mouseY, final int state) { return false; }

	/**
	 * Draws the background layer of this widget.
	 * Called only if a call to {@link #rendersInLayer(GuiPieceLayer) rendersInLayer(BACKGROUND)} returns true.
	 * Implementation note: Widgets should always render at 0,0 and not care about actual position.
	 *           GL transforms are applied before this call is made.
	 * @param container The container to render on.
	 * @param partialTicks Fraction of the current tick elapsed before this frame.
	 * @param mouseX X coordinate of mouse cursor in local widget coordinates.
	 * @param mouseY Y coordinate of mouse cursor in local widget coordinates.
	 */
	default void drawBackgroundLayer(final MMDGuiContainer container, final float partialTicks, final int mouseX, final int mouseY) {
		// some widgets might not have a background layer
	}

	/**
	 * Draws the middle layer of this widget.
	 * Called only if a call to {@link #rendersInLayer(GuiPieceLayer) rendersInLayer(MIDDLE)} returns true.
	 * Implementation note: Widgets should always render at 0,0 and not care about actual position.
	 *           GL transforms are applied before this call is made.
	 * @param container The container to render on.
	 * @param partialTicks Fraction of the current tick elapsed before this frame.
	 * @param mouseX X coordinate of mouse cursor in local widget coordinates.
	 * @param mouseY Y coordinate of mouse cursor in local widget coordinates.
	 */
	default void drawMiddleLayer(final MMDGuiContainer container, final float partialTicks, final int mouseX, final int mouseY) {
		// some widgets might not have a middle layer
	}

	/**
	 * Draws the foreground layer of this widget.
	 * Called only if a call to {@link #rendersInLayer(GuiPieceLayer) rendersInLayer(FOREGROUND)} returns true.
	 * Implementation note: Widgets should always render at 0,0 and not care about actual position.
	 *           GL transforms are applied before this call is made.
	 * @param container The container to render on.
	 * @param mouseX X coordinate of mouse cursor in local widget coordinates.
	 * @param mouseY Y coordinate of mouse cursor in local widget coordinates.
	 */
	default void drawForegroundLayer(final MMDGuiContainer container, final int mouseX, final int mouseY) {
		// some widgets might not have a foreground layer
	}

	/**
	 * Draws the top layer of this widget.
	 * Called only if a call to {@link #rendersInLayer(GuiPieceLayer) rendersInLayer(TOP)} returns true.
	 * Implementation note: Widgets should always render at 0,0 and not care about actual position.
	 *           GL transforms are applied before this call is made.
	 * @param container The container to render on.
	 * @param mouseX X coordinate of mouse cursor in local widget coordinates.
	 * @param mouseY Y coordinate of mouse cursor in local widget coordinates.
	 */
	default void drawForegroundTopLayer(final MMDGuiContainer container, final int mouseX, final int mouseY) {
		// some widgets might not have a foreground (top) layer
	}

	/**
	 * Allows the tooltip to provide tooltip lines. Called only while the mouse is over the widget.
	 * @param lines The list of strings this widget's tooltip lines should be added to.
	 */
	default void getTooltip(final List<String> lines) {
		// might be no tooltip
	}

	/**
	 * Returns a flag saying if this widget is visible or not.
	 * @return True if the widget is visible. False otherwise.
	 */
	default boolean isVisible() { return true; }

	/**
	 * Sets if this widget is visible or not.
	 * @param isVisible True if this widget should be visible. False otherwise.
	 * @return The actual value of {@link #isVisible()} after this call. Some widgets might just choose to ignore it.
	 */
	boolean setVisibility(boolean isVisible);

	/**
	 * Returns the layout that is the direct parent of this widget.
	 * @return The layout that is the direct parent of this widget.
	 */
	@Nullable
	IWidgetLayout getParentLayout();

	/**
	 * Sets the layout that is the direct parent of this widget.
	 * @param layout The layout that is the direct parent of this widget.
	 * @return A reference to self.
	 */
	IWidgetGui setParentLayout(@Nullable IWidgetLayout layout);

	/**
	 * Return the render offset relative to the base Container.
	 * @return The render offset relative to the base Container.
	 */
	default Size2D getRenderOffset() {
		int left = 0;
		int top = 0;

		final IWidgetLayout layout = this.getParentLayout();
		if (layout != null) {
			final Size2D offset = layout.getRenderOffset();
			final Size2D position = layout.getChildPosition(this);
			left = offset.width + position.width;
			top = offset.height + position.height;
		}

		return new Size2D(left, top);
	}

	/**
	 * Initializes this widget gui. Called right after it is added to a {@link MMDGuiContainer}.
	 * @param context The gui context this widget lives in.
	 */
	default void init(final GuiContext context) {

	}

	/**
	 * Called after this widget is added to a {@link MMDGuiContainer} and the initial layout is done.
	 * @param context The gui context this widget lives in.
	 */
	default void postInit(final GuiContext context) {

	}

	/**
	 * Called on every {@link MMDGuiContainer#updateScreen}.
	 * @param context The gui context this widget lives in.
	 */
	default void tick(final GuiContext context) {

	}
}
