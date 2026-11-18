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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** Resolver, excluir e encerrar pedidos antigos. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Ajuda: resolver, excluir e encerrar antigos")
class HelpFlowIT extends OracleSupport {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;

  private long create(Account who) throws Exception {
    String response = mvc.perform(post("/ajuda").header("Authorization", bearer(who))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"modo":"pedido","categoria":"Remedio","descricao":"Para o fluxo",
                 "urgencia":"alta","latitude":-23.55,"longitude":-46.63}"""))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return read(response).path("id").asLong();
  }

  private String statusOf(long id) {
    return jdbc.sql("SELECT status FROM pedido_ajuda WHERE id = :id")
        .param("id", id).query(String.class).single();
  }

  @Test
  @DisplayName("resolver e so do autor, e leva o pedido para resolvido")
  void resolver() throws Exception {
    Account owner = resident(mvc);
    long id = create(owner);

    mvc.perform(patch("/ajuda/" + id).header("Authorization", bearer(resident(mvc)))
            .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"resolver\"}"))
        .andExpect(status().isForbidden());

    mvc.perform(patch("/ajuda/" + id).header("Authorization", bearer(owner))
            .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"resolver\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("resolvido"));

    assertThat(statusOf(id)).isEqualTo("resolvido");
  }

  @Test
  @DisplayName("acao diferente de resolver da 400, e id inexistente da 404")
  void acaoEIdInvalidos() throws Exception {
    Account owner = resident(mvc);
    long id = create(owner);

    mvc.perform(patch("/ajuda/" + id).header("Authorization", bearer(owner))
            .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"cancelar\"}"))
        .andExpect(status().isBadRequest());

    mvc.perform(patch("/ajuda/99999999").header("Authorization", bearer(owner))
            .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"resolver\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("excluir e so do autor")
  void excluir() throws Exception {
    Account owner = resident(mvc);
    long id = create(owner);

    mvc.perform(delete("/ajuda/" + id).header("Authorization", bearer(resident(mvc))))
        .andExpect(status().isForbidden());

    mvc.perform(delete("/ajuda/" + id).header("Authorization", bearer(owner)))
        .andExpect(status().isNoContent());

    assertThat(jdbc.sql("SELECT COUNT(*) FROM pedido_ajuda WHERE id = :id")
        .param("id", id).query(Integer.class).single()).isZero();

    mvc.perform(delete("/ajuda/" + id).header("Authorization", bearer(owner)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("encerrar antigos e so para voluntario e nao toca em pedido recente")
  void encerrarAntigos() throws Exception {
    Account owner = resident(mvc);
    long recente = create(owner);
    long antigo = create(owner);
    jdbc.sql("UPDATE pedido_ajuda SET criado_em = SYSTIMESTAMP - 30 WHERE id = :id")
        .param("id", antigo).update();

    mvc.perform(post("/ajuda/encerrar-antigos").header("Authorization", bearer(owner))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());

    mvc.perform(post("/ajuda/encerrar-antigos").header("Authorization", bearer(volunteer(mvc)))
            .contentType(MediaType.APPLICATION_JSON).content("{\"dias\":7}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.encerrados").exists());

    assertThat(statusOf(antigo)).as("o antigo e encerrado como atendido").isEqualTo("atendido");
    assertThat(statusOf(recente)).as("o recente segue aguardando").isEqualTo("aguardando");
  }

  @Test
  @DisplayName("dias fora da faixa da 400, e corpo vazio usa o padrao 7")
  void diasInvalidos() throws Exception {
    Account vol = volunteer(mvc);

    for (String corpo : new String[] {"{\"dias\":0}", "{\"dias\":366}", "{\"dias\":\"sete\"}"}) {
      mvc.perform(post("/ajuda/encerrar-antigos").header("Authorization", bearer(vol))
              .contentType(MediaType.APPLICATION_JSON).content(corpo))
          .andExpect(status().isBadRequest());
    }

    mvc.perform(post("/ajuda/encerrar-antigos").header("Authorization", bearer(vol))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isOk());
  }
}
