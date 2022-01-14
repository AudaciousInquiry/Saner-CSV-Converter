package com.ainq.saner.converters;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SanerCSVConverterTest extends SanerCSVConverter {
    private static final String TEST_BASE = "src/test/resources/";
    private static final String TEST_REPORT_BASE = TEST_BASE + "MeasureReport-";
    private static final String TEST_MEASURE_BASE = TEST_BASE + "Measure-";

    @ParameterizedTest
    @CsvSource(value = {
        "CSVExportExample,CSVExportExample"
    })
    void testToCsv(String idInput, String idOutput) throws IOException {
        File f = new File(TEST_REPORT_BASE + idInput + ".json");
        String basename = StringUtils.substringBeforeLast(f.getName(),".");
        File outputFile = new File("target", basename + ".csv");

        try (FileWriter fw = new FileWriter(outputFile)) {
            convertResourceToCsv(f, null, fw);
        }
        compareFiles(outputFile, new File(TEST_REPORT_BASE + idOutput + ".csv"), false);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "CSVExportExample,CSVExportExample,CSVExportExampleFEMADailyHospitalCOVID19Reporting",
        "CSVExportExample1,CSVExportExample,CSVExportExampleFEMADailyHospitalCOVID19Reporting"
    })
    void testToResource(String idInput, String idOutput, String measureId) throws IOException {
        File f = new File(TEST_REPORT_BASE + idInput + ".csv");
        File measureFile = new File(TEST_MEASURE_BASE + measureId + ".json");
        String basename = StringUtils.substringBeforeLast(f.getName(),".");
        File outputFile = new File("target", basename + ".yaml");

        try (FileWriter fw = new FileWriter(outputFile)) {
            convertCsvToResource(f, measureFile, null, null, fw);
        }
        compareFiles(outputFile, new File(TEST_REPORT_BASE + idOutput + ".yaml"), true);
    }

    private static void compareFiles(File actual, File expected, boolean strip) throws IOException {
        String actualValue = FileUtils.readFileToString(actual, StandardCharsets.UTF_8).replaceFirst("\uFEFF", "");
        String expectedValue = FileUtils.readFileToString(expected, StandardCharsets.UTF_8).replaceFirst("\uFEFF", "");
        if (strip) {
            actualValue = stripDisplayAndTextLines(actualValue);
            expectedValue = stripDisplayAndTextLines(expectedValue);
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
}
