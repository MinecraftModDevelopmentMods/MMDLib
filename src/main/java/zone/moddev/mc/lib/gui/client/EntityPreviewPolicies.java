package zone.moddev.mc.lib.gui.client;

import java.util.Optional;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import zone.moddev.mc.lib.api.entity.EntitySnapshot;
import zone.moddev.mc.lib.api.entity.EntitySnapshotPolicy;
import zone.moddev.mc.lib.gui.model.ProcessorVariable;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;

/**
 * Read-only schema service; portraits still consume only their synchronized
 * snapshot.
 */
@FunctionalInterface
public interface EntityPreviewPolicies {
    Optional<EntitySnapshotPolicy<?>> policy(ProcessorVariable variable);

    static EntityPreviewPolicies forHost(ScreenHost host) {
        return variable -> host.screenFeatures() == null
            ? Optional.empty()
            : host.screenFeatures().findProcessor(variable.processor())
                .flatMap(
                    processor -> processor.portableVariable(variable.name()))
                .flatMap(reference -> reference.entityPolicy());
    }

    static LivingEntity create(EntitySnapshot snapshot, Level level,
        Optional<EntitySnapshotPolicy<?>> policy) {
        return policy.isPresent()
            ? policy.get().createDetached(snapshot, level)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast).orElse(null)
            : snapshot.createDetached(level, LivingEntity.class).orElse(null);
    }
}
