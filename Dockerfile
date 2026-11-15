
FROM eclipse-temurin:21-jdk-alpine AS deps

WORKDIR /app

COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw ./

RUN chmod +x mvnw && \
    ./mvnw dependency:go-offline -B --no-transfer-progress -q

FROM eclipse-temurin:21-jdk-alpine AS dev

WORKDIR /app

COPY --from=deps /root/.m2 /root/.m2
COPY --from=deps /app ./

EXPOSE 8080

ENV SPRING_DEVTOOLS_RESTART_ENABLED=true \
    SPRING_DEVTOOLS_LIVERELOAD_ENABLED=false

CMD ["./mvnw", "spring-boot:run", "-o", "--no-transfer-progress", \
     "-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.poll-interval=1000ms -Dspring.devtools.restart.quiet-period=400ms"]

FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY --from=deps /root/.m2 /root/.m2
COPY --from=deps /app ./
COPY src ./src

RUN ./mvnw package -B --no-transfer-progress -q \
    -DskipTests \
    -Dspring-boot.repackage.layered=true

RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

FROM eclipse-temurin:21-jre-alpine AS prod

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /app/target/extracted/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /app/target/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /app/target/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /app/target/extracted/application/ ./

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
