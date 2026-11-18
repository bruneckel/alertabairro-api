package br.com.fiap.alertabairro;

import static br.com.fiap.alertabairro.TestSupport.bearer;
import static br.com.fiap.alertabairro.TestSupport.read;
import static br.com.fiap.alertabairro.TestSupport.resident;
import static br.com.fiap.alertabairro.TestSupport.volunteer;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * Entrada malformada precisa virar 400 na camada web.
 *
 * <p>O plano eleva "nunca 500" a criterio de aceitacao em cinco linhas de Review Focus, e
 * os testes que existiam asseveravam pouco: conferiam a acao desconhecida mas nunca a
 * acao AUSENTE, que e o caso que estourava NullPointerException.
 *
 * <p>Tambem exige que o 400 venha da validacao e nao do banco: a spec 9.3 diz que a
 * validacao existe para recusar "antes de ocupar conexao de banco", e um ORA-01400
 * traduzido em 400 faz o oposto -- alem de nao nomear o campo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Validacao de entrada")
class ValidationIT extends OracleSupport {

  /** Mensagem que o tratador usa para ORA-01400: se ela aparecer, o banco e que recusou. */
  private static final String RECUSA_DO_BANCO = "campo obrigatorio nao informado";

  @Autowired private MockMvc mvc;

  private long criarOcorrencia(Account quem) throws Exception {
    return read(mvc.perform(post("/ocorrencias").header("Authorization", bearer(quem))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"tipo":"Sem luz","descricao":"Para validacao","latitude":-23.55,"longitude":-46.63}"""))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString()).path("id").asLong();
  }

  @Test
  @DisplayName("PATCH /ocorrencias sem acao da 400, nunca 500")
  void patchOcorrenciaSemAcao() throws Exception {
    Account quem = resident(mvc);
    long id = criarOcorrencia(quem);

    for (String corpo : new String[] {"{}", "{\"acao\":null}", "{\"acao\":\"\"}"}) {
      mvc.perform(patch("/ocorrencias/" + id).header("Authorization", bearer(quem))
              .contentType(MediaType.APPLICATION_JSON).content(corpo))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.erro").exists());
    }
  }

  @Test
  @DisplayName("PATCH /ajuda sem acao da 400, nunca 500")
  void patchAjudaSemAcao() throws Exception {
    Account quem = resident(mvc);
    long id = read(mvc.perform(post("/ajuda").header("Authorization", bearer(quem))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"modo":"pedido","categoria":"Val","descricao":"d","urgencia":"alta"}"""))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString()).path("id").asLong();

    for (String corpo : new String[] {"{}", "{\"acao\":null}"}) {
      mvc.perform(patch("/ajuda/" + id).header("Authorization", bearer(quem))
              .contentType(MediaType.APPLICATION_JSON).content(corpo))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  @DisplayName("quantidade fracionaria em vagas da 400, nao e truncada para inteiro")
  void quantidadeFracionaria() throws Exception {
    long idAbrigo = 1L;
    mvc.perform(patch("/abrigos/" + idAbrigo + "/vagas")
            .header("Authorization", bearer(volunteer(mvc)))
            .contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":1.5}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("campo de dominio ausente e recusado pela validacao, nao pelo banco")
  void dominioAusenteRecusadoNaWeb() throws Exception {
    Account quem = resident(mvc);

    String semStatus = mvc.perform(post("/checkins").header("Authorization", bearer(quem))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andReturn().getResponse().getContentAsString();
    assertThat(semStatus)
        .as("o 400 veio do Oracle (ORA-01400), depois de gastar conexao de banco")
        .doesNotContain(RECUSA_DO_BANCO);

    String semModo = mvc.perform(post("/ajuda").header("Authorization", bearer(quem))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"categoria\":\"X\",\"descricao\":\"d\",\"urgencia\":\"alta\"}"))
        .andExpect(status().isBadRequest())
        .andReturn().getResponse().getContentAsString();
    assertThat(semModo).doesNotContain(RECUSA_DO_BANCO);

    String semUrgencia = mvc.perform(post("/ajuda").header("Authorization", bearer(quem))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"modo\":\"pedido\",\"categoria\":\"X\",\"descricao\":\"d\"}"))
        .andExpect(status().isBadRequest())
        .andReturn().getResponse().getContentAsString();
    assertThat(semUrgencia).doesNotContain(RECUSA_DO_BANCO);
  }

  @Test
  @DisplayName("cadastro sem perfil e recusado antes do bcrypt e do banco")
  void cadastroSemPerfil() throws Exception {
    String corpo = mvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"nome":"Sem Perfil","email":"%s","bairro":"Centro","senha":"senha123"}"""
                .formatted(TestSupport.newEmail("val"))))
        .andExpect(status().isBadRequest())
        .andReturn().getResponse().getContentAsString();

    assertThat(corpo)
        .as("rota publica: recusar antes do bcrypt evita gastar CPU com requisicao anonima")
        .doesNotContain(RECUSA_DO_BANCO);
  }
}
