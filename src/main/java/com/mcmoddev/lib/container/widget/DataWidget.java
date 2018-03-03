package com.mcmoddev.lib.container.widget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.container.IContainerSlot;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.util.NBTUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.TriConsumer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * Annotation based implementation of a widget that provides data.
 */
@SuppressWarnings("WeakerAccess")
public class DataWidget extends BaseWidget implements IProxyWidget, INBTSerializable<NBTTagCompound> {
    private final List<DataHandler> dataHandlers = new ArrayList<>();

    /**
     * Initializes a new instance of DataWidget.
     * @param key The key that uniquely identified this widget.
     */
    protected DataWidget(final String key) {
        super(key, false);

        final Map<String, DataProvider> providers = new HashMap<>();
        //#region SCAN FIELDS

        for(final Field field: this.getClass().getFields()) {
            final DataField thing = field.getAnnotation(DataField.class);
            if (thing != null) {
                providers.put(field.getName(), new FieldDataProvider(field));
            }
        }

        //#endregion
        //#region SCAN PROPERTIES

        final Map<String, Method> getters = new HashMap<>();
        final Map<String, Method> setters = new HashMap<>();
        for(final Method method : this.getClass().getMethods()) {
            final DataGetter getter = method.getAnnotation(DataGetter.class);
            if (getter != null) {
                String getterName = getter.value();
                if (getterName.isEmpty()) {
                    getterName = method.getName();
                    if (getterName.startsWith("get")) {
                        getterName = StringUtils.uncapitalize(getterName.substring(3));
                    }
                }
                getters.put(getterName, method);
            }
            else {
                final DataSetter setter = method.getAnnotation(DataSetter.class);
                if (setter != null) {
                    String setterName = setter.value();
                    if (setterName.isEmpty()) {
                        setterName = method.getName();
                        if (setterName.startsWith("set")) {
                            setterName = StringUtils.uncapitalize(setterName.substring(3));
                        }
                    }
                    setters.put(setterName, method);
                }
            }
        }

        for(final String methodKey : getters.keySet()) {
            final Method getter = getters.getOrDefault(methodKey, null);
            final Method setter = setters.getOrDefault(methodKey, null);
            if ((getter == null) || (setter == null)) {
                continue;
            }

            if ((setter.getParameterCount() != 1) || !getter.getReturnType().equals(setter.getParameterTypes()[0])) {
                MMDLib.logger.warn("Type mismatch between data widget getter and setter for data: '" + methodKey + "'.");
                continue;
            }

            providers.put(methodKey, new MethodDataProvider(getter, setter));
        }

        //#endregion

        for(final String providerKey: providers.keySet()) {
            final DataHandler handler = DataWidget.getDataHandler(providers.get(providerKey), providerKey);
            if (handler == null) {
                MMDLib.logger.warn("Could not find data handler for data widget field: '" + providerKey + "'.");
                continue;
            }

            this.dataHandlers.add(handler);
        }
    }

    /**
     * Sets the value of a data field.
     * @param valueKey The key of the data field.
     * @param value The value to be set to.
     * @implNote Method will have no effect if key is not found or value type doesn't match.
     */
    public void setValue(final String valueKey, final Object value) {
        for(final DataHandler handler: this.dataHandlers) {
            if (handler.providerKey.equals(valueKey) && handler.expectedType.isInstance(value)) {
                //noinspection unchecked
                handler.setValue(this, handler.expectedType.cast(value));
                break;
            }
        }
    }

    /**
     * Gets the value of a data field.
     * @param valueKey The key of the data field.
     * @return The value of the data field. Or null if key is not found.
     */
    @Nullable
    public Object getValue(final String valueKey) {
        for(final DataHandler handler: this.dataHandlers) {
            if (handler.providerKey.equals(valueKey)) {
                return handler.getValue(this);
            }
        }
        return null;
    }

    /**
     * Gets a typed value of a data field.
     * @param expected Expected type of the data field's value.
     * @param valueKey The key of the data field.
     * @param <T> Expected type of the data field's value.
     * @return The value of the data field. Or null if key not found or value has different type.
     */
    @Nullable
    public <T> T getValue(final Class<T> expected, final String valueKey) {
        for(final DataHandler handler: this.dataHandlers) {
            if (handler.providerKey.equals(valueKey) && expected.isAssignableFrom(handler.expectedType)) {
                return expected.cast(handler.getValue(this));
            }
        }
        return expected.cast(null);
    }

    //#region DATA ANNOTATIONS & HANDLERS

    /**
     * Annotate a method with this to be handled as a data field getter.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface DataGetter {
        String value() default "";
    }

    /**
     * Annotate a method with this to be handled as a data field setter.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface DataSetter {
        String value() default "";
    }

    /**
     * Annotate a field with this to be handled as a data field.
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface DataField { }

    private abstract class DataProvider {
        @Nullable
        public abstract Object getValue(DataWidget widget);
        public abstract void setValue(DataWidget widget, Object value);

        public abstract Class<?> getDataType();
    }

    private class MethodDataProvider extends DataProvider {
        private final Method getter;
        private final Method setter;

        public MethodDataProvider(final Method getter, final Method setter) {
            (this.getter = getter).setAccessible(true);
            (this.setter = setter).setAccessible(true);
        }

        @Override
        @Nullable
        public Object getValue(final DataWidget widget) {
            try {
                return this.getter.invoke(widget);
            } catch (IllegalAccessException | InvocationTargetException e) {
                MMDLib.logger.error("Error getting data widget value.", e);
                return null;
            }
        }

        @Override
        public void setValue(final DataWidget widget, final Object value) {
            try {
                this.setter.invoke(widget, value);
            } catch (IllegalAccessException | InvocationTargetException e) {
                MMDLib.logger.error("Error setting data widget value.", e);
            }
        }

        @Override
        public Class<?> getDataType() {
            return this.getter.getReturnType();
        }
    }

    private class FieldDataProvider extends DataProvider {
        private final Field field;

        private FieldDataProvider(final Field field) {
            (this.field = field).setAccessible(true);
        }

        @Nullable
        @Override
        public Object getValue(final DataWidget widget) {
            try {
                return this.field.get(widget);
            } catch (final IllegalAccessException e) {
                MMDLib.logger.error("Error getting data widget value.", e);
                return null;
            }
        }

        @Override
        public void setValue(final DataWidget widget, final Object value) {
            try {
                this.field.set(widget, value);
            } catch (final IllegalAccessException e) {
                MMDLib.logger.error("Error setting data widget value.", e);
            }
        }

        @Override
        public Class<?> getDataType() {
            return this.field.getType();
        }
    }

    @Nullable
    private static DataHandler getDataHandler(final DataProvider provider, final String key) {
        final Class<?> type = provider.getDataType();
        if (type.equals(Integer.class) || type.equals(int.class)) {
            return new IntegerHandler(provider, key);
        }
        else if (type.equals(Float.class) || type.equals(float.class)) {
            return new FloatHandler(provider, key);
        }
        else if (type.equals(String.class)) {
            return new StringHandler(provider, key);
        }
        return null;
    }

    private abstract static class DataHandler<T> {
        private final Class<T> expectedType;
        private final DataProvider provider;
        private final String providerKey;
        private final BiFunction<NBTTagCompound, String, T> getter;
        private final TriConsumer<NBTTagCompound, String, T> setter;

        protected DataHandler(final Class<T> expectedType, final DataProvider provider, final String providerKey,
                              final BiFunction<NBTTagCompound, String, T> getter,
                              final TriConsumer<NBTTagCompound, String, T> setter) {
            this.expectedType = expectedType;
            this.provider = provider;
            this.providerKey = providerKey;
            this.getter = getter;
            this.setter = setter;
        }

        protected T getValue(final DataWidget widget) {
            return this.expectedType.cast(this.provider.getValue(widget));
        }

        protected void setValue(final DataWidget widget, final T value) {
            this.provider.setValue(widget, value);
        }

        public void readFromNBT(final NBTTagCompound nbt, final DataWidget widget) {
            this.provider.setValue(widget, this.getter.apply(nbt, this.providerKey));
        }

        public void writeToNBT(final NBTTagCompound nbt, final DataWidget widget) {
            this.setter.accept(nbt, this.providerKey, this.expectedType.cast(this.provider.getValue(widget)));
        }

        public String getProviderKey() {
            return this.providerKey;
        }
    }

    private final static class IntegerHandler extends DataHandler<Integer> {
        private IntegerHandler(final DataProvider provider, final String providerKey) {
            super(Integer.class, provider, providerKey, NBTTagCompound::getInteger, NBTTagCompound::setInteger);
        }
    }

    private final static class FloatHandler extends DataHandler<Float> {
        private FloatHandler(final DataProvider provider, final String providerKey) {
            super(Float.class, provider, providerKey, NBTTagCompound::getFloat, NBTTagCompound::setFloat);
        }
    }

    private final static class StringHandler extends DataHandler<String> {
        private StringHandler(final DataProvider provider, final String providerKey) {
            super(String.class, provider, providerKey, NBTTagCompound::getString,
                (nbt, key, value) -> {
                    if (value == null) {
                        nbt.setString(key, "");
                    } else {
                        nbt.setString(key, value);
                    }
                });
        }
    }

    //#endregion

    //#region DIRTY DATA HANDLING

    private class DataWidgetProxy implements IWidget, IProxiedWidget {
        private NBTTagCompound snapshot;

        public DataWidgetProxy() {
        }

        //#region TRANSPARENT METHODS

        @Override
        public String getKey() {
            return DataWidget.this.getKey();
        }

        @Override
        public Collection<IContainerSlot> getSlots() {
            return DataWidget.this.getSlots();
        }

        @Override
        public void handleMessageFromClient(final NBTTagCompound tag) {
            DataWidget.this.handleMessageFromClient(tag);
        }

        @Override
        public void handleMessageFromServer(final NBTTagCompound tag) {
            DataWidget.this.handleMessageFromServer(tag);
        }

        //#endregion

        @Override
        public boolean isDirty() {
            final NBTTagCompound nbt = DataWidget.this.getSnapshot();
            return ((this.snapshot == null) || !nbt.equals(this.snapshot));
        }

        @Override
        public void resetDirtyFlag() {
            this.snapshot = DataWidget.this.getSnapshot();
        }

        @Nullable
        @Override
        public NBTTagCompound getUpdateCompound() {
            final NBTTagCompound snapshot = DataWidget.this.getSnapshot();
            return (this.snapshot == null) ? snapshot : NBTUtils.getPatch(this.snapshot, snapshot);
        }

        @Override
        public IWidget getOriginalWidget() {
            return DataWidget.this;
        }
    }

    @Override
    public IWidget getContextualWidget(final GuiContext context) {
        return new DataWidgetProxy();
    }

    private NBTTagCompound getSnapshot() {
        final NBTTagCompound nbt = new NBTTagCompound();

        for(final DataHandler handler: this.dataHandlers) {
            handler.writeToNBT(nbt, this);
        }

        return nbt;
    }

    private void refreshData(final NBTTagCompound nbt) {
        for(final DataHandler handler: this.dataHandlers) {
            if (nbt.hasKey(handler.providerKey)) {
                handler.readFromNBT(nbt, this);
            } // ELSE: value is unchanged and wasn't included in update patch
        }
    }

    //#endregion

    @Override
    public void handleMessageFromClient(final NBTTagCompound tag) {
        MMDLib.logger.info("Data Widget info received from client :: " + tag.toString());
        this.refreshData(tag);
    }

    @Override
    public void handleMessageFromServer(final NBTTagCompound tag) {
        MMDLib.logger.info("Data Widget info received from server :: " + tag.toString());
        this.refreshData(tag);
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return this.getSnapshot();
    }

    @Override
    public void deserializeNBT(final NBTTagCompound nbt) {
        this.refreshData(nbt);
    }

    /**
     * Gets an {@link ActionTextWidget} that can be used to update a text field of this data widget.
     * @param widgetKey The key that will uniquely identify the newly created action widget.
     * @param dataKey The key of the data field.
     * @return The action widget instance that can be used to update a text field of this data widget.
     */
    @Nullable
    public ActionTextWidget getStringUpdateActionWidget(final String widgetKey, final String dataKey) {
        final DataHandler rawHandler = this.dataHandlers.stream().filter(w -> w.getProviderKey().equals(dataKey)).findFirst().orElse(null);
        if ((rawHandler == null) || !(rawHandler instanceof StringHandler)) {
            // TODO: consider throwing an error
            return null;
        }
        final StringHandler handler = (StringHandler)rawHandler;
        final ActionTextWidget widget = new ActionTextWidget(widgetKey, handler.getValue(this));
        widget.setServerSideTextConsumer(text -> handler.setValue(this, text));
        return widget;
    }
}
