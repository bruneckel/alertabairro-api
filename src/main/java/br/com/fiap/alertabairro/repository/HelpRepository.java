package br.com.fiap.alertabairro.repository;

import br.com.fiap.alertabairro.web.dto.NewHelpRequest;
import br.com.fiap.alertabairro.web.dto.Pagination;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class HelpRepository {

  /** Raw row: carries the coordinate and author id that stop at the service layer. */
  public record HelpWithLocation(
      long id, long userId, String mode, String category, String description,
      String urgency, String status, Double latitude, Double longitude) {}

  private static final String COLUMNS =
      "id, id_usuario, modo, categoria, descricao, urgencia, status, latitude, longitude";

  private final JdbcClient jdbc;

  public HelpRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Waiting items first, then newest. ROWNUM paging, same reason as the incidents. */
  public List<HelpWithLocation> list(Pagination page) {
    return jdbc.sql("""
            SELECT * FROM (
              SELECT q.*, ROWNUM rn FROM (
                SELECT %s FROM pedido_ajuda
                 ORDER BY CASE status WHEN 'aguardando' THEN 0 ELSE 1 END,
                          criado_em DESC, id DESC
              ) q WHERE ROWNUM <= :lastRow
            ) WHERE rn > :offset
            """.formatted(COLUMNS))
        .param("lastRow", page.lastRow())
        .param("offset", page.offset())
        .query(HelpRepository::map)
        .list();
  }

  public Optional<HelpWithLocation> find(long id) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM pedido_ajuda WHERE id = :id")
        .param("id", id)
        .query(HelpRepository::map)
        .optional();
  }

  public long create(long userId, NewHelpRequest request, Double latitude, Double longitude) {
    long id = jdbc.sql("SELECT seq_pedido_ajuda.NEXTVAL FROM dual").query(Long.class).single();
    jdbc.sql("""
            INSERT INTO pedido_ajuda
                   (id, id_usuario, modo, categoria, descricao, urgencia, latitude, longitude)
            VALUES (:id, :user, :mode, :category, :description, :urgency, :lat, :lng)
            """)
        .param("id", id)
        .param("user", userId)
        .param("mode", request.mode())
        .param("category", request.category())
        .param("description", request.description())
        .param("urgency", request.urgency())
        .param("lat", latitude)
        .param("lng", longitude)
        .update();
    return id;
  }

  static HelpWithLocation map(ResultSet rs, int row) throws SQLException {
    double lat = rs.getDouble("latitude");
    boolean latNull = rs.wasNull();
    double lng = rs.getDouble("longitude");
    boolean lngNull = rs.wasNull();
    return new HelpWithLocation(
        rs.getLong("id"), rs.getLong("id_usuario"), rs.getString("modo"),
        rs.getString("categoria"), rs.getString("descricao"), rs.getString("urgencia"),
        rs.getString("status"), latNull ? null : lat, lngNull ? null : lng);
  }
}
