package zone.moddev.mc.lib.api.capabilities;

import java.util.function.Consumer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Raw displaced contents survive missing registrations and can be handed to a
 * recovery item.
 */
public final class StorageRecovery {
    private StorageRecovery() {
    }

    public static void handoff(ListTag entries, String type, String key,
        Consumer<CompoundTag> receiver) {
        if (entries.isEmpty())
            return;
        var resources = new ListTag();
        for (var entry : entries) {
            var data = new CompoundTag();
            data.put(key, entry.copy());
            var resource = new CompoundTag();
            resource.putString("type", type);
            resource.put("data", data);
            resources.add(resource);
        }
        var payload = new CompoundTag();
        payload.put("resources", resources);
        receiver.accept(payload);
        entries.clear();
    }
}
