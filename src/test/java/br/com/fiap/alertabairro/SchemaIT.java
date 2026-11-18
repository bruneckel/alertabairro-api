package br.com.fiap.alertabairro;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Prova que o Flyway aplicou as migracoes de database/migrations/ e que os objetos
 * PL/SQL ficaram compilados.
 *
 * <p>Conferir apenas a EXISTENCIA nao bastaria: um objeto PL/SQL com erro de compilacao
 * continua existindo em user_objects, com status INVALID, e so falha quando alguem o
 * chama. Por isso cada rotina e verificada tambem pelo status.
 */
@SpringBootTest
@DisplayName("Schema: migracoes aplicadas e objetos PL/SQL validos")
class SchemaIT extends OracleSupport {

  @Autowired private JdbcClient jdbc;

  private static final List<String> TABELAS = List.of(
      "USUARIO", "OCORRENCIA", "ABRIGO", "PEDIDO_AJUDA", "CHECKIN", "CONFIRMACAO_OCORRENCIA");

  private static final List<String> ROTINAS = List.of(
      "PRC_REGISTRAR_OCORRENCIA", "PRC_CONTROLE_VAGAS", "PRC_ENCERRAR_PEDIDOS_ANTIGOS",
      "FN_ABRIGO_MAIS_PROXIMO", "FN_OCUPACAO_ABRIGOS",
      "TRG_OCORRENCIA_ESCALONA", "VW_INDICADORES");

  @Test
  @DisplayName("as 6 tabelas do modelo existem")
  void tabelasExistem() {
    for (String tabela : TABELAS) {
      Long qtd = jdbc.sql("SELECT COUNT(*) FROM user_tables WHERE table_name = ?")
          .param(tabela).query(Long.class).single();
      assertThat(qtd).as("tabela %s", tabela).isEqualTo(1L);
    }
  }

  @Test
  @DisplayName("as rotinas PL/SQL existem e estao VALID")
  void rotinasCompiladas() {
    for (String rotina : ROTINAS) {
      List<String> status = jdbc.sql("SELECT status FROM user_objects WHERE object_name = ?")
          .param(rotina).query(String.class).list();
      assertThat(status).as("objeto %s deveria existir", rotina).hasSize(1);
      assertThat(status.getFirst()).as("objeto %s deveria estar compilado", rotina).isEqualTo("VALID");
    }
  }

  @Test
  @DisplayName("as 5 sequences de chave primaria existem")
  void sequencesExistem() {
    Long qtd = jdbc.sql("SELECT COUNT(*) FROM user_sequences WHERE sequence_name LIKE 'SEQ_%'")
        .query(Long.class).single();
    assertThat(qtd).isEqualTo(5L);
  }

  @Test
  @DisplayName("a carga inicial de abrigos chegou")
  void cargaDeAbrigos() {
    Long qtd = jdbc.sql("SELECT COUNT(*) FROM abrigo").query(Long.class).single();
    assertThat(qtd).isGreaterThanOrEqualTo(3L);
  }
}
