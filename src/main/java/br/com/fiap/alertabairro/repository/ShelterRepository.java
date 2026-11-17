package br.com.fiap.alertabairro.repository;

import br.com.fiap.alertabairro.web.dto.ShelterResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

@Repository
public class ShelterRepository {

  private static final String COLUMNS =
      "id, nome, endereco, latitude, longitude, capacidade, vagas_ocupadas";

  private final JdbcClient jdbc;
  private final SimpleJdbcCall controlSpotsCall;

  public ShelterRepository(JdbcClient jdbc, JdbcTemplate template) {
    this.jdbc = jdbc;
    this.controlSpotsCall = new SimpleJdbcCall(template)
        .withProcedureName("prc_controle_vagas")
        .withoutProcedureColumnMetaDataAccess()
        .declareParameters(
            new SqlParameter("p_id_abrigo", Types.NUMERIC),
            new SqlParameter("p_quantidade", Types.NUMERIC));
  }

  public List<ShelterResponse> list() {
    return jdbc.sql("SELECT " + COLUMNS + " FROM abrigo ORDER BY nome")
        .query(ShelterRepository::map)
        .list();
  }

  public Optional<ShelterResponse> find(long id) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM abrigo WHERE id = :id")
        .param("id", id)
        .query(ShelterRepository::map)
        .optional();
  }

  /**
   * The function decides which shelter is nearest, so only one row crosses the wire —
   * instead of shipping every shelter here to compare. Empty when none has a vacancy.
   */
  public Optional<ShelterResponse> findNearest(double latitude, double longitude) {
    return jdbc.sql("""
            SELECT %s FROM abrigo
             WHERE id = (SELECT fn_abrigo_mais_proximo(:lat, :lng) FROM dual)
            """.formatted(COLUMNS))
        .param("lat", latitude)
        .param("lng", longitude)
        .query(ShelterRepository::map)
        .optional();
  }

  /** The procedure holds the row lock and raises -20002/-20003/-20004. */
  public void controlSpots(long shelterId, int amount) {
    controlSpotsCall.execute(Map.of("p_id_abrigo", shelterId, "p_quantidade", amount));
  }

  static ShelterResponse map(ResultSet rs, int row) throws SQLException {
    return new ShelterResponse(
        rs.getLong("id"),
        rs.getString("nome"),
        rs.getString("endereco"),
        rs.getDouble("latitude"),
        rs.getDouble("longitude"),
        rs.getInt("capacidade"),
        rs.getInt("vagas_ocupadas"));
  }
}
