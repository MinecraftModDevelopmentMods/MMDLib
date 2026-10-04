package zone.moddev.mc.mmdlib.compat;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

/** Installs only API name translation, before dependent mod classes can link. */
@IFMLLoadingPlugin.Name("MMDLib legacy API compatibility")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions("zone.moddev.mc.mmdlib.compat.")
public final class LegacyNamespaceBootstrap implements IFMLLoadingPlugin {
    @Override public String[] getASMTransformerClass() {
        return new String[] { "zone.moddev.mc.mmdlib.compat.LegacyNamespaceTransformer" };
    }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(final Map<String, Object> data) { }
    @Override public String getAccessTransformerClass() { return null; }
}
