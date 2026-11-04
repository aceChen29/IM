FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd -g 1001 appgroup && useradd -r -u 1001 -g appgroup appuser
COPY --chown=appuser:appgroup target/AiService-0.0.1-SNAPSHOT.jar app.jar
USER appuser
EXPOSE 8105
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
