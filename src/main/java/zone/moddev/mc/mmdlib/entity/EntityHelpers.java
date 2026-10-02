package zone.moddev.mc.mmdlib.entity;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class EntityHelpers {

	private EntityHelpers() {

	}

	public static NBTTagCompound writeToNBTItemStack(final NBTTagCompound compound,
			final ItemStack itemStack) {
		final NBTTagCompound itemStackCompound = new NBTTagCompound();
		itemStack.writeToNBT(itemStackCompound);
		compound.setTag("itemstack", itemStackCompound);
		return compound;
	}

	public static ItemStack readFromNBTItemStack(final NBTTagCompound compound) {
		return new ItemStack(compound.getCompoundTag("itemstack"));
	}

}
