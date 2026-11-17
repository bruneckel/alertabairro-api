package br.com.fiap.alertabairro.service;

/** Haversine and the text the app prints. Same formula as the app's {@code utils/geo.ts}. */
public final class Distance {

  private static final double EARTH_RADIUS_KM = 6371;

  private Distance() {}

  public static double km(double lat1, double lng1, double lat2, double lng2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double a = Math.pow(Math.sin(dLat / 2), 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.pow(Math.sin(dLng / 2), 2);
    return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
  }

  /** Comma as the decimal separator: the app is in Portuguese. */
  public static String format(double km) {
    if (km < 1) {
      return Math.round(km * 1000) + " m";
    }
    return String.format(java.util.Locale.of("pt", "BR"), "%.1f km", km);
  }
}
