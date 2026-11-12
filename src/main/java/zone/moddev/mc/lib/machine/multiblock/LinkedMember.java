package zone.moddev.mc.lib.machine.multiblock;

/** Small ownership record on a physical part. Null means unclaimed. */
public interface LinkedMember {
    MemberLink memberLink();
    void memberLink(MemberLink link);
}
