# Build monorepo : contexte = racine du repo (parent pom + libs accessibles).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl apps/api-gateway -am -DskipTests package
# Run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/apps/api-gateway/target/*.jar app.jar
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational"
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
