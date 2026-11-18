package br.com.fiap.alertabairro.config;

/** Raised when a request body exceeds the configured limit while being read. */
public class RequestTooLargeException extends RuntimeException {

  public RequestTooLargeException(long limitBytes) {
    super("corpo da requisicao muito grande (limite " + limitBytes + " bytes)");
  }
}
