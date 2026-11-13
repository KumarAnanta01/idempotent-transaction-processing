
package com.mphasis.transaction.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transactionOpenAPI() {

        return new OpenAPI()
                .info(new Info()
                        .title("Transaction Processing API")
                        .description("Idempotent Background Transaction Processing Service")
                        .version("1.0.0"));
    }
}
