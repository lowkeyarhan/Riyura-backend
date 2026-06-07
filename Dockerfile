# ═══════════════════════════════════════════════════════════════════════════════
# Riyura Backend — Multi-stage Dockerfile
#
# Stages:
#   deps    → downloads all Maven dependencies into cache (shared by dev & builder)
#   dev     → development image: JDK + Maven + DevTools hot-reload (source bind-mounted)
#   builder → compiles and packages the fat/layered JAR (CI / prod builds)
#   prod    → final runtime image: JRE-only, non-root, layered JAR
#
# Usage:
#   Dev  : docker compose up --build                          (uses 'dev' target)
#   Prod : docker compose -f docker-compose.yml \
#                         -f docker-compose.prod.yml up -d    (uses 'prod' target)
# ═══════════════════════════════════════════════════════════════════════════════

# ── Stage 1: dependency cache ─────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS deps

WORKDIR /app

# Copy only the files Maven needs to resolve dependencies.
# This layer is invalidated only when pom.xml changes, keeping rebuilds fast.
COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw ./

RUN chmod +x mvnw && \
    ./mvnw dependency:go-offline -B --no-transfer-progress -q

# ── Stage 2: dev (hot-reload) ─────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS dev

WORKDIR /app

# Bring pre-resolved deps from the cache stage so dev startup is fast
COPY --from=deps /root/.m2 /root/.m2
COPY --from=deps /app ./

# Source code is NOT copied here — it is bind-mounted at runtime by docker-compose
# so that any local file change is immediately visible inside the container.

EXPOSE 8080

# Spring Boot DevTools detects class changes and triggers a context restart.
# Maven -o (offline) mode avoids network calls after deps are cached.
# SPRING_DEVTOOLS_RESTART_ENABLED is true by default; set to false in prod.
ENV SPRING_DEVTOOLS_RESTART_ENABLED=true \
    SPRING_DEVTOOLS_LIVERELOAD_ENABLED=false

CMD ["./mvnw", "spring-boot:run", "-o", "--no-transfer-progress", \
     "-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.poll-interval=1000ms -Dspring.devtools.restart.quiet-period=400ms"]

# ── Stage 3: builder (compile + package) ──────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY --from=deps /root/.m2 /root/.m2
COPY --from=deps /app ./
COPY src ./src

# Package the JAR, skip tests (tests run in CI separately), generate layer index
RUN ./mvnw package -B --no-transfer-progress -q \
    -DskipTests \
    -Dspring-boot.repackage.layered=true

# Extract layered JAR for optimised Docker layer caching in prod
RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ── Stage 4: prod (final, minimal runtime) ────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS prod

# Security: run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy layered JAR layers in dependency-frequency order:
# dependencies change least often → application code changes most often.
# Docker caches each COPY as a separate layer — only changed layers are pushed.
COPY --from=builder --chown=appuser:appgroup /app/target/extracted/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /app/target/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /app/target/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /app/target/extracted/application/ ./

USER appuser

EXPOSE 8080

# JVM tuned for containerised environments:
#   UseContainerSupport   → respects cgroup memory/CPU limits instead of host values
#   MaxRAMPercentage=75   → caps heap at 75 % of container memory limit
#   UseG1GC               → throughput + pause-time balance for web services
#   -Djava.security.egd   → faster entropy source, speeds up startup
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
