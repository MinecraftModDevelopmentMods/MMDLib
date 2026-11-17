package zone.moddev.mc.lib.gui.model.elements;

import java.util.function.BooleanSupplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandlerModifiable;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.SlotKind;

/**
 * Manual upgrade socket, bounded by its storage and the admitted item's stack
 * limit.
 */
public class UpgradeSlotElement extends ItemSlotElement {
    private final TagKey<Item> tag;
    public UpgradeSlotElement(Common common, int index, Appearance appearance,
        ResourceLocation tag) {
        super(common, index, SlotKind.UPGRADE, appearance);
        this.tag = TagKey.create(Registries.ITEM, tag);
    }
    public TagKey<Item> acceptedItems() {
        return tag;
    }
    @Override
    public java.util.Optional<Integer> tint() {
        return java.util.Optional.of(appearance().tint().orElse(0xffffff));
    }
    @Override
    public java.util.List<String> associatedGroups(
        zone.moddev.mc.lib.gui.model.Screen screen,
        java.util.List<zone.moddev.mc.lib.gui.model.ElementGroup> groups) {
        return java.util.List.of();
    }
    public MenuSlot createSlot(IItemHandlerModifiable storage, int local,
        Rect bounds, BooleanSupplier visible, BooleanSupplier interacting,
        Runnable changed) {
        var xy = itemPosition(bounds);
        return new MenuSlot(storage, local, xy[0], xy[1], visible, interacting,
            changed, true) {
            public boolean mayPlace(ItemStack stack) {
                return stack.is(tag) && super.mayPlace(stack);
            }
        };
    }
    public String toString() {
        return "UpgradeSlot[" + super.toString() + "," + tag.location() + "]";
    }
    public boolean equals(Object other) {
        return super.equals(other) && other instanceof UpgradeSlotElement slot
            && tag.equals(slot.tag);
    }
    public int hashCode() {
        return 31 * super.hashCode() + tag.hashCode();
    }
}
