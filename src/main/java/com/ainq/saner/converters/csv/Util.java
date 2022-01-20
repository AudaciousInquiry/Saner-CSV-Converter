package com.ainq.saner.converters.csv;

import java.util.LinkedHashMap;
import java.util.Map;

import org.hl7.fhir.r4.model.Coding;

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

    public static boolean stringMatchesCoding(String code, Coding coding) {
        if (code == null) {
            throw new NullPointerException();
        }
        String codingCode = coding.getCode();
        if (code.equals(codingCode)) {
            return true;
        }
        int i = code.indexOf('#');
        if (i < 0) {
            return false;
        }
        String system = coding.hasSystem() ? coding.getSystem() : "";
        return code.substring(0, i).equals(system) && code.substring(i + 1).equals(codingCode);
    }
}
