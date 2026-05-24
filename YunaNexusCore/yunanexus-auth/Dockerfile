FROM amazoncorretto:22-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8200
ENV JAVA_OPTS="-Xms256m -Xmx512m"
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD curl -f http://localhost:8200/ || exit 1
ENTRYPOINT sh -c "java ${JAVA_OPTS} -jar app.jar"
