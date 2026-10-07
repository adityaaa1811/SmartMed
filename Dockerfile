FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY backend/mvnw backend/mvnw
COPY backend/.mvn backend/.mvn
COPY backend/pom.xml backend/pom.xml

RUN chmod +x backend/mvnw
RUN cd backend && ./mvnw -B dependency:go-offline

COPY backend/src backend/src

RUN cd backend && ./mvnw -B package -DskipTests


FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/backend/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]
