package zone.moddev.mc.mmdlib.common;

import net.minecraft.world.World;

public interface IModInstanceProvider {
    Object getModInstance(World world);
}
