package br.com.fiap.alertabairro.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Recusa corpo de requisicao acima do limite configurado.
 *
 * <p>Existe porque nenhuma propriedade do Spring Boot cobre o tamanho de um corpo JSON:
 * {@code server.tomcat.max-http-form-post-size} vale apenas para
 * {@code application/x-www-form-urlencoded}, e {@code max-http-request-header-size} so
 * para cabecalhos. Sem isto, um POST grande vira pressao de memoria no heap antes de
 * qualquer validacao rodar.
 *
 * <p>Responde 413, e nao 400, porque o problema e o tamanho e nao o conteudo.
 */
@Component
public class LimiteDeCorpoFilter extends OncePerRequestFilter {

  private final long limiteBytes;

  public LimiteDeCorpoFilter(
      @Value("${alertabairro.tamanho-maximo-corpo-bytes:102400}") long limiteBytes) {
    this.limiteBytes = limiteBytes;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
      throws ServletException, IOException {

    long declarado = requisicao.getContentLengthLong();
    if (declarado > limiteBytes) {
      resposta.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
      resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
      resposta.setCharacterEncoding("UTF-8");
      resposta.getWriter().write("{\"erro\":\"corpo da requisicao muito grande\"}");
      return;
    }
    cadeia.doFilter(requisicao, resposta);
  }
}
