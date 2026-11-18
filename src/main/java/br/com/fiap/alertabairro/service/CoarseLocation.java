package br.com.fiap.alertabairro.service;

/**
 * Coarsens a coordinate onto a fixed grid before any distance is computed from it.
 *
 * <p>Omitting latitude and longitude from the response is not enough to protect whoever
 * asked for help. If the caller chooses the reference point and the API answers an exact
 * metric distance, three ordinary calls trilaterate the target: a review recovered a help
 * request's position to within 0.2 m using only public routes.
 *
 * <p><strong>Both endpoints are snapped</strong>, not just the stored one. Snapping only
 * the request would still let an attacker refine the answer by moving the probe a few
 * metres at a time; snapping the viewer too means a probe displaced inside the same cell
 * returns an identical answer, so extra probes add no information.
 *
 * <p>The grid is 0.002°, about 222 m north-south and 204 m east-west at this latitude.
 * What leaks is the cell, not the position.
 */
public final class CoarseLocation {

  static final double GRID_DEGREES = 0.002;

  private CoarseLocation() {}

  public static Coordinate snap(Coordinate exact) {
    return exact == null
        ? null
        : new Coordinate(snapDegrees(exact.latitude()), snapDegrees(exact.longitude()));
  }

  public static Double snapDegrees(Double degrees) {
    if (degrees == null) {
      return null;
    }
    double snapped = Math.round(degrees / GRID_DEGREES) * GRID_DEGREES;
    // The multiplication leaves artefacts like -23.560000000000002, which would reach the
    // NUMBER(9,6) column as noise.
    return Math.round(snapped * 1_000_000d) / 1_000_000d;
  }
}
