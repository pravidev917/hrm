# syntax=docker/dockerfile:1.7
#
# HRM frontend image: the built single-page app served by nginx, which also
# proxies /api to the backend so the browser only ever talks to one origin.
#
#   docker build -t hrm-web:local ./frontend
#   docker run --rm -p 8080:80 -e BACKEND_UPSTREAM=backend:8080 hrm-web:local
#
# VITE_API_BASE_URL is deliberately empty: the SPA calls "/api/..." on its own
# origin and nginx forwards it. Pointing it at a hostname builds CORS problems
# into the bundle, so the default is same-origin.

########## build ##########
FROM node:22-alpine AS build
WORKDIR /app

# npm ci needs the lockfile; copying only the manifests keeps this layer cached.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .

ARG VITE_API_BASE_URL=
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

########## runtime ##########
FROM nginx:1.27-alpine AS runtime

# The official image runs envsubst over /etc/nginx/templates/*.template at
# startup. Limiting the filter to BACKEND_UPSTREAM keeps nginx's own $host,
# $uri and $request_uri variables intact.
ENV BACKEND_UPSTREAM=backend:8080 \
    NGINX_ENVSUBST_FILTER=BACKEND_UPSTREAM

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
