package mmdlib.namespace.fixture;

import com.google.gson.JsonObject;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.data.NameToken;
import com.mcmoddev.lib.data.Names;
import com.mcmoddev.lib.integration.IIntegration;
import com.mcmoddev.lib.integration.IIntegrationEvent;
import com.mcmoddev.lib.integration.IntegrationInitEvent;
import com.mcmoddev.lib.integration.IntegrationManager;
import com.mcmoddev.lib.integration.MMDPlugin;
import java.util.Arrays;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.IConditionFactory;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Compiled against the published old library, never against canonical implementation classes. */
@Mod(modid = "mmdlib_legacy_probe", name = "Legacy MMDLib Consumer Probe", version = "1",
        dependencies = "required-after:mmdlib", acceptedMinecraftVersions = "[1.12.2]")
public final class LegacyConsumerProbe {
    private static int pluginInitializations;
    private static int oldEventCallbacks;
    private static int lambdaCalls;

    public static void verify() throws Exception {
        String canonical = "zone.moddev.mc.mmdlib.";
        require(MMDLib.getVersion().equals(System.getProperty("mmdlib.probe.version")), "Legacy version method failed");
        require(MMDLib.instance.getClass() == Class.forName(canonical + "MMDLib"), "Split mod instance");
        require(MMDLib.proxy.getClass().getName().startsWith(canonical), "Legacy proxy field did not link");
        require(Names.INGOT == Class.forName(canonical + "data.Names").getField("INGOT").get(null), "Split enum identity");
        require(IntegrationManager.INSTANCE == Class.forName(canonical + "integration.IntegrationManager")
                .getField("INSTANCE").get(null), "Split integration singleton");
        NameToken oldToken = new NameToken(Names.INGOT);
        Object newToken = Class.forName(canonical + "data.NameToken")
                .getConstructor(Class.forName(canonical + "data.Names")).newInstance(Names.INGOT);
        require(oldToken.equals(newToken), "Legacy constructor, parameter descriptor or equality failed");
        require(new LegacyEvent() instanceof IIntegrationEvent, "Legacy subclass/interface failed");
        require(LegacyEvent.class.getSuperclass() == Class.forName(canonical + "integration.IntegrationInitEvent"),
                "Legacy superclass was not relinked");
        IIntegration lambda = () -> lambdaCalls++;
        lambda.init();
        require(lambdaCalls > 0 && Arrays.asList(lambda.getClass().getInterfaces())
                .contains(Class.forName(canonical + "integration.IIntegration")), "Legacy lambda linkage failed");
        require(pluginInitializations == 1, "Old MMDPlugin annotation was missed or initialized twice");
        require(oldEventCallbacks == 1, "Legacy event subscriber did not receive the canonical event exactly once");
        // This name arrives externally, bypassing bytecode literal remapping.
        IConditionFactory factory = (IConditionFactory) Class.forName(System.getProperty("mmdlib.probe.legacyFactory"))
                .newInstance();
        IConditionFactory modern = (IConditionFactory) Class.forName(canonical + "recipe.conditions.HammerEnabled").newInstance();
        require(factory.parse(new JsonContext("mmdlib_legacy_probe"), new JsonObject()).getAsBoolean()
                == modern.parse(new JsonContext("mmdlib_legacy_probe"), new JsonObject()).getAsBoolean(),
                "Reflective recipe condition alias diverged");
    }

    public static final class LegacyEvent extends IntegrationInitEvent { }

    @MMDPlugin(addonId = "mmdlib_legacy_probe", pluginId = "mmdlib")
    public static final class Plugin implements IIntegration {
        @Override public void init() {
            pluginInitializations++;
            MinecraftForge.EVENT_BUS.register(this);
        }
        @SubscribeEvent public void onOldEvent(final IntegrationInitEvent event) { oldEventCallbacks++; }
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
