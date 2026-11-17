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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Funcionalidade 1: registrar e listar ocorrencias. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Ocorrencias: listagem e registro")
class IncidentListIT {

  @Autowired private MockMvc mvc;

  private static String newIncident(String type, String description) {
    return """
        {"tipo":"%s","descricao":"%s","latitude":-23.5489,"longitude":-46.6388}
        """.formatted(type, description);
  }

  private String create(Account account, String type, String description) throws Exception {
    return mvc.perform(post("/ocorrencias").header("Authorization", bearer(account))
            .contentType(MediaType.APPLICATION_JSON).content(newIncident(type, description)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
  }

  @Test
  @DisplayName("POST passa pela procedure: status aberta, relatos 1 e bairro herdado do usuario")
  void registroPassaPelaProcedure() throws Exception {
    Account owner = TestSupport.signUp(mvc, "morador", "Vila da Prova");

    JsonNode o = read(create(owner, "Alagamento", "Rua alagada no teste"));
    assertThat(o.path("id").asLong()).isPositive();
    assertThat(o.path("status").asText()).isEqualTo("aberta");
    assertThat(o.path("relatos").asInt()).isEqualTo(1);
    assertThat(o.path("bairro").asText())
        .as("o bairro vem do USUARIO, o que prova que passou por prc_registrar_ocorrencia")
        .isEqualTo("Vila da Prova");
    assertThat(o.path("prioridade").asText())
        .as("Alagamento nasce com prioridade alta")
        .isEqualTo("alta");
  }

  @Test
  @DisplayName("a data sai em ISO-8601 COM offset")
  void dataComOffset() throws Exception {
    JsonNode o = read(create(resident(mvc), "Sem luz", "Poste apagado"));
    assertThat(o.path("criadaEm").asText())
        .as("sem offset, o \"ha X min\" do app mente conforme o fuso do container")
        .matches("^\\d{4}-\\d{2}-\\d{2}T[\\d:.]+([+-]\\d{2}:\\d{2}|Z)$");
  }

  @Test
  @DisplayName("acento atravessa API, Oracle e volta identico")
  void acentoSobrevive() throws Exception {
    JsonNode o = read(create(resident(mvc), "Árvore caída", "Galho sobre a calçada, trânsito parado"));
    assertThat(o.path("tipo").asText()).isEqualTo("Árvore caída");
    assertThat(o.path("descricao").asText()).isEqualTo("Galho sobre a calçada, trânsito parado");
  }

  @Test
  @DisplayName("descricao longa com acento cabe: o limite e em caracteres, nao em bytes")
  void descricaoLongaAcentuada() throws Exception {
    String longa = "çã".repeat(240); // 480 caracteres, 960 bytes em UTF-8
    JsonNode o = read(create(resident(mvc), "Alagamento", longa));
    assertThat(o.path("descricao").asText()).hasSize(480);
  }

  @Test
  @DisplayName("a listagem nunca expoe o id do autor, e marca meu corretamente")
  void listagemNaoExpoeAutorEMarcaMeu() throws Exception {
    Account a = resident(mvc);
    Account b = resident(mvc);
    long idDeA = read(create(a, "Alagamento", "Ocorrencia de A")).path("id").asLong();

    String corpo = mvc.perform(get("/ocorrencias").header("Authorization", bearer(b)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    assertThat(corpo)
        .as("o id do autor nao pode sair da API")
        .doesNotContain("idUsuario").doesNotContain("id_usuario");

    JsonNode lista = read(corpo);
    JsonNode deA = null;
    for (JsonNode item : lista) {
      if (item.path("id").asLong() == idDeA) {
        deA = item;
      }
      if (item.path("meu").asBoolean()) {
        throw new AssertionError("nenhuma ocorrencia deveria ser 'minha' para B ainda");
      }
    }
    assertThat(deA).as("a ocorrencia de A deveria aparecer na listagem").isNotNull();

    String corpoDeA = mvc.perform(get("/ocorrencias").header("Authorization", bearer(a)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    for (JsonNode item : read(corpoDeA)) {
      if (item.path("id").asLong() == idDeA) {
        assertThat(item.path("meu").asBoolean()).as("para A, a propria ocorrencia e 'minha'").isTrue();
      }
    }
  }

  @Test
  @DisplayName("a listagem vem da mais recente para a mais antiga")
  void listagemOrdenadaDoMaisRecente() throws Exception {
    Account owner = resident(mvc);
    long primeira = read(create(owner, "Sem luz", "Primeira")).path("id").asLong();
    long segunda = read(create(owner, "Sem luz", "Segunda")).path("id").asLong();

    JsonNode lista = read(mvc.perform(get("/ocorrencias").header("Authorization", bearer(owner)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

    int posPrimeira = -1;
    int posSegunda = -1;
    for (int i = 0; i < lista.size(); i++) {
      long id = lista.get(i).path("id").asLong();
      if (id == primeira) posPrimeira = i;
      if (id == segunda) posSegunda = i;
    }
    assertThat(posSegunda).as("a mais recente vem antes").isGreaterThanOrEqualTo(0).isLessThan(posPrimeira);
  }

  @Test
  @DisplayName("paginacao: limite e deslocamento recortam a lista")
  void paginacaoRecorta() throws Exception {
    Account owner = resident(mvc);
    create(owner, "Sem luz", "Pagina A");
    create(owner, "Sem luz", "Pagina B");

    JsonNode pagina1 = read(mvc.perform(get("/ocorrencias?limite=1")
            .header("Authorization", bearer(owner)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(pagina1).hasSize(1);

    JsonNode pagina2 = read(mvc.perform(get("/ocorrencias?limite=1&deslocamento=1")
            .header("Authorization", bearer(owner)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(pagina2).hasSize(1);
    assertThat(pagina2.get(0).path("id").asLong())
        .as("a segunda pagina nao repete o item da primeira")
        .isNotEqualTo(pagina1.get(0).path("id").asLong());
  }

  // ------------------------------------------- Review Focus 3: paginacao suja

  @Test
  @DisplayName("limite e deslocamento invalidos dao 400, nunca 500 nem a lista inteira")
  void paginacaoInvalida() throws Exception {
    Account owner = resident(mvc);
    String[] ruins = {"?limite=0", "?limite=201", "?limite=abc", "?limite=-1",
                      "?deslocamento=-1", "?deslocamento=abc"};
    for (String query : ruins) {
      mvc.perform(get("/ocorrencias" + query).header("Authorization", bearer(owner)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.erro").exists());
    }
  }

  // ------------------------------------------------------- validacao de corpo

  @Test
  @DisplayName("corpo invalido no POST da 400")
  void corpoInvalido() throws Exception {
    Account owner = resident(mvc);

    mvc.perform(post("/ocorrencias").header("Authorization", bearer(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"descricao\":\"sem type\",\"latitude\":-23.5,\"longitude\":-46.6}"))
        .andExpect(status().isBadRequest());

    mvc.perform(post("/ocorrencias").header("Authorization", bearer(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content(newIncident("Alagamento", "x".repeat(501))))
        .andExpect(status().isBadRequest());

    mvc.perform(post("/ocorrencias").header("Authorization", bearer(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"tipo":"Alagamento","descricao":"ok","latitude":91,"longitude":-46.6}"""))
        .andExpect(status().isBadRequest());

    mvc.perform(post("/ocorrencias").header("Authorization", bearer(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"tipo":"Alagamento","descricao":"ok","longitude":-46.6}"""))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("sem token, 401")
  void semToken() throws Exception {
    mvc.perform(get("/ocorrencias")).andExpect(status().isUnauthorized());
    mvc.perform(post("/ocorrencias").contentType(MediaType.APPLICATION_JSON)
            .content(newIncident("Alagamento", "x"))).andExpect(status().isUnauthorized());
  }
}
