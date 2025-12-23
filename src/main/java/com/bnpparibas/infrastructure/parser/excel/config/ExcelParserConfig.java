package com.bnpparibas.infrastructure.parser.excel.config;

import com.bnpparibas.infrastructure.parser.excel.StreamingExcelReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExcelParserConfig {

    @Bean
    public StreamingExcelReader streamingExcelReader() {
        return new StreamingExcelReader();
    }
}
