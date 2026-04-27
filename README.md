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
│   ├── yunanexus-common-web/      # 提供 LocalDateTime/Result/Exception SDK ( 🚧 开发中... )
│   ├── yunanexus-auth/            # 提供安全认证相关的后端支持 ( 🚧 开发中... )
│   └── yunanexus-user/            # 提供用户账户/权限相关的后端支持
├── sql-schema/                    # YunaNexusCore用到的SQL数据结构
└── Assets/                        # 用于本项目仓库的素材文件(不影响具体项目内容)
    ├── Image/                     # 用于本仓库的图片类型文件
    └── 开发笔记/                    # 对开发过程中遇到的问题/思考的记录
```

## 🔧 application-local.yaml配置文件(开发完毕后该板块会被替换为项目运行指南，届时代码仓库中会提供模板文件)

位于`YunaNexusCore/yunanexus-user/src/main/resources/`下

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
    <img alt="MaiTTxLogo" style="border-radius: 8px" src="Assets/Image/MaiTTx_ICON.jpg"/>
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

\[🚧OC小作文占位.]

## 🚩 备注：

1. RSA相关配置在 `YunaNexusCore/yunanexus-common-security/src/main/resources/application.yaml` 中
2. 各个服务都有自己的 RocketMQ服务配置，运行前需要自行根据自己的RocketMQ环境配置 `name-server`
3. <br />


