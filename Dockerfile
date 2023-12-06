FROM eclipse-temurin:17-jdk
WORKDIR /app
RUN mkdir temp
COPY target/*.jar app.jar
COPY BlasSecretKey.p12 BlasSecretKey.p12
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
