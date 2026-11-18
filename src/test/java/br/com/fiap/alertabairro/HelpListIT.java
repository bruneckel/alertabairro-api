package br.com.fiap.alertabairro;

import static br.com.fiap.alertabairro.TestSupport.bearer;
import static br.com.fiap.alertabairro.TestSupport.read;
import static br.com.fiap.alertabairro.TestSupport.resident;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.alertabairro.TestSupport.Account;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Funcionalidade 3: rede de ajuda. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Ajuda: lista e criacao")
class HelpListIT extends OracleSupport {

  @Autowired private MockMvc mvc;

  private String create(Account who, String mode, String category, String urgency,
      String extraCoords) throws Exception {
    String body = """
        {"modo":"%s","categoria":"%s","descricao":"Pedido de teste","urgencia":"%s"%s}
        """.formatted(mode, category, urgency, extraCoords);
    return mvc.perform(post("/ajuda").header("Authorization", bearer(who))
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
  }

  @Test
  @DisplayName("criar devolve 201 com status aguardando")
  void criar() throws Exception {
    JsonNode p = read(create(resident(mvc), "pedido", "Remédio", "alta",
        ",\"latitude\":-23.5495,\"longitude\":-46.6370"));
    assertThat(p.path("id").asLong()).isPositive();
    assertThat(p.path("status").asText()).isEqualTo("aguardando");
    assertThat(p.path("categoria").asText()).isEqualTo("Remédio");
    assertThat(p.path("meu").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("a API NUNCA devolve a coordenada de quem pediu ajuda, so a distancia")
  void privacidadeDoSolicitante() throws Exception {
    Account quem = resident(mvc);
    create(quem, "pedido", "Transporte", "media", ",\"latitude\":-23.5495,\"longitude\":-46.6370");

    String corpo = mvc.perform(get("/ajuda?lat=-23.5489&lng=-46.6388")
            .header("Authorization", bearer(quem)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    assertThat(corpo)
        .as("a localizacao de quem pede ajuda nao pode sair do servidor")
        .doesNotContain("latitude").doesNotContain("longitude")
        .doesNotContain("idUsuario").doesNotContain("id_usuario");
    assertThat(read(corpo).get(0).path("distancia").asText()).isNotBlank();
  }

  @Test
  @DisplayName("a distancia e calculada a partir da coordenada enviada na consulta")
  void distanciaDaConsulta() throws Exception {
    Account quem = resident(mvc);
    create(quem, "pedido", "Agua", "alta", ",\"latitude\":-23.5489,\"longitude\":-46.6388");

    JsonNode lista = read(mvc.perform(get("/ajuda?lat=-23.5489&lng=-46.6388&limite=200")
            .header("Authorization", bearer(quem)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

    boolean achou = false;
    for (JsonNode p : lista) {
      if (p.path("meu").asBoolean() && "Agua".equals(p.path("categoria").asText())) {
        assertThat(p.path("distancia").asText())
            .as("no mesmo ponto a distancia e zero metros")
            .isEqualTo("0 m");
        achou = true;
      }
    }
    assertThat(achou).as("o pedido criado deveria estar na lista").isTrue();
  }

  @Test
  @DisplayName("sem coordenada e sem check-in, ainda responde 200 com texto de fallback")
  void semPosicaoConhecida() throws Exception {
    Account novo = resident(mvc);
    create(novo, "oferta", "Abrigo", "baixa", "");

    JsonNode lista = read(mvc.perform(get("/ajuda?limite=200").header("Authorization", bearer(novo)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

    for (JsonNode p : lista) {
      assertThat(p.path("distancia").asText())
          .as("a distancia nunca pode vir vazia: o app escreve \"<distancia> de voce\"")
          .isNotBlank();
    }
  }

  @Test
  @DisplayName("aguardando aparece antes de atendido")
  void ordenacaoPorStatus() throws Exception {
    Account quem = resident(mvc);
    create(quem, "pedido", "Ordem", "alta", ",\"latitude\":-23.55,\"longitude\":-46.63");

    JsonNode lista = read(mvc.perform(get("/ajuda?limite=200").header("Authorization", bearer(quem)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

    assertThat(lista).as("sem itens, a ordenacao nao e exercitada").isNotEmpty();

    boolean viAtendidoOuResolvido = false;
    for (JsonNode p : lista) {
      String status = p.path("status").asText();
      if (!"aguardando".equals(status)) {
        viAtendidoOuResolvido = true;
      } else if (viAtendidoOuResolvido) {
        throw new AssertionError("um aguardando apareceu depois de um ja encerrado");
      }
    }
  }

  @Test
  @DisplayName("modo, urgencia e categoria invalidos dao 400")
  void corpoInvalido() throws Exception {
    Account quem = resident(mvc);
    String[] ruins = {
        "{\"modo\":\"emprestimo\",\"categoria\":\"X\",\"descricao\":\"d\",\"urgencia\":\"alta\"}",
        "{\"modo\":\"pedido\",\"categoria\":\"X\",\"descricao\":\"d\",\"urgencia\":\"urgentissima\"}",
        "{\"modo\":\"pedido\",\"categoria\":\"\",\"descricao\":\"d\",\"urgencia\":\"alta\"}",
        "{\"modo\":\"pedido\",\"categoria\":\"X\",\"urgencia\":\"alta\"}",
        "{\"modo\":\"pedido\",\"categoria\":\"X\",\"descricao\":\"d\",\"urgencia\":\"alta\",\"latitude\":91,\"longitude\":-46.6}"};
    for (String corpo : ruins) {
      mvc.perform(post("/ajuda").header("Authorization", bearer(quem))
              .contentType(MediaType.APPLICATION_JSON).content(corpo))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.erro").exists());
    }
  }

  @Test
  @DisplayName("sem token, 401")
  void semToken() throws Exception {
    mvc.perform(get("/ajuda")).andExpect(status().isUnauthorized());
  }
}
