FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /workspace

COPY MovieService/ MovieService/

RUN mvn -f MovieService/pom.xml package -DskipTests -B

FROM eclipse-temurin:17
VOLUME /tmp
RUN mkdir -p /app/logs/
COPY --from=builder /workspace/MovieService/target/movie-service-1.0.jar /app/app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
