package com.ainq.saner.converters;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupComponent;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupPopulationComponent;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupStratifierComponent;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupComponent;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupComponentComponent;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupPopulationComponent;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ainq.saner.converters.csv.AbstractConverter;
import com.ainq.saner.converters.csv.Util;
import com.opencsv.CSVReader;

class SanerCSVConverterTest extends SanerCSVConverter {
    private static final String TEST_BASE = "src/test/resources/";
    private static final String TEST_REPORT_BASE = TEST_BASE + "MeasureReport-";
    private static final String TEST_MEASURE_BASE = TEST_BASE + "Measure-";
    private static String[] TEST_HEADERS = {
        "stratifier",
        "age",
        "gender",
        "race",
        "ethnicity",
        "totalOrdersIncrease",
        "totalTestResultsIncrease",
        "positiveIncrease",
        "positiveIncreasePercent",
        "totalOrders",
        "rejected",
        "totalTestResults",
        "positive",
        "allReports",
        "latestReports",
        "positivePercent"
    };


    @ParameterizedTest
    @CsvSource(value = {
        "CSVExportExample,CSVExportExample,true",
        "CSVExportExample,CSVExportExample1,false"
    })
    void testToCsv(String idInput, String idOutput, boolean simplify) throws IOException {
        execToCsv(idInput, idOutput, null, simplify);
    }

    private void execToCsv(String idInput, String idOutput, Map<String, String> map, boolean simplify) throws IOException {
        File f = new File(TEST_REPORT_BASE + idInput + ".yaml");
        String basename = StringUtils.substringBeforeLast(f.getName(),".");
        File outputFile = new File("target", basename + ".csv");

        try (FileWriter fw = new FileWriter(outputFile)) {
            MeasureReport mr = getResource(MeasureReport.class, f);
            convertMeasureReportToCSV(mr, map, fw, simplify);
        }
        compareFiles(outputFile, new File(TEST_REPORT_BASE + idOutput + ".csv"), true);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "CSVExportExample,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1,CSVExportExample,FEMADailyHospitalCOVID19Reporting"
    })

    void testToResource(String idInput, String idOutput, String measureId) throws IOException {
        execToResource(idInput, idOutput, measureId, null, null);
    }

    private String defaultCodeSystemConverter(String s) {
        return s.contains("#") ? s : ("http://example.com/foo#" + s);
    }

    private void execToResource(String idInput, String idOutput, String measureId, Reference subject,
        Map<String, String> headerMap) throws IOException {
        File f = new File(TEST_REPORT_BASE + idInput + ".csv");
        File measureFile = new File(TEST_MEASURE_BASE + measureId + ".json");
        String basename = StringUtils.substringBeforeLast(f.getName(),".");
        File outputFile = new File("target", basename + ".yaml");
        File compareFile = new File(TEST_REPORT_BASE + idOutput + ".yaml");

        MeasureReport originalMr = getResource(MeasureReport.class, compareFile);

        try (FileWriter fw = new FileWriter(outputFile)) {
            MeasureReport mr = convertCsvToResource(f, measureFile, subject, headerMap, this::defaultCodeSystemConverter);

            // Do some cleanup for diffs that do not matter
            mr.setId(originalMr.getId());
            mr.setSubject(originalMr.getSubject());
            mr.setDateElement(originalMr.getDateElement());
            mr.setPeriod(originalMr.getPeriod());
            mr.getIdentifier().clear();
            mr.getIdentifier().addAll(originalMr.getIdentifier());

            writeMeasureReport(fw, mr);
        }
        compareFiles(outputFile, compareFile, true);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "CSVExportExample-deleteCol-stratifier,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-age,CSVExportExample-deleteCol-age,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-gender,CSVExportExample-deleteCol-gender,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-race,CSVExportExample-deleteCol-race,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-ethnicity,CSVExportExample-deleteCol-ethnicity,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-totalOrdersIncrease,CSVExportExample-deleteCol-totalOrdersIncrease,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-totalTestResultsIncrease,CSVExportExample-deleteCol-totalTestResultsIncrease,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-positiveIncrease,CSVExportExample-deleteCol-positiveIncrease,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-positiveIncreasePercent,CSVExportExample-deleteCol-positiveIncreasePercent,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-totalOrders,CSVExportExample-deleteCol-totalOrders,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-rejected,CSVExportExample-deleteCol-rejected,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-totalTestResults,CSVExportExample-deleteCol-totalTestResults,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-positive,CSVExportExample-deleteCol-positive,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-allReports,CSVExportExample-deleteCol-allReports,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-latestReports,CSVExportExample-deleteCol-latestReports,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-positivePercent,CSVExportExample-deleteCol-positivePercent,FEMADailyHospitalCOVID19Reporting",

        "CSVExportExample1-deleteCol-stratifier,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-age,CSVExportExample-deleteCol-age,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-gender,CSVExportExample-deleteCol-gender,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-race,CSVExportExample-deleteCol-race,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-ethnicity,CSVExportExample-deleteCol-ethnicity,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-totalOrdersIncrease,CSVExportExample-deleteCol-totalOrdersIncrease,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-totalTestResultsIncrease,CSVExportExample-deleteCol-totalTestResultsIncrease,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-positiveIncrease,CSVExportExample-deleteCol-positiveIncrease,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-positiveIncreasePercent,CSVExportExample-deleteCol-positiveIncreasePercent,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-totalOrders,CSVExportExample-deleteCol-totalOrders,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-rejected,CSVExportExample-deleteCol-rejected,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-totalTestResults,CSVExportExample-deleteCol-totalTestResults,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-positive,CSVExportExample-deleteCol-positive,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-allReports,CSVExportExample-deleteCol-allReports,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-latestReports,CSVExportExample-deleteCol-latestReports,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-positivePercent,CSVExportExample-deleteCol-positivePercent,FEMADailyHospitalCOVID19Reporting",

    })
    void testToResourceWithDeletedColumn(String idInput, String idOutput, String measureId) throws IOException {
        testToResource(idInput, idOutput, measureId);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "CSVExportExample-deleteCol-stratifier,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-age,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-gender,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-race,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-ethnicity,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-totalOrdersIncrease,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-totalTestResultsIncrease,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-positiveIncrease,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-positiveIncreasePercent,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-totalOrders,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-rejected,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-totalTestResults,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-positive,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-allReports,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-latestReports,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-positivePercent,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",

        "CSVExportExample1-deleteCol-stratifier,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-age,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-gender,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-race,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-ethnicity,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-totalOrdersIncrease,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-totalTestResultsIncrease,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-positiveIncrease,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-positiveIncreasePercent,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-totalOrders,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-rejected,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-totalTestResults,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-positive,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-allReports,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-latestReports,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-positivePercent,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting"
    })
    void testToCsvWithDeletedColumn(String idOutput, boolean simplify, String idInput, String measureId) throws IOException {
        Map<String, String> deletedHeaderMap = new LinkedHashMap<String, String>();
        String col = StringUtils.substringAfter(idOutput, "-deleteCol-");

        for (int i = 0; i < TEST_HEADERS.length; i++) {
            if (!col.equals(TEST_HEADERS[i])) {
                deletedHeaderMap.put(TEST_HEADERS[i], TEST_HEADERS[i]);
            }
        }
        execToCsv(idInput, idOutput, deletedHeaderMap, simplify);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "CSVExportExample,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1,false, CSVExportExample,FEMADailyHospitalCOVID19Reporting"
    })
    void testToCsvWithReversedColumns(String idOutput, boolean simplify, String idInput, String measureId) throws IOException {

        Map<String, String> headerMap = new LinkedHashMap<String, String>();

        for (int i = TEST_HEADERS.length - 1; i >= 0 ; i--) {
            headerMap.put(TEST_HEADERS[i], TEST_HEADERS[i]);
        }
        execToCsv(idInput, idOutput + "-reversed", headerMap, simplify);
    }


    @ParameterizedTest
    @CsvSource(value = {
        "CSVExportExample,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1,false, CSVExportExample,FEMADailyHospitalCOVID19Reporting"
    })
    void testToCsvWithRenamedColumns(String idOutput, boolean simplify, String idInput, String measureId) throws IOException {
        execToCsv(idInput, idOutput + "-renamed", getRenamedHeaderMap(), simplify);
    }

    private Map<String, String> getRenamedHeaderMap() {
        Map<String, String> headerMap = new LinkedHashMap<String, String>();

        for (int i = 0; i < TEST_HEADERS.length ; i++) {
            headerMap.put(TEST_HEADERS[i], "header" + (i+1));
        }
        return headerMap;
    }

    @ParameterizedTest
    @CsvSource(value = {
        "CSVExportExample,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1,CSVExportExample,FEMADailyHospitalCOVID19Reporting"
    })
    void testToResourceWithExtraColumn(String idInput, String idOutput, String measureId) throws IOException {
        testToResource(idInput + "-extra", idOutput, measureId);
    }


    @ParameterizedTest
    @CsvSource(value = {
        "CSVExportExample,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1,CSVExportExample,FEMADailyHospitalCOVID19Reporting"
    })
    void testToResourceReversedColumns(String idInput, String idOutput, String measureId) throws IOException {
        testToResource(idInput + "-reversed", idOutput, measureId);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "CSVExportExample,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1,CSVExportExample,FEMADailyHospitalCOVID19Reporting"
    }) void testToResourceRenamedColumns(String idInput, String idOutput, String measureId) throws IOException {
        execToResource(idInput + "-renamed", idOutput, measureId, null, getRenamedHeaderMap());
    }

    @ParameterizedTest
    @CsvSource(value = {
        "CSVExportExample",
        "CSVExportExample1"
    })
    @Disabled("This is a tool to prepare test files, not really a test")
    /**
     * This is a utility method to prepare test files for CSV Conversion.
     * It permutes a baseline CSV/MeasureReport pair into a colection
     * of CSV and MeasureReport files which represent what should happen
     * when a reportable component is deleted from the CSV/MeasureReport,
     * when the order of CSV output is altered, introducing an extra column
     * into the CSV file, or renaming fields in the CSV file.
     *
     * The resulting output produces additional test inputs for other unit tests
     *
     * @param id    The Id of the base measure and CSV files to permute
     * @throws IOException  If an IO error occurs
     */
    void permuteCsvFile(String id) throws IOException {
        File f = new File(TEST_REPORT_BASE + id + ".csv");
        List<String[]> rows = null;
        try (CSVReader r = new CSVReader(new FileReader(f))) {
            rows = r.readAll();
        }
        // Remove any leading Unicode BOM if present
        String firstCell = rows.get(0)[0];
        if (firstCell.charAt(0) == '\uFEFF') {
            rows.get(0)[0] = firstCell.substring(1);
        }

        String mrId = id.endsWith("1") ? id.substring(0, id.length() - 1) : id;
        MeasureReport mr = yp.parseResource(MeasureReport.class, new FileReader(TEST_REPORT_BASE + mrId + ".yaml"));

        int columns = rows.get(0).length;
        for (int i = 0; i < columns; i++) {
            File outputFile = new File(TEST_REPORT_BASE + id + "-deleteCol-" + TEST_HEADERS[i] + ".csv");
            int k = i;
            mutateCsv(outputFile, rows, (String rr[], Boolean b) -> columnDeleter(rr, b, k));
            // Create measure files for Ftest case
            MeasureReport adjusted = removeFieldFromMeasureReport(mr, TEST_HEADERS[i]);
            try (FileWriter fw = new FileWriter(TEST_REPORT_BASE + id + "-deleteCol-" + TEST_HEADERS[i] + ".yaml")) {
                yp.encodeResourceToWriter(adjusted, fw);
            }
        }

        mutateCsv(new File(TEST_REPORT_BASE + id + "-reversed.csv"), rows, this::rowReverser);
        mutateCsv(new File(TEST_REPORT_BASE + id + "-extra.csv"), rows, this::rowAdder);
        mutateCsv(new File(TEST_REPORT_BASE + id + "-renamed.csv"), rows, this::renamer);
        assertTrue(true);
    }

    private void mutateCsv(File outputFile, List<String[]> rows, BiFunction<String[], Boolean, String[]> mutator) throws IOException {
        try (FileWriter fw = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
            boolean firstRow = true;
            for (String row[]: rows) {
                row = mutator.apply(row, firstRow);
                firstRow = false;
                boolean first = true;
                for (String col: row) {
                    if (first) {
                        first = false;
                    } else {
                        fw.write(',');
                    }
                    fw.write(StringEscapeUtils.escapeCsv(col));
                }
                fw.write('\n');
            }
        }
    }

    private MeasureReport removeFieldFromMeasureReport(MeasureReport mr, String column) {
        if (AbstractConverter.STRATIFIER.getCodingFirstRep().getCode().equals(column)) {
            return mr;
        }
        MeasureReport adjusted = mr.copy();
        // column represents a code that is part of the measure report.
        // It is either a group, a population in a group, or the code for a stratifer component.
        switch (column) {
        case "age":
        case "gender":
        case "race":
        case "ethnicity":
            removeStratifier(adjusted, column);
            break;
        case "totalOrdersIncrease":
        case "totalTestResultsIncrease":
        case "positiveIncrease":
            removePopulation(adjusted, column);
            break;
        case "positiveIncreasePercent":
            removeGroup(adjusted, column);
            break;
        case "totalOrders":
        case "rejected":
        case "totalTestResults":
        case "positive":
        case "allReports":
        case "latestReports":
            removePopulation(adjusted, column);
            break;
        case "positivePercent":
            removeGroup(adjusted, column);
            break;
        }
        return adjusted;
    }

    private void removeStratifier(MeasureReport adjusted, String column) {
        for (MeasureReportGroupComponent g: adjusted.getGroup()) {
            for (MeasureReportGroupPopulationComponent p: g.getPopulation()) {
                for (MeasureReportGroupStratifierComponent stratifier: g.getStratifier()) {
                    for (StratifierGroupComponent strata : stratifier.getStratum()) {
                        for (StratifierGroupComponentComponent comp: strata.getComponent()) {
                            if (comp.getCode().getCoding().stream().anyMatch(s -> Util.stringMatchesCoding(column, s))) {
                                comp.setValue(null);
                            }
                        }
                    }
                }
            }
        }
    }

    private void removePopulation(MeasureReport adjusted, String column) {
        for (MeasureReportGroupComponent g: adjusted.getGroup()) {
            for (MeasureReportGroupPopulationComponent p: g.getPopulation()) {
                if (p.getCode().getCoding().stream().anyMatch(s -> Util.stringMatchesCoding(column, s))) {
                    p.setCountElement(null);
                    for (MeasureReportGroupStratifierComponent stratifier: g.getStratifier()) {
                        for (StratifierGroupComponent strata : stratifier.getStratum()) {
                            List<StratifierGroupPopulationComponent> removePop = new ArrayList();
                            for (StratifierGroupPopulationComponent pop: strata.getPopulation()) {
                                if (pop.getCode().getCoding().stream().anyMatch(s -> Util.stringMatchesCoding(column, s))) {
                                    removePop.add(pop);
                                }
                            }
                            strata.getPopulation().removeAll(removePop);
                        }
                    }
                    return;
                }
            }
        }
    }

    private void removeGroup(MeasureReport adjusted, String column) {
        for (MeasureReportGroupComponent g: adjusted.getGroup()) {
            if (g.getCode().getCoding().stream().anyMatch(s -> Util.stringMatchesCoding(column, s))) {
                // Set measure score to null for group
                g.setMeasureScore(null);

                // for each stratifier in group, set measure score to null
                for (MeasureReportGroupStratifierComponent stratifier: g.getStratifier()) {
                    for (StratifierGroupComponent strata : stratifier.getStratum()) {
                        strata.setMeasureScore(null);
                    }
                }
                return;
            }
        }
    }

    private String[] columnDeleter(String rows[], boolean firstRow, int i) {
        List<String> result = new ArrayList<>();
        for (int k = 0; k < rows.length; k++) {
            if (k != i) {
                result.add(rows[k]);
            }
        }
        return result.toArray(new String[result.size()]);
    }

    private String[] rowReverser(String rows[], boolean firstRow) {
        String[] newRows = new String[rows.length];
        for (int i = 0, k = rows.length - 1; i <= k; i++, k--) {
            newRows[i] = rows[k];
            newRows[k] = rows[i];
        }
        return newRows;
    }

    private String[] rowAdder(String rows[], boolean firstRow) {
        String[] newRow = Arrays.copyOf(rows, rows.length + 1);
        newRow[rows.length] = firstRow ? "extra" : "999.999";
        return newRow;
    }

    private String[] renamer(String rows[], boolean firstRow) {
        if (firstRow) {
            for (int i = 0; i < rows.length; i++) {
                @SuppressWarnings("unused")
                String old = rows[i];
                rows[i] = "header" + (i+1);
                //System.out.println(old + "=" + rows[i]);
            }
        }
        return rows;
    }


    private static void compareFiles(File actual, File expected, boolean strip) throws IOException {
        String actualValue = FileUtils.readFileToString(actual, StandardCharsets.UTF_8).replaceFirst("\uFEFF", "");
        String expectedValue = FileUtils.readFileToString(expected, StandardCharsets.UTF_8).replaceFirst("\uFEFF", "");
        String ext = StringUtils.substringAfterLast(expected.getName(), ".");

        if (strip) {
            switch (ext) {
            case "yaml":
                actualValue = stripDisplayAndTextLines(actualValue);
                expectedValue = stripDisplayAndTextLines(expectedValue);
                break;
            case "csv":
                actualValue = stripExtraCommas(actualValue);
                expectedValue = stripExtraCommas(expectedValue);
                break;
            default:
                break;
            }
        }
        assertEquals(expectedValue, actualValue);
    }

    private static String stripDisplayAndTextLines(String value) {
        String[] lines = value.split("[\\r\\n]+");
        StringBuilder newValue = new StringBuilder();
        for (String line: lines) {
            if (!line.matches("^\\s*(display|text):.*$")) {
                newValue.append(line).append('\n');
            }
        }
        return newValue.toString();
    }


    private static String stripExtraCommas(String value) {
        String[] lines = value.split(",*[\\r\\n]+");
        StringBuilder newValue = new StringBuilder();
        for (String line: lines) {
            newValue.append(line).append('\n');
        }
        return newValue.toString();
    }

}
