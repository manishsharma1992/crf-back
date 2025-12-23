package com.bnpparibas.infrastructure.parser.excel;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class StreamingExcelReader {

    private final DataFormatter dataFormatter;

    public StreamingExcelReader() {
        this.dataFormatter = new DataFormatter();
    }

    public void processSheet(InputStream inputStream, String sheetName, RowProcessor rowProcessor) throws Exception {

        log.info("Starting streaming read of sheet: {}", sheetName);

        // Step 1: Open the Excel file as a ZIP package
        try (OPCPackage opcPackage = OPCPackage.open(inputStream)) {

            // Step 2: Get the reader for Excel internals
            XSSFReader xssfReader = new XSSFReader(opcPackage);

            // Step 3: Get shared strings (Excel stores strings separately for efficiency)
            SharedStrings sharedStrings = xssfReader.getSharedStringsTable();

            // Step 4: Get styles (for formatting, dates, etc.)
            StylesTable stylesTable = xssfReader.getStylesTable();

            // Step 5: Find and process the requested sheet
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) xssfReader.getSheetsData();

            while (sheets.hasNext()) {
                try (InputStream sheetStream = sheets.next()) {
                    String currentSheetName = sheets.getSheetName();

                    if (currentSheetName.equals(sheetName)) {
                        log.info("Found sheet: {}, starting streaming parse", sheetName);

                        parseSheet(sheetStream, sharedStrings, stylesTable, rowProcessor);

                        log.info("Completed streaming parse of sheet: {}", sheetName);
                        return;
                    }
                }
            }
            throw new IllegalArgumentException("Sheet not found: " + sheetName);
        }
    }

    /**
     * Parse a single sheet using SAX
     */
    private void parseSheet(
            InputStream sheetStream,
            SharedStrings sharedStrings,
            StylesTable stylesTable,
            RowProcessor rowProcessor) throws Exception {

        SAXParserFactory saxFactory = SAXParserFactory.newInstance();
        SAXParser saxParser = saxFactory.newSAXParser();
        XMLReader xmlReader = saxParser.getXMLReader();

        SheetHandler handler = new SheetHandler(
                sharedStrings,  // ✅ CHANGED
                stylesTable,
                dataFormatter,
                rowProcessor
        );

        xmlReader.setContentHandler(handler);
        xmlReader.parse(new InputSource(sheetStream));

    }

    private static class SheetHandler extends DefaultHandler {

        private final SharedStrings sharedStrings;
        private final StylesTable stylesTable;
        private final DataFormatter dataFormatter;
        private final RowProcessor rowProcessor;

        private int currentRowNumber = -1;
        private String currentCellReference;
        private String currentCellType;
        private StringBuilder currentCellValue;
        private List<String> currentRowCells;
        private int maxColumnIndex = -1;

        public SheetHandler(
                SharedStrings sharedStrings,
                StylesTable stylesTable,
                DataFormatter dataFormatter,
                RowProcessor rowProcessor) {
            this.sharedStrings = sharedStrings;
            this.stylesTable = stylesTable;
            this.dataFormatter = dataFormatter;
            this.rowProcessor = rowProcessor;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes  attributes) {
            if ("row".equals(qName)) {
                String rowNumStr = attributes.getValue("r");
                currentRowNumber = Integer.parseInt(rowNumStr) - 1;
                currentRowCells = new ArrayList<>();
                maxColumnIndex = -1;
            }
            else if ("c".equals(qName)) {
                currentCellReference = attributes.getValue("r");
                currentCellType = attributes.getValue("t");
                currentCellValue = new StringBuilder();
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (currentCellValue != null) {
                currentCellValue.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if ("c".equals(qName)) {
                String value = processCellValue();
                int columnIndex = getColumnIndex(currentCellReference);

                while (currentRowCells.size() <= columnIndex) {
                    currentRowCells.add(null);
                }

                currentRowCells.set(columnIndex, value);
                maxColumnIndex = Math.max(maxColumnIndex, columnIndex);
                currentCellValue = null;
            }
            else if ("row".equals(qName)) {
                while (currentRowCells.size() <= maxColumnIndex) {
                    currentRowCells.add(null);
                }

                try {
                    rowProcessor.processRow(currentRowNumber, currentRowCells);
                } catch (Exception e) {
                    log.error("Error processing row {}: {}", currentRowNumber, e.getMessage());
                    throw new SAXException("Row processing failed", e);
                }
            }
        }

        /**
         * Process cell value based on its type
         */
        private String processCellValue() {
            if (currentCellValue == null || currentCellValue.length() == 0) {
                return null;
            }

            String value = currentCellValue.toString();

            if ("s".equals(currentCellType)) {
                try {
                    int idx = Integer.parseInt(value);
                    return sharedStrings.getItemAt(idx).getString();
                } catch (NumberFormatException e) {
                    log.warn("Invalid shared string index: {}", value);
                    return value;
                }
            }
            else if ("b".equals(currentCellType)) {
                return "1".equals(value) ? "true" : "false";
            }

            return value;
        }

        /**
         * Convert cell reference to column index
         * Examples: "A" -> 0, "B" -> 1, "AA" -> 26, "AB" -> 27
         */
        private int getColumnIndex(String cellReference) {
            if (cellReference == null || cellReference.isEmpty()) {
                return 0;
            }

            StringBuilder columnLetters = new StringBuilder();
            for (char c : cellReference.toCharArray()) {
                if (Character.isLetter(c)) {
                    columnLetters.append(c);
                } else {
                    break;
                }
            }

            String col = columnLetters.toString().toUpperCase();
            int index = 0;
            for (int i = 0; i < col.length(); i++) {
                index = index * 26 + (col.charAt(i) - 'A' + 1);
            }
            return index - 1;
        }
    }
}
