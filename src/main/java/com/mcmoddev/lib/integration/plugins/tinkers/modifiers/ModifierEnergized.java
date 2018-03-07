package com.mcmoddev.lib.integration.plugins.tinkers.modifiers;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.energy.ForgeEnergyStorage;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import slimeknights.tconstruct.library.modifiers.ModifierTrait;
import slimeknights.tconstruct.library.tinkering.TinkersItem;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.library.utils.TinkerUtil;

public class ModifierEnergized extends ModifierTrait {
    public static final String KEY = "mmd_energized";
    public static final int ENERGY_PER_DAMAGE_POINT = 50;

    public ModifierEnergized() {
        super(KEY, Color.CYAN.getRGB());

        this.addItem(Items.CARROT);

        // here is hoping this object get created only once
        // and only if tinkers is actually loaded and such
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public int onToolDamage(final ItemStack tool, final int damage, final int newDamage, final EntityLivingBase entity) {
        if (entity.getEntityWorld().isRemote) {
            return 0;
        }

        final IEnergyStorage battery = tool.getCapability(CapabilityEnergy.ENERGY, null);
        if (battery != null) {
            final int toDrain = newDamage * ENERGY_PER_DAMAGE_POINT;
            final int drained = battery.extractEnergy(toDrain, false);
            return (toDrain == drained) ? 0 : ((toDrain - drained) / ENERGY_PER_DAMAGE_POINT);
        }

        return newDamage;
    }

    @Override
    public String getTooltip(final NBTTagCompound modifierTag, final boolean detailed) {
        return String.format("%d FE / %d FE", modifierTag.getInteger("energy"), modifierTag.getInteger("capacity"));
    }

    @Override
    public List<String> getExtraInfo(final ItemStack tool, final NBTTagCompound modifierTag) {
        final List<String> info = new ArrayList<>();

        final IEnergyStorage battery = tool.getCapability(CapabilityEnergy.ENERGY, null);
        if (battery != null) {
            info.add(String.format("%d FE / %d FE",  battery.getEnergyStored(), battery.getMaxEnergyStored()));
        }

        return info;
    }

    @SubscribeEvent
    public void hackCapabilities(final AttachCapabilitiesEvent<ItemStack> ev) {
        final ItemStack stack = ev.getObject();
        if (stack.getItem() instanceof TinkersItem) {
            // TODO: maybe test the item for some properties and such
            // final TinkersItem tinkers = (TinkersItem)stack.getItem();

            ev.addCapability(new ResourceLocation(MMDLib.MODID, KEY),
                new ForgeEnergyStorage(0, 42000)
                    .setInputRate(80).setoutputRate(80)
                    .getProviderForItemStack(stack,
                        (s, c) -> {
                            // only export cap for tools with this modifier on...
                            final TinkersItem tinkers = (s.getItem() instanceof TinkersItem)
                                ? (TinkersItem) s.getItem()
                                : null;
                            return ((tinkers != null) && TinkerUtil.hasModifier(TagUtil.getTagSafe(s), KEY));
                        },
                        (s, e) -> {
                            if (e != null) {
                                final NBTTagCompound nbt = TinkerUtil.getModifierTag(s, KEY);
                                nbt.setInteger("energy", e.getEnergyStored());
                                nbt.setInteger("capacity", e.getMaxEnergyStored());
                            }
                        })
            );
        }
    }
}
