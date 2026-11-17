package br.com.fiap.alertabairro.repository;

import br.com.fiap.alertabairro.web.dto.NewIncidentRequest;
import br.com.fiap.alertabairro.web.dto.Pagination;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

@Repository
public class IncidentRepository {

  /** Raw row, carrying the author id that only the service layer sees. */
  public record IncidentWithAuthor(
      long id, long userId, String type, String description,
      double latitude, double longitude, String status, String priority,
      int reports, String neighborhood, String photoUri, OffsetDateTime createdAt) {}

  private static final String COLUMNS = """
      id, id_usuario, tipo, descricao, latitude, longitude,
      status, prioridade, relatos, bairro, foto_uri, criada_em
      """;

  private final JdbcClient jdbc;
  private final SimpleJdbcCall registerCall;

  public IncidentRepository(JdbcClient jdbc, JdbcTemplate template) {
    this.jdbc = jdbc;
    // Parameter ORDER is the contract with the procedure. Declaring it here instead of
    // letting the driver read metadata keeps the contract visible and avoids a data
    // dictionary lookup per call.
    this.registerCall = new SimpleJdbcCall(template)
        .withProcedureName("prc_registrar_ocorrencia")
        .withoutProcedureColumnMetaDataAccess()
        .declareParameters(
            new SqlParameter("p_id_usuario", Types.NUMERIC),
            new SqlParameter("p_tipo", Types.VARCHAR),
            new SqlParameter("p_descricao", Types.VARCHAR),
            new SqlParameter("p_latitude", Types.NUMERIC),
            new SqlParameter("p_longitude", Types.NUMERIC),
            new SqlParameter("p_foto_uri", Types.VARCHAR),
            new SqlOutParameter("p_id_ocorrencia", Types.NUMERIC));
  }

  /**
   * Paginates with the ROWNUM idiom, not {@code OFFSET ... FETCH NEXT}, which requires
   * Oracle 12c+. The project already rejected FETCH FIRST for that reason, and using it
   * here would make the portability promise in the database README false.
   */
  public List<IncidentWithAuthor> list(Pagination page) {
    return jdbc.sql("""
            SELECT * FROM (
              SELECT q.*, ROWNUM rn FROM (
                SELECT %s FROM ocorrencia ORDER BY criada_em DESC, id DESC
              ) q WHERE ROWNUM <= :lastRow
            ) WHERE rn > :offset
            """.formatted(COLUMNS))
        .param("lastRow", page.lastRow())
        .param("offset", page.offset())
        .query(IncidentRepository::map)
        .list();
  }

  public Optional<IncidentWithAuthor> find(long id) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM ocorrencia WHERE id = :id")
        .param("id", id)
        .query(IncidentRepository::map)
        .optional();
  }

  /** Locks the row before the service decides, so two readers cannot both win. */
  public Optional<IncidentWithAuthor> findForUpdate(long id) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM ocorrencia WHERE id = :id FOR UPDATE")
        .param("id", id)
        .query(IncidentRepository::map)
        .optional();
  }

  /** The procedure derives the neighbourhood and the initial priority, and returns the id. */
  public long register(long userId, NewIncidentRequest request) {
    Map<String, Object> out = registerCall.execute(Map.of(
        "p_id_usuario", userId,
        "p_tipo", request.type(),
        "p_descricao", request.description(),
        "p_latitude", request.latitude(),
        "p_longitude", request.longitude(),
        "p_foto_uri", request.photoUri() == null ? "" : request.photoUri()));
    return ((Number) out.get("p_id_ocorrencia")).longValue();
  }

  /** Relative increment: an absolute SET would lose a concurrent confirmation. */
  public int incrementReports(long id) {
    return jdbc.sql("UPDATE ocorrencia SET relatos = relatos + 1 WHERE id = :id")
        .param("id", id)
        .update();
  }

  public int changeStatus(long id, String status) {
    return jdbc.sql("UPDATE ocorrencia SET status = :status WHERE id = :id")
        .param("status", status)
        .param("id", id)
        .update();
  }

  public int delete(long id) {
    return jdbc.sql("DELETE FROM ocorrencia WHERE id = :id").param("id", id).update();
  }

  /** Returns false when this user had already confirmed. */
  public boolean registerConfirmation(long incidentId, long userId) {
    try {
      jdbc.sql("""
              INSERT INTO confirmacao_ocorrencia (id_ocorrencia, id_usuario)
                   VALUES (:incident, :user)
              """)
          .param("incident", incidentId)
          .param("user", userId)
          .update();
      return true;
    } catch (org.springframework.dao.DuplicateKeyException alreadyConfirmed) {
      return false;
    }
  }

  static IncidentWithAuthor map(ResultSet rs, int row) throws SQLException {
    return new IncidentWithAuthor(
        rs.getLong("id"),
        rs.getLong("id_usuario"),
        rs.getString("tipo"),
        rs.getString("descricao"),
        rs.getDouble("latitude"),
        rs.getDouble("longitude"),
        rs.getString("status"),
        rs.getString("prioridade"),
        rs.getInt("relatos"),
        rs.getString("bairro"),
        rs.getString("foto_uri"),
        withOffset(rs, "criada_em"));
  }

  /**
   * The column is TIMESTAMP, without a zone. The offset is attached from the JVM zone,
   * which the container pins to America/Sao_Paulo, the same as the database.
   */
  static OffsetDateTime withOffset(ResultSet rs, String column) throws SQLException {
    var timestamp = rs.getTimestamp(column);
    return timestamp == null
        ? null
        : timestamp.toLocalDateTime().atZone(ZoneId.systemDefault()).toOffsetDateTime();
  }
}
