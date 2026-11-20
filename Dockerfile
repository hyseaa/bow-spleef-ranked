FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q
COPY src/ src/
RUN ./mvnw package -DskipTests -Dcheckstyle.skip=true -q

FROM eclipse-temurin:21-jre-alpine
# The commit the image is built from, given by the CI (spec 039); shown by GET /actuator/info
ARG APP_COMMIT=unknown
ENV APP_COMMIT=${APP_COMMIT}
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
