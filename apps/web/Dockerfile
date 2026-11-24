# syntax=docker/dockerfile:1.7
# SkillSieve web: static Vite build served by unprivileged nginx.
# Build: docker buildx build --platform linux/arm64,linux/amd64 -t ghcr.io/<owner>/skillsieve-web:sha-xxxx apps/web

# ---- build stage (runs natively on the builder; output is platform-independent) ----
FROM --platform=$BUILDPLATFORM node:22-alpine@sha256:1314fb30ec37592d5845e5a160d522c0e680972aa534549940b8d73bde88481a AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# true = ship with MSW fake data (no backend). Set false once core-api is deployed.
ARG VITE_USE_MOCKS=false
ENV VITE_USE_MOCKS=$VITE_USE_MOCKS
RUN npm run build

# ---- runtime stage ----
# nginx mainline on Alpine, pinned by digest (Dependabot bumps it). Mainline gets security fixes
# first; apk upgrade picks up Alpine patches released after the image was built.
FROM nginxinc/nginx-unprivileged:mainline-alpine@sha256:b5047b9b76845eac124358373c6852377c52e4b05834a47647e955dd91cb8f1e
USER root
RUN apk upgrade --no-cache
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
USER 10001:10001
EXPOSE 8080
