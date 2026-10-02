package zone.moddev.mc.mmdlib.events;

import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;
import net.minecraftforge.registries.IForgeRegistry;

import zone.moddev.mc.mmdlib.properties.MMDMaterialPropertyBase;
import zone.moddev.mc.mmdlib.properties.MaterialProperties;

public class MMDLibRegisterMaterialProperties extends Event implements IContextSetter {
	private final IForgeRegistry<MMDMaterialPropertyBase> reg;

	public MMDLibRegisterMaterialProperties() {
		this.reg = MaterialProperties.get();
	}

	public IForgeRegistry<MMDMaterialPropertyBase> getRegistry() {
		return this.reg;
	}
}
