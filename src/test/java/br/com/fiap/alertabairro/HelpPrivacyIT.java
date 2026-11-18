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
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A API nao pode permitir localizar quem pediu ajuda.
 *
 * <p>Nao basta omitir latitude e longitude da resposta. Se o cliente escolhe o ponto de
 * referencia e a API responde a distancia exata, tres chamadas normais trilateram a
 * posicao -- foi exatamente assim que uma revisao recuperou a coordenada de um pedido com
 * 0,2 m de erro, usando so rotas publicas da API.
 *
 * <p>A propriedade testada aqui e direta: a API nao pode distinguir duas posicoes dentro
 * da mesma celula da grade. Se nao distingue, nao ha o que trilaterar abaixo do tamanho
 * da celula.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Ajuda: privacidade de quem pede")
class HelpPrivacyIT {

  private static final double LAT_A = -23.561000;
  private static final double LNG_A = -46.611000;

  /** ~1,2 km: celula claramente diferente. */
  private static final double LAT_LONGE = -23.572000;
  private static final double LNG_LONGE = -46.611000;

  /**
   * Sonda PERTO do alvo, a ~30 m. E esse o regime em que o ataque funciona: abaixo de
   * 1 km a resposta sai em metros inteiros, com resolucao de 1 m. Sondar de longe nao
   * prova nada, porque o formato "1,4 km" ja esconde a diferenca por arredondamento.
   */
  private static final double LAT_PROBE = -23.561250;
  private static final double LNG_PROBE = -46.611150;

  @Autowired private MockMvc mvc;

  private long criar(Account quem, String categoria, double lat, double lng) throws Exception {
    String corpo = """
        {"modo":"pedido","categoria":"%s","descricao":"Privacidade","urgencia":"alta",
         "latitude":%s,"longitude":%s}
        """.formatted(categoria, lat, lng);
    return read(mvc.perform(post("/ajuda").header("Authorization", bearer(quem))
            .contentType(MediaType.APPLICATION_JSON).content(corpo))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString()).path("id").asLong();
  }

  private String distanciaDe(Account quem, long id, double lat, double lng) throws Exception {
    JsonNode lista = read(mvc.perform(get("/ajuda?limite=200&lat=" + lat + "&lng=" + lng)
            .header("Authorization", bearer(quem)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    for (JsonNode p : lista) {
      if (p.path("id").asLong() == id) {
        return p.path("distancia").asText();
      }
    }
    throw new AssertionError("pedido " + id + " nao apareceu na lista");
  }

  @Test
  @DisplayName("sondar o mesmo pedido de 20 pontos vizinhos nao refina a posicao")
  void sondagemRepetidaNaoRefina() throws Exception {
    Account quem = resident(mvc);
    long id = criar(quem, "PrivSonda", LAT_A, LNG_A);

    // O ataque e iterativo: cada sonda de um ponto um pouco diferente acrescenta uma
    // equacao, e a intersecao converge na posicao exata. A defesa nao e fazer dois
    // pontos vizinhos coincidirem -- qualquer arredondamento deterministico falha nas
    // fronteiras -- e sim fazer a RESPOSTA carregar pouca informacao.
    //
    // Com distancia exata, 20 sondas distintas dao ~20 respostas distintas.
    Set<String> respostas = new HashSet<>();
    for (int i = 0; i < 20; i++) {
      respostas.add(distanciaDe(quem, id, LAT_PROBE - i * 0.00007, LNG_PROBE + i * 0.00005));
    }

    assertThat(respostas)
        .as("20 sondas vizinhas produziram %s respostas distintas: cada uma e uma equacao "
            + "a mais para a trilateracao", respostas.size())
        .hasSizeLessThanOrEqualTo(3);
  }

  @Test
  @DisplayName("a funcionalidade continua util: 1,2 km de distancia ainda se distingue")
  void aindaDistingueDistanciasUteis() throws Exception {
    Account quem = resident(mvc);
    long perto = criar(quem, "PrivPerto", LAT_A, LNG_A);
    long longe = criar(quem, "PrivLonge", LAT_LONGE, LNG_LONGE);

    assertThat(distanciaDe(quem, perto, LAT_PROBE, LNG_PROBE))
        .as("proteger a posicao nao pode tornar a distancia inutil")
        .isNotEqualTo(distanciaDe(quem, longe, LAT_PROBE, LNG_PROBE));
  }

  @Test
  @DisplayName("a coordenada precisa nunca e persistida")
  void coordenadaPrecisaNaoPersiste() throws Exception {
    Account quem = resident(mvc);
    long id = criar(quem, "PrivPersist", LAT_A, LNG_A);

    Double lat = jdbcLatitude(id);
    assertThat(lat).isNotNull();
    assertThat(Math.abs(lat - LAT_A))
        .as("a latitude exata informada nao pode ficar gravada no banco")
        .isGreaterThan(0.0000001);
  }

  @Autowired private org.springframework.jdbc.core.simple.JdbcClient jdbc;

  private Double jdbcLatitude(long id) {
    return jdbc.sql("SELECT latitude FROM pedido_ajuda WHERE id = :id")
        .param("id", id).query(Double.class).single();
  }

  @Test
  @DisplayName("a resposta segue sem latitude, longitude nem id do autor")
  void respostaSemCoordenada() throws Exception {
    Account quem = resident(mvc);
    criar(quem, "PrivCampos", LAT_A, LNG_A);

    String corpo = mvc.perform(get("/ajuda?limite=200&lat=" + LAT_PROBE + "&lng=" + LNG_PROBE)
            .header("Authorization", bearer(quem)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    assertThat(corpo).doesNotContain("latitude").doesNotContain("longitude")
        .doesNotContain("idUsuario").doesNotContain("id_usuario");
  }

  @Test
  @DisplayName("coordenada invalida na consulta da 400, como em /abrigos/proximo")
  void coordenadaInvalidaNaConsulta() throws Exception {
    Account quem = resident(mvc);
    String[] ruins = {"&lat=91&lng=-46.6", "&lat=-23.5&lng=-181",
                      "&lat=NaN&lng=0", "&lat=Infinity&lng=0", "&lng=200"};
    for (String q : ruins) {
      mvc.perform(get("/ajuda?limite=10" + q).header("Authorization", bearer(quem)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.erro").exists());
    }
  }
}
