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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** "Estou bem" e o botao SOS. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Check-ins")
class CheckinIT {

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("check-in com coordenada devolve 201 com id e status")
  void comCoordenada() throws Exception {
    mvc.perform(post("/checkins").header("Authorization", bearer(resident(mvc)))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"estou_bem\",\"latitude\":-23.5489,\"longitude\":-46.6388}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.status").value("estou_bem"));
  }

  @Test
  @DisplayName("a coordenada e opcional")
  void semCoordenada() throws Exception {
    mvc.perform(post("/checkins").header("Authorization", bearer(resident(mvc)))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"preciso_de_ajuda\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("preciso_de_ajuda"));
  }

  @Test
  @DisplayName("status fora do dominio e coordenada fora de faixa dao 400")
  void invalidos() throws Exception {
    Account quem = resident(mvc);
    String[] ruins = {
        "{\"status\":\"mais_ou_menos\"}",
        "{}",
        "{\"status\":\"estou_bem\",\"latitude\":91,\"longitude\":-46.6}",
        "{\"status\":\"estou_bem\",\"latitude\":-23.5,\"longitude\":-181}"};
    for (String corpo : ruins) {
      mvc.perform(post("/checkins").header("Authorization", bearer(quem))
              .contentType(MediaType.APPLICATION_JSON).content(corpo))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.erro").exists());
    }
  }

  @Test
  @DisplayName("depois de um check-in com coordenada, GET /ajuda sem lat/lng calcula a distancia")
  void checkinAlimentaADistanciaDaAjuda() throws Exception {
    Account quem = resident(mvc);

    mvc.perform(post("/checkins").header("Authorization", bearer(quem))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"estou_bem\",\"latitude\":-23.5489,\"longitude\":-46.6388}"))
        .andExpect(status().isCreated());

    mvc.perform(post("/ajuda").header("Authorization", bearer(quem))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"modo":"pedido","categoria":"Agua","descricao":"Apos check-in",
                 "urgencia":"alta","latitude":-23.5489,"longitude":-46.6388}"""))
        .andExpect(status().isCreated());

    JsonNode lista = read(mvc.perform(get("/ajuda?limite=200").header("Authorization", bearer(quem)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

    boolean achou = false;
    for (JsonNode p : lista) {
      if (p.path("meu").asBoolean() && "Agua".equals(p.path("categoria").asText())) {
        assertThat(p.path("distancia").asText())
            .as("sem lat/lng na consulta, a posicao vem do ultimo check-in")
            .isEqualTo("0 m");
        achou = true;
      }
    }
    assertThat(achou).isTrue();
  }

  @Test
  @DisplayName("sem token, 401")
  void semToken() throws Exception {
    mvc.perform(post("/checkins").contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"estou_bem\"}"))
        .andExpect(status().isUnauthorized());
  }
}
