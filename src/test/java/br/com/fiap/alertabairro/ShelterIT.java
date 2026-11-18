package br.com.fiap.alertabairro;

import static br.com.fiap.alertabairro.TestSupport.bearer;
import static br.com.fiap.alertabairro.TestSupport.read;
import static br.com.fiap.alertabairro.TestSupport.resident;
import static br.com.fiap.alertabairro.TestSupport.volunteer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.alertabairro.TestSupport.Account;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** Funcionalidade 2: abrigos, abrigo mais proximo e controle de vagas. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Abrigos: lista, mais proximo e vagas")
class ShelterIT extends OracleSupport {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;

  @Test
  @DisplayName("lista os abrigos em ordem de nome")
  void listaOrdenada() throws Exception {
    JsonNode lista = read(mvc.perform(get("/abrigos").header("Authorization", bearer(resident(mvc))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

    assertThat(lista.size()).isGreaterThanOrEqualTo(3);
    List<String> nomes = new ArrayList<>();
    lista.forEach(a -> nomes.add(a.path("nome").asText()));
    assertThat(nomes).isSorted();
    assertThat(lista.get(0).has("capacidade")).isTrue();
    assertThat(lista.get(0).has("vagasOcupadas")).isTrue();
  }

  @Test
  @DisplayName("o mais proximo tem vaga e vem com os campos do app")
  void maisProximo() throws Exception {
    JsonNode abrigo = read(mvc.perform(get("/abrigos/proximo?lat=-23.55&lng=-46.63")
            .header("Authorization", bearer(resident(mvc))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

    assertThat(abrigo.path("id").asLong()).isPositive();
    assertThat(abrigo.path("vagasOcupadas").asInt())
        .as("o mais proximo precisa ter vaga")
        .isLessThan(abrigo.path("capacidade").asInt());
  }

  @Test
  @DisplayName("com todos os abrigos lotados, devolve corpo nulo")
  void todosLotados() throws Exception {
    List<int[]> antes = new ArrayList<>();
    jdbc.sql("SELECT id, vagas_ocupadas FROM abrigo").query()
        .listOfRows()
        .forEach(linha -> antes.add(new int[] {
            ((Number) linha.get("ID")).intValue(),
            ((Number) linha.get("VAGAS_OCUPADAS")).intValue()}));
    try {
      jdbc.sql("UPDATE abrigo SET vagas_ocupadas = capacidade").update();

      mvc.perform(get("/abrigos/proximo?lat=-23.55&lng=-46.63")
              .header("Authorization", bearer(resident(mvc))))
          .andExpect(status().isOk())
          .andExpect(content().string(org.hamcrest.Matchers.anyOf(
              org.hamcrest.Matchers.is(""), org.hamcrest.Matchers.is("null"))));
    } finally {
      for (int[] linha : antes) {
        jdbc.sql("UPDATE abrigo SET vagas_ocupadas = :v WHERE id = :id")
            .param("v", linha[1]).param("id", linha[0]).update();
      }
    }
  }

  // ------------------------------------------- Review Focus 4: coordenada suja

  @Test
  @DisplayName("coordenada fora de faixa ou ausente da 400, nunca 500")
  void coordenadaInvalida() throws Exception {
    Account quem = resident(mvc);
    String[] ruins = {"?lat=91&lng=-46.63", "?lat=-23.55&lng=-181", "?lat=abc&lng=-46.63",
                      "?lat=-23.55", "?lng=-46.63", ""};
    for (String query : ruins) {
      mvc.perform(get("/abrigos/proximo" + query).header("Authorization", bearer(quem)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.erro").exists());
    }
  }

  // ---------------------------------------------------------- controle de vagas

  private long algumAbrigoComVaga() {
    return jdbc.sql("""
            SELECT id FROM (SELECT id FROM abrigo WHERE vagas_ocupadas < capacidade - 2
                             ORDER BY id) WHERE ROWNUM = 1
            """).query(Long.class).single();
  }

  @Test
  @DisplayName("entrada de pessoas e so para voluntario, e soma a ocupacao")
  void entradaDePessoas() throws Exception {
    long id = algumAbrigoComVaga();
    int antes = jdbc.sql("SELECT vagas_ocupadas FROM abrigo WHERE id = :id")
        .param("id", id).query(Integer.class).single();

    mvc.perform(patch("/abrigos/" + id + "/vagas").header("Authorization", bearer(resident(mvc)))
            .contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":2}"))
        .andExpect(status().isForbidden());

    try {
      mvc.perform(patch("/abrigos/" + id + "/vagas")
              .header("Authorization", bearer(volunteer(mvc)))
              .contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":2}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.vagasOcupadas").value(antes + 2));
    } finally {
      jdbc.sql("UPDATE abrigo SET vagas_ocupadas = :v WHERE id = :id")
          .param("v", antes).param("id", id).update();
    }
  }

  @Test
  @DisplayName("quantidade invalida da 400")
  void quantidadeInvalida() throws Exception {
    long id = algumAbrigoComVaga();
    Account vol = volunteer(mvc);
    String[] ruins = {"{\"quantidade\":0}", "{\"quantidade\":1001}", "{\"quantidade\":-1001}",
                      "{\"quantidade\":\"dois\"}", "{}"};
    for (String corpo : ruins) {
      mvc.perform(patch("/abrigos/" + id + "/vagas").header("Authorization", bearer(vol))
              .contentType(MediaType.APPLICATION_JSON).content(corpo))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  @DisplayName("estourar a capacidade vira 422 com a mensagem do banco")
  void estourarCapacidade() throws Exception {
    long id = algumAbrigoComVaga();
    mvc.perform(patch("/abrigos/" + id + "/vagas").header("Authorization", bearer(volunteer(mvc)))
            .contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":1000}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.erro").value("Abrigo sem vagas suficientes"));
  }

  @Test
  @DisplayName("abrigo inexistente vira 422 com a mensagem do banco")
  void abrigoInexistente() throws Exception {
    mvc.perform(patch("/abrigos/99999999/vagas").header("Authorization", bearer(volunteer(mvc)))
            .contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":1}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.erro").value("Abrigo nao encontrado"));
  }

  @Test
  @DisplayName("sem token, 401")
  void semToken() throws Exception {
    mvc.perform(get("/abrigos")).andExpect(status().isUnauthorized());
    mvc.perform(get("/abrigos/proximo?lat=-23.55&lng=-46.63")).andExpect(status().isUnauthorized());
  }
}
