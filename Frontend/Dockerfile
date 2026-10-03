FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /workspace

COPY Frontend/ Frontend/

RUN mvn -f Frontend/pom.xml package -DskipTests -B

FROM eclipse-temurin:17
VOLUME /tmp
RUN mkdir -p /app/logs/
COPY --from=builder /workspace/Frontend/target/frontend-1.0.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
