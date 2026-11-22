# syntax=docker/dockerfile:1.7
# SkillSieve web: static Vite build served by unprivileged nginx.
# Build: docker buildx build --platform linux/arm64,linux/amd64 -t ghcr.io/<owner>/skillsieve-web:sha-xxxx apps/web

# ---- build stage (runs natively on the builder; output is platform-independent) ----
FROM --platform=$BUILDPLATFORM node:26.13-alpine@sha256:6bd9ab43c0ec0190ca7b2d193906cb8b7edf6e7f4afa43ddeccd6452710bffb5 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# true = ship with MSW fake data (no backend). Set false once core-api is deployed.
ARG VITE_USE_MOCKS=false
ENV VITE_USE_MOCKS=$VITE_USE_MOCKS
RUN npm run build

# ---- runtime stage ----
FROM nginxinc/nginx-unprivileged:1.27-alpine@sha256:65e3e85dbaed8ba248841d9d58a899b6197106c23cb0ff1a132b7bfe0547e4c0
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
USER 10001
EXPOSE 8080
