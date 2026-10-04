package zone.moddev.mc.mmdlib.util;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import zone.moddev.mc.mmdlib.MMDLib;
import zone.moddev.mc.mmdlib.container.IWidgetContainer;
import zone.moddev.mc.mmdlib.container.MMDContainer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import mcp.MethodsReturnNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class LoggingUtil {
    private static final String LOGGING_ID = "__logging_id__";
    private static long currentId = 0;

    private LoggingUtil() { }

    public static boolean isExtraLoggingEnabled() {
        // Extra NBT diagnostics are currently always enabled.
        return true;
    }

    public static void logNbtMessage(final String reason, @Nullable final NBTBase nbt) {
        LoggingUtil.logNbtMessage(null, reason, nbt);
    }

    public static void logNbtMessage(@Nullable final Object sender, final String reason, @Nullable final NBTBase nbt) {
        if (!LoggingUtil.isExtraLoggingEnabled()) {
            return;
        }

        final StringBuilder log = new StringBuilder();

        if (nbt instanceof NBTTagCompound) {
            final NBTTagCompound compound = (NBTTagCompound)nbt;
            final long id;
            if (compound.hasKey(LOGGING_ID, Constants.NBT.TAG_LONG)) {
                id = compound.getLong(LOGGING_ID);
            } else if (!nbt.isEmpty()) {
                id = LoggingUtil.currentId++;
                compound.setLong(LOGGING_ID, id);
            } else {
                id = -1;
            }
            log.append("#");
            log.append(id);
            log.append(" ");
        }

        if (sender instanceof TileEntity) {
            LoggingUtil.appendTileEntityInfo(log, (TileEntity)sender);
        } else if (sender instanceof MMDContainer) {
            final IWidgetContainer provider = ((MMDContainer) sender).getProvider();
            if (provider instanceof TileEntity) {
                LoggingUtil.appendTileEntityInfo(log, (TileEntity)provider);
            }
        } else if (sender != null) {
            log.append("[sender: ");
            log.append(sender);
            log.append("] ");
        }

        if (!reason.isEmpty()) {
            log.append(reason);
            log.append(": ");
        }

        if (nbt != null) {
            log.append(nbt);
        }
        else {
            log.append("{NO TAG}");
        }

        MMDLib.logger.info(log.toString());
    }

    private static void appendTileEntityInfo(final StringBuilder log, final TileEntity sender) {
        log.append("[");
        final World world = sender.getWorld();
        //noinspection ConstantConditions // Tile coordinates may be unavailable during construction.
        if (world != null) {
            log.append("dim: ");
            log.append(world.provider.getDimension());
            log.append(", ");
        } else {
            log.append("no dim info, ");
        }
        final BlockPos pos = sender.getPos();
        //noinspection ConstantConditions // Tile coordinates may be unavailable during construction.
        if (pos != null) {
            log.append(pos.toString());
        } else {
            log.append("no pos info");
        }

        log.append(", ");
        log.append(sender.getClass().getSimpleName());
        log.append("] ");
    }
}
