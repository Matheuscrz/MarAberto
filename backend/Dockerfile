FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./
RUN chmod +x gradlew

COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --create-home --uid 10001 maraberto
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN chown maraberto:maraberto app.jar

USER maraberto
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
