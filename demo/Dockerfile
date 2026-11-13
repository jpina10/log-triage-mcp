# One image per service: docker build --build-arg SERVICE=orders-api -t log-triage/orders-api:dev .
FROM eclipse-temurin:25-jre
ARG SERVICE
WORKDIR /app
COPY ${SERVICE}/target/${SERVICE}.jar app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
