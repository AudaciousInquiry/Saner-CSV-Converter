package com.ainq.saner.converters.csv;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupComponent;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupPopulationComponent;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupStratifierComponent;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupComponent;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupComponentComponent;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupPopulationComponent;
import org.hl7.fhir.r4.model.Quantity;

public class ReportToCsvConverter extends AbstractConverter {
    protected static final boolean SIMPLIFY_CODES = false;
    private final MeasureReport measureReport;
    private PrintWriter csvOutput;

    public ReportToCsvConverter(Writer csvOutput, MeasureReport measureReport, Map<String, String> orderedHeaderMap) {
        super(getMeasure(measureReport), orderedHeaderMap);
        this.measureReport = measureReport;
        this.csvOutput = csvOutput instanceof PrintWriter ? (PrintWriter) csvOutput : new PrintWriter(csvOutput);
    }

    private static String getMeasureScore(Quantity measureScore) {
        if (measureScore == null || measureScore.isEmpty()) {
            return "";
        }
        if (measureScore.hasValue()) {
            String value = measureScore.getValue().toPlainString();
            if ("%".equals(measureScore.getUnit()) || "%".equals(measureScore.getCode())) {
                return value + "%";
            }
            return value;
        }
        Extension dataAbsentReason = measureScore.getExtensionByUrl(DATA_ABSENT_REASON_EXTENSION_URL);
        if (dataAbsentReason == null || !dataAbsentReason.hasValue()) {
            return "";
        }
        Coding coding = (Coding) dataAbsentReason.getValue();
        return coding.hasCode() ? coding.getCode() : "";
    }

    private void getStratumData(String[] fields, int measurePos, int pos, StratifierGroupComponent stratum) {
        if (!stratum.hasComponent()) {
            // If there are no components, output the value for this stratum.
            fields[pos] = codeToString(stratum.getValue());
        }
        for (StratifierGroupComponentComponent comp: stratum.getComponent()) {
            CodeableConcept componentCode = comp.getCode();
            pos = getFieldPosition(componentCode);
            fields[pos] = codeToString(comp.getValue());
        }
        for (StratifierGroupPopulationComponent population: stratum.getPopulation()) {
            CodeableConcept populationCode = population.getCode();
            pos = getFieldPosition(populationCode);
            fields[pos] = getPopulationCount(population.getCountElement());
        }
        fields[measurePos] = getMeasureScore(stratum.getMeasureScore());
    }

    private static String getPopulationCount(IntegerType countElement) {
        if (countElement == null || countElement.isEmpty()) {
            return "";
        }

        if (countElement.hasValue()) {
            return countElement.getValueAsString();
        }

        Extension dataAbsentReason = countElement.getExtensionByUrl(DATA_ABSENT_REASON_EXTENSION_URL);
        if (dataAbsentReason == null || !dataAbsentReason.hasValue()) {
            return "";
        }
        return dataAbsentReason.getValue().toString();
    }

    public List<List<String>> generateDataRows() {
        List<List<String>> data = new ArrayList<>();
        // Report on unstratified measure
        data.add(getMeasureData());
        // Report on strata
        getStrataData(data);
        return data;
    }

    public List<String> generateHeaderRows() {
        List<String> headers = getCanonicalHeaders();
        headers = remapHeaders(headers);
        return headers;
    }

    private List<String> getMeasureData() {
        List<String> data = new ArrayList<>();
        for (int i = 0; i < getNumStrataColumns(); i++) {
            data.add("");
        }
        // For each group in the measure report
        for (MeasureReportGroupComponent group: measureReport.getGroup()) {
            //  For each population in the group
            for (MeasureReportGroupPopulationComponent population: group.getPopulation()) {
                data.add(getPopulationCount(population.getCountElement()));
            }
            // Get the Measure
            data.add(getMeasureScore(group.getMeasureScore()));
        }
        return data;
    }

    private void getStrataData(List<List<String>> data) {
        // For each distinct stratifier.code in each group
        for (MeasureReportGroupComponent group: measureReport.getGroup()) {
            int measurePos = getFieldPosition(group.getCode());
            for (MeasureReportGroupStratifierComponent stratifier: group.getStratifier()) {
                CodeableConcept stratifierCode = stratifier.getCode().get(0);
                int pos = getFieldPosition(STRATIFIER);

                //  For each distinct stratifier.component.code of each stratifier
                for (StratifierGroupComponent stratum: stratifier.getStratum()) {
                    String[] fields = new String[getNumFields()];
                    Arrays.fill(fields, "");
                    fields[pos] = codeToString(stratifierCode);

                    getStratumData(fields, measurePos, pos, stratum);
                    data.add(Arrays.asList(fields));
                }
            }
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
    protected List<String> remapHeaders(List<String> headers) {

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
        return reorderedHeaders;
    }

    private List<String> reorderData(List<String> list) {
        List<String> newList = new ArrayList<>(getNumFields());
        for (int i = 0; i < getNumFields(); i++) {
            String value = "";
            int originalPosition = getOriginalPosition(i);
            if (originalPosition < list.size() && originalPosition >= 0) {
                value = list.get(originalPosition);
            }
            while (i >= newList.size()) {
                newList.add("");
            }
            newList.set(i, value);
        }
        return newList;
    }

    public void writeData(List<List<String>> dataRows) {
        for (List<String> list: dataRows) {
            writeHeader(reorderData(list));
        }
    }

    public void writeHeader(List<String> headers) {
        boolean first = true;
        for (String header: headers) {
            if (first) {
                first = false;
            } else {
                csvOutput.print(",");
            }
            String value = header;
            if (codeConverter != null) {
                value = codeConverter.apply(value);
            }
            csvOutput.print(StringEscapeUtils.escapeCsv(value));
        }
        csvOutput.println();

    }

    public void convert() {
        writeHeader(generateHeaderRows());
        writeData(generateDataRows());
    }

    public void setSimplifyCodes(boolean simplify) {
        if (simplify) {
            setConverter(this::simplifyingConverter);
        } else {
            setConverter(null);
        }
    }
}