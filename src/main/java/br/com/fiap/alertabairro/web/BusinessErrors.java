package br.com.fiap.alertabairro.web;

/** Vocabulary the service layer throws without knowing about HTTP. */
public final class BusinessErrors {

  private BusinessErrors() {}

  /** 404 */
  public static class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
      super(message);
    }
  }

  /** 409: the request is valid but the current state forbids it. */
  public static class ConflictException extends RuntimeException {
    public ConflictException(String message) {
      super(message);
    }
  }

  /**
   * 401 with a single message on purpose: telling "no such e-mail" apart from "wrong
   * password" would reveal who has an account.
   */
  public static class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
      super("e-mail ou senha invalidos");
    }
  }
}
