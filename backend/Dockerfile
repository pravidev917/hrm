# syntax=docker/dockerfile:1
#
# HRM backend image: Spring Boot 4.1.2 / Java 21 fat jar on a JRE-only runtime.
# Built as a two-stage build so the runtime layer carries no Maven, no sources
# and no test dependencies.
#
# Build from the repository root:
#   docker build --build-arg VERSION=1.0.0 -t <dockerhub-user>/hrm-backend:1.0.0 ./backend

ARG VERSION=dev
ARG VCS_REF=unknown

# ---------------------------------------------------------------- build stage
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Resolve dependencies from pom.xml alone: a source-only change then reuses this
# layer instead of re-downloading the world.
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src

# Tests are skipped here on purpose: the Spring context test needs a live
# PostgreSQL. Inside the image build there is no database.
RUN mvn -B -DskipTests package \
 && cp target/backend-*.jar /build/backend.jar

# -------------------------------------------------------------- runtime stage
FROM eclipse-temurin:21-jre

ARG VERSION
ARG VCS_REF

LABEL org.opencontainers.image.title="HRM backend"
LABEL org.opencontainers.image.description="Spring Boot HRM API"
LABEL org.opencontainers.image.version="${VERSION}"
LABEL org.opencontainers.image.revision="${VCS_REF}"
LABEL org.opencontainers.image.licenses="Proprietary"

# curl is needed for the container healthcheck against /api/health.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system hrm \
 && useradd --system --gid hrm --home-dir /app --shell /usr/sbin/nologin hrm

WORKDIR /app

COPY --from=build --chown=hrm:hrm /build/backend.jar /app/backend.jar

USER hrm

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=12 \
    CMD curl -fsS http://127.0.0.1:8080/api/health || exit 1

# exec, so java is PID 1 and receives SIGTERM for the graceful shutdown the
# application.properties already enables (server.shutdown=graceful).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/backend.jar"]
