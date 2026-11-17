package br.com.fiap.alertabairro.repository;

import br.com.fiap.alertabairro.service.Coordinate;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CheckinRepository {

  private final JdbcClient jdbc;

  public CheckinRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Registers the check-in and returns the generated id. */
  public long register(long userId, String status, Double latitude, Double longitude) {
    long id = jdbc.sql("SELECT seq_checkin.NEXTVAL FROM dual").query(Long.class).single();
    jdbc.sql("""
            INSERT INTO checkin (id, id_usuario, status, latitude, longitude)
                 VALUES (:id, :user, :status, :lat, :lng)
            """)
        .param("id", id)
        .param("user", userId)
        .param("status", status)
        .param("lat", latitude)
        .param("lng", longitude)
        .update();
    return id;
  }

  /**
   * Last known position of a user, used as the fallback for the distance shown in the help
   * tab when the app sends no coordinate. ROWNUM instead of FETCH FIRST, for portability.
   */
  public Optional<Coordinate> lastPosition(long userId) {
    return jdbc.sql("""
            SELECT latitude, longitude FROM (
              SELECT latitude, longitude FROM checkin
               WHERE id_usuario = :user AND latitude IS NOT NULL
               ORDER BY criado_em DESC, id DESC
            ) WHERE ROWNUM = 1
            """)
        .param("user", userId)
        .query((rs, row) -> new Coordinate(rs.getDouble("latitude"), rs.getDouble("longitude")))
        .optional();
  }
}
