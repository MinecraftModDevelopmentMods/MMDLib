package zone.moddev.mc.mmdlib.asm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import zone.moddev.mc.mmdlib.util.Platform;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.Name;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.SortingIndex;

@Name("MMDLib")
@SortingIndex(1001)
/**
 * Historical horse-armor patch loader. The release manifest loads the namespace
 * bootstrap instead, so these gameplay transformers remain inactive.
 */
public class ASMPlugin implements IFMLLoadingPlugin {

	static List<ITransformer> transformerList = new ArrayList<>();

	private static final boolean NEEDS_HORSE_ARMOR_PATCH = ForgeVersion.getMajorVersion() < 14 || ForgeVersion.getMinorVersion() < 23 || ForgeVersion.getRevisionVersion() < 1 || ForgeVersion.getBuildVersion() < 2592;

	public ASMPlugin() {
		if (NEEDS_HORSE_ARMOR_PATCH) {
			transformerList.add(new EntityHorseTransformer());
			transformerList.add(new HorseArmorTypeTransformer());
		}
		// Keep the loader guard in getASMTransformerClass aligned with the transformers added here.
	}

	@Override
	public String[] getASMTransformerClass() {
		if (!NEEDS_HORSE_ARMOR_PATCH) {
			return null;
		}
		return new String[] { ASMTransformer.class.getName() };
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Nullable
	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public void injectData(final Map<String, Object> data) {
		Platform.setDev((Boolean) data.get("runtimeDeobfuscationEnabled"));
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}
}
