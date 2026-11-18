package br.com.fiap.alertabairro.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps the request body size.
 *
 * <p>Exists because no Spring Boot property limits a JSON body:
 * {@code server.tomcat.max-http-form-post-size} only covers form encoding.
 *
 * <p>Checking {@code getContentLength()} alone is not enough — it returns −1 for
 * {@code Transfer-Encoding: chunked}, so one header defeated the limit on a public route.
 * The stream is therefore counted as it is read, and the declared length is only a fast
 * path that rejects before any byte is consumed.
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
      reject(response);
      return;
    }
    chain.doFilter(new CountingRequest(request, limitBytes), response);
  }

  private static void reject(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"erro\":\"corpo da requisicao muito grande\"}");
  }

  /** Counts bytes as they are consumed, whatever the transfer encoding says. */
  private static final class CountingRequest extends HttpServletRequestWrapper {

    private final long limitBytes;

    CountingRequest(HttpServletRequest request, long limitBytes) {
      super(request);
      this.limitBytes = limitBytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      ServletInputStream origem = super.getInputStream();
      return new ServletInputStream() {
        private long lidos;

        private int contar(int quantidade) {
          if (quantidade > 0) {
            lidos += quantidade;
            if (lidos > limitBytes) {
              throw new RequestTooLargeException(limitBytes);
            }
          }
          return quantidade;
        }

        @Override
        public int read() throws IOException {
          int b = origem.read();
          if (b != -1) {
            contar(1);
          }
          return b;
        }

        @Override
        public int read(byte[] destino, int inicio, int tamanho) throws IOException {
          return contar(origem.read(destino, inicio, tamanho));
        }

        @Override
        public boolean isFinished() {
          return origem.isFinished();
        }

        @Override
        public boolean isReady() {
          return origem.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
          origem.setReadListener(listener);
        }
      };
    }
  }
}
