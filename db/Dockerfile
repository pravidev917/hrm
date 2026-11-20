# Schema-only PostgreSQL image for a fresh HRM installation.
# It contains the empty database schema (V1-V5) and no application data.
FROM postgres:16-alpine

ARG VERSION=dev
ARG VCS_REF=unknown

LABEL org.opencontainers.image.title="HRM database schema" \
      org.opencontainers.image.description="PostgreSQL 16 with the empty HRM schema (V1-V5), no records" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.licenses="Proprietary"

# PostgreSQL runs these only when the data directory is empty (first boot).
# No dumps, credentials, .env files or company records are copied.
COPY V*.sql /docker-entrypoint-initdb.d/
