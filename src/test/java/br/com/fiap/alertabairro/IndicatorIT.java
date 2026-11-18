package br.com.fiap.alertabairro;

import static br.com.fiap.alertabairro.TestSupport.bearer;
import static br.com.fiap.alertabairro.TestSupport.read;
import static br.com.fiap.alertabairro.TestSupport.resident;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.alertabairro.TestSupport.Account;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Painel: agregacao vinda da view vw_indicadores. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Indicadores")
class IndicatorIT extends OracleSupport {

  private static final List<String> CHAVES = List.of(
      "ocorrenciasAbertas", "ocorrenciasEmAtendimento", "ocorrenciasResolvidas",
      "ocorrenciasCriticas", "capacidadeAbrigos", "vagasOcupadas", "ocupacaoAbrigosPct",
      "pedidosAguardando", "pedidosAtendidos", "pedidosResolvidos",
      "checkinsEstouBem24h", "checkinsPrecisoAjuda24h");

  @Autowired private MockMvc mvc;

  private JsonNode indicadores(Account quem) throws Exception {
    return read(mvc.perform(get("/indicadores").header("Authorization", bearer(quem)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  @Test
  @DisplayName("devolve as 12 chaves que o painel espera")
  void chavesExatas() throws Exception {
    JsonNode i = indicadores(resident(mvc));
    for (String chave : CHAVES) {
      assertThat(i.has(chave)).as("falta a chave %s", chave).isTrue();
    }
  }

  @Test
  @DisplayName("a ocupacao e coerente com capacidade e vagas ocupadas, com uma casa decimal")
  void ocupacaoCoerente() throws Exception {
    JsonNode i = indicadores(resident(mvc));
    int capacidade = i.path("capacidadeAbrigos").asInt();
    int ocupadas = i.path("vagasOcupadas").asInt();
    double pct = i.path("ocupacaoAbrigosPct").asDouble();

    assertThat(capacidade).isPositive();
    double esperado = Math.round(((double) ocupadas / capacidade) * 100 * 10) / 10.0;
    assertThat(pct).isEqualTo(esperado);
    assertThat(pct).isBetween(0.0, 100.0);
  }

  @Test
  @DisplayName("um check-in novo entra no contador de 24h")
  void checkinEntraNoContadorDe24h() throws Exception {
    Account quem = resident(mvc);
    int antes = indicadores(quem).path("checkinsPrecisoAjuda24h").asInt();

    mvc.perform(post("/checkins").header("Authorization", bearer(quem))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"preciso_de_ajuda\",\"latitude\":-23.55,\"longitude\":-46.63}"))
        .andExpect(status().isCreated());

    assertThat(indicadores(quem).path("checkinsPrecisoAjuda24h").asInt()).isEqualTo(antes + 1);
  }

  @Test
  @DisplayName("uma ocorrencia nova entra no contador de abertas")
  void ocorrenciaEntraNoContador() throws Exception {
    Account quem = resident(mvc);
    int antes = indicadores(quem).path("ocorrenciasAbertas").asInt();

    mvc.perform(post("/ocorrencias").header("Authorization", bearer(quem))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"tipo":"Sem luz","descricao":"Para o painel","latitude":-23.55,"longitude":-46.63}"""))
        .andExpect(status().isCreated());

    assertThat(indicadores(quem).path("ocorrenciasAbertas").asInt()).isEqualTo(antes + 1);
  }

  @Test
  @DisplayName("sem token, 401")
  void semToken() throws Exception {
    mvc.perform(get("/indicadores")).andExpect(status().isUnauthorized());
  }
}
