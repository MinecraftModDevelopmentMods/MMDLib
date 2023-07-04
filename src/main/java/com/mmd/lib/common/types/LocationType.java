package com.mmd.lib.common.types;

import java.util.Locale;

public enum LocationType {
    NONE("NONE"),
    MATERIAL("MATERIAL"),
    BLOCK("BLOCK"),
    ITEM("ITEM"),
    CONFIG("CONFIG");

    private final String myTypeName;

    LocationType(String typeName) {
        myTypeName = typeName;
    }

    @Override
    public String toString() {
        return "LocationType{" +
                "myTypeName='" + myTypeName.toLowerCase(Locale.ENGLISH) + '\'' +
                '}';
    }

    public String getMyTypeName() {
        return myTypeName.toLowerCase(Locale.ENGLISH);
    }
}
