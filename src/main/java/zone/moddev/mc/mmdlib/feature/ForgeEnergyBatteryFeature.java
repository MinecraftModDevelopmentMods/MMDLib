package zone.moddev.mc.mmdlib.feature;

import java.util.List;
import javax.annotation.Nullable;
import zone.moddev.mc.mmdlib.container.IWidgetContainer;
import zone.moddev.mc.mmdlib.container.gui.BaseWidgetGui;
import zone.moddev.mc.mmdlib.container.gui.GuiContext;
import zone.moddev.mc.mmdlib.container.gui.GuiPieceLayer;
import zone.moddev.mc.mmdlib.container.gui.GuiSprites;
import zone.moddev.mc.mmdlib.container.gui.IGuiSprite;
import zone.moddev.mc.mmdlib.container.gui.IWidgetGui;
import zone.moddev.mc.mmdlib.container.gui.MMDGuiContainer;
import zone.moddev.mc.mmdlib.container.gui.SpriteBackgroundGui;
import zone.moddev.mc.mmdlib.container.gui.layout.CanvasLayout;
import zone.moddev.mc.mmdlib.container.gui.util.Size2D;
import zone.moddev.mc.mmdlib.energy.ForgeEnergyStorage;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.Constants;

public class ForgeEnergyBatteryFeature extends BaseFeature implements ICapabilityProvider, IClientFeature, IWidgetContainer {
    public static final int DEFAULT_INPUT_OUTPUT_RATE = 120;

    private final ForgeEnergyStorage battery;

    public ForgeEnergyBatteryFeature(final String key, final int capacity) {
        this(key, 0, capacity);
    }

    public ForgeEnergyBatteryFeature(final String key, final int initial, final int capacity) {
        this(key, initial, capacity, DEFAULT_INPUT_OUTPUT_RATE, DEFAULT_INPUT_OUTPUT_RATE);
    }

    public ForgeEnergyBatteryFeature(final String key, final int initial, final int capacity, final int inputRate, final int outputRate) {
        super(key);

        this.battery = new ForgeEnergyStorage(initial, capacity) {
            @Override
            protected void onChanged() {
                super.onChanged();

                // Mark GUI energy data for synchronization when the stored charge changes.
                ForgeEnergyBatteryFeature.this.setDirty(FeatureDirtyLevel.GUI);
            }
        };
        this.battery.setInputRate(inputRate);
        this.battery.setoutputRate(outputRate);
    }

    public ForgeEnergyStorage getEnergyStorage() {
        return this.battery;
    }

    @Override
    protected void writeToNBT(final NBTTagCompound tag) {
        tag.setTag("battery", this.battery.serializeNBT());
    }

    @Override
    public void deserializeNBT(final NBTTagCompound nbt) {
        if (nbt.hasKey("battery", Constants.NBT.TAG_COMPOUND)) {
            this.battery.deserializeNBT(nbt.getCompoundTag("battery"));
        }
//        else {

    }

    @Override
    public boolean hasCapability(final Capability<?> capability, @Nullable final EnumFacing facing) {
        return this.battery.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(final Capability<T> capability, @Nullable final EnumFacing facing) {
        return this.battery.getCapability(capability, facing);
    }

    @Override
    public IWidgetGui getRootWidgetGui(final GuiContext context) {
        final CanvasLayout layout = new CanvasLayout();

        layout.addPiece(new SpriteBackgroundGui(GuiSprites.TANK_CONTAINER), 0, 0);
        layout.addPiece(new SpriteBackgroundGui(GuiSprites.ENERGY_RF_EMPTY), 3, 3);
        layout.addPiece(new ForgeEnergyWidgetGui(GuiSprites.ENERGY_RF_FULL), 3, 3);

        return layout;
    }

    private class ForgeEnergyWidgetGui extends BaseWidgetGui {
        private final IGuiSprite sprite;

        ForgeEnergyWidgetGui(final IGuiSprite sprite) {
            super(sprite.getWidth(), sprite.getHeight());
            this.sprite = sprite;
        }

        @Override
        public GuiPieceLayer getLayer() {
            return GuiPieceLayer.MIDDLE;
        }

        @Override
        public void drawMiddleLayer(final MMDGuiContainer container, final float partialTicks, final int mouseX, final int mouseY) {
            final float percent = MathHelper.clamp(
                (float)ForgeEnergyBatteryFeature.this.battery.getStored() / (float)ForgeEnergyBatteryFeature.this.battery.getCapacity(),
                0f, 1f);
            if (percent > 0f) {
                final Size2D size = this.getSize();
                final int top = Math.round((1f - percent) * size.height);
                this.sprite.draw(container, 0, top, 0, top, size.width, size.height - top);
            }
        }

        @Override
        public void getTooltip(final List<String> lines) {
            if (ForgeEnergyBatteryFeature.this.battery.getStored() == 0) {
                lines.add(TextFormatting.DARK_GRAY + "EMPTY BATTERY");
            } else {
                lines.add(TextFormatting.AQUA + ForgeEnergyBatteryFeature.this.battery.getStoredValue().toString());
            }

            if (ForgeEnergyBatteryFeature.this.battery.getInputRate() == 0) {
                lines.add(TextFormatting.DARK_GRAY + "[no input allowed]");
            } else {
                lines.add(TextFormatting.WHITE + "Input Rate: " + TextFormatting.DARK_AQUA + ForgeEnergyBatteryFeature.this.battery.getInputRateValue().toString());
            }

            if (ForgeEnergyBatteryFeature.this.battery.getOutputRate() == 0) {
                lines.add(TextFormatting.DARK_GRAY + "[no output allowed]");
            } else {
                lines.add(TextFormatting.WHITE + "Output Rate: " + TextFormatting.DARK_AQUA + ForgeEnergyBatteryFeature.this.battery.getOutputRateValue().toString());
            }
        }
    }
}
