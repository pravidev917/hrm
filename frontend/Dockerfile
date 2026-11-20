# syntax=docker/dockerfile:1
#
# HRM frontend image: the Vite build of the SPA, served by nginx, which also
# proxies /api to the backend container. One origin for the app and the API, so
# the HttpOnly HRM_AUTH cookie stays a plain first-party cookie.
#
# Build from the repository root:
#   docker build --build-arg VERSION=1.0.0 -t <dockerhub-user>/hrm-web:1.0.0 ./frontend

ARG VERSION=dev
ARG VCS_REF=unknown

# ---------------------------------------------------------------- build stage
FROM node:22-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Same origin (empty) by default: the SPA calls relative /api/... paths.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build

# -------------------------------------------------------------- runtime stage
FROM nginx:1.29-alpine

ARG VERSION
ARG VCS_REF

LABEL org.opencontainers.image.title="HRM web"
LABEL org.opencontainers.image.description="HRM React SPA served by nginx with same-origin /api proxy"
LABEL org.opencontainers.image.version="${VERSION}"
LABEL org.opencontainers.image.revision="${VCS_REF}"
LABEL org.opencontainers.image.licenses="Proprietary"

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
