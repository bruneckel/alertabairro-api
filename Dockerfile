# ---------------------------------------------------------------------------
# Build. O contexto e a RAIZ do repositorio, nao uma subpasta: a imagem
# precisa copiar database/migrations para o Flyway encontrar as migracoes.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# pom primeiro: enquanto as dependencias nao mudam, esta camada e reaproveitada
# e o build nao baixa a internet de novo a cada alteracao de codigo.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------------------------------------------------------------------------
# Runtime: so JRE. Imagem menor e menos superficie do que carregar o Maven.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime

# O app joga criadaEm direto em new Date(). O fuso do container precisa bater
# com o do banco, senao o "ha X min" das telas mente.
ENV TZ=America/Sao_Paulo

# MaxRAMPercentage em vez de -Xmx fixo: o heap respeita o limite de memoria do
# container em vez de ignora-lo.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

WORKDIR /app
COPY --from=build /build/target/alertabairro-api-*.jar app.jar
COPY database/migrations /app/migrations
ENV CAMINHO_MIGRACAO=/app/migrations

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
