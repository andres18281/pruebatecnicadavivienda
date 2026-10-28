FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY libs ./libs
COPY services ./services
RUN --mount=type=cache,id=techdocs-m2,sharing=locked,target=/root/.m2 \
    mvn -B -pl services/document-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget \
    && addgroup -g 10001 -S app \
    && adduser -u 10001 -S -G app app
WORKDIR /app
COPY --from=build /src/services/document-service/target/app.jar /app/app.jar
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.11.0/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
RUN chmod 644 /otel/opentelemetry-javaagent.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-javaagent:/otel/opentelemetry-javaagent.jar -Djava.io.tmpdir=/tmp -Duser.home=/tmp"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
