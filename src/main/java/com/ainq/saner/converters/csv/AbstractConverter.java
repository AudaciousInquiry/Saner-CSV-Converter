package com.ainq.saner.converters.csv;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.Measure.MeasureGroupComponent;
import org.hl7.fhir.r4.model.Measure.MeasureGroupPopulationComponent;
import org.hl7.fhir.r4.model.Measure.MeasureGroupStratifierComponent;
import org.hl7.fhir.r4.model.Measure.MeasureGroupStratifierComponentComponent;

public abstract class AbstractConverter {
    private int numStrataColumns;
    private Map<String, Integer> strataFieldMap = new TreeMap<>();
    private List<Integer> reorder = new ArrayList<>();
    protected static final String DATA_ABSENT_REASON_EXTENSION_URL = "http://hl7.org/fhir/StructureDefinition/data-absent-reason";
    protected static final String STRATIFIER_CODE = "stratifier";
    protected static final CodeableConcept STRATIFIER = new CodeableConcept().addCoding(new Coding().setCode(STRATIFIER_CODE));
    /**
     * @return the numStrata
     */
    public int getNumStrataColumns() {
        return numStrataColumns;
    }

    /**
     * @param numStrata the numStrata to set
     */
    public void setNumStrataColumns(int numStrata) {
        this.numStrataColumns = numStrata;
    }

    public int getNumFields() {
        return strataFieldMap.size();
    }


    public void mapField(CodeableConcept fieldName, int column) {
        strataFieldMap.put(codeToString(fieldName), column);
    }

    public int getFieldPosition(CodeableConcept fieldName) {

        Integer value = strataFieldMap.get(codeToString(fieldName));
        if (value == null) {
            return -1;
        }
        return value;
    }

    public void addPosition(int originalPosition) {
        reorder.add(originalPosition);
    }

    public int getOriginalPosition(int newPosition) {
        return reorder.get(newPosition);
    }

    protected static <T> int indexOf(T find, List<T> list) {
        int i = 0;
        for (T t: list) {
            if ((find == null && t == null) || (find != null && find.equals(t))) {
                return i;
            }
            i++;
        }
        return -1;
    }

    protected static List<String> simplifyHeaders(List<String> headers) {
        return headers.stream().map(s -> s.contains("#") ? StringUtils.substringAfter(s, "#") : s).collect(Collectors.toList());
    }

    public static String codeToString(CodeableConcept codeableConcept) {
        // Return first coded value
        return codeToString(codeableConcept.getCoding().get(0));
    }

    static String codeToString(Coding coding) {
        if (coding == null || coding.isEmpty()) {
            return null;
        }
        if (coding.hasSystem()) {
            return String.format("%s#%s", coding.getSystem(), coding.getCode());
        }
        return String.format("#%s", coding.getCode());
    }

    protected static int addHeader(List<String> headers, CodeableConcept code) {
        try {
            return addHeader(headers, code, true);
        } catch (CSVConversionException e) {
            // Swallow this exception
            return -1;
        }
    }

    private static int addHeader(List<String> headers, CodeableConcept code, boolean canBeDuplicated) throws CSVConversionException {
        String header = codeToString(code);
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).equals(header)) {
                if (!canBeDuplicated) {
                    throw new CSVConversionException("Population or Group name is duplicated: " + header, code);
                }
                return i;
            }
        }
        // Add if not already present
        headers.add(header);
        return headers.size() - 1;
    }

    public List<String> generateHeaderRows(Measure measure, Map<String, String> orderedHeaderMap) {
        List<String> headers = getCanonicalHeaders(measure);
        headers = remapHeaders(headers, orderedHeaderMap);
        return headers;
    }

    public Map<String, String> getCanonicalHeaderMap(Measure measure) {
        List<String> headers = getCanonicalHeaders(measure);
        Map<String, String> canonicalHeaderMap = new LinkedHashMap<>();
        for (String header: headers) {
            canonicalHeaderMap.put(header, StringUtils.substringAfter(header, "#"));
        }
        return canonicalHeaderMap;
    }

    /**
     * Get the canonical list of headers for the given measureReport.
     * @param measureReport The measureReport to compute the headers for.
     * @return  The canonical list of headers.
     * @throws CSVConversionException
     */
    protected List<String> getCanonicalHeaders(Measure measure) {
        List<String> headers = getStrataHeaders(measure);
        getGroupAndPopulationHeaders(measure, headers);
        return headers;
    }


    private List<String> getStrataHeaders(Measure measure) {
        List<String> headers = new ArrayList<>();
        // For each distinct stratifier.code in each group
        for (MeasureGroupComponent group: measure.getGroup()) {
            for (MeasureGroupStratifierComponent stratifier: group.getStratifier()) {
                int pos = addHeader(headers, STRATIFIER);
                mapField(STRATIFIER, pos);
                for (MeasureGroupStratifierComponentComponent comp: stratifier.getComponent()) {
                    pos = addHeader(headers, comp.getCode());
                    mapField(comp.getCode(), pos);
                }
            }
        }
        setNumStrataColumns(headers.size());
        return headers;
    }

    private void getGroupAndPopulationHeaders(Measure measure, List<String> headers) {
        int pos;

        // For each group in the measure report
        for (MeasureGroupComponent group: measure.getGroup()) {
            //  For each population in the group
            for (MeasureGroupPopulationComponent population: group.getPopulation()) {
                // Get the name of the population from group.population.code
                pos = addHeader(headers, population.getCode());
                mapField(population.getCode(), pos);
            }
            // Get the name of the measure from group.code
            pos = addHeader(headers, group.getCode());
            mapField(group.getCode(), pos);
        }
    }

    /**
     * Given a list of canonical headers, and a remapping of those headers, return the header values
     * to output in the CSV file.
     *
     * @param headers   The headers to remap.
     * @param orderedHeaderMap  The remapping instructions.
     * @return The list of remapped headers.
     */
    private List<String> remapHeaders(List<String> headers, Map<String, String> orderedHeaderMap) {
        if (orderedHeaderMap == null || orderedHeaderMap.isEmpty()) {
            for (int i = 0; i < headers.size(); i++) {
                addPosition(i);
            }
            return simplifyHeaders(headers);
        }

        List<String> newHeaders = new ArrayList<>();
        for (String header: headers) {
            String value = orderedHeaderMap.get(header);
            if (value == null) {
                // No mapping means leave it out.
                continue;
            }
            newHeaders.add(value);
        }

        List<String> reorderedHeaders = new ArrayList<>();
        int originalPosition = 0;
        for (Map.Entry<String, String> e: orderedHeaderMap.entrySet()) {
            originalPosition = indexOf(e.getKey(), headers);
            if (originalPosition >= 0) {
                reorderedHeaders.add(e.getValue());
            }
            addPosition(originalPosition);
        }
        return newHeaders;
    }
}