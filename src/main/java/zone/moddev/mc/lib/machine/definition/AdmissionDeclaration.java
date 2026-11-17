package zone.moddev.mc.lib.machine.definition;

import zone.moddev.mc.lib.machine.feature.StorageAdmission;

/** Either a per-machine rule instance or an explicitly named processor. */
public record AdmissionDeclaration(Class<? extends StorageAdmission> type,
    String processor) {
    public AdmissionDeclaration {
        if ((type == null) == (processor == null)
            || processor != null && processor.isBlank())
            throw new IllegalArgumentException(
                "Specify one filter-class or filter-processor");
    }

    public static AdmissionDeclaration load(String name, String processor,
        ClassLoader loader) {
        if (name == null)
            return processor == null
                ? null
                : new AdmissionDeclaration(null, processor);
        if (processor != null)
            throw new IllegalArgumentException(
                "Specify one filter-class or filter-processor");
        try {
            var type = Class.forName(name, false, loader)
                .asSubclass(StorageAdmission.class);
            type.getConstructor();
            return new AdmissionDeclaration(type, null);
        } catch (ReflectiveOperationException | ClassCastException ex) {
            throw new IllegalArgumentException(
                "Invalid admission class: " + name, ex);
        }
    }

    public StorageAdmission create() {
        try {
            return type.getConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException(
                "Cannot instantiate admission class: " + type.getName(), ex);
        }
    }
}
