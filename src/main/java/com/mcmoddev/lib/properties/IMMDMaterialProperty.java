package com.mcmoddev.lib.properties;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

public interface IMMDMaterialProperty {
	/**
	 * Does this property have an effect in the current situation ? (EntityPlayer/EntityLivingBase separate version)
	 * @param stack
	 * @return
	 */
	boolean hasEffect(ItemStack stack);
	default boolean hasEffect(Block block) { return hasEffect(Item.getItemFromBlock(block)); };
	default boolean hasEffect(Item item) { return hasEffect(new ItemStack(item)); };
	void apply(ItemStack stack);
	default void apply(Block block) { apply(Item.getItemFromBlock(block)); };
	default void apply(Item item) { apply(new ItemStack(item)); };
	
	/**
	 * Does this property have an effect in the current situation ? (EntityPlayer version)
	 * @param stack
	 * @param player
	 * @return
	 */
	boolean hasEffect(ItemStack stack, EntityPlayer player);
	default boolean hasEffect(Block block, EntityPlayer player) { return hasEffect(Item.getItemFromBlock(block), player); };
	default boolean hasEffect(Item item, EntityPlayer player) { return hasEffect(new ItemStack(item), player); };
	void apply(ItemStack stack, EntityPlayer player);
	default void apply(Block block, EntityPlayer player) { apply(Item.getItemFromBlock(block), player); };
	default void apply(Item item, EntityPlayer player) { apply(new ItemStack(item), player); };

	/**
	 * Does this property have an effect in the current situation ? (EntityLivingBase version)
	 * @param stack
	 * @param player
	 * @return
	 */
	boolean hasEffect(ItemStack stack, EntityLivingBase ent);
	default boolean hasEffect(Block block, EntityLivingBase ent) { return hasEffect(Item.getItemFromBlock(block), ent); };
	default boolean hasEffect(Item item, EntityLivingBase ent) { return hasEffect(new ItemStack(item), ent); };
	void apply(ItemStack stack, EntityLivingBase ent);
	default void apply(Block block, EntityLivingBase ent) { apply(Item.getItemFromBlock(block), ent); };
	default void apply(Item item, EntityLivingBase ent) { apply(new ItemStack(item), ent); };
}
