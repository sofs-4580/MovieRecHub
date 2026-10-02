FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app app
WORKDIR /app
COPY --from=build --chown=app:app /build/target/profile-service-*.jar /app/app.jar
USER app
ENV SERVER_PORT=8082
EXPOSE 8082
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
    CMD curl --fail --silent "http://127.0.0.1:${SERVER_PORT}/actuator/health" > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
