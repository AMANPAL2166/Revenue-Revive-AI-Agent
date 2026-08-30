package com.reviveai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI reviveAiOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ReviveAI API")
                .description("AI Revenue Recovery Agent — detect revenue at risk, calculate decision metrics, "
                        + "get an AI-recommended recovery action, validate it through the Policy Engine, "
                        + "execute it, and measure the outcome.")
                .version("v0.1 (MVP)")
                .contact(new Contact().name("ReviveAI")));
    }
}
