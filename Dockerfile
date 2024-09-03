FROM vinhdat4999/java-21:jdk-21
USER root

RUN apt update && apt install curl -y

WORKDIR /app

RUN mkdir temp

RUN curl -sSLo opentelemetry-javaagent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v1.28.0/opentelemetry-javaagent.jar

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} ./app.jar

COPY BlasSecretKey.p12 ./BlasSecretKey.p12
COPY entrypoint.sh ./entrypoint.sh

RUN chmod u+x ./entrypoint.sh

EXPOSE 8082

ENTRYPOINT ["./entrypoint.sh"]
