package com.bnpparibas.exposition.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Credit Rating Framework - Next")
                        .version("1.0")
                        .description("API for credit rating framework")
                        .contact(new Contact()
                                .name("CRF Next Development - Team")
                                .email("dev@company.com")));
    }
}
