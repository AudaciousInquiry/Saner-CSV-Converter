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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CanonicalType;
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
                convertCsvToResource(csvFile, new File(arg), null, Util.invertMap(columns), new FileWriter(outputFile));
                csvFile = null;
                columns.clear();
            } else if (arg.contains("=")) {
                String[] parts = arg.split("=");
                columns.put(parts[0], parts.length > 1 ? parts[1] : "");
            } else if (arg.endsWith(".xml") || arg.endsWith(".json")) {
                File f = new File(arg);
                String basename = StringUtils.substringBeforeLast(f.getName(),".");
                outputFile = new File("target", basename + ".csv");
                convertResourceToCsv(f, columns, new FileWriter(outputFile));
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

    private static <R extends Resource> R getResource(Class<R> cz, File f) {
        if (!f.exists()) {
            System.err.printf("File %s does not exist.%n", f);
            errors++;
            return null;
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

    public static void convertResourceToCsv(File f, Map<String, String> columns, Writer writer) {
        MeasureReport mr = getResource(MeasureReport.class, f);
        if (mr == null) {
            return;
        }

        PrintWriter pw = writer instanceof PrintWriter ? (PrintWriter) writer : new PrintWriter(writer);

        convertMeasureReportToCSV(mr, columns, pw);
    }

    public static void convertCsvToResource(File f, File measureFile, Reference subject, Map<String, String> columns, Writer writer) {
        Measure measure = getResource(Measure.class, measureFile);
        if (measure == null) {
            return;
        }

        MeasureReport mr = null;
        try (FileReader r = new FileReader(f, StandardCharsets.UTF_8)) {
            mr = convertCSVToMeasureReport(r, measure, subject, columns);
            mr.setId(StringUtils.substringBefore(f.getName(), "."));
        } catch (FileNotFoundException e) {
            System.err.printf("File %s could not be found.%n", f);
        } catch (IOException e) {
            System.err.printf("Error reading %s.%n", f);
        }

        writeMeasureReport(f, writer, mr);
    }

    private static void writeMeasureReport(File f, Writer writer, MeasureReport mr) {
        PrintWriter pw = writer instanceof PrintWriter ? (PrintWriter) writer : new PrintWriter(writer);
        try {
            yp.encodeResourceToWriter(mr, pw);
        } catch (DataFormatException e) {
            System.err.printf("Error converting resource %s.%n", f);
            errors++;
        } catch (IOException e) {
            System.err.printf("Error writing resource for %s.%n", f);
            errors++;
        }
    }

    /**
     * Convert a measureReport from FHIR Resource format to CSV format.
     * @param measureReport The FHIR MeasureReport to convert.
     * @param orderedHeaderMap The map from canonical headers to output header names.  Fields are reported in the order that map.entries() returns keys. See {@link java.util.LinkedHashMap}.
     * @param csvOutput The place to store the CSV Output
     * @throws CSVConversionException
     */
    public static void convertMeasureReportToCSV(MeasureReport measureReport, Map<String, String> orderedHeaderMap, Writer csvOutput) {
        ReportToCsvConverter converter = new ReportToCsvConverter(csvOutput);
        Measure measure = getMeasure(measureReport);
        if (orderedHeaderMap == null || orderedHeaderMap.isEmpty()) {
            orderedHeaderMap = converter.getCanonicalHeaderMap(measure);
        }
        List<String> headers = converter.generateHeaderRows(measure, orderedHeaderMap);
        converter.writeHeader(headers);
        List<List<String>> dataRows = converter.generateDataRows(measureReport);
        converter.writeData(dataRows);
    }

    /**
     * Given a MeasureReport resource, return a Measure describing the reporting model
     * either from the cached value, or computed from the MeasureReport.
     *
     * @param measureReport The MeasureReport
     * @return
     */
    private static Measure getMeasure(MeasureReport measureReport) {
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

    public static MeasureReport convertCSVToMeasureReport(Reader r, Measure measure, Reference subject, Map<String, String> orderedHeaderMap) throws IOException {
        CsvToReportConverter converter = new CsvToReportConverter();
        if (orderedHeaderMap == null || orderedHeaderMap.isEmpty()) {
            orderedHeaderMap = converter.getCanonicalHeaderMap(measure);
        }
        MeasureReport mr = initializeReportFromMeasure(measure, subject);

        try (CSVReader csvReader = new CSVReader(r)) {
            // Get actual headers in CSV File
            String[] firstLine = csvReader.readNext();
            // Remove BOM from first line if present
            if (firstLine.length > 0 && firstLine[0].charAt(0) == '\uFEFF') {
                firstLine[0] = firstLine[0].substring(1);
            }
            List<String> readHeaders = Arrays.asList(firstLine);

            // Update headerData with instructions to restore a row to canonical order
            converter.remapCSVHeaders(readHeaders, measure, orderedHeaderMap);
            String[] row;
            while ((row = csvReader.readNext()) != null) {
                List<String> data = Arrays.asList(row);
                if (converter.hasStrata(row)) {
                    converter.updateMeasureReportStrata(mr, measure, data);
                } else {
                    converter.updateMeasureReport(mr, data);
                }
            }
            return mr;
        }
    }

}
