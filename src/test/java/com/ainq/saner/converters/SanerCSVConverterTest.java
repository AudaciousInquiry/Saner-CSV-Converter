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
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
        File f = new File(TEST_REPORT_BASE + idInput + ".json");
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
        "CSVExportExample-deleteCol-1,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-2,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-3,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-4,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-5,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-6,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-7,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-8,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-9,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-10,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-11,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-12,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-13,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-14,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-15,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-16,CSVExportExample,FEMADailyHospitalCOVID19Reporting",

        "CSVExportExample1-deleteCol-1,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-2,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-3,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-4,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-5,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-6,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-7,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-8,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-9,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-10,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-11,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-12,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-13,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-14,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-15,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-16,CSVExportExample,FEMADailyHospitalCOVID19Reporting"

    })
    void testToResourceWithDeletedColumn(String idInput, String idOutput, String measureId) throws IOException {
        testToResource(idInput, idOutput, measureId);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "CSVExportExample-deleteCol-1,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-2,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-3,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-4,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-5,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-6,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-7,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-8,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-9,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-10,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-11,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-12,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-13,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-14,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-15,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample-deleteCol-16,true,CSVExportExample,FEMADailyHospitalCOVID19Reporting",

        "CSVExportExample1-deleteCol-1,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-2,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-3,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-4,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-5,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-6,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-7,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-8,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-9,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-10,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-11,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-12,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-13,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-14,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-15,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1-deleteCol-16,false,CSVExportExample,FEMADailyHospitalCOVID19Reporting"
    })
    void testToCsvWithDeletedColumn(String idOutput, boolean simplify, String idInput, String measureId) throws IOException {
        Map<String, String> deletedHeaderMap = new LinkedHashMap<String, String>();

        for (int i = 0; i < TEST_HEADERS.length; i++) {
            String col = "-" + Integer.toString(i + 1);
            if (!idOutput.endsWith(col)) {
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

        int columns = rows.get(0).length;
        for (int i = 0; i < columns; i++) {
            File outputFile = new File(TEST_REPORT_BASE + id + "-deleteCol-" + (i + 1) + ".csv");
            int k = i;
            mutateCsv(outputFile, rows, (String rr[], Boolean b) -> columnDeleter(rr, b, k));
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
