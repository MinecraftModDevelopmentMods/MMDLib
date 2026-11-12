package zone.moddev.mc.lib.machine.inventory;

import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Saved item samples with no physical inventory or automation capability. */
public final class GhostItemGroup implements ItemGroup {
    private final ItemStack[] samples;
    private long revision;

    public GhostItemGroup(int size) {
        if (size < 1 || size > 54)
            throw new IllegalArgumentException("Ghost group size must be 1..54");
        samples = new ItemStack[size];
        java.util.Arrays.fill(samples, ItemStack.EMPTY);
    }

    public boolean set(int slot, ItemStack sample) {
        if (slot < 0 || slot >= samples.length)
            return false;
        var next = sample.isEmpty() ? ItemStack.EMPTY : sample.copyWithCount(1);
        if (!ItemStack.matches(samples[slot], next)) {
            samples[slot] = next;
            revision++;
        }
        return true;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        for (int i = 0; i < samples.length; i++)
            tag.put(Integer.toString(i),
                samples[i].isEmpty() ? new CompoundTag() : samples[i].save(registries));
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        for (int i = 0; i < samples.length; i++)
            set(i, ItemStack.parseOptional(registries, tag.getCompound(Integer.toString(i))));
    }

    public boolean ghost() {
        return true;
    }
    public long revision() {
        return revision;
    }
    public int size() {
        return samples.length;
    }
    public ItemStack peek(int slot) {
        return slot < 0 || slot >= size() ? ItemStack.EMPTY : samples[slot].copy();
    }
    public int free(int slot) {
        return peek(slot).getCount();
    }
    public int extract(int slot, int count, MachineTx tx) {
        return 0;
    }
    public int insert(ItemStack stack, MachineTx tx) {
        return 0;
    }
    public int insert(int slot, ItemStack stack, MachineTx tx) {
        return 0;
    }
    public boolean reserve(int slot, SlotPlan plan, MachineTx tx) {
        return false;
    }
    public boolean restore(int slot, SlotPlan plan, MachineTx tx) {
        return false;
    }
    public void release(int slot, SlotPlan plan, MachineTx tx) {
    }
    public int[][] place(List<ItemStack> stacks, long[] taken) {
        return stacks.isEmpty() ? new int[0][size()] : null;
    }
}
