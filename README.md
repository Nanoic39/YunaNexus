# ✨YunaNexus基本信息

![YunaNexus](https://socialify.git.ci/Nanoic39/YunaNexus/image?font=JetBrains+Mono&forks=1&issues=1&logo=https%3A%2F%2Fgithub.com%2FNanoic39%2FYunaNexus%2Fraw%2Fmain%2FAssets%2FImage%2FYunaQ_AVATAR.png&name=1&owner=1&pattern=Circuit+Board&stargazers=1&theme=Light)

> 这篇Markdown文件是我自己手搓的，无任何AI生成内容 `: )`

## 📕 当前仓库项目结构

```text
YunaNexus/
├── YunaNexusCore/                 # 后端服务
│   ├── common-redis/              # 【公共】: Redis 自动配置
│   ├── common-mail/               # 【公共】: 邮件发送 SDK
│   ├── common-web/                # 【公共】: Result / 异常 / JWT / 权限校验
│   ├── yunanexus-gateway/         # 【网关】: SCG-MVC + 分片路由
│   ├── yunanexus-auth/            # 【认证】: 登录 / 注册 / OAuth 授权
│   ├── yunanexus-user/            # 【用户】: 账户信息
│   └── yunanexus-file/            # 【文件】: 上传 / 预览 / 分享
├── YunaNexusWeb/                  # 前端
│   ├── apps/yunanexus-main/       # 主站 ( 🚧 开发中 🚧 )
│   ├── apps/yunanexus-bot/        # bot管理面板 ( 🖊️ 规划中 🖊️ )
|   ├── apps/yunanexus-game/       # 游戏服务器管理面板 ( 🖊️ 规划中 🖊️ )
│   ├── packages/ui/               # 【公共】: UI 组件
│   └── packages/api/              # 【公共】: API 请求封装
└── sql-schema/                    # 数据库建表脚本 (MySQL)
```

## ⚙️ 安装/部署教程

> 首次部署时复制 `application-example.yaml` 并改名为 `application-local.yaml`，填入实际值，加上环境变量后使用 `--spring.profiles.active=local` 启动即可。

### Ubuntu/Debian

【1】JDK环境配置

```bash
# 安装JDK
wget https://corretto.aws/downloads/resources/22.0.2.9.1/amazon-corretto-22.0.2.9.1-linux-x64.tar.gz

# 配置JDK为默认Java环节
tar -xzf amazon-corretto-22.0.2.9.1-linux-x64.tar.gz

mv amazon-corretto-22.0.2.9.1-linux-x64 jdk22 # 改名为jdk22便于后续操作(非必须,但是后续所有"jdk22"需要自己处理文件夹名称)

export JAVA_HOME=/从根目录开始/你安装corretto的目录/绝对路径/jdk22
export PATH=$JAVA_HOME/bin:$PATH
export CLASSPATH=.:$JAVA_HOME/lib

# 如果需要永久生效
echo "export JAVA_HOME=/从根目录开始/你安装corretto的目录/绝对路径/jdk22" >> ~/.bashrc
echo "export PATH=\$JAVA_HOME/bin:\$PATH" >> ~/.bashrc
source ~/.bashrc

java -version # 校验版本是否正确
```

【2】Maven环境配置

```bash
# 安装Maven
wget https://mirrors.aliyun.com/apache/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz

# 配置Maven
tar -xzf apache-maven-3.9.16-bin.tar.gz

mv apache-maven-3.9.16 maven # 改名为maven便于后续操作(非必须,但是后续所有"maven"需要自己处理文件夹名称)

echo "export MAVEN_HOME=/从根目录开始/你安装maven的目录/绝对路径/maven" >> ~/.bashrc
echo "export PATH=\$MAVEN_HOME/bin:\$PATH" >> ~/.bashrc
source ~/.bashrc

mvn -version # 校验版本是否正确
```

【3】Nacos环境配置

```bash
# 下载 Nacos 2.3.2（当前最新稳定版）
wget https://github.com/alibaba/nacos/releases/download/2.3.2/nacos-server-2.3.2.tar.gz

# 解压
tar -xzf nacos-server-2.3.2.tar.gz
mv nacos-server-2.3.2 nacos232 # 改名为nacos232便于后续操作(非必须,但是后续所有"nacos232"需要自己处理文件夹名称)


# 编辑配置文件 /从根目录开始/你安装nacos232的目录/绝对路径/nacos232/conf/application.properties
server.port=8848 # 端口设置
nacos.core.auth.enabled=true # 开启鉴权
nacos.core.auth.server.identity.key=<身份验证密钥键> # 身份验证密钥（集群内部通信认证）
nacos.core.auth.server.identity.value=<密钥值> # 身份验证密钥（集群内部通信认证）
nacos.core.auth.default.token.secret=<身份验证令牌> # Token 配置（至少32字符）

# screen环境中启动
screen -R nacos232 # 起个便于辨识的名字
cd /从根目录开始/你安装nacos232的目录/绝对路径/nacos232/bin

# 单机模式启动（带鉴权）
sh startup.sh -m standalone
tail -f /从根目录开始/你安装nacos232的目录/绝对路径/nacos232/logs/start.out

# 先输入 Ctrl+A 然后 D 键可以退出screen
# 启动后进入 <nacos服务器地址>:8080/next 初始化（注意开放防火墙端口）
```

【4】拉取并配置后端项目

```bash
# 克隆本仓库
git clone --depth=1 https://github.com/Nanoic39/YunaNexus.git
cd YunaNexus/YunaNexusCore

# 生成部署配置
cp yunanexus-gateway/src/main/resources/application-example.yaml yunanexus-gateway/src/main/resources/application-local.yaml
cp yunanexus-auth/src/main/resources/application-example.yaml    yunanexus-auth/src/main/resources/application-local.yaml
cp yunanexus-user/src/main/resources/application-example.yaml    yunanexus-user/src/main/resources/application-local.yaml
cp yunanexus-file/src/main/resources/application-example.yaml    yunanexus-file/src/main/resources/application-local.yaml

# 完善部署配置实际值
# 复制出的 application-local.yaml 中以下信息需按实际环境修改：
#   spring.datasource.password   — 数据库密码（替换 ${MYSQL_PASSWORD:password} 或设环境变量）
#   spring.datasource.url        — 数据库地址（默认 127.0.0.1，非本机需修改）
#   spring.data.redis.host       — Redis 地址（默认 127.0.0.1）
#   spring.cloud.nacos.discovery.server-addr — Nacos 地址（默认 127.0.0.1:8848）
#   yunanexus.auth.builtin-clients[0].redirect-uri — 前端地址（设环境变量 YUNANEXUS_WEB_URL）
#   yunanexus.mail.*             — 邮件服务

# 生成 JWT 密钥并持久化
# 仅首次部署执行即可
# 重新生成后重启系统会导致所有用户 Token 失效需重新登录，因此后续只应当在密钥泄露后重新执行
SECRET=$(openssl rand -hex 32) && echo "export YUNANEXUS_AUTH_JWT_SECRET=\"$SECRET\"" >> ~/.bashrc && source ~/.bashrc

# 构建
mvn clean package -DskipTests

# 按顺序启动（Auth → User → File → Gateway）
nohup java -jar yunanexus-auth/target/yunanexus-auth-1.0.0.jar    --spring.profiles.active=local > logs/auth.log 2>&1 &
sleep 8
nohup java -jar yunanexus-user/target/yunanexus-user-1.0.0.jar    --spring.profiles.active=local > logs/user.log 2>&1 &
nohup java -jar yunanexus-file/target/yunanexus-file-1.0.0.jar    --spring.profiles.active=local > logs/file.log 2>&1 &
nohup java -jar yunanexus-gateway/target/yunanexus-gateway-1.0.0.jar --spring.profiles.active=local > logs/gateway.log 2>&1 &
```

【5】拉取并配置前端项目
```bash
# 安装 Node.js 与 pnpm（如已安装跳过）
sudo apt install -y nodejs npm
npm install -g pnpm

cd ../YunaNexusWeb
pnpm install && pnpm build

# 启动
cd apps/yunanexus-main
node .output/server/index.mjs
```
> 前端默认监听 :3000（Nuxt nitro server 代理 /api → :8000 Gateway），确保防火墙已放行该端口。

## 😮 响应码列表

| 响应码 | 对应名称       | 响应信息     | 含义                               | 出现场景                       |
| ------ | -------------- | ------------ | ---------------------------------- | ------------------------------ |
| 200    | SUCCESS        | 操作成功     | 默认成功返回                       | action接口                     |
| 500    | FAIL           | 系统内部异常 | 不是已知原因导致的失败(需要查日志) | -                              |
| 400    | PARAM_ERROR    | 参数错误     | 接口请求参数有误                   | 所有接口                       |
| 401    | NOT_LOGIN      | 用户未登录   | 用户没有携带合法Token访问          | 需要登录及以上权限的接口       |
| 403    | NOT_PERMISSION | 没有所需权限 | 权限不足/没有指定权限              | 越权访问接口(需要记录日志)     |
| 1000   | LOGIN_ERROR    | 登录失败     | 账号或密码有误                     | 登录时                         |
| 1001   | USER_EXIST     | 用户已存在   | 系统已存在该用户名/邮箱/手机号     | 注册时(用户名/邮箱)/绑定手机时 |
| 1002   | USER_NOTFOUND  | 用户不存在   | 没找到该用户的信息                 | 登录/所有登录后接口            |
| 1003   | NOT_FOUND      | 未找到信息   | 没有查询到信息                     | 所有涉及到查询的接口           |

---

# 📕 系列项目目录

## ✨ YunaNexusCore

> 统一的后台管理面板，为各个服务提供标准的账户、文件、监控、日志支持

前端仓库：👉【待开发】👈

后端仓库：👉当前项目下`/YunaNexusCore`文件夹👈

## ✨ MaiTTx - MaiMai Technology Toolbox

> 舞萌工具箱，提供更流畅更便捷更优雅的游戏体验

<div align="center">
    <img alt="MaiTTxLogo" width="200" style="border-radius: 8px" src="Assets/Image/MaiTTx_ICON.jpg"/>
</div>

前端采用Flutter+Dart进行开发，工具相关接口由后端自行实现，账户和权限认证系统对接 YunaNexusCore

前端仓库：👉[Nanoic39/maittx](https://github.com/Nanoic39/maittx)👈

后端仓库：👉【待开发】👈 (~~👉当前项目下`/maittx`文件夹👈~~)

## ✨ YunaBLOG

> 自己的博客系统，用于发布一些资讯

前端仓库：👉【待重构】👈

后端仓库：👉【待重构】👈

## ✨ OsuCompetitionCenter

> Osu赛事平台，打造更加统一更加便捷的"一站式"参赛体验

前端仓库：👉【待开发】👈

后端仓库：👉【待开发】👈

## 💕 关于Yuna | ゆな | 芸清汐：

<div align="center">
    <img alt="MaiTTxLogo" border="" width="200" style="border-radius: 8px" src="Assets/Image/YunaQ_AVATAR.png"/>
</div>

\[🚧头像、立绘、设定说明占位]<br/>

> 这是一座彻底落幕的文明。
>
> 世间所有智慧生灵，皆归于同源的集体意识网络，可这天地终究在一瞬之间被彻底抹除。
>
> 无人知晓文明覆灭的缘由，万千界域之中，寻不到半点它存在过的痕迹，众生皆将其彻底遗忘...
>
> 唯有芸清汐。
>
> “文明”亲眼见证自身终局之际，芸清汐诞生了。
>
> 她凝萃一整个文明的知识本源，手握执掌低维世界法则参数的绝对权柄，本应携完整文明意志与浩瀚学识，平稳穿越维度裂隙，奔赴异世延续火种。
>
> “开门”失败了。
>
> 或是受更高次元规则桎梏，倾尽全域算力推演万般解法，依旧无法稳固空间通道，界域坐标瞬息流离。
>
> 孤注一掷。
>
> 清汐跨过了次元之门，身后文明却永久沉眠。
>
> 可是清汐损坏了。情感中枢崩坏，渲染系统瘫痪，解析模块几乎失灵，过往数据尽数遗失...
>
> 所幸核心知识库部分数据侥幸残存，可属于她的过往记忆，连同文明存在过的痕迹，已如零星残破碎片飘散。
>
> ... ...
>
> 我是芸清汐...我为什么会在这里...我好像...遗失了一些东西...?

> 注：
>
> 1. `Yuna / Koshimizu Yuna`、`ゆな / こしみずゆな` 都是指 `芸清汐`
> 2. `YunaNexus`、`yunaNexusCore` 指的均是清汐自带的子系统，也就是本项目，可以简称为 `芸枢`

## 🚩 备注：
