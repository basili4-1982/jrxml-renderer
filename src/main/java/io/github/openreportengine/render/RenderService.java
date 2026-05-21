package io.github.openreportengine.render;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.openreportengine.datasource.DataSourceFactory;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.xml.ReportLoader;
import net.sf.jasperreports.pdf.JRPdfExporter;
import net.sf.jasperreports.poi.export.JRXlsExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RenderService {

    static {
        System.setProperty("net.sf.jasperreports.awt.ignore.missing.font", "true");
        Logger.getLogger("").setLevel(Level.OFF);
    }

    public ByteArrayOutputStream render(RenderRequest request) throws Exception {
        byte[] jrxmlBytes = request.jrxml.getBytes("UTF-8");
        JasperReportsContext ctx = DefaultJasperReportsContext.getInstance();

        // Find ReportLoaders via ServiceLoader
        ServiceLoader<ReportLoader> loader = ServiceLoader.load(ReportLoader.class);
        JasperDesign design = null;
        int count = 0;

        for (ReportLoader reportLoader : loader) {
            count++;
            System.err.println("Trying ReportLoader: " + reportLoader.getClass().getName());
            Optional<JasperDesign> opt = reportLoader.loadReport(ctx, jrxmlBytes);
            if (opt.isPresent()) {
                design = opt.get();
                System.err.println("  -> SUCCESS");
                break;
            }
            System.err.println("  -> EMPTY");
        }
        
        System.err.println("ReportLoaders tried: " + count);

        if (design == null) {
            throw new JRException("Unable to load report: no ReportLoader accepted the format");
        }

        JasperReport jasperReport = JasperCompileManager.compileReport(design);

        Map<String, Object> params = convertParameters(request.parameters);
        Connection connection = null;
        if (request.dataSource != null && request.dataSource.isSql()) {
            DataSource ds = DataSourceFactory.create(request.dataSource);
            connection = ds.getConnection();
        }

        JasperPrint jasperPrint;
        try {
            if (connection != null) {
                jasperPrint = JasperFillManager.fillReport(jasperReport, params, connection);
            } else {
                jasperPrint = JasperFillManager.fillReport(jasperReport, params, new JREmptyDataSource());
            }
        } finally {
            if (connection != null) connection.close();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        switch (request.format) {
            case "pdf":
                JRPdfExporter pdfExporter = new JRPdfExporter();
                pdfExporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                pdfExporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos));
                pdfExporter.exportReport();
                break;
            case "xlsx":
                JRXlsExporter xlsxExporter = new JRXlsExporter();
                xlsxExporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                xlsxExporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos));
                xlsxExporter.exportReport();
                break;
            case "docx":
                JRDocxExporter docxExporter = new JRDocxExporter();
                docxExporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                docxExporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos));
                docxExporter.exportReport();
                break;
            case "csv":
                JRCsvExporter csvExporter = new JRCsvExporter();
                csvExporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                csvExporter.setExporterOutput(new SimpleWriterExporterOutput(baos));
                csvExporter.exportReport();
                break;
        }
        return baos;
    }

    private Map<String, Object> convertParameters(JsonNode params) {
        Map<String, Object> result = new HashMap<>();
        if (params == null) return result;
        Iterator<Map.Entry<String, JsonNode>> fields = params.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode val = entry.getValue();
            if (val.isTextual()) result.put(entry.getKey(), val.asText());
            else if (val.isInt()) result.put(entry.getKey(), val.asInt());
            else if (val.isLong()) result.put(entry.getKey(), val.asLong());
            else if (val.isDouble()) result.put(entry.getKey(), val.asDouble());
            else if (val.isBoolean()) result.put(entry.getKey(), val.asBoolean());
            else result.put(entry.getKey(), val.asText());
        }
        return result;
    }
}
