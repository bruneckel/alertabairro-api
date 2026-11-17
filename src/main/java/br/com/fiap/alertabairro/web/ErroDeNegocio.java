package br.com.fiap.alertabairro.web;

/**
 * Excecoes de negocio que o {@link TratadorDeErros} traduz em codigo HTTP.
 *
 * <p>Ficam aqui, juntas, porque sao um vocabulario e nao implementacoes: o servico lanca
 * "nao encontrado" ou "conflito" sem conhecer HTTP, e so a camada web decide o numero.
 */
public final class ErroDeNegocio {

  private ErroDeNegocio() {}

  /** 404. */
  public static class NaoEncontradoException extends RuntimeException {
    public NaoEncontradoException(String mensagem) {
      super(mensagem);
    }
  }

  /** 409: a requisicao e valida, mas o estado atual nao permite. */
  public static class ConflitoException extends RuntimeException {
    public ConflitoException(String mensagem) {
      super(mensagem);
    }
  }

  /**
   * 401. Mensagem unica de proposito: distinguir "e-mail nao existe" de "senha errada"
   * contaria a um estranho quem tem conta no aplicativo.
   */
  public static class CredenciaisInvalidasException extends RuntimeException {
    public CredenciaisInvalidasException() {
      super("e-mail ou senha invalidos");
    }
  }
}
