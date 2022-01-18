package com.ainq.saner.converters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import com.ainq.fhir.utils.YamlUtils;
import com.ainq.saner.converters.csv.CSVConversionException;
import com.ainq.saner.converters.csv.CsvToReportConverter;
import com.ainq.saner.converters.csv.ReportToCsvConverter;
import com.ainq.saner.util.Util;
import com.opencsv.CSVReader;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;

/**
 * This class performs Conversion between a MeasureReport and CSV Format
 * as defined by the HL7 SANER Implementation Guide.
 *
 * @author Keith W. Boone
 *
 */
public class SanerCSVConverter {

    private static FhirContext ctx = FhirContext.forR4();
    private static IParser jp = ctx.newJsonParser().setPrettyPrint(true);
    private static IParser xp = ctx.newXmlParser().setPrettyPrint(true);
    private static IParser yp = YamlUtils.newYamlParser(ctx).setPrettyPrint(true);
    private static int errors = 0;

    /**
     * Hide the default public constructor.
     */
    protected SanerCSVConverter() {
        // Hiding the constructor
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            args = new String[] { "src/test/resources/MeasureReport-CSVExportExample.json" };
        }
        File csvFile = null;
        File outputFile = null;
        Map<String, String> columns = new TreeMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (csvFile != null) {
                MeasureReport mr = convertCsvToResource(csvFile, new File(arg), null, Util.invertMap(columns), null);
                writeMeasureReport(new FileWriter(outputFile), mr);
                csvFile = null;
                columns.clear();
            } else if (arg.contains("=")) {
                String[] parts = arg.split("=");
                columns.put(parts[0], parts.length > 1 ? parts[1] : "");
            } else if (arg.endsWith(".xml") || arg.endsWith(".json")) {
                File f = new File(arg);
                String basename = StringUtils.substringBeforeLast(f.getName(),".");
                outputFile = new File("target", basename + ".csv");
                MeasureReport mr = getResource(MeasureReport.class, f);
                convertMeasureReportToCSV(mr, columns, new FileWriter(outputFile), true);
                columns.clear();
            } else if (arg.endsWith(".csv")) {
                csvFile = new File(arg);
                String basename = StringUtils.substringBeforeLast(csvFile.getName(),".");
                outputFile = new File("target", basename + ".json");
            }
        }
        if (csvFile != null) {
            System.err.println("No measure provided for CSV conversion");
            errors++;
        }

        System.out.printf("%d errors%n", errors);
        System.exit(errors);
    }

    protected static <R extends Resource> R getResource(Class<R> cz, File f) throws IOException {
        if (!f.exists()) {
            errors++;
            throw new IOException("File " + f + " does not exist.");
        }
        try (FileReader r = new FileReader(f)) {
            if (f.getName().endsWith(".xml")) {
                return xp.parseResource(cz, r);
            } else if (f.getName().endsWith(".yaml")) {
                return yp.parseResource(cz, r);
            } else {
                return jp.parseResource(cz, r);
            }
        } catch (FileNotFoundException e) {
            System.err.printf("File %s could not be found.%n", f);
        } catch (IOException e) {
            System.err.printf("Error reading %s.%n", f);
        } catch (DataFormatException e) {
            System.err.printf("Error parsing %s.%n", f);
        }
        errors++;
        return null;
    }

    public static MeasureReport convertCsvToResource(
        File f, File measureFile, Reference subject, Map<String, String> columns, UnaryOperator<String> converter) throws IOException {
        Measure measure = getResource(Measure.class, measureFile);
        MeasureReport mr = null;
        try (FileReader r = new FileReader(f, StandardCharsets.UTF_8)) {
            mr = convertCSVToMeasureReport(r, measure, subject, columns, converter);
            mr.setId(StringUtils.substringBefore(f.getName(), "."));
        }
        return mr;
    }

    protected static void writeMeasureReport(Writer writer, MeasureReport mr) throws IOException {
        try {
            yp.encodeResourceToWriter(mr, writer);
        } catch (DataFormatException | IOException e) {
            errors++;
            throw e;
        }
    }

    /**
     * Convert a measureReport from FHIR Resource format to CSV format.
     * @param measureReport The FHIR MeasureReport to convert.
     * @param orderedHeaderMap The map from canonical headers to output header names.  Fields are reported in the order that map.entries() returns keys. See {@link java.util.LinkedHashMap}.
     * @param csvOutput The place to store the CSV Output
     * @param simplify
     * @throws CSVConversionException
     */
    public static void convertMeasureReportToCSV(MeasureReport measureReport, Map<String, String> orderedHeaderMap, Writer csvOutput, boolean simplify) {
        ReportToCsvConverter converter = new ReportToCsvConverter(csvOutput, measureReport, orderedHeaderMap);
        converter.setSimplifyCodes(simplify);
        converter.convert();
    }

    public static MeasureReport convertCSVToMeasureReport(
        Reader r, Measure measure, Reference subject, Map<String, String> orderedHeaderMap, UnaryOperator<String> codeConverter) throws IOException {
        CsvToReportConverter converter = new CsvToReportConverter(measure, subject, orderedHeaderMap);
        if (codeConverter != null) {
            converter.setConverter(codeConverter);
        }
        return converter.convert(r);
    }

}
