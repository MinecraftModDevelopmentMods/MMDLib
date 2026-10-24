package zone.moddev.mc.lib.common.parsers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public abstract class ParserBase<I, B> {
    protected JsonObject baseData;
    protected B builder;

    public interface BuilderGetter<T> {
        public T run();
    }

    public ParserBase(JsonObject data, BuilderGetter<B> getter) {
        baseData = data;
        builder = getter.run();
    }

    public abstract I parse();
    protected abstract B parseKey(final String key);

    protected final boolean validateValueIsString(JsonElement elem) {
        return elem.isJsonPrimitive() && (elem.getAsString().length() > 0);
    }

    protected final String getAsString(JsonElement elem) {
        return elem.getAsString();
    }
}
