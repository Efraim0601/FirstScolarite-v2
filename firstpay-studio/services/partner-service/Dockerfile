FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl services/partner-service -am -Dmaven.test.skip=true package
# Run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/partner-service/target/*.jar app.jar
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational"
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
