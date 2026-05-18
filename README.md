# ✨YunaNexus基本信息

> 这篇Markdown文件是我自己手搓的，无任何AI生成内容 `: )`

## 📕 当前仓库项目结构

```text
YunaNexus/
├── YunaNexusCore/                 # YunaNexus系列中核心项目，作为全局的后台管理面板为其它服务提供基本支持
│   ├── yunanexus-common-redis/    # 提供 Redis SDK
│   ├── yunanexus-common-mail/     # 提供 Mail SDK
│   ├── yunanexus-common-security/ # 提供 Security SDK
│   ├── yunanexus-common-rocketmq/ # 提供 RocketMQ SDK
│   ├── yunanexus-common-web/      # 提供 LocalDateTime/Result/Exception SDK
│   ├── yunanexus-auth/            # 提供安全认证相关的后端支持
│   ├── yunanexus-file/            # 提供文件相关的后端支持 ( 🚧 开发中... )
│   └── yunanexus-user/            # 提供用户账户/权限相关的后端支持
├── YunaNexusWeb/                  # YunaNexus 系列前端工作区 ( 🚧 开发中... )
│   ├── apps/                      # 具体前端应用
│   │   ├── yunanexus-main/        # 主站客户端前端
│   │   └── yunanexus-admin/       # 管理端前端（预留/待开发）
│   ├── packages/                  # 共享模块
│   │   ├── config/                # ESLint/Prettier/TypeScript/公共开发 配置
│   │   ├── ui/                    # 通用 UI 组件库
│   │   ├── theme/                 # CSS变量、Tailwind、Nuxt UI主题
│   │   ├── sdk/                   # API、types、contracts、request
│   │   ├── stores/                # 跨应用共享 Pinia Store
│   │   ├── composable/            # 跨应用共享 composable
│   │   └── utils/                 # 纯工具函数
│   ├── docs/                      # 前端约定、组件规范、开发文档
│   ├── package.json               # workspace 前端工作区根配置
│   ├── pnpm-workspace.yaml        # workspace 工作区声明文件
│   ├── tsconfig.base.json         # 共享 TypeScript 基础配置
│   ├── .env.example               # .env 模板
│   └── .editorconfig              # 编辑器统一格式配置
├── sql-schema/                    # YunaNexusCore用到的SQL数据结构
└── Assets/                        # 用于本项目仓库的素材文件(不影响具体项目内容)
    ├── Image/                     # 用于本仓库的图片类型文件
    └── 开发笔记/                    # 对开发过程中遇到的问题/思考的记录
```

## 🔧 application-local.yaml配置文件(开发完毕后该板块会被替换为项目运行指南，届时代码仓库中会提供模板文件)

位于`YunaNexusCore/yunanexus-{xxx}/src/main/resources/`下

与`application.yaml`同一层级

```yaml
spring:
  datasource:
    url: jdbc:mysql://{ip:port}/{database_name}?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true # 测试时启用useSSL=false和allowPublicKeyRetrieval=true便于测试,生产环境中禁止启用！
    username: { username }
    password: { password }
    driver-class-name: com.mysql.cj.jdbc.Driver
yunanexus:
  mail:
    enabled: true
    from-address: { from-address } # 必填，显示的发送者邮箱，必须与username一致
    from-name: { from-name } # 必填，显示的发送者名称
    subject-prefix: "" # 非必填，邮件主题前缀
    host: { smtp-host:smtp.qq.com }
    port: 587
    username: { username } # 必填，发送者邮箱
    password: { password } # 必填，授权码
    protocol: { protocol:smtp }
    properties: # 必须保持这种神必写法，否则会报530 Login fail.错误
      "[mail.smtp.auth]": "true"
      "[mail.smtp.starttls.enable]": "true"
      "[mail.smtp.starttls.required]": "true"
    verify:
      code:
        expire-time: 600 # 邮箱验证码有效期，默认值为10分钟，单位：秒
  redis:
    enabled: { boolean:true }
    host: { ip }
    port: { port }
    database: { database }
    username: { username }
    password: { password }
    ssl: { boolean:false }
    timeout: 3s # 超时时间
  rocketmq:
    enabled: { boolean:true } # 启用RocketMQ?
    send-timeout-ms: 3000 # 发送超时时间(ms)
    send-retry-times: 3 # 发送重试次数
  auth:
    jwt:
      secret: { jwtSecurity } # JWT密钥
      access-exp: 7200 # 默认access过期时间
      refresh-exp: 604800 # 默认refresh过期时间
rocketmq:
  name-server: 127.0.0.1:9876 # RocketMQ默认地址，生产/消费均需要配置
  producer: # 生产者需要配置，消费者只需要在注解中声明即可
    group: { group-name }-Producer # -Producer可以不要，这里是为了规范
```

***

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

## 💕 关于Yuna：

<div align="center">
    <img alt="MaiTTxLogo" border="" width="200" style="border-radius: 8px" src="Assets/Image/YunaQ_AVATAR.png"/>
</div>
\[🚧头像、立绘、设定说明占位]<br/>
\[🚧OC小作文占位.]

## 🚩 备注(带*>的为必须处理的内容)：

## [验证码]
邮箱验证码：默认限速 1req/60s·1邮箱，这是写死在代码中的，暂无改动计划，有需要请自行修改
相关文件：
- 限流说明文本：`YunaNexusCore\yunanexus-user\src\main\java\cc\nanoic\yunanexus\user\controller\UserController.java`
```java
// 校验是否被限流
if(usersService.isSendLimited(email)) {
  return Result.fail(R.REQ_API_LIMIT, "请求过于频繁，请 60 秒后再试");
}
```
- 限流具体数值：`YunaNexusCore\yunanexus-user\src\main\java\cc\nanoic\yunanexus\user\service\impl\UsersServiceImpl.java`
```java
// 修改第2、3个参数，分别为最大请求数和一段时间，意思是每【第三个参数】时间内最多请求【第二个参数】次
@Override
public boolean isSendLimited(String email) {
  // 👇
  return !yunaRedisService.allowRequest(sendLimitKeyPrefix + email, 1, Duration.ofSeconds(60));
}
@Override
public boolean isCheckLimited(String email) {
  // 👇
  return !yunaRedisService.allowRequest(checkLimitKeyPrefix + email, 5, Duration.ofSeconds(60));
}
```

### [RSA]
RSA相关配置在 `YunaNexusCore/yunanexus-common-security/src/main/resources/application.yaml` 中，不建议进行改动

### *[RocketMQ]
各个服务都有自己的 RocketMQ服务配置，运行前需要自行根据自己的RocketMQ环境配置 `name-server`

### *[OAuth]
YunaNexusCore的OAuth数据需要使用 `/sql-schema/scripts/` 中的 `oauth-info-init.py` 脚本进行初始化

所有脚本所需的依赖位于requirements.txt文件中，可以使用 `pip install -r requirements.txt` 进行安装

建议在脚本文件夹中构建Python虚拟环境，然后在虚拟环境内运行脚本

上述完整操作命令如下：

```bash
# {YOUR_PATH}\YunaNexus 表示你的项目根目录，下方的指令格式为：
# > {你当前步骤应该在的项目路径} ~: #{该步骤用处解释}
# {需要执行的指令}
> {YOUR_PATH}\YunaNexus ~: # 进入脚本文件夹
cd .\sql-sechma\script

> {YOUR_PATH}\YunaNexus\sql-sechma\script ~: # 创建虚拟环境
python -m venv .venv

> {YOUR_PATH}\YunaNexus\sql-sechma\script ~: # 激活虚拟环境(有些终端检测到虚拟环境后也会自动激活)
.venv\Scripts\Activate.ps1

> {YOUR_PATH}\YunaNexus\sql-sechma\script ~: # 安装依赖
pip install -r requirements.txt
```

随后自行执行需要使用的脚本即可，如：`./oauth-info-init.py`

## *[file]
启动时配置环境变量可以修改本地存储文件的保存位置
```bash
$env:YUNANEXUS_FILE_STORAGE_ROOT="YOUR_ROOT_PATH\FILE_STORAGE_PATH"
```

## [前端]
前端部分采用AI作为主力开发，我这里只专注于后端设计与开发，如果有更好的改进方案，欢迎提交Pull Request进行贡献

## *[.env]
前端环境变量位于`YunaNexusWeb/.env`，代码仓库中同级目录下仅存在`.env.example`

使用时需要先创建文件`.env`并根据`.env.example`完善所有配置