package br.com.fiap.alertabairro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** O contrato de erro e o documento OpenAPI que o video usa para demonstrar a API. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Contrato: corpo de erro e OpenAPI")
class ApiContractIT {

  private static final String SEGREDO = "segredo-de-teste-com-mais-de-trinta-e-dois-caracteres";

  @Autowired private MockMvc mvc;

  private static String token(Instant expiraEm) throws Exception {
    SignedJWT jwt = new SignedJWT(
        new JWSHeader(JWSAlgorithm.HS256),
        new JWTClaimsSet.Builder().subject("1").claim("perfil", "morador")
            .issueTime(Date.from(Instant.now().minusSeconds(60)))
            .expirationTime(Date.from(expiraEm)).build());
    jwt.sign(new MACSigner(SEGREDO.getBytes()));
    return jwt.serialize();
  }

  @Test
  @DisplayName("401 de token invalido tem corpo {\"erro\": ...}, igual aos outros erros")
  void erroDeTokenTemCorpo() throws Exception {
    // O app le error.response.data.erro em um unico caminho. Um 401 sem corpo deixa a
    // tela muda -- e token expirado e o 401 mais provavel na vida real, a cada 8 horas.
    String[] ruins = {
        token(Instant.now().minusSeconds(120)),   // expirado
        "lixo.que.nao.e.jwt",
        token(Instant.now().plusSeconds(600)).replaceAll(".$", "X")};  // assinatura adulterada

    for (String t : ruins) {
      mvc.perform(get("/ocorrencias").header("Authorization", "Bearer " + t))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.erro").exists());
    }
  }

  @Test
  @DisplayName("401 sem cabecalho tambem tem corpo, e o mesmo formato")
  void erroSemCabecalhoTemCorpo() throws Exception {
    mvc.perform(get("/ocorrencias"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.erro").exists());
  }

  @Test
  @DisplayName("o motivo da recusa do token nao vaza em WWW-Authenticate")
  void motivoNaoVaza() throws Exception {
    String adulterado = token(Instant.now().plusSeconds(600)).replaceAll(".$", "X");
    String cabecalho = mvc.perform(get("/ocorrencias")
            .header("Authorization", "Bearer " + adulterado))
        .andExpect(status().isUnauthorized())
        .andReturn().getResponse().getHeader("WWW-Authenticate");

    if (cabecalho != null) {
      assertThat(cabecalho.toLowerCase())
          .as("o detalhe da falha de decodificacao nao precisa chegar ao cliente")
          .doesNotContain("signature").doesNotContain("expired").doesNotContain("jwt rejected");
    }
  }

  // ------------------------------------------------------------------- OpenAPI

  private JsonNode openApi() throws Exception {
    return TestSupport.read(mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  @Test
  @DisplayName("toda operacao protegida exige o esquema JWT, para o Authorize do Swagger funcionar")
  void operacoesExigemJwt() throws Exception {
    JsonNode doc = openApi();
    assertThat(doc.path("components").path("securitySchemes").has("bearerAuth"))
        .as("o esquema precisa estar declarado").isTrue();

    List<String> semSeguranca = new ArrayList<>();
    JsonNode caminhos = doc.path("paths");
    boolean raizTemSeguranca = doc.has("security") && doc.path("security").size() > 0;

    caminhos.fieldNames().forEachRemaining(caminho -> {
      if (caminho.startsWith("/auth") || caminho.startsWith("/actuator")) {
        return;
      }
      caminhos.path(caminho).fieldNames().forEachRemaining(metodo -> {
        JsonNode op = caminhos.path(caminho).path(metodo);
        boolean temSeguranca = op.has("security") && op.path("security").size() > 0;
        if (!temSeguranca && !raizTemSeguranca) {
          semSeguranca.add(metodo.toUpperCase() + " " + caminho);
        }
      });
    });

    assertThat(semSeguranca)
        .as("sem requisito de seguranca, o botao Authorize do Swagger nao anexa o token "
            + "e todo try-it-out das rotas protegidas da 401")
        .isEmpty();
  }

  @Test
  @DisplayName("AuthenticatedUser nao aparece como parametro nem como schema publico")
  void usuarioAutenticadoNaoVazaNoDocumento() throws Exception {
    String bruto = mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    assertThat(bruto)
        .as("o springdoc nao sabe que AuthenticatedUser vem do token, e o documenta como "
            + "parametro de query obrigatorio -- o que torna o Swagger inutilizavel")
        .doesNotContain("AuthenticatedUser");
  }
}
