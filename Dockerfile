FROM vinhdat4999/java-21:jdk-21
USER root

RUN apt update && apt install curl jq -y

WORKDIR /app

RUN mkdir temp

ARG PACKAGE
ARG OTEL_AGENT_VERSION=1.28.0
ARG BLAS_OTEL_EXTENSION_VERSION=1.0.0

COPY download-script.sh ./download-script.sh
RUN chmod u+x ./download-script.sh && ./download-script.sh

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} ./app.jar

COPY BlasSecretKey.p12 ./BlasSecretKey.p12
COPY entrypoint.sh ./entrypoint.sh

RUN chmod u+x ./entrypoint.sh

EXPOSE 8082

ENTRYPOINT ["./entrypoint.sh"]
