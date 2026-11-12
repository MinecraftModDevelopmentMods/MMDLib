package zone.moddev.mc.lib.machine.multiblock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** One controller's committed formation. Placement is deliberately outside this lifecycle. */
public final class MultiblockInstance {
    public enum Status {
        UNFORMED, READY, SUSPENDED, DISASSEMBLING
    }
    public enum Cause {
        SURVIVAL, CREATIVE, EXPLOSION, RECOVERY, INVALIDATED
    }
    public interface Listener {
        default void changed(Status previous, Status current, String reason) {
        }
        /** Runs once, before owned blocks are removed; the consumer settles contents and loot. */
        default void dismantling(Cause cause) {
        }
    }

    private final BlockEntity owner;
    private final MultiblockDefinition definition;
    private final StructureMembership membership;
    private final Listener listener;
    private ResolvedStructure structure;
    private java.util.Map<String, StructureMember> memberIndex = java.util.Map.of();
    private StructureTransform transform;
    private UUID formation;
    private Status status = Status.UNFORMED;
    private String diagnostic = "unformed";
    private boolean committed;
    private boolean changing;
    private long revision;
    private CompoundTag incompatible;

    public MultiblockInstance(BlockEntity owner, MultiblockDefinition definition,
        StructureMembership membership, Listener listener) {
        this.owner = Objects.requireNonNull(owner);
        this.definition = Objects.requireNonNull(definition);
        this.membership = Objects.requireNonNull(membership);
        this.listener = Objects.requireNonNull(listener);
    }
    public BlockEntity owner() {
        return owner;
    }
    public MultiblockDefinition definition() {
        return definition;
    }
    public ResolvedStructure structure() {
        return structure;
    }
    public StructureTransform transform() {
        return transform;
    }
    public UUID formation() {
        return formation;
    }
    public long revision() {
        return revision;
    }
    public Status status() {
        return status;
    }
    public String diagnostic() {
        return diagnostic;
    }
    public boolean changing() {
        return changing;
    }
    public boolean committed() {
        return committed;
    }

    /** Prepares an inactive manifest before a placement attempt writes its remaining members. */
    public boolean stage(ResolvedStructure candidate, Direction facing) {
        var level = owner.getLevel();
        if (level == null || level.isClientSide || changing || formation != null
            || incompatible != null)
            return false;
        definition.validate(candidate);
        structure = candidate;
        memberIndex = candidate.members().stream().collect(
            java.util.stream.Collectors.toUnmodifiableMap(StructureMember::key, member -> member));
        transform = new StructureTransform(owner.getBlockPos(), facing);
        formation = UUID.randomUUID();
        revision++;
        owner.setChanged();
        return true;
    }

    /** Formation of existing members: no placement writes and no item consumption. */
    public boolean form(ResolvedStructure candidate, Direction facing) {
        if (!stage(candidate, facing))
            return false;
        if (commit())
            return true;
        discard();
        return false;
    }

    /** Claims an existing candidate and activates only after all reciprocal checks succeed. */
    public boolean commit() {
        var level = owner.getLevel();
        if (level == null || level.isClientSide || changing || structure == null || committed)
            return false;
        changing = true;
        var claimed = new ArrayList<StructureMember>();
        try {
            for (var member : structure.members()) {
                var pos = transform.position(member.offset());
                if (!level.hasChunkAt(pos) || !level.getBlockState(pos)
                    .equals(member.state().rotate(transform.rotation())))
                    return false;
                var previous = membership.link(level, pos);
                if (previous != null && !previous.equals(link(member)))
                    return false;
                if (!membership.claim(level, pos, link(member)))
                    return false;
                claimed.add(member);
            }
            for (var rule : structure.requirements()) {
                var pos = transform.position(rule.offset());
                if (!level.hasChunkAt(pos) || !definition.rules().get(rule.rule()).test(level, pos))
                    return false;
            }
            if (!matchesAll())
                return false;
            committed = true;
            owner.setChanged();
        } finally {
            if (!committed)
                for (var member : claimed)
                    membership.release(level, transform.position(member.offset()), link(member));
            changing = false;
        }
        transition(Status.READY, "formed");
        return true;
    }

    /** Abandons an uncommitted attempt without machine loot or world writes. */
    public void discard() {
        if (committed)
            throw new IllegalStateException("Cannot discard a committed machine");
        structure = null;
        transform = null;
        formation = null;
        owner.setChanged();
    }

    public MemberLink memberLink(String key) {
        return link(structure.member(key));
    }

    public MemberLink controllerLink() {
        if (structure == null || formation == null || owner.getLevel() == null)
            return null;
        return link(structure.members().stream().filter(m -> m.role().equals("controller"))
            .findFirst().orElseThrow());
    }

    private MemberLink link(StructureMember member) {
        return new MemberLink(owner.getLevel().dimension().location(), owner.getBlockPos(),
            formation, member.key());
    }

    /** Reciprocal check also used by stale cached handlers and remote menu anchors. */
    public boolean owns(BlockPos pos, MemberLink expected) {
        if (!contains(pos, expected))
            return false;
        var member = memberIndex.get(expected.member());
        return owner.getLevel().getBlockState(pos)
            .equals(member.state().rotate(transform.rotation()))
            && expected.equals(membership.link(owner.getLevel(), pos));
    }

    /** Checks the manifest identity/position even while a block removal is changing its state. */
    public boolean contains(BlockPos pos, MemberLink expected) {
        var level = owner.getLevel();
        if (level == null || owner.isRemoved() || structure == null || formation == null
            || expected == null
            || !formation.equals(expected.formation())
            || !expected.controller().equals(owner.getBlockPos())
            || !expected.dimension().equals(level.dimension().location()) || !level.hasChunkAt(pos))
            return false;
        var member = memberIndex.get(expected.member());
        return member != null && transform.position(member.offset()).equals(pos);
    }

    private boolean matchesAll() {
        for (var member : structure.members())
            if (!owns(transform.position(member.offset()), link(member)))
                return false;
        return true;
    }

    /** Bounded fixed-layout validation. No discovery or forced chunk loading occurs here. */
    public void tick() {
        var level = owner.getLevel();
        if (level == null || level.isClientSide || changing || structure == null)
            return;
        if (!committed) {
            dismantle(Cause.RECOVERY);
            return;
        }
        for (var member : structure.members()) {
            if (!level.hasChunkAt(transform.position(member.offset()))) {
                transition(Status.SUSPENDED, "unloaded_member:" + member.key());
                return;
            }
        }
        for (var rule : structure.requirements()) {
            if (rule.continuous() && !level.hasChunkAt(transform.position(rule.offset()))) {
                transition(Status.SUSPENDED, "unloaded_requirement:" + rule.rule());
                return;
            }
        }
        if (!matchesAll()) {
            dismantle(Cause.RECOVERY);
            return;
        }
        for (var rule : structure.requirements()) {
            if (rule.continuous() && !definition.rules().get(rule.rule()).test(level,
                transform.position(rule.offset()))) {
                transition(Status.SUSPENDED, "obstructed:" + rule.rule());
                return;
            }
        }
        transition(Status.READY, "ready");
    }

    public boolean ready() {
        tick();
        return committed && !changing && status == Status.READY && !owner.isRemoved();
    }

    public void dismantle(Cause cause) {
        var level = owner.getLevel();
        if (level == null || level.isClientSide || changing || structure == null)
            return;
        changing = true;
        boolean wasCommitted = committed;
        committed = false;
        revision++;
        try {
            transition(Status.DISASSEMBLING, cause.name().toLowerCase(java.util.Locale.ROOT));
            if (wasCommitted)
                listener.dismantling(cause);
            // Controller last: keep the owner available while linked parts are removed.
            var ordered = new ArrayList<>(structure.members());
            ordered
                .sort(java.util.Comparator.comparing(member -> member.role().equals("controller")));
            for (var member : ordered) {
                var pos = transform.position(member.offset());
                if (!level.hasChunkAt(pos) || !owns(pos, link(member)))
                    continue;
                membership.release(level, pos, link(member));
                if (definition.dismantling() == MultiblockDefinition.Dismantling.REMOVE_OWNED)
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            structure = null;
            transform = null;
            formation = null;
            transition(Status.UNFORMED, "dismantled");
            owner.setChanged();
        } finally {
            changing = false;
        }
    }

    private void transition(Status next, String reason) {
        var previous = status;
        boolean changed = previous != next || !diagnostic.equals(reason);
        status = next;
        diagnostic = reason;
        if (changed) {
            owner.setChanged();
            var level = owner.getLevel();
            if (level != null) {
                if (structure != null)
                    for (var member : structure.members())
                        membership.availabilityChanged(level, transform.position(member.offset()));
                level.sendBlockUpdated(owner.getBlockPos(), owner.getBlockState(),
                    owner.getBlockState(), 2);
            }
        }
        if (previous != next)
            listener.changed(previous, next, reason);
    }

    public CompoundTag save() {
        if (incompatible != null)
            return incompatible.copy();
        var tag = new CompoundTag();
        tag.putInt("schema", 1);
        if (structure == null)
            return tag;
        tag.putString("definition", definition.id().toString());
        tag.putInt("version", definition.version());
        tag.putString("policy", definition.dismantling().name());
        tag.putUUID("formation", formation);
        tag.putString("facing", transform.facing().getName());
        tag.putBoolean("committed", committed);
        tag.putLong("revision", revision);
        var members = new ListTag();
        for (var member : structure.members()) {
            var value = new CompoundTag();
            value.putString("key", member.key());
            value.putString("role", member.role());
            value.putLong("offset", member.offset().asLong());
            value.put("state", NbtUtils.writeBlockState(member.state()));
            members.add(value);
        }
        tag.put("members", members);
        var rules = new ListTag();
        for (var rule : structure.requirements()) {
            var value = new CompoundTag();
            value.putString("rule", rule.rule());
            value.putLong("offset", rule.offset().asLong());
            value.putBoolean("continuous", rule.continuous());
            rules.add(value);
        }
        tag.put("requirements", rules);
        var measurements = new CompoundTag();
        structure.measurements().forEach(measurements::putInt);
        tag.put("measurements", measurements);
        return tag;
    }

    /** World-save restoration only. Malformed/incompatible manifests are preserved but inactive. */
    public void load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        structure = null;
        transform = null;
        formation = null;
        committed = false;
        incompatible = null;
        status = Status.UNFORMED;
        diagnostic = "unformed";
        if (tag.isEmpty())
            return;
        try {
            if (tag.getInt("schema") != 1)
                throw new IllegalArgumentException("Unknown schema");
            if (!tag.contains("definition"))
                return;
            if (!tag.getString("definition").equals(definition.id().toString())
                || tag.getInt("version") != definition.version()
                || !tag.getString("policy").equals(definition.dismantling().name()))
                throw new IllegalArgumentException("Definition changed");
            var entries = tag.getList("members", Tag.TAG_COMPOUND);
            var requirements = tag.getList("requirements", Tag.TAG_COMPOUND);
            if (entries.size() > definition.maxMembers() || requirements.size() > 4096)
                throw new IllegalArgumentException("Manifest too large");
            var members = new ArrayList<StructureMember>();
            for (var entry : entries) {
                var value = (CompoundTag) entry;
                members.add(new StructureMember(value.getString("key"), value.getString("role"),
                    BlockPos.of(value.getLong("offset")), NbtUtils.readBlockState(
                        registries.lookupOrThrow(Registries.BLOCK), value.getCompound("state"))));
            }
            var rules = new ArrayList<StructureRequirement>();
            for (var entry : requirements) {
                var value = (CompoundTag) entry;
                rules.add(new StructureRequirement(value.getString("rule"),
                    BlockPos.of(value.getLong("offset")), value.getBoolean("continuous")));
            }
            var measurements = new LinkedHashMap<String, Integer>();
            var values = tag.getCompound("measurements");
            if (values.size() > 64)
                throw new IllegalArgumentException("Too many measurements");
            for (var key : values.getAllKeys())
                measurements.put(key, values.getInt(key));
            var candidate = new ResolvedStructure(members, rules, measurements);
            definition.validate(candidate);
            var facing = Direction.byName(tag.getString("facing"));
            transform = new StructureTransform(owner.getBlockPos(), Objects.requireNonNull(facing));
            formation = tag.getUUID("formation");
            revision = tag.getLong("revision");
            committed = tag.getBoolean("committed");
            structure = candidate;
            memberIndex = candidate.members().stream().collect(java.util.stream.Collectors
                .toUnmodifiableMap(StructureMember::key, member -> member));
            status = Status.SUSPENDED;
            diagnostic = "awaiting_validation";
        } catch (RuntimeException failure) {
            structure = null;
            transform = null;
            formation = null;
            committed = false;
            incompatible = tag.copy();
            diagnostic = "incompatible_manifest";
        }
    }

    public CompoundTag clientState() {
        var tag = new CompoundTag();
        tag.putString("status", status.name());
        tag.putString("diagnostic", diagnostic);
        if (formation != null) {
            tag.putUUID("formation", formation);
            tag.putString("facing", transform.facing().getName());
        }
        return tag;
    }
    public void readClientState(CompoundTag tag) {
        if (owner.getLevel() != null && !owner.getLevel().isClientSide)
            return;
        try {
            status = Status.valueOf(tag.getString("status"));
        } catch (IllegalArgumentException ignored) {
            status = Status.UNFORMED;
        }
        diagnostic = tag.getString("diagnostic");
        formation = tag.hasUUID("formation") ? tag.getUUID("formation") : null;
        var facing = Direction.byName(tag.getString("facing"));
        transform = facing != null && facing.getAxis().isHorizontal()
            ? new StructureTransform(owner.getBlockPos(), facing)
            : null;
    }
}
