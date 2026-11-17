package br.com.fiap.alertabairro;

import static br.com.fiap.alertabairro.TestSupport.bearer;
import static br.com.fiap.alertabairro.TestSupport.read;
import static br.com.fiap.alertabairro.TestSupport.resident;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.alertabairro.TestSupport.Account;
import br.com.fiap.alertabairro.repository.IncidentRepository;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** "Tambem vejo isso aqui": uma confirmacao por usuario, numa unica transacao. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Ocorrencias: confirmacao")
class IncidentConfirmIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;

  @MockitoSpyBean private IncidentRepository repositorySpy;

  private long createIncident(Account owner, String type) throws Exception {
    String body = """
        {"tipo":"%s","descricao":"Ocorrencia para confirmar","latitude":-23.5489,"longitude":-46.6388}
        """.formatted(type);
    String response = mvc.perform(post("/ocorrencias").header("Authorization", bearer(owner))
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return read(response).path("id").asLong();
  }

  private int reportsOf(long id) {
    return jdbc.sql("SELECT relatos FROM ocorrencia WHERE id = :id")
        .param("id", id).query(Integer.class).single();
  }

  private String priorityOf(long id) {
    return jdbc.sql("SELECT prioridade FROM ocorrencia WHERE id = :id")
        .param("id", id).query(String.class).single();
  }

  private int confirmationsOf(long id) {
    return jdbc.sql("SELECT COUNT(*) FROM confirmacao_ocorrencia WHERE id_ocorrencia = :id")
        .param("id", id).query(Integer.class).single();
  }

  private org.springframework.test.web.servlet.ResultActions confirm(Account who, long id)
      throws Exception {
    return mvc.perform(patch("/ocorrencias/" + id).header("Authorization", bearer(who))
        .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"confirmar\"}"));
  }

  @Test
  @DisplayName("confirmar ocorrencia de outro soma um relato")
  void confirmarSomaRelato() throws Exception {
    Account owner = resident(mvc);
    Account other = resident(mvc);
    long id = createIncident(owner, "Sem luz");

    confirm(other, id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.relatos").value(2));
    assertThat(reportsOf(id)).isEqualTo(2);
    assertThat(confirmationsOf(id)).isEqualTo(1);
  }

  @Test
  @DisplayName("confirmar duas vezes da 409 e NAO soma de novo")
  void confirmarDuasVezes() throws Exception {
    Account owner = resident(mvc);
    Account other = resident(mvc);
    long id = createIncident(owner, "Sem luz");

    confirm(other, id).andExpect(status().isOk());
    confirm(other, id)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.erro").exists());

    assertThat(reportsOf(id)).as("o contador nao pode subir na recusa").isEqualTo(2);
    assertThat(confirmationsOf(id)).isEqualTo(1);
  }

  @Test
  @DisplayName("confirmar a propria ocorrencia da 409")
  void confirmarAPropria() throws Exception {
    Account owner = resident(mvc);
    long id = createIncident(owner, "Sem luz");

    confirm(owner, id).andExpect(status().isConflict());
    assertThat(reportsOf(id)).isEqualTo(1);
    assertThat(confirmationsOf(id)).isZero();
  }

  @Test
  @DisplayName("id inexistente da 404 e acao desconhecida da 400")
  void idEAcaoInvalidos() throws Exception {
    Account who = resident(mvc);

    mvc.perform(patch("/ocorrencias/99999999").header("Authorization", bearer(who))
            .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"confirmar\"}"))
        .andExpect(status().isNotFound());

    long id = createIncident(resident(mvc), "Sem luz");
    mvc.perform(patch("/ocorrencias/" + id).header("Authorization", bearer(who))
            .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"dancar\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("tres usuarios distintos levam a prioridade de alta a critica pelo trigger")
  void tresConfirmacoesEscalam() throws Exception {
    Account owner = resident(mvc);
    long id = createIncident(owner, "Alagamento");
    assertThat(priorityOf(id)).isEqualTo("alta");

    confirm(resident(mvc), id).andExpect(status().isOk());
    assertThat(priorityOf(id)).as("com 2 relatos ainda e alta").isEqualTo("alta");

    confirm(resident(mvc), id).andExpect(status().isOk());
    assertThat(priorityOf(id))
        .as("o terceiro relato dispara trg_ocorrencia_escalona")
        .isEqualTo("critica");
  }

  // ------------------------------------------------- Review Focus 2: corrida

  @Test
  @DisplayName("duas confirmacoes simultaneas do MESMO usuario: uma aplica, a outra 409")
  void confirmacoesSimultaneas() throws Exception {
    Account owner = resident(mvc);
    Account other = resident(mvc);
    long id = createIncident(owner, "Sem luz");

    Callable<Integer> tentativa = () -> confirm(other, id).andReturn().getResponse().getStatus();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> resultados = pool.invokeAll(List.of(tentativa, tentativa));
      List<Integer> status = List.of(resultados.get(0).get(), resultados.get(1).get());

      assertThat(status).as("exatamente uma das duas pode aplicar").containsOnlyOnce(200);
      assertThat(status).as("a outra tem de ser recusada com 409").containsOnlyOnce(409);
    } finally {
      pool.shutdownNow();
    }

    assertThat(reportsOf(id)).as("o contador sobe exatamente 1").isEqualTo(2);
    assertThat(confirmationsOf(id)).isEqualTo(1);
  }

  // ------------------------------------------------------------ atomicidade

  @Test
  @DisplayName("se o incremento falhar, a confirmacao NAO fica gravada")
  void confirmacaoEIncrementoSaoAtomicos() throws Exception {
    Account owner = resident(mvc);
    Account other = resident(mvc);
    long id = createIncident(owner, "Sem luz");

    doThrow(new IllegalStateException("falha forcada no incremento"))
        .when(repositorySpy).incrementReports(anyLong());

    mvc.perform(patch("/ocorrencias/" + id).header("Authorization", bearer(other))
            .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"confirmar\"}"))
        .andExpect(status().isInternalServerError());

    assertThat(confirmationsOf(id))
        .as("sem transacao, o usuario gastaria a confirmacao sem o contador subir")
        .isZero();
    assertThat(reportsOf(id)).isEqualTo(1);
  }
}
