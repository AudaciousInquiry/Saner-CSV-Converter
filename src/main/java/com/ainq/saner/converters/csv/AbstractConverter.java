package com.ainq.saner.converters.csv;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.Measure.MeasureGroupComponent;
import org.hl7.fhir.r4.model.Measure.MeasureGroupPopulationComponent;
import org.hl7.fhir.r4.model.Measure.MeasureGroupStratifierComponent;
import org.hl7.fhir.r4.model.Measure.MeasureGroupStratifierComponentComponent;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupComponent;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupPopulationComponent;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupStratifierComponent;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupComponent;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupComponentComponent;

import com.ainq.saner.util.Util;

public abstract class AbstractConverter {
    /** The measure this converter works on */
    protected final Measure measure;
    protected final Map<String, String> orderedHeaderMap;
    private int numStrataColumns;
    private final Map<String, Integer> strataFieldMap = new TreeMap<>();
    private final List<Integer> reorder = new ArrayList<>();
    protected UnaryOperator<String> codeConverter = this::simplifyingConverter;
    protected static final String DATA_ABSENT_REASON_EXTENSION_URL = "http://hl7.org/fhir/StructureDefinition/data-absent-reason";
    protected static final String STRATIFIER_CODE = "stratifier";
    protected static final CodeableConcept STRATIFIER = new CodeableConcept().addCoding(new Coding().setCode(STRATIFIER_CODE));

    protected AbstractConverter(Measure measure, Map<String, String> orderedHeaderMap) {
        this.measure = measure;
        if (orderedHeaderMap == null || orderedHeaderMap.isEmpty()) {
            this.orderedHeaderMap = getCanonicalHeaderMap();
        } else {
            this.orderedHeaderMap = updateHeaderMapFromCanonical(orderedHeaderMap);
        }
    }

    protected String simplifyingConverter(String code) {
        if (code == null) {
            return null;
        }
        return code.contains("#") ? StringUtils.substringAfter(code, "#") : code;
    }

    public void setConverter(UnaryOperator<String> codeConverter) {
        this.codeConverter = codeConverter;
    }

    public UnaryOperator<String> getConverter() {
        return codeConverter;
    }
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
        if (newPosition >= reorder.size()) {
            return -1;
        }
        return reorder.get(newPosition);
    }

    protected static <T> int indexOf(T find, Iterable<T> list) {
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

    public Map<String, String> getCanonicalHeaderMap() {
        List<String> headers = getCanonicalHeaders();
        Map<String, String> canonicalHeaderMap = new LinkedHashMap<>();
        for (String header: headers) {
            canonicalHeaderMap.put(header, StringUtils.substringAfter(header, "#"));
        }
        return canonicalHeaderMap;
    }

    public Map<String, String> updateHeaderMapFromCanonical(Map<String, String> headerMap) {
        Map<String, String> canonicalMap = Util.invertMap(getCanonicalHeaderMap());
        Map<String, String> newHeaderMap = new LinkedHashMap<>();

        for (Map.Entry<String, String> e: headerMap.entrySet()) {
            String key = e.getKey();
            if (!key.contains("#")) {
                key = canonicalMap.get(key);
            }
            newHeaderMap.put(key, e.getValue());
        }

        return newHeaderMap;
    }

    /**
     * Get the canonical list of headers for the given measureReport.
     * @param measureReport The measureReport to compute the headers for.
     * @return  The canonical list of headers.
     * @throws CSVConversionException
     */
    protected List<String> getCanonicalHeaders() {
        List<String> headers = getStrataHeaders();
        getGroupAndPopulationHeaders(headers);
        return headers;
    }


    private List<String> getStrataHeaders() {
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

    private void getGroupAndPopulationHeaders(List<String> headers) {
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
     * Craft enough of a measure from a Measure report so that we understand the
     * reporting model.
     *
     * @param measureReport The measure report.
     * @return  A Measure resource describing the reporting model.
     */
    private static Measure computeMeasureFromReport(MeasureReport measureReport) {
        Measure measure = new Measure();
        measure.setUrl(measureReport.getMeasure());
        for (MeasureReportGroupComponent group: measureReport.getGroup()) {
            MeasureGroupComponent g = measure.addGroup();
            g.setCode(group.getCode());

            for (MeasureReportGroupPopulationComponent pop: group.getPopulation()) {
                MeasureGroupPopulationComponent p = g.addPopulation();
                p.setCode(pop.getCode());
            }

            for (MeasureReportGroupStratifierComponent strat: group.getStratifier()) {
                MeasureGroupStratifierComponent s = g.addStratifier();
                s.setCode(strat.getCodeFirstRep());

                StratifierGroupComponent stratum = strat.getStratumFirstRep();
                for (StratifierGroupComponentComponent comp: stratum.getComponent()) {
                    MeasureGroupStratifierComponentComponent c = s.addComponent();
                    c.setCode(comp.getCode());
                }
            }
        }
        return measure;
    }

    /**
     * Given a MeasureReport resource, return a Measure describing the reporting model
     * either from the cached value, or computed from the MeasureReport.
     *
     * @param measureReport The MeasureReport
     * @return
     */
    protected static Measure getMeasure(MeasureReport measureReport) {
        CanonicalType measure = measureReport.getMeasureElement();
        Object resource = measure == null ? null : measure.getUserData("resource");
        if (resource == null) {
            resource = computeMeasureFromReport(measureReport);
            if (measure != null) {
                measure.setUserData("resource", resource);
            }
        }
        return (Measure) resource;
    }
}