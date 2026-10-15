ARG JAVA_BASE_IMAGE=mirror.gcr.io/library/eclipse-temurin:21-jre-jammy
FROM ${JAVA_BASE_IMAGE}

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/lynx-ad-app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
