package br.com.fiap.alertabairro.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI at /swagger-ui.html. Seven of the nineteen routes are not reachable from the
 * app, and this is what makes them demonstrable without Postman.
 */
@Configuration
public class OpenApiConfig {

  static final String JWT_SCHEME = "bearerAuth";

  @Bean
  OpenAPI openApi() {
    return new OpenAPI()
        .info(new Info()
            .title("AlertaBairro API")
            .version("1.0.0")
            .description("""
                Rede comunitaria de alerta e ajuda para eventos climaticos extremos.

                As rotas protegidas exigem Authorization: Bearer <token>, obtido em
                POST /auth/login ou POST /auth/cadastro.
                """))
        .schemaRequirement(JWT_SCHEME, new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT"));
  }
}
