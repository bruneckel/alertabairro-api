package br.com.fiap.alertabairro.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import br.com.fiap.alertabairro.security.AuthenticatedUser;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI at /swagger-ui.html. Seven of the seventeen endpoints are not reachable from the
 * app, and this is what makes them demonstrable without Postman.
 */
@Configuration
public class OpenApiConfig {

  static final String JWT_SCHEME = "bearerAuth";

  static {
    // springdoc cannot know AuthenticatedUser is resolved from the token by WebConfig, so
    // it documented it as a REQUIRED query parameter of object type -- which made every
    // try-it-out unusable and published the record as a public schema.
    SpringDocUtils.getConfig().addRequestWrapperToIgnore(AuthenticatedUser.class);
  }

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
        // Declaring the scheme is not enough: without a requirement, Swagger UI's
        // Authorize button never attaches the token.
        .addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME))
        .schemaRequirement(JWT_SCHEME, new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT"));
  }
}
