FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
# Integration tests require PostgreSQL/Redis and run in verify/CI, not image build.
RUN sh ./mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S releasepilot && adduser -S -G releasepilot releasepilot
WORKDIR /app
COPY --from=build /build/target/releasepilot-backend-0.0.1-SNAPSHOT.jar app.jar
USER releasepilot
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -q -O - http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
