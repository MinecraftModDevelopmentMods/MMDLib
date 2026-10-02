package zone.moddev.mc.mmdlib.feature;

import javax.annotation.Nullable;
import zone.moddev.mc.mmdlib.capability.ICapabilitiesContainer;
import zone.moddev.mc.mmdlib.container.gui.IWidgetGui;
import zone.moddev.mc.mmdlib.container.widget.IWidget;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * A feature is basically a way to group serialization, capabilities, {@link IWidget widgets} and {@link IWidgetGui widget guis} together.
 */
public interface IFeature extends INBTSerializable<NBTTagCompound> {
    /**
     * Gets the unique key of this feature.
     * @return The unique key of this feature.
     */
    String getKey();

    /**
     * Gets the {@link IFeatureHolder} that contains this feature.
     * @return The {@link IFeatureHolder} that contains this feature.
     */
    @Nullable
    IFeatureHolder getHolder();

    /**
     * Sets the {@link IFeatureHolder} that contains this feature.
     * @param holder The {@link IFeatureHolder} that contains this feature.
     */
    void setHolder(@Nullable IFeatureHolder holder);

    /**
     * In this method the feature should specify all the capabilities it exports or might export in the future. <br>
     * This method is only called once after a feature is added to a {@link IFeatureHolder}.
     * But only if the holder itself supports capabilities.
     * @param container The capabilities container that stores capability for this feature's {@link IFeatureHolder}.
     */
    default void initCapabilities(final ICapabilitiesContainer container) { }
}
