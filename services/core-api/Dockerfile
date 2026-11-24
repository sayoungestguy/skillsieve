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
FROM gcr.io/distroless/java21-debian12:nonroot@sha256:7e37784d94dccbf5ccb195c73b295f5ad00cd266512dfbac12eb9c3c28f8077d
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
