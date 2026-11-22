# syntax=docker/dockerfile:1.7
# SkillSieve web: static Vite build served by unprivileged nginx.
# Build: docker buildx build --platform linux/arm64,linux/amd64 -t ghcr.io/<owner>/skillsieve-web:sha-xxxx apps/web

# ---- build stage (runs natively on the builder; output is platform-independent) ----
FROM --platform=$BUILDPLATFORM node:22.12-alpine@sha256:51eff88af6dff26f59316b6e356188ffa2c422bd3c3b76f2556a2e7e89d080bd AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# true = ship with MSW fake data (no backend). Set false once core-api is deployed.
ARG VITE_USE_MOCKS=false
ENV VITE_USE_MOCKS=$VITE_USE_MOCKS
RUN npm run build

# ---- runtime stage ----
FROM nginxinc/nginx-unprivileged:1.31-alpine@sha256:b5047b9b76845eac124358373c6852377c52e4b05834a47647e955dd91cb8f1e
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
USER 10001
EXPOSE 8080
