# syntax=docker/dockerfile:1
#
# YunaNexus 通用服务镜像（多阶段构建）。
#
# 用法（构建上下文必须是仓库根目录）：
#   docker build -f deploy/docker/Dockerfile --build-arg SERVICE=yunanexus-meta -t yunanexus/yunanexus-meta:latest .
# 或经 deploy/docker-compose.yml 编排（推荐）。
#
# 说明：
#   - 构建阶段用 Maven + JDK 22 打包（spring-boot-maven-plugin repackage 产出可执行 jar）；
#   - 运行阶段仅保留 JRE 22 与可执行 jar，减小镜像体积；
#   - SERVICE 为模块目录名（yunanexus-meta / yunanexus-auth / yunanexus-gateway / yunanexus-user /
#     yunanexus-permission / yunanexus-file / yunanexus-notice / yunanexus-audit）。

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-22 AS build
ARG SERVICE
WORKDIR /build

# 复制整个仓库（.dockerignore 已裁剪 target/.git/.rocketmq/data 等）
COPY . .

# -pl <module> -am：仅构建目标服务及其依赖模块（common-*）；跳过测试以加速镜像构建
RUN mvn -B -q -Pprd -pl "${SERVICE}" -am -DskipTests package \
 && cp "$(ls /build/${SERVICE}/target/${SERVICE}-*.jar | grep -v -- '-sources' | head -n 1)" /build/app.jar

# ---------- 运行阶段 ----------
FROM eclipse-temurin:22-jre
ARG SERVICE
LABEL org.opencontainers.image.title="${SERVICE}" \
      org.opencontainers.image.source="https://github.com/Nanoic39/YunaNexus"

ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=prd \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Shanghai"

WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar

# 端口由各服务 server.port / management.server.port 决定（见 docs/deployment.md 端口规划），
# 容器网络内可直接互访，宿主机映射见 deploy/docker-compose.yml。
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
