package zone.moddev.mc.lib.machine.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import zone.moddev.mc.lib.machine.multiblock.LinkedMember;
import zone.moddev.mc.lib.machine.multiblock.MemberLink;

/** Non-ticking physical part; the consuming mod registers its type and blocks. */
public class MultiblockPartEntity extends BlockEntity implements LinkedMember {
    private MemberLink link;
    public MultiblockPartEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
    @Override
    public MemberLink memberLink() {
        return link;
    }
    @Override
    public void memberLink(MemberLink value) {
        link = value;
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (link != null)
            tag.put("member", link.save());
    }
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        try {
            link = tag.contains("member") ? MemberLink.load(tag.getCompound("member")) : null;
        } catch (RuntimeException malformed) {
            link = null;
        }
    }
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide)
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        if (link != null)
            tag.put("member", link.save());
        return tag;
    }
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
