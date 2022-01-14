package com.ainq.saner.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class Util {
    /**
     * Produces the inversion of a map. Assumes that map defines a function that is reversible.
     * @param <K>   The type of keys in the input map
     * @param <V>   The type of values in the input map
     * @param map   The map to invert.
     * @return  A map that performs the inverse transformation
     */
    public static <K, V> Map<V, K> invertMap(Map<K, V> map) {
        Map<V, K> inversion = new LinkedHashMap<>();
        for (Map.Entry<K, V> e: map.entrySet()) {
            K oldValue = inversion.put(e.getValue(), e.getKey());
            if (oldValue != null) {
                throw new IllegalArgumentException(e.getValue() + " maps to both " + oldValue + " and " + e.getKey());
            }
        }
        return inversion;
    }
}
