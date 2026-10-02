package zone.moddev.mc.mmdlib.test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;

import zone.moddev.mc.mmdlib.MMDLib;
import zone.moddev.mc.mmdlib.init.Materials;
import zone.moddev.mc.mmdlib.integration.IntegrationManager;
import zone.moddev.mc.mmdlib.registry.CrusherRecipeRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

/** Test-only acceptance probe; excluded from all published archives. */
@Mod(modid = "mmdlib_runtime_probe", name = "MMDLib Runtime Probe", version = "1",
        dependencies = "required-after:mmdlib", acceptedMinecraftVersions = "[1.12.2]")
public final class MMDLibRuntimeProbe {
    @Mod.EventHandler
    public void started(final FMLServerStartedEvent event) throws Exception {
        if (Boolean.getBoolean("mmdlib.probe.client")) return;
        verify();
        FMLCommonHandler.instance().getMinecraftServerInstance().initiateShutdown();
    }

    public static void verify() throws Exception {
        Class.forName("mmdlib.namespace.fixture.LegacyConsumerProbe").getMethod("verify").invoke(null);
        require(Loader.isModLoaded("mmdlib"), "MMDLib did not load");
        require(System.getProperty("mmdlib.probe.version").equals(MMDLib.getVersion()), "Wrong MMDLib version");
        require(IntegrationManager.INSTANCE != null, "Integration manager unavailable");
        require(CrusherRecipeRegistry.class.getName().startsWith("zone.moddev.mc.mmdlib."), "Library API moved");
        for (String mod : System.getProperty("mmdlib.probe.mods", "").split(",")) {
            if (!mod.isEmpty()) require(Loader.isModLoaded(mod), "Expected optional mod missing: " + mod);
        }
        boolean baseMetals = Boolean.parseBoolean(System.getProperty("mmdlib.probe.basemetals", "false"));
        require(Loader.isModLoaded("basemetals") == baseMetals, "Unexpected Base Metals presence");
        if (baseMetals) {
            require(Materials.hasMaterial("copper"), "Base Metals materials were not registered");
            require(!Materials.getMaterialByName("copper").isEmpty(), "Copper material is empty");
        }
        Properties result = new Properties();
        result.setProperty("version", MMDLib.getVersion());
        result.setProperty("basemetals", Boolean.toString(baseMetals));
        result.setProperty("mods", System.getProperty("mmdlib.probe.mods", ""));
        result.setProperty("phase", System.getProperty("mmdlib.probe.phase", "fresh"));
        result.setProperty("legacy-api", "passed");
        result.setProperty("canonical-namespace", "zone.moddev.mc.mmdlib");
        File marker = new File(System.getProperty("mmdlib.probe.marker"));
        try (FileOutputStream output = new FileOutputStream(marker)) {
            result.store(output, "MMDLib packaged runtime acceptance");
        }
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
