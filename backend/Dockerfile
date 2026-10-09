# syntax=docker/dockerfile:1
FROM maven:3.9.16-eclipse-temurin-26 AS build
WORKDIR /app
COPY pom.xml .
# A cache mount keeps the downloaded dependencies between builds instead of fetching them all again.
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -DskipTests \
    && java -Djarmode=tools -jar target/genealogy-*.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:26-jre
# Not root: a hole in the app should not hand over the container as well (§8.11 #18).
RUN useradd --system --uid 10001 --no-create-home genealogy
WORKDIR /app
# Dependencies first and the app's own classes last, so a code change rebuilds only the last layer.
COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./
USER genealogy
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
