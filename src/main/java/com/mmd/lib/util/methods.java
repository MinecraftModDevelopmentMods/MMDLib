package com.mmd.lib.util;

import java.util.Random;

public class methods {
    public static String makeRandomName() {
        Random r = new Random();
        String valid = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-./";
        StringBuilder k = new StringBuilder();
        for( int i = 0; i < 16; i++) k.append(valid.charAt(r.nextInt(valid.length())));
        return String.format("mmdlib:%s", k);
    }
}
