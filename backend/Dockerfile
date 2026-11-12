FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/pom.xml .
COPY backend/src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package
FROM eclipse-temurin:17-jre-jammy
RUN groupadd -g 10001 app && useradd -u 10001 -g app -m app && mkdir -p /data && chown app:app /data
WORKDIR /app
COPY --from=build /build/target/rental-backend.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=65","-XX:+ExitOnOutOfMemoryError","-Dfile.encoding=UTF-8","-Duser.timezone=Asia/Ho_Chi_Minh","-jar","/app/app.jar"]
