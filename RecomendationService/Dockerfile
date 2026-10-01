FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /workspace

COPY RecomendationService/ RecomendationService/
RUN mvn -f RecomendationService/pom.xml package -DskipTests -B

FROM eclipse-temurin:17
COPY --from=builder /workspace/RecomendationService/target/recommendation-service-1.0.jar /app/app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
