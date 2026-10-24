package zone.moddev.mc.lib.common.runtime;

import zone.moddev.mc.lib.common.types.MMDMaterial;

/** Runtime items expose the same immutable material definition used during planning. */
public interface MaterialBacked {
    MMDMaterial material();
}
