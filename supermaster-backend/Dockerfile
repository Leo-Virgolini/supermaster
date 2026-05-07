# --- Stage 1: Build ---
FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

# Copiar archivos de Maven
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Dar permisos al wrapper
RUN chmod +x mvnw

# Descargar dependencias (cacheado si pom.xml no cambia)
RUN ./mvnw dependency:go-offline -B

# Copiar el codigo fuente
COPY src src

# Compilar sin tests
RUN ./mvnw package -Dmaven.test.skip=true -B

# --- Stage 2: Runtime ---
FROM eclipse-temurin:25-jre

WORKDIR /app

# Instalar curl (necesario para el HEALTHCHECK via Actuator)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Copiar el JAR compilado
COPY --from=build /app/target/*.jar app.jar

# Puerto de la aplicacion y puerto de management (Actuator)
EXPOSE 8080 8081

# Health check via Actuator (puerto interno 8081)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8081/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-Xms512m", "-Xmx1024m", "-jar", "app.jar"]

