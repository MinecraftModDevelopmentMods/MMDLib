package zone.moddev.mc.mmdlib.container.widget;

import java.util.Collection;
import javax.annotation.Nullable;
import com.google.common.collect.Lists;
import zone.moddev.mc.mmdlib.container.IContainerSlot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Represents the smallest piece of a container.
 */
public interface IWidget {
    /**
     * @return The unique key used to identify this widget within the container.
     */
    String getKey();

    /**
     * Only need to override in case this widget provides Slots.
     * Implementation note: The position on the slots is not relevant here. They will be positioned by the corresponding Widget Gui.
     * Implementation note: The slots should be created on every call of this method and not be cached.
     * @return The list of slots exposed by this widget.
     */

    default Collection<IContainerSlot> getSlots() {
        return Lists.newArrayList();
    }

    /**
     * Provides a value telling the container if this widget needs to be updated on client thread.
     * Implementation note: Only called on server thread.
     * @return Returns {@code true} if this widget has information to be sent to client thread.
     */
    default boolean isDirty() { return false; }

    /**
     * Tells this widget that its provided information is ready to be sent to client thread and it should reset its dirty flag.
     */
    default void resetDirtyFlag() {}

    /**
     * Called on server thread to get update information to be sent to client.
     * Only called if {@link #isDirty} returns true.
     * Implementation note: Only called on server thread.
     * @return The compound containing information to be sent to client or null if no such information is required.
     */
    @Nullable
    default NBTTagCompound getUpdateCompound() { return null; }

    /**
     * Called on server thread with data received from client.
     * Implementation note: Only called on server thread.
     * @param tag The compound received from the client thread.
     */
    default void handleMessageFromClient(final NBTTagCompound tag) {}

    /**
     * Called on client thread to handle update data from server thread.
     * Implementation note: Only called on client thread.
     * @param tag The compound received from the server thread.
     */
    @SideOnly(Side.CLIENT)
    default void handleMessageFromServer(final NBTTagCompound tag) {}
}
