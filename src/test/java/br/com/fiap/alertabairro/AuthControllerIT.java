package br.com.fiap.alertabairro;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Cadastro, login e a porta de entrada do JWT.
 *
 * <p>Usa e-mails aleatorios a cada execucao: o teste roda contra um banco de verdade e nao
 * pode depender de o schema estar vazio nem deixar o proximo em estado diferente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties =
    "alertabairro.jwt.segredo=segredo-de-teste-com-mais-de-trinta-e-dois-caracteres")
@DisplayName("Auth: cadastro, login e validacao de token")
class AuthControllerIT extends OracleSupport {

  private static final String SEGREDO =
      "segredo-de-teste-com-mais-de-trinta-e-dois-caracteres";
  private static final String OUTRO_SEGREDO =
      "outro-segredo-completamente-diferente-com-32-mais";

  @Autowired private MockMvc mvc;

  private static String emailNovo() {
    return "t8." + UUID.randomUUID().toString().substring(0, 8) + "@alertabairro.test";
  }

  private static String corpoCadastro(String email, String perfil, String senha) {
    return """
        {"nome":"Pessoa Teste","email":"%s","bairro":"Centro","senha":"%s","perfil":"%s"}
        """.formatted(email, senha, perfil);
  }

  private MvcResult cadastrar(String email) throws Exception {
    return mvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
            .content(corpoCadastro(email, "morador", "senha123")))
        .andExpect(status().isCreated())
        .andReturn();
  }

  /** Monta um JWT assinado na mao, para exercitar token adulterado e expirado. */
  private static String token(String segredo, Instant expiraEm, String algoritmo) throws Exception {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject("1")
        .claim("perfil", "morador")
        .issueTime(Date.from(Instant.now().minusSeconds(60)))
        .expirationTime(Date.from(expiraEm))
        .build();
    SignedJWT jwt = new SignedJWT(
        new JWSHeader(JWSAlgorithm.parse(algoritmo)), claims);
    jwt.sign(new MACSigner(segredo.getBytes()));
    return jwt.serialize();
  }

  // ------------------------------------------------------------------ cadastro

  @Test
  @DisplayName("cadastro valido devolve 201 com token e usuario, e nunca a senha")
  void cadastroValido() throws Exception {
    String email = emailNovo();
    mvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
            .content(corpoCadastro(email, "morador", "senha123")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token", not(blankOrNullString())))
        .andExpect(jsonPath("$.usuario.id").exists())
        .andExpect(jsonPath("$.usuario.email", is(email)))
        .andExpect(jsonPath("$.usuario.perfil", is("morador")))
        .andExpect(jsonPath("$.usuario.senha").doesNotExist())
        .andExpect(jsonPath("$.usuario.senhaHash").doesNotExist())
        .andExpect(jsonPath("$.usuario.senha_hash").doesNotExist());
  }

  @Test
  @DisplayName("o e-mail e normalizado: espacos fora e caixa baixa")
  void emailNormalizado() throws Exception {
    String email = emailNovo();
    mvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
            .content(corpoCadastro("  " + email.toUpperCase() + "  ", "idoso", "senha123")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.usuario.email", is(email)));
  }

  @Test
  @DisplayName("e-mail repetido devolve 409, mesmo em outra caixa")
  void emailRepetido() throws Exception {
    String email = emailNovo();
    cadastrar(email);

    mvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
            .content(corpoCadastro(email, "morador", "senha123")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.erro").exists());

    mvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
            .content(corpoCadastro(email.toUpperCase(), "morador", "senha123")))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("perfil fora do dominio devolve 400")
  void perfilInvalido() throws Exception {
    mvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
            .content(corpoCadastro(emailNovo(), "sindico", "senha123")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erro").exists());
  }

  @Test
  @DisplayName("senha curta devolve 400")
  void senhaCurta() throws Exception {
    mvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
            .content(corpoCadastro(emailNovo(), "morador", "12345")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("e-mail malformado devolve 400")
  void emailInvalido() throws Exception {
    mvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
            .content(corpoCadastro("nao-e-email", "morador", "senha123")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("JSON malformado devolve 400, nunca 500")
  void jsonMalformado() throws Exception {
    mvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
            .content("{\"nome\":"))
        .andExpect(status().isBadRequest());
  }

  // --------------------------------------------------------------------- login

  @Test
  @DisplayName("login correto devolve 200 com token")
  void loginCorreto() throws Exception {
    String email = emailNovo();
    cadastrar(email);

    mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"senha\":\"senha123\"}".formatted(email)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token", not(blankOrNullString())))
        .andExpect(jsonPath("$.usuario.email", is(email)));
  }

  @Test
  @DisplayName("login aceita o e-mail em outra caixa")
  void loginIgnoraCaixa() throws Exception {
    String email = emailNovo();
    cadastrar(email);

    mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"senha\":\"senha123\"}".formatted(email.toUpperCase())))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("senha errada e e-mail inexistente devolvem 401 com a MESMA mensagem")
  void loginNaoRevelaQuemTemConta() throws Exception {
    String email = emailNovo();
    cadastrar(email);

    String comSenhaErrada = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"senha\":\"errada999\"}".formatted(email)))
        .andExpect(status().isUnauthorized())
        .andReturn().getResponse().getContentAsString();

    String semConta = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"senha\":\"senha123\"}".formatted(emailNovo())))
        .andExpect(status().isUnauthorized())
        .andReturn().getResponse().getContentAsString();

    assertThat(comSenhaErrada)
        .as("a resposta precisa ser identica, senao ela revela quem tem conta")
        .isEqualTo(semConta);
  }

  // --------------------------------------------- Review Focus 1: porta do JWT

  @Test
  @DisplayName("rota protegida sem token devolve 401")
  void semToken() throws Exception {
    mvc.perform(get("/ocorrencias")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("token que nao e JWT devolve 401, nunca 500")
  void tokenLixo() throws Exception {
    mvc.perform(get("/ocorrencias").header("Authorization", "Bearer lixo"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("token assinado com outro segredo devolve 401")
  void tokenDeOutroSegredo() throws Exception {
    String falso = token(OUTRO_SEGREDO, Instant.now().plusSeconds(3600), "HS256");
    mvc.perform(get("/ocorrencias").header("Authorization", "Bearer " + falso))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("token expirado devolve 401")
  void tokenExpirado() throws Exception {
    String vencido = token(SEGREDO, Instant.now().minusSeconds(120), "HS256");
    mvc.perform(get("/ocorrencias").header("Authorization", "Bearer " + vencido))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("esquema diferente de Bearer devolve 401")
  void esquemaErrado() throws Exception {
    String valido = token(SEGREDO, Instant.now().plusSeconds(3600), "HS256");
    mvc.perform(get("/ocorrencias").header("Authorization", "Basic " + valido))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("token emitido pelo proprio cadastro abre a rota protegida")
  void tokenDoCadastroFunciona() throws Exception {
    String corpo = cadastrar(emailNovo()).getResponse().getContentAsString();
    String jwt = corpo.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    assertThat(jwt).as("token extraido do corpo").isNotBlank().doesNotContain("{");

    // is(not(401)) aceitaria um 500: a asercao precisa dizer que a rota FUNCIONOU.
    mvc.perform(get("/ocorrencias").header("Authorization", "Bearer " + jwt))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("erro sempre sai no formato {\"erro\": ...}")
  void formatoDoErro() throws Exception {
    mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"senha\":\"errada999\"}".formatted(emailNovo())))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.erro").exists());
  }
}
