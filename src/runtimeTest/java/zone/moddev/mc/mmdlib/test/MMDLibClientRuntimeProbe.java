package zone.moddev.mc.mmdlib.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Creates a disposable integrated world and exits after client acceptance. */
@Mod(modid = "mmdlib_client_probe", name = "MMDLib Client Probe", version = "1",
        dependencies = "required-after:mmdlib", acceptedMinecraftVersions = "[1.12.2]", clientSideOnly = true)
public final class MMDLibClientRuntimeProbe {
    private boolean starting;
    private boolean complete;

    @Mod.EventHandler
    public void init(final FMLInitializationEvent event) {
        if (Boolean.getBoolean("mmdlib.probe.client")) FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void tick(final TickEvent.ClientTickEvent event) throws Exception {
        if (event.phase != TickEvent.Phase.END || complete) return;
        Minecraft client = Minecraft.getMinecraft();
        if (!starting && client.currentScreen instanceof GuiMainMenu) {
            starting = true;
            client.launchIntegratedServer("mmdlib-smoke", "MMDLib acceptance",
                    new WorldSettings(112021L, GameType.CREATIVE, false, false, WorldType.FLAT));
        }
        if (starting && client.world != null && client.player != null) {
            complete = true;
            MMDLibRuntimeProbe.verify();
            client.shutdown();
        }
    }
}
