package br.com.fiap.alertabairro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Creates real users through the API and hands back the token.
 *
 * <p>Every call uses a random e-mail: these tests run against a real, shared database, so
 * none of them may assume an empty schema or step on its neighbour.
 */
final class TestSupport {

  private static final ObjectMapper JSON = new ObjectMapper();

  private TestSupport() {}

  record Account(long id, String email, String token, String neighborhood, String role) {}

  static String newEmail(String prefix) {
    return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@alertabairro.test";
  }

  static Account signUp(MockMvc mvc, String role, String neighborhood) throws Exception {
    String email = newEmail("t" + role.charAt(0));
    String body = """
        {"nome":"Pessoa %s","email":"%s","bairro":"%s","senha":"senha123","perfil":"%s"}
        """.formatted(role, email, neighborhood, role);

    String response = mvc.perform(post("/auth/cadastro")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andReturn().getResponse().getContentAsString();

    JsonNode node = JSON.readTree(response);
    if (!node.has("token")) {
      throw new IllegalStateException("sign-up returned no token: " + response);
    }
    return new Account(
        node.path("usuario").path("id").asLong(),
        node.path("usuario").path("email").asText(),
        node.path("token").asText(),
        neighborhood,
        role);
  }

  static Account resident(MockMvc mvc) throws Exception {
    return signUp(mvc, "morador", "Centro");
  }

  static Account volunteer(MockMvc mvc) throws Exception {
    return signUp(mvc, "voluntario", "Centro");
  }

  static String bearer(Account account) {
    return "Bearer " + account.token();
  }

  static JsonNode read(String json) throws Exception {
    return JSON.readTree(json);
  }
}
