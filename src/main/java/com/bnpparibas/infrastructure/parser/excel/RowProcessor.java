package com.bnpparibas.infrastructure.parser.excel;

import java.util.List;

/**
 * Functional interface for processing each row
 */
@FunctionalInterface
public interface RowProcessor {

    /**
     * Process a single row
     * @param rowIndex 0-based row index
     * @param cellValues List of cell values (may contain nulls for empty cells)
     */
    void processRow(int rowIndex, List<String> cellValues) throws Exception;
}
