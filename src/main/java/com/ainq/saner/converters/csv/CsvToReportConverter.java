package com.ainq.saner.converters.csv;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.commons.lang3.StringUtils;
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
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupComponent;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupComponentComponent;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupPopulationComponent;

import org.hl7.fhir.r4.model.Quantity;

import com.ainq.saner.util.Util;

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

    private List<Codeable> codes = new ArrayList<>();
    /** The list of codes identifying groups */
    private List<String> groups = new ArrayList<>();
    /** The list of codes identifying populations */
    private List<String> populations = new ArrayList<>();
    /** The list of codes identifying strata */
    private List<String> strata = new ArrayList<>();
    /** The headers in the CSVFile */
    private List<String> headers = new ArrayList<>();
    /** The column in which the stratifier is found */
    private int stratifierColumn = -1;

    /**
     * Given the headers from the CSV file, and the headers from the measure,
     * encode instructions on how to copy the rows of csv data into the measureReport
     *
     * @param csvHeaders       The headers in the CSV File
     * @param measure          The measure for the MeasureReport
     * @param orderedHeaderMap A map for translating header names to codes identifying measure report components
     */
    public void remapCSVHeaders(List<String> csvHeaders, Measure measure, Map<String, String> orderedHeaderMap) {
        // For each header from the CSV File
        // Provide instructions for how to get the component of the MeasureReport from the code
        headers.addAll(csvHeaders);
        int columnPos = 0;
        String stratifierCode = "#" + STRATIFIER_CODE;

        Map<String, String> invertedHeaderMap = Util.invertMap(orderedHeaderMap);
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
        }
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

    public void updateMeasureReport(MeasureReport mr, List<String> data) {
        int maxLen = Math.min(codes.size(), data.size());
        for (int columnPos = 0; columnPos < maxLen; columnPos++) {
            Codeable codeable = codes.get(columnPos);
            if (codeable == null) {
                continue;
            }
            IBase comp = codeable.getComponent();
            String value = data.get(columnPos);
            if (comp instanceof MeasureGroupComponent) {
                setMeasureScore(getMeasureScoreElement(mr, codeable.getCode()), value);
            } else if (comp instanceof MeasureGroupPopulationComponent) {
                setCount(getCountElement(mr, codeable.getCode()), value);
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

    private Quantity getMeasureScoreElement(MeasureReport mr, Coding code) {
        return getGroup(mr, code).getMeasureScore();
    }


    private IntegerType getCountElement(MeasureReport mr, Coding code) {
        return getPopulation(mr, code).getCountElement();
    }

    private MeasureReportGroupComponent getGroup(MeasureReport mr, Coding code) {
        for (MeasureReportGroupComponent group: mr.getGroup()) {
            if (group.hasCode() && group.getCode().hasCoding(code.getSystem(), code.getCode())) {
                return group;
            }
        }
        MeasureReportGroupComponent newGroup = mr.addGroup();
        newGroup.getCode().addCoding(code);
        return newGroup;
    }

    private MeasureReportGroupPopulationComponent getPopulation(MeasureReport mr, Coding code) {
        MeasureReportGroupComponent group = getGroupForPopulation(mr, code);

        for (MeasureReportGroupPopulationComponent pop: group.getPopulation()) {
            if (pop.hasCode() && pop.getCode().hasCoding(code.getSystem(), code.getCode())) {
                return pop;
            }
        }
        MeasureReportGroupPopulationComponent newPop = group.addPopulation();
        newPop.getCode().addCoding(code);
        return newPop;
    }

    private MeasureReportGroupComponent getGroupForPopulation(MeasureReport mr, Coding code) {
        for (Codeable codeable: codes) {
            if (codeable == null) {
                continue;
            }
            if (codeable.getComponent() instanceof MeasureGroupPopulationComponent && codeable.matches(code)) {
                MeasureGroupPopulationComponent pop = (MeasureGroupPopulationComponent) codeable.getComponent();
                MeasureGroupComponent group = (MeasureGroupComponent) pop.getUserData("parent");
                if (group != null) {
                    return getGroup(mr, group.getCode().getCodingFirstRep());
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
        return stratifierColumn >= 0 && row.length >= stratifierColumn && !StringUtils.isBlank(row[stratifierColumn]);
    }

    public void updateMeasureReportStrata(MeasureReport mr, Measure measure, List<String> data) {
        // The row has strata, get the stratifier encoded by this row.
        String stratifier = data.get(stratifierColumn);

        for (String group: groups) {
            MeasureReportGroupComponent g = getMeasureReportComponent(mr.getGroup(), group);
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


    private void encodeStratum(List<String> data, MeasureGroupStratifierComponent ss,
        StratifierGroupComponent stratum) {

        // TODO: Handle simple case of a stratum where code is the value of concern

        for (MeasureGroupStratifierComponentComponent scc: ss.getComponent()) {
            String value = null;

            CodeableConcept cc = scc.getCode();
            StratifierGroupComponentComponent sc = stratum.addComponent().setCode(cc);
            for (Coding coding: cc.getCoding()) {
                String code = coding.getCode();
                if (headers.contains(code) && strata.contains(code)) {
                    value = getDatumAtColumn(data, coding.getCode());
                }
            }
            if (value != null) {
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
        String codingCode = coding.getCode();
        if (code.equals(codingCode)) {
            return true;
        }
        int i = code.indexOf('#');
        if (i < 0) {
            return false;
        }
        String system = coding.hasSystem() ? "" : coding.getSystem();
        return code.substring(0, i - 1).equals(system) && code.substring(i + 1).equals(codingCode);
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

}