package br.com.fiap.alertabairro.web.dto;

/**
 * Every listing is paginated, so the heap a request can spend is bound by contract rather
 * than by the table still being small.
 */
public record Pagination(int limit, int offset) {

  public static final int MAX_LIMIT = 200;

  /** Throws {@link IllegalArgumentException}, which the handler turns into 400. */
  public static Pagination of(Integer limit, Integer offset) {
    int l = limit == null ? MAX_LIMIT : limit;
    int o = offset == null ? 0 : offset;

    if (l < 1 || l > MAX_LIMIT) {
      throw new IllegalArgumentException("limite deve ser inteiro de 1 a " + MAX_LIMIT);
    }
    if (o < 0) {
      throw new IllegalArgumentException("deslocamento deve ser inteiro maior ou igual a 0");
    }
    return new Pagination(l, o);
  }

  public int lastRow() {
    return offset + limit;
  }
}
