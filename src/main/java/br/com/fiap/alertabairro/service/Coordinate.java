package br.com.fiap.alertabairro.service;

public record Coordinate(double latitude, double longitude) {

  /**
   * Range check shared by every route that accepts a coordinate. Throws
   * {@link IllegalArgumentException}, which the handler turns into 400 instead of letting
   * a nonsense coordinate through to produce a nonsense distance.
   */
  public static void requireValid(double latitude, double longitude) {
    if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
      throw new IllegalArgumentException("latitude deve estar entre -90 e 90");
    }
    if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
      throw new IllegalArgumentException("longitude deve estar entre -180 e 180");
    }
  }

  /** Validates a pair that may be absent, and returns null when it is. */
  public static Coordinate ofNullable(Double latitude, Double longitude) {
    if (latitude == null || longitude == null) {
      return null;
    }
    requireValid(latitude, longitude);
    return new Coordinate(latitude, longitude);
  }
}
