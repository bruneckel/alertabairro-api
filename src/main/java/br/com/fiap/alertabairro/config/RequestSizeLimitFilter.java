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
 * Exists because no Spring Boot property limits a JSON body:
 * {@code server.tomcat.max-http-form-post-size} only covers form encoding. Without it a
 * large POST becomes heap pressure before any validation runs.
 */
@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

  private final long limitBytes;

  public RequestSizeLimitFilter(
      @Value("${alertabairro.tamanho-maximo-corpo-bytes:102400}") long limitBytes) {
    this.limitBytes = limitBytes;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    if (request.getContentLengthLong() > limitBytes) {
      response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write("{\"erro\":\"corpo da requisicao muito grande\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}
