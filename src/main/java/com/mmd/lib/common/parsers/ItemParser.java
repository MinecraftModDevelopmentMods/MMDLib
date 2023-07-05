package com.mmd.lib.common.parsers;

import com.google.gson.JsonObject;
import com.mmd.lib.common.builders.ItemBuilder;
import com.mmd.lib.common.types.MMDItem;

import java.util.Locale;

import static com.mmd.lib.MMDLib.LOGGER;
import static com.mmd.lib.util.methods.makeRandomName;

public class ItemParser extends ParserBase<MMDItem, ItemBuilder> {
    public ItemParser(JsonObject obj) {
        super(obj, () -> new ItemBuilder());
    }

    @Override
    public MMDItem parse() {
        baseData.entrySet().forEach(entry -> {
            switch(entry.getKey().toLowerCase(Locale.ENGLISH)) {
                case "type":
                    // ignore this, it's already handled at a higher level
                    break;
                case "name":
                    if (validateValueIsString(entry.getValue())) {
                        String randomName = makeRandomName();
                        LOGGER.error("Name field in parse is not a String, skipping this entry, setting name to {}", randomName);
                        builder.setName(randomName);
                    } else {
                        builder.setName(getAsString(entry.getValue()));
                    }
                    break;
            }
        });
        return null;
    }

    @Override
    protected ItemBuilder parseKey(String key) {
        return null;
    }
}
