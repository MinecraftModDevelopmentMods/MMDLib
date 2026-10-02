package zone.moddev.mc.mmdlib.integration;

import zone.moddev.mc.mmdlib.init.Materials;
import zone.moddev.mc.mmdlib.material.MMDMaterial;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Interface that all events of the MMDLib Mod-Integration plugin system present.
 * 
 * @author D. Hazelton
 *
 */
public interface IIntegrationEvent {

	default List<MMDMaterial> materials() {
		return Collections.<MMDMaterial>unmodifiableList(
				Materials.getAllMaterials().stream().collect(Collectors.toList()));
	}
}
