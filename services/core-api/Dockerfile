# syntax=docker/dockerfile:1.7
# SkillSieve core-api: Spring Boot fat jar, unpacked into layers, on distroless Java 21.
# Build: docker buildx build --platform linux/arm64,linux/amd64 -t ghcr.io/<owner>/skillsieve-core-api:sha-xxxx services/core-api

# ---- build stage ----
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-noble@sha256:a09e64f4976df9c4f2029c6abe64fc26f069a342ec9a7f147dce7b1933ebda16 AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml spotbugs-exclude.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
# Tests, format and static analysis run in CI (ci.yml); the image build only packages.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package \
      -DskipTests -Dspotless.check.skip=true -Dspotbugs.skip=true -Djacoco.skip=true \
 && java -Djarmode=tools -jar target/core-api-*.jar extract --layers --launcher --destination /layers

# ---- runtime stage ----
# Debian 13: the debian12 image still ships unpatched brotli/expat/lcms2 (Trivy, 2026-11-24).
FROM gcr.io/distroless/java21-debian13:nonroot@sha256:128bd878463537eef7771b50fecf68faace9089f654a2d233f1c4f344e6817d9
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER 10001:10001
EXPOSE 8080
# Small heap on a shared node (CODING_STANDARDS §7). /tmp is an emptyDir in the cluster.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp -Duser.home=/tmp"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
