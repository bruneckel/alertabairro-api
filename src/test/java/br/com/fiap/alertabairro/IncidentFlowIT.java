package br.com.fiap.alertabairro;

import static br.com.fiap.alertabairro.TestSupport.bearer;
import static br.com.fiap.alertabairro.TestSupport.read;
import static br.com.fiap.alertabairro.TestSupport.resident;
import static br.com.fiap.alertabairro.TestSupport.volunteer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.alertabairro.TestSupport.Account;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Maquina de estados da ocorrencia e exclusao pelo autor. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Ocorrencias: atender, resolver e excluir")
class IncidentFlowIT extends OracleSupport {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;

  private long createIncident(Account owner) throws Exception {
    String response = mvc.perform(post("/ocorrencias").header("Authorization", bearer(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"tipo":"Sem luz","descricao":"Para o fluxo","latitude":-23.5489,"longitude":-46.6388}"""))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return read(response).path("id").asLong();
  }

  private ResultActions act(Account who, long id, String action) throws Exception {
    return mvc.perform(patch("/ocorrencias/" + id).header("Authorization", bearer(who))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"acao\":\"%s\"}".formatted(action)));
  }

  @Test
  @DisplayName("atender e so para voluntario")
  void atenderSoVoluntario() throws Exception {
    long id = createIncident(resident(mvc));

    act(resident(mvc), id, "atender")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.erro").exists());

    act(volunteer(mvc), id, "atender")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("em_atendimento"));
  }

  @Test
  @DisplayName("atender o que nao esta aberta da 409")
  void atenderDuasVezes() throws Exception {
    long id = createIncident(resident(mvc));
    Account vol = volunteer(mvc);

    act(vol, id, "atender").andExpect(status().isOk());
    act(vol, id, "atender").andExpect(status().isConflict());
  }

  @Test
  @DisplayName("resolver e so para voluntario, e nao repete")
  void resolver() throws Exception {
    long id = createIncident(resident(mvc));
    Account vol = volunteer(mvc);

    act(resident(mvc), id, "resolver").andExpect(status().isForbidden());

    act(vol, id, "resolver")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("resolvida"));

    act(vol, id, "resolver").andExpect(status().isConflict());
  }

  @Test
  @DisplayName("o status nunca retrocede: resolvida nao volta a em_atendimento")
  void statusSoAvanca() throws Exception {
    long id = createIncident(resident(mvc));
    Account vol = volunteer(mvc);

    act(vol, id, "resolver").andExpect(status().isOk());
    act(vol, id, "atender")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.erro").exists());

    String status = jdbc.sql("SELECT status FROM ocorrencia WHERE id = :id")
        .param("id", id).query(String.class).single();
    assertThat(status).isEqualTo("resolvida");
  }

  @Test
  @DisplayName("excluir e so do autor, e leva as confirmacoes junto")
  void excluir() throws Exception {
    Account owner = resident(mvc);
    Account other = resident(mvc);
    long id = createIncident(owner);

    act(other, id, "confirmar").andExpect(status().isOk());
    assertThat(jdbc.sql("SELECT COUNT(*) FROM confirmacao_ocorrencia WHERE id_ocorrencia = :id")
        .param("id", id).query(Integer.class).single()).isEqualTo(1);

    mvc.perform(delete("/ocorrencias/" + id).header("Authorization", bearer(other)))
        .andExpect(status().isForbidden());

    mvc.perform(delete("/ocorrencias/" + id).header("Authorization", bearer(owner)))
        .andExpect(status().isNoContent());

    assertThat(jdbc.sql("SELECT COUNT(*) FROM ocorrencia WHERE id = :id")
        .param("id", id).query(Integer.class).single()).isZero();
    assertThat(jdbc.sql("SELECT COUNT(*) FROM confirmacao_ocorrencia WHERE id_ocorrencia = :id")
        .param("id", id).query(Integer.class).single())
        .as("ON DELETE CASCADE evita erro de FK na exclusao")
        .isZero();
  }

  @Test
  @DisplayName("excluir id inexistente da 404")
  void excluirInexistente() throws Exception {
    mvc.perform(delete("/ocorrencias/99999999").header("Authorization", bearer(resident(mvc))))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("dois voluntarios atendendo ao mesmo tempo: uma 200 e uma 409")
  void atenderSimultaneo() throws Exception {
    long id = createIncident(resident(mvc));
    Account v1 = volunteer(mvc);
    Account v2 = volunteer(mvc);

    Callable<Integer> a = () -> act(v1, id, "atender").andReturn().getResponse().getStatus();
    Callable<Integer> b = () -> act(v2, id, "atender").andReturn().getResponse().getStatus();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> r = pool.invokeAll(List.of(a, b));
      List<Integer> status = List.of(r.get(0).get(), r.get(1).get());
      assertThat(status).as("so um voluntario assume").containsOnlyOnce(200);
      assertThat(status).as("o outro recebe 409, nunca duas 200").containsOnlyOnce(409);
    } finally {
      pool.shutdownNow();
    }
  }
}
