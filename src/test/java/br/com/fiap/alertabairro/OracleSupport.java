package br.com.fiap.alertabairro;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Oracle próprio para os testes de integração, criado por Testcontainers.
 *
 * <p>Antes disto, a suíte rodava contra o mesmo banco que o app e a gravação do vídeo
 * usam: uma revisão encontrou 848 usuários, 839 deles {@code @alertabairro.test}, e 255
 * ocorrências chamadas "Ocorrencia para confirmar" — o suficiente para dominar
 * {@code /indicadores}, que é uma das funcionalidades avaliadas, e para passar das 200
 * linhas que a tela de Detalhe consegue alcançar.
 *
 * <p>O Flyway aplica {@code database/migrations/} dentro do container, então as rotinas
 * PL/SQL são exercitadas de verdade, e não contra um dublê.
 *
 * <p>O container é um singleton de JVM, iniciado no bloco estático e não anotado com
 * {@code @Container}: assim ele sobe uma vez para toda a suíte, em vez de uma vez por
 * classe de teste. Com {@code testcontainers.reuse.enable=true} em
 * {@code ~/.testcontainers.properties}, ele também sobrevive entre execuções.
 */
abstract class OracleSupport {

  private static final OracleContainer ORACLE =
      new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:23-slim"))
          .withUsername("alertabairro")
          .withPassword("alertabairro_test")
          // Mesmo fuso do banco de produção local: a API atribui o offset a partir do
          // fuso da JVM, e divergir entre os dois deslocaria o instante gravado.
          .withEnv("TZ", "America/Sao_Paulo")
          .withReuse(true);

  static {
    ORACLE.start();
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
    registry.add("spring.datasource.username", ORACLE::getUsername);
    registry.add("spring.datasource.password", ORACLE::getPassword);
  }
}
