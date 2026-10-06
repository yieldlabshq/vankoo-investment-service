package com.liquilabs.vankoo.investment.infrastructure.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Declares both places the API can be called from. Without it springdoc only lists the
 * address the request reached, which through the gateway is the container's internal one, so
 * Scalar's "try it" could not work from the gateway's documentation. Same shape as Finance's.
 */
@Configuration
public class OpenApiConfiguration {

    @Value("${documentation.application.title}")
    private String applicationTitle;

    @Value("${documentation.application.description}")
    private String applicationDescription;

    @Value("${documentation.application.version}")
    private String applicationVersion;

    @Value("${documentation.local-url}")
    private String localUrl;

    @Value("${documentation.gateway-url}")
    private String gatewayUrl;

    @Bean
    public OpenAPI investmentServiceOpenApi() {
        var info = new Info()
                .title(applicationTitle)
                .description(applicationDescription)
                .version(applicationVersion);

        return new OpenAPI()
                .info(info)
                .servers(List.of(
                        new Server().url(gatewayUrl).description("API Gateway"),
                        new Server().url(localUrl).description("Investment service (direct access)")
                ));
    }
}
