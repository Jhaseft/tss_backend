package com.jhaseft.tss.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class FileReaderService {

    public ParsedFile parse(MultipartFile file) throws IOException {
        String name = Objects.requireNonNull(file.getOriginalFilename()).toLowerCase();
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            return parseExcel(file);
        }
        return parseCsv(file);
    }

    // ── CSV ──────────────────────────────────────────────────────────────────

    private ParsedFile parseCsv(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String content = decodeContent(bytes);

        // Normaliza saltos de línea
        content = content.replace("\r\n", "\n").replace("\r", "\n");

        // Detecta separador
        String firstLine = content.lines().findFirst().orElse("");
        char sep = countChar(firstLine, ';') >= countChar(firstLine, ',') ? ';' : ',';

        CSVFormat format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setDelimiter(sep)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true)
                .build();

        List<String> headers = new ArrayList<>();
        List<Map<String, String>> rows = new ArrayList<>();

        try (CSVParser parser = CSVParser.parse(content, format)) {
            headers.addAll(parser.getHeaderNames());
            for (CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                for (String h : headers) {
                    try {
                        row.put(h, record.get(h) == null ? "" : record.get(h).trim());
                    } catch (Exception e) {
                        row.put(h, "");
                    }
                }
                rows.add(row);
            }
        }

        return new ParsedFile(headers, rows);
    }

    // Intenta UTF-8; si hay muchos caracteres mojibake (Ã seguido de continuación), usa Latin-1
    private String decodeContent(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        long mojibake = 0;
        for (int i = 0; i < utf8.length() - 1; i++) {
            char c = utf8.charAt(i);
            char next = utf8.charAt(i + 1);
            if (c == 'Ã' && (next == '±' || next == '³' || next == '©' || next == '¡' || next == 'ó' || next == 'ú')) {
                mojibake++;
            }
        }
        if (mojibake > 3) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
        return utf8;
    }

    private long countChar(String s, char c) {
        return s.chars().filter(x -> x == c).count();
    }

    // ── Excel ────────────────────────────────────────────────────────────────

    private ParsedFile parseExcel(MultipartFile file) throws IOException {
        List<String> headers = new ArrayList<>();
        List<Map<String, String>> rows = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> rowIter = sheet.rowIterator();
            if (!rowIter.hasNext()) return new ParsedFile(headers, rows);

            // Primera fila = encabezados
            Row headerRow = rowIter.next();
            DataFormatter formatter = new DataFormatter();
            for (Cell cell : headerRow) {
                headers.add(formatter.formatCellValue(cell).trim());
            }

            while (rowIter.hasNext()) {
                Row dataRow = rowIter.next();
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = dataRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    row.put(headers.get(i), cell == null ? "" : formatter.formatCellValue(cell).trim());
                }
                rows.add(row);
            }
        }

        return new ParsedFile(headers, rows);
    }

    // ── ParsedFile ───────────────────────────────────────────────────────────

    public static class ParsedFile {
        private final List<String> headers;
        private final List<Map<String, String>> rows;

        public ParsedFile(List<String> headers, List<Map<String, String>> rows) {
            this.headers = headers;
            this.rows = rows;
        }

        public List<String> getHeaders() { return headers; }
        public List<Map<String, String>> getRows() { return rows; }
    }
}
