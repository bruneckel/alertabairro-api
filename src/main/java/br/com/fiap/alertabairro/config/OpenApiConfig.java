package br.com.fiap.alertabairro.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacao navegavel em /swagger-ui.html.
 *
 * <p>Sete das dezenove rotas nao sao usadas pelo app (acoes de voluntario, exclusao,
 * indicadores). O Swagger UI e o que as torna demonstraveis no video tecnico sem
 * depender de Postman.
 */
@Configuration
public class OpenApiConfig {

  static final String ESQUEMA_JWT = "bearerAuth";

  @Bean
  OpenAPI openApi() {
    return new OpenAPI()
        .info(new Info()
            .title("AlertaBairro API")
            .version("1.0.0")
            .description("""
                Rede comunitaria de alerta e ajuda para eventos climaticos extremos.

                As rotas protegidas exigem o cabecalho Authorization: Bearer <token>,
                obtido em POST /auth/login ou POST /auth/cadastro.
                """))
        .schemaRequirement(ESQUEMA_JWT, new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT"));
  }
}
