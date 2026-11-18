package br.com.fiap.alertabairro;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * O limite de corpo precisa de HTTP real para ser testado: o MockMvc sempre define
 * Content-Length, e e justamente a AUSENCIA desse cabecalho (Transfer-Encoding: chunked)
 * que furava a protecao.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Limite de corpo da requisicao")
class BodyLimitIT extends OracleSupport {

  @LocalServerPort private int porta;

  private static final int TRES_CENTOS_KB = 300 * 1024;

  private String corpoGrande() {
    return "{\"nome\":\"" + "x".repeat(TRES_CENTOS_KB) + "\",\"email\":\"a@b.co\","
        + "\"bairro\":\"C\",\"senha\":\"senha123\",\"perfil\":\"morador\"}";
  }

  @Test
  @DisplayName("corpo grande com Content-Length e recusado com 413")
  void comContentLength() throws Exception {
    HttpResponse<String> r = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/auth/cadastro"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(corpoGrande()))
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertThat(r.statusCode()).isEqualTo(413);
  }

  @Test
  @DisplayName("corpo grande SEM Content-Length (chunked) tambem e recusado")
  void comChunked() throws Exception {
    // ofInputStream nao sabe o tamanho, entao o cliente envia Transfer-Encoding: chunked
    // e getContentLengthLong() devolve -1 no servidor.
    byte[] corpo = corpoGrande().getBytes(StandardCharsets.UTF_8);
    HttpResponse<String> r = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/auth/cadastro"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(corpo)))
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertThat(r.statusCode())
        .as("rota publica: um chamador anonimo nao pode contornar o limite com um cabecalho")
        .isEqualTo(413);
  }

  @Test
  @DisplayName("corpo normal passa")
  void corpoNormal() throws Exception {
    String corpo = "{\"nome\":\"Normal\",\"email\":\"" + TestSupport.newEmail("limite")
        + "\",\"bairro\":\"Centro\",\"senha\":\"senha123\",\"perfil\":\"morador\"}";
    HttpResponse<String> r = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/auth/cadastro"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofInputStream(
                () -> new ByteArrayInputStream(corpo.getBytes(StandardCharsets.UTF_8))))
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertThat(r.statusCode()).isEqualTo(201);
  }
}
