FROM amazoncorretto:22-alpine
RUN apk add --no-cache curl
WORKDIR /app
RUN mkdir -p /data/storage/yunanexus-file /data/storage/yunanexus-file-temp
COPY target/yunanexus-file-1.0.0.jar app.jar
EXPOSE 8300
ENV JAVA_OPTS="-Xms256m -Xmx512m"
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD curl -f http://localhost:8300/ || exit 1
ENTRYPOINT sh -c "java ${JAVA_OPTS} -jar app.jar"
