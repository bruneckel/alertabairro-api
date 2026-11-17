package br.com.fiap.alertabairro;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fiap.alertabairro.service.Distance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unitario: nao sobe contexto nem toca no banco, entao roda em "mvnw test" sem Docker.
 */
@DisplayName("Distance: Haversine e formatacao")
class DistanceTest {

  @Test
  @DisplayName("abaixo de 1 km sai em metros arredondados")
  void metros() {
    assertThat(Distance.format(0.3)).isEqualTo("300 m");
    assertThat(Distance.format(0.0)).isEqualTo("0 m");
    assertThat(Distance.format(0.9994)).isEqualTo("999 m");
  }

  @Test
  @DisplayName("a partir de 1 km sai em km com virgula decimal, nao ponto")
  void quilometros() {
    assertThat(Distance.format(1.23))
        .as("o app e em portugues: separador decimal e virgula")
        .isEqualTo("1,2 km");
    assertThat(Distance.format(12.0)).isEqualTo("12,0 km");
  }

  @Test
  @DisplayName("um grau de latitude mede cerca de 111,19 km")
  void umGrauDeLatitude() {
    double km = Distance.km(-23.0, -46.0, -24.0, -46.0);
    assertThat(km).isCloseTo(111.19, org.assertj.core.data.Offset.offset(0.05));
  }

  @Test
  @DisplayName("na latitude de Sao Paulo, um grau de longitude encurta por cos(lat)")
  void grauDeLongitudeEncurta() {
    double norte = Distance.km(-23.55, -46.63, -23.55 - 1, -46.63);
    double leste = Distance.km(-23.55, -46.63, -23.55, -46.63 + 1);
    assertThat(leste)
        .as("sem o peso cos(latitude), leste e norte dariam a mesma distancia")
        .isLessThan(norte);
    assertThat(leste / norte).isCloseTo(Math.cos(Math.toRadians(23.55)),
        org.assertj.core.data.Offset.offset(0.01));
  }

  @Test
  @DisplayName("mesmo ponto da zero")
  void mesmoPonto() {
    assertThat(Distance.km(-23.55, -46.63, -23.55, -46.63)).isZero();
  }
}
