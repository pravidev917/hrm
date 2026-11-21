# syntax=docker/dockerfile:1.7
#
# HRM backend image.
#
# Two stages: Maven builds the fat jar, then a slim JRE image runs it as a
# non-root user. Nothing from the build stage (Maven, the local repository, the
# source tree) reaches the runtime image.
#
#   docker build -t hrm-backend:local ./backend
#   docker run --rm -p 8080:8080 \
#     -e DB_URL=jdbc:postgresql://host:5432/hrm \
#     -e DB_USERNAME=hrm -e DB_PASSWORD=... -e JWT_SECRET=... hrm-backend:local
#
# The application refuses to start without DB_URL, DB_USERNAME, DB_PASSWORD and
# a JWT_SECRET of at least 32 bytes; that is deliberate and not papered over here.

########## build ##########
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependency resolution is its own layer so source edits do not re-download the
# world. `dependency:go-offline` is best-effort; the package step is the gate.
COPY pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline || true

COPY src ./src
RUN mvn -B -DskipTests package \
 && find target -maxdepth 1 -name '*.jar' ! -name '*.original' -exec cp {} /build/app.jar \;

########## runtime ##########
FROM eclipse-temurin:21-jre-alpine AS runtime

# curl is for the HEALTHCHECK; tzdata keeps the pinned application timezone real.
RUN apk add --no-cache curl tzdata \
 && addgroup -g 10001 -S hrm \
 && adduser -u 10001 -S -G hrm -h /app hrm

WORKDIR /app
COPY --from=build --chown=10001:10001 /build/app.jar /app/app.jar

# Container-aware sizing: MaxRAMPercentage follows the cgroup limit, so the same
# image works on a laptop and on a 2 GB task without extra flags.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=25 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -Djava.security.egd=file:/dev/urandom" \
    SERVER_PORT=8080 \
    HOME=/tmp

USER 10001:10001
EXPOSE 8080

# /api/health answers 200 when the database is reachable and 503 when it is not,
# so "healthy" means "can actually serve requests".
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/api/health" >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
