FROM maven:3.9.6-eclipse-temurin-17
WORKDIR /app
COPY app/ .
RUN mvn clean package -DskipTests \
    && cp target/*.jar app.jar

EXPOSE 8787
ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8787", "-jar", "app.jar"]
