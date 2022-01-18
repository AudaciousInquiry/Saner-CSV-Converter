package com.ainq.saner.converters.csv;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.s;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Element;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.Measure.MeasureGroupComponent;
import org.hl7.fhir.r4.model.Measure.MeasureGroupPopulationComponent;
import org.hl7.fhir.r4.model.Measure.MeasureGroupStratifierComponent;
import org.hl7.fhir.r4.model.Measure.MeasureGroupStratifierComponentComponent;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupComponent;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupPopulationComponent;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupStratifierComponent;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportStatus;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportType;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupComponent;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupComponentComponent;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupPopulationComponent;

import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;

import com.ainq.saner.util.Util;
import com.opencsv.CSVReader;

public class CsvToReportConverter extends AbstractConverter {
    private static final String DATA_ABSENT_REASON_URL = "http://hl7.org/fhir/StructureDefinition/data-absent-reason";

    class Codeable {
        private final IBase component;
        private final Coding code;
        Codeable(IBase component, Coding code) {
            this.component = component;
            this.code = code;
        }
        /**
         * @return the component
         */
        public IBase getComponent() {
            return component;
        }
        /**
         * @return the code
         */
        public Coding getCode() {
            return code;
        }

        public boolean matches(CodeableConcept cc) {
            return cc.getCoding().stream().anyMatch(this::matches);
        }

        public boolean matches(Coding coding) {
            return code.getCode().equals(coding.getCode()) && code.getSystem().equals(coding.getSystem());
        }

        public String toString() {
            return String.format("%s#%s:%s", code.getSystem(), code.getCode(), component);
        }
    }
    private static final String UCUM_SYSTEM =  "http://unitsofmeasure.org";
    private static final List<String> DATA_ABSENT_REASONS =
        Arrays.asList(
            "unknown", "asked-unknown", "temp-unknown", "not-asked", "asked-declined", "masked", "not-applicable",
            "unsupported", "as-text", "error", "non-a-number", "negative-infinity", "positive-infinity",
            "not-performed", "not-permitted");

    /** The measure being produced */
    private final MeasureReport measureReport;

    /** The list of codes identifying fields in the CSV */
    private final List<Codeable> codes = new ArrayList<>();
    /** The list of codes identifying groups */
    private final List<String> groups = new ArrayList<>();
    /** The list of codes identifying populations */
    private final List<String> populations = new ArrayList<>();
    /** The list of codes identifying strata */
    private final List<String> strata = new ArrayList<>();
    /** The headers in the CSVFile */
    private final List<String> headers = new ArrayList<>();
    /** The order for processing data in columns */
    private final List<Integer> columnOrder = new ArrayList<>();
    /** The column in which the stratifier is found */
    private int stratifierColumn = -1;

    public CsvToReportConverter(Measure measure, Reference subject, Map<String, String> orderedHeaderMap) {
        super(measure, orderedHeaderMap);
        this.measureReport = initializeReportFromMeasure(measure, subject);
        setConverter(s -> s);
    }

    /**
     * Given the headers from the CSV file, and the headers from the measure,
     * encode instructions on how to copy the rows of csv data into the measureReport
     *
     * @param csvHeaders       The headers in the CSV File
     */
    public void remapCSVHeaders(List<String> csvHeaders) {
        // For each header from the CSV File
        // Provide instructions for how to get the component of the MeasureReport from the code
        headers.addAll(csvHeaders);
        int columnPos = 0;
        String stratifierCode = "#" + STRATIFIER_CODE;

        Map<String, String> invertedHeaderMap = Util.invertMap(orderedHeaderMap);
        Set<String> processingOrder = invertedHeaderMap.keySet();

        for (String header: csvHeaders) {
            String codeValue = invertedHeaderMap.get(header);
            // If the codeValue associated with the header is not present in orderedHeaderMap, ignore it
            if (stratifierCode.equals(codeValue)) {
                stratifierColumn = columnPos;
                codes.add(new Codeable(null, STRATIFIER.getCodingFirstRep()));
            } else if (codeValue != null) {
                Codeable codeable = getCodeableFromCode(codeValue, measure);
                if (codeable != null) {
                    IBase component = codeable.getComponent();
                    if (component instanceof MeasureGroupComponent) {
                        groups.add(header);
                    } else if (component instanceof MeasureGroupPopulationComponent) {
                        populations.add(header);
                    } else if (component instanceof MeasureGroupStratifierComponentComponent) {
                        strata.add(header);
                    }
                }
                codes.add(codeable);
            }
            columnOrder.add(indexOf(header, processingOrder));
            columnPos++;
        }

        // Sort the lists of keys into the correct processing order
        Comparator<String> comp = Comparator.comparingInt(s -> indexOf(s, processingOrder));
        groups.sort(comp);
        populations.sort(comp);
        strata.sort(comp);
    }

    private Codeable getCodeableFromCode(String coding, Measure measure) {
        Codeable codeable = null;
        String code = coding.contains("#") ? StringUtils.substringAfter(coding, "#") : coding;

        @SuppressWarnings("unchecked")
        Map<String, Codeable> codeMap = (Map<String, Codeable>) measure.getUserData("codeToSystemMap");
        if (codeMap == null) {
            codeMap = new TreeMap<>();
            measure.setUserData("codeToSystemMap", codeMap);
        } else if ((codeable = codeMap.get(code)) != null) {
            return codeable;
        }
        codeable = findCodeInMeasure(code, measure);
        codeMap.put(code, codeable);
        return codeable;
    }

    private Codeable findCodeInMeasure(String code, Measure measure) {
        Codeable codeable = null;
        for (MeasureGroupComponent group: measure.getGroup()) {
            if ((codeable = getCodeable(group, code, null)) != null) {
                return codeable;
            }
            for (MeasureGroupPopulationComponent pop: group.getPopulation()) {
                if ((codeable = getCodeable(pop, code, group)) != null) {
                    return codeable;
                }
            }
            if ((codeable = findCodeInStratifier(code, group)) != null) {
                return codeable;
            }
        }
        return null;
    }


    private Codeable findCodeInStratifier(String code, MeasureGroupComponent group) {
        Codeable codeable;
        for (MeasureGroupStratifierComponent stratifier: group.getStratifier()) {
            if ((codeable = getCodeable(stratifier, code, group)) != null) {
                return codeable;
            }
            for (MeasureGroupStratifierComponentComponent comp: stratifier.getComponent()) {
                if ((codeable = getCodeable(comp, code, stratifier)) != null) {
                    return codeable;
                }
            }
        }
        return null;
    }


    private Codeable getCodeable(IBase base, String code, IBase parent) {
        CodeableConcept cc = getCode(base);
        if (cc == null) {
            return null;
        }
        for (Coding c : cc.getCoding()) {
            if (code.equals(c.getCode())) {
                base.setUserData("parent", parent);
                return new Codeable(base, c);
            }
        }
        return null;
    }

    private CodeableConcept getCode(IBase item) {
        if (item instanceof MeasureGroupComponent) {
            return ((MeasureGroupComponent) item).getCode();
        } else if (item instanceof MeasureGroupPopulationComponent) {
            return ((MeasureGroupPopulationComponent) item).getCode();
        } else if (item instanceof MeasureGroupStratifierComponent) {
            return ((MeasureGroupStratifierComponent) item).getCode();
        } else if (item instanceof MeasureGroupStratifierComponentComponent) {
            return ((MeasureGroupStratifierComponentComponent) item).getCode();
        } else if (item instanceof MeasureReportGroupComponent) {
            return ((MeasureReportGroupComponent) item).getCode();
        } else if (item instanceof MeasureReportGroupPopulationComponent) {
            return ((MeasureReportGroupPopulationComponent) item).getCode();
        } else if (item instanceof MeasureReportGroupStratifierComponent) {
            return ((MeasureReportGroupStratifierComponent) item).getCodeFirstRep();
        } else if (item instanceof StratifierGroupPopulationComponent) {
            return ((StratifierGroupPopulationComponent) item).getCode();
        } else if (item instanceof StratifierGroupComponentComponent) {
            return ((StratifierGroupComponentComponent) item).getCode();
        }
        return null;
    }

    public void updateMeasureReport(List<String> data) {
        int maxLen = Math.min(codes.size(), data.size());
        // TODO: Consider just processing group and population header values here

        for (int columnPos = 0; columnPos < maxLen; columnPos++) {
            int getPos = columnOrder.get(columnPos);
            if (getPos >= maxLen) {
                continue;
            }
            Codeable codeable = codes.get(getPos);
            if (codeable == null) {
                continue;
            }
            IBase comp = codeable.getComponent();
            String value = data.get(getPos);
            if (comp instanceof MeasureGroupComponent) {
                setMeasureScore(getMeasureScoreElement(codeable.getCode()), value);
            } else if (comp instanceof MeasureGroupPopulationComponent) {
                setCount(getCountElement(codeable.getCode()), value);
            }
        }

    }

    private void setMeasureScore(Quantity measureScoreElement, String value) {
        if (value == null) {
            value = "";
        } else {
            value = value.replace(" ", "");
        }
        if (value.endsWith("%")) {
            value = value.replace("%", "");
            measureScoreElement.setCode("%");
            measureScoreElement.setUnit("%");
            measureScoreElement.setSystem(UCUM_SYSTEM);
        }
        if (value.matches("^[+\\-]?[0-9]+(\\.[0-9]*)?([eE][+\\-][0-9]*)?$")) {
            measureScoreElement.setValue(new BigDecimal(value));
        } else {
            setDataAbsent(measureScoreElement.addExtension(), value);
        }
    }

    private Quantity getMeasureScoreElement(Coding code) {
        return getGroup(code).getMeasureScore();
    }


    private IntegerType getCountElement(Coding code) {
        return getPopulation(code).getCountElement();
    }

    private MeasureReportGroupComponent getGroup(Coding code) {
        for (MeasureReportGroupComponent group: measureReport.getGroup()) {
            if (group.hasCode() && group.getCode().hasCoding(code.getSystem(), code.getCode())) {
                return group;
            }
        }
        MeasureReportGroupComponent newGroup = measureReport.addGroup();
        newGroup.getCode().addCoding(code);
        return newGroup;
    }

    private MeasureReportGroupPopulationComponent getPopulation(Coding code) {
        MeasureReportGroupComponent group = getGroupForPopulation(code);

        for (MeasureReportGroupPopulationComponent pop: group.getPopulation()) {
            if (pop.hasCode() && pop.getCode().hasCoding(code.getSystem(), code.getCode())) {
                return pop;
            }
        }
        MeasureReportGroupPopulationComponent newPop = group.addPopulation();
        newPop.getCode().addCoding(code);
        return newPop;
    }

    private MeasureReportGroupComponent getGroupForPopulation(Coding code) {
        for (Codeable codeable: codes) {
            if (codeable == null) {
                continue;
            }
            if (codeable.getComponent() instanceof MeasureGroupPopulationComponent && codeable.matches(code)) {
                MeasureGroupPopulationComponent pop = (MeasureGroupPopulationComponent) codeable.getComponent();
                MeasureGroupComponent group = (MeasureGroupComponent) pop.getUserData("parent");
                if (group != null) {
                    return getGroup(group.getCode().getCodingFirstRep());
                }
            }
        }
        return null;
    }


    private void setCount(IntegerType count, String value) {
        if (value == null) {
            value = "";
        } else {
            value = value.trim();
        }
        if (value.matches("^[0-9]+$")) {
            count.setValueAsString(value);
        } else {
            setDataAbsent(count.addExtension(), value);
        }
    }

    private void setDataAbsent(Extension ex, String value) {
        if (DATA_ABSENT_REASONS.contains(value.toLowerCase())) {
            ex.setUrl(DATA_ABSENT_REASON_URL).setValue(new CodeType(value));
        } else if (StringUtils.isBlank(value)) {
            ex.setUrl(DATA_ABSENT_REASON_URL).setValue(new CodeType("unknown"));
        } else {
            throw new IllegalArgumentException("Cannot set value to " + value);
        }
    }


    public boolean hasStrata(String[] row) {
        if (stratifierColumn >= 0) {
            return row.length >= stratifierColumn && !StringUtils.isBlank(row[stratifierColumn]);
        }
        // determine stratifier from column values
        for (String stratum: strata) {
            if (!StringUtils.isBlank(getDatumAtColumn(Arrays.asList(row), stratum))) {
                return true;
            }
        }
        return false;
    }

    public void updateMeasureReportStrata(List<String> data) {
        // The row has strata, get the stratifier encoded by this row.
        String stratifier = getStratifier(data);

        for (String group: groups) {
            MeasureReportGroupComponent g = getMeasureReportComponent(measureReport.getGroup(), group);
            MeasureGroupComponent gg = getMeasureReportComponent(measure.getGroup(), group);
            if (g == null || gg == null) {
                continue;
            }
            MeasureReportGroupStratifierComponent s = getStratifier(measure, stratifier, group, g);
            MeasureGroupStratifierComponent ss = getMeasureReportComponent(gg.getStratifier(), stratifier);

            if (s == null || ss == null) {
                // The stratifier does not apply to this measure group
                continue;
            }


            // This row identifies a unique stratum within the group that it occupies, create it.
            StratifierGroupComponent stratum = s.addStratum();
            encodeStratum(data, ss, stratum);
            setMeasureScore(stratum.getMeasureScore(), getDatumAtColumn(data, group));
            updateStrataPopulations(data, g, stratum);
        }
    }

    /**
     * Given a row, get the stratifier associated with it, or null if it cannot be determined
     * @param data  The row to get the stratifier from
     * @return  The stratifier associated with it, or null if it cannot be determined
     */
    private String getStratifier(List<String> data) {
        if (stratifierColumn >= 0) {
            return data.get(stratifierColumn);
        }

        // Get the list of stratifiers that are present
        List<String> stratumPresent =
            strata.stream().filter(s -> !StringUtils.isBlank(getDatumAtColumn(data, s))).collect(Collectors.toList());

        if (stratumPresent.isEmpty()) {
            return null;
        }

        for (MeasureGroupComponent g: measure.getGroup()) {
            for (MeasureGroupStratifierComponent comp: g.getStratifier()) {
                if (stratumPresent.stream().allMatch(s -> hasStratum(comp, s))) {
                    Coding coding = comp.getCode().getCodingFirstRep();
                    return String.format("%s#%s", coding.getSystem() == null ? "" : coding.getSystem(), coding.getCode());
                }
            }
        }
        return null;
    }
    private boolean hasStratum(MeasureGroupStratifierComponent comp, String s) {
        for (MeasureGroupStratifierComponentComponent c: comp.getComponent()) {
            if (c.getCode().getCoding().stream().anyMatch(coding -> stringMatchesCoding(s, coding))) {
                return true;
            }
        }
        return false;
    }

    private void encodeStratum(List<String> data, MeasureGroupStratifierComponent ss,
        StratifierGroupComponent stratum) {

        if (ss.getComponent().isEmpty()) {
            // TODO: Handle simple case of a stratum where code is the value of concern
            return;
        }

        for (MeasureGroupStratifierComponentComponent scc: ss.getComponent()) {
            String value = null;

            CodeableConcept cc = scc.getCode();
            StratifierGroupComponentComponent sc = stratum.addComponent().setCode(cc);
            for (Coding coding: cc.getCoding()) {
                String code = coding.getCode();
                if (headers.contains(code) && strata.contains(code)) {
                    value = getDatumAtColumn(data, coding.getCode());
                    break;
                }
            }
            if (value != null) {
                if (codeConverter != null) {
                    value = codeConverter.apply(value);
                }
                cc = new CodeableConcept();
                String system = null;
                if (value.contains("#")) {
                    system = StringUtils.substringBefore(value, "#");
                    value  = StringUtils.substringAfter(value, "#");
                }
                cc.addCoding().setCode(value).setSystem(system);
                sc.setValue(cc);
            }
        }
    }

    private MeasureReportGroupStratifierComponent getStratifier(Measure measure, String stratifier, String group,
        MeasureReportGroupComponent g) {
        MeasureReportGroupStratifierComponent s = getMeasureReportComponent(g.getStratifier(), stratifier);

        if (s == null) {
            MeasureGroupComponent gg = getMeasureReportComponent(measure.getGroup(), group);
            if (gg != null) {
                MeasureGroupStratifierComponent ss = getMeasureReportComponent(gg.getStratifier(), stratifier);
                if (ss != null) {
                    s = g.addStratifier();
                    s.addCode(ss.getCode());
                }
            }
        }
        return s;
    }


    private void updateStrataPopulations(List<String> data, MeasureReportGroupComponent g,
        StratifierGroupComponent stratum) {
        for (String population: populations) {
            MeasureReportGroupPopulationComponent p = getMeasureReportComponent(g.getPopulation(), population);
            if (p == null) {
                continue;
            }
            StratifierGroupPopulationComponent pop = stratum.addPopulation();
            pop.setCode(p.getCode());
            String value = getDatumAtColumn(data, population);
            setCount(pop.getCountElement(), value);
        }
    }


    private List<String> getCodesForComponent(Class<? extends Element> cls) {
        List<String> dataCodes = new ArrayList<>();
        for (Codeable codeable: codes) {
            if (codeable == null) {
                continue;
            }
            if (cls.isInstance(codeable.getComponent())) {
                dataCodes.add(codeable.getCode().getCode());
            }
        }
        return dataCodes;
    }

    private <T extends IBase> T getMeasureReportComponent(List<T> list, String code) {
        for (T item: list) {
            CodeableConcept cc = getCode(item);
            if (cc == null) {
                continue;
            }
            if (cc.getCoding().stream().anyMatch(c -> stringMatchesCoding(code, c))) {
                return item;
            }
        }
        return null;
    }

    private boolean stringMatchesCoding(String code, Coding coding) {
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


    private String getDatumAtColumn(List<String> data, String code) {
        for (int i = 0; i < codes.size(); i++) {
            Codeable codeable= codes.get(i);
            if (codeable == null) {
                continue;
            }
            if (stringMatchesCoding(code, codeable.getCode())) {
                return data.get(i);
            }
        }
        return null;
    }

    private static MeasureReport initializeReportFromMeasure(Measure measure, Reference subject) {
        MeasureReport measureReport = new MeasureReport();
        measureReport.getMeta().addProfile("http://hl7.org/fhir/uv/saner/StructureDefinition/PublicHealthMeasureReport");
        measureReport.setMeasure(measure.getUrl());
        measureReport.addIdentifier().setSystem("urn:ietf:rfc:3986").setValue("urn:uuid:" + UUID.randomUUID().toString());
        measureReport.setDate(new Date());
        measureReport.setSubject(subject);
        measureReport.setStatus(MeasureReportStatus.COMPLETE);
        measureReport.setType(MeasureReportType.SUMMARY);
        for (MeasureGroupComponent group: measure.getGroup()) {
            MeasureReportGroupComponent g = measureReport.addGroup();
            g.setCode(group.getCode());

            for (MeasureGroupPopulationComponent pop: group.getPopulation()) {
                MeasureReportGroupPopulationComponent p = g.addPopulation();
                p.setCode(pop.getCode());
            }
        }
        return measureReport;
    }

    public MeasureReport convert(Reader r) throws IOException {
        try (CSVReader csvReader = new CSVReader(r)) {
            // Get actual headers in CSV File
            String[] firstLine = csvReader.readNext();
            // Remove BOM from first line if present
            if (firstLine.length > 0 && firstLine[0].charAt(0) == '\uFEFF') {
                firstLine[0] = firstLine[0].substring(1);
            }
            List<String> readHeaders = Arrays.asList(firstLine);

            // Update headerData with instructions to restore a row to canonical order
            remapCSVHeaders(readHeaders);
            String[] row;
            while ((row = csvReader.readNext()) != null) {
                List<String> data = Arrays.asList(row);
                if (hasStrata(row)) {
                    updateMeasureReportStrata(data);
                } else {
                    updateMeasureReport(data);
                }
            }
            return measureReport;
        }
    }

}