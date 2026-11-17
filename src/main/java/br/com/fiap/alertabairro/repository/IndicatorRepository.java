package br.com.fiap.alertabairro.repository;

import br.com.fiap.alertabairro.web.dto.IndicatorsResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IndicatorRepository {

  private final JdbcClient jdbc;

  public IndicatorRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * One query against the view. Aggregating in the database brings back a single row,
   * instead of fetching rows here only to count them.
   */
  public IndicatorsResponse read() {
    return jdbc.sql("""
            SELECT ocorrencias_abertas, ocorrencias_em_atendimento, ocorrencias_resolvidas,
                   ocorrencias_criticas, capacidade_abrigos, vagas_ocupadas,
                   ocupacao_abrigos_pct, pedidos_aguardando, pedidos_atendidos,
                   pedidos_resolvidos, checkins_estou_bem_24h, checkins_preciso_ajuda_24h
              FROM vw_indicadores
            """)
        .query((rs, row) -> new IndicatorsResponse(
            rs.getInt("ocorrencias_abertas"),
            rs.getInt("ocorrencias_em_atendimento"),
            rs.getInt("ocorrencias_resolvidas"),
            rs.getInt("ocorrencias_criticas"),
            rs.getInt("capacidade_abrigos"),
            rs.getInt("vagas_ocupadas"),
            rs.getDouble("ocupacao_abrigos_pct"),
            rs.getInt("pedidos_aguardando"),
            rs.getInt("pedidos_atendidos"),
            rs.getInt("pedidos_resolvidos"),
            rs.getInt("checkins_estou_bem_24h"),
            rs.getInt("checkins_preciso_ajuda_24h")))
        .single();
  }
}
