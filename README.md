# ✨YunaNexus基本信息

> 这篇Markdown文件是我自己手搓的，无任何AI生成内容 `: )`

## 📕 当前仓库项目结构

```text
YunaNexus/
├── YunaNexusCore/                 # YunaNexus系列中核心项目，作为全局的后台管理面板为其它服务提供基本支持
│   ├── yunanexus-common-redis/    # 提供 Redis SDK
│   ├── yunanexus-common-mail/     # 提供 Mail SDK
│   ├── yunanexus-common-web/      # 提供 Result/Exception SDK
│   ├── yunanexus-auth/            # 提供安全认证相关的后端支持
│   ├── yunanexus-file/            # 提供文件相关的后端支持
│   └── yunanexus-user/            # 提供用户账户相关的后端支持
├── YunaNexusWeb/                  # YunaNexus 系列前端工作区（待重新设计页面样式）
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

## ⚙️ .yaml配置文件(开发完毕后该板块会被替换为项目运行指南，届时代码仓库中会提供模板文件)

> 当前配置文件存在`application.yaml`与`application-local.yaml`混用的情况。
> 
> 项目主模块开发完后会做调整：
> 1. 把固定配置和可选修改的配置放到`application.yaml`中。
> 2. 把需要填写的配置放入`application-example.yaml`中。
> 3. 开发时把`application-example.yaml`中`example`改为`pom.xml`中支持的`spring.profiles.active`即可
> 4. 默认`spring.profiles.active`支持`local`和`prod`
> 5. 修改后例如`application-local.yaml`，放到`application.yaml`同一目录下即可

## 😮 响应码列表
| 响应码  | 对应名称           | 响应信息   | 含义                 | 出现场景              |
|------|----------------|--------|--------------------|-------------------|
| 200  | SUCCESS        | 操作成功   | 默认成功返回             | action接口          |
| 500  | FAIL           | 系统内部异常 | 不是已知原因导致的失败(需要查日志) | -                 |
| 400  | PARAM_ERROR    | 参数错误   | 接口请求参数有误           | 所有接口              |
| 401  | NOT_LOGIN      | 用户未登录  | 用户没有携带合法Token访问    | 需要登录及以上权限的接口      |
| 403  | NOT_PERMISSION | 没有所需权限 | 权限不足/没有指定权限        | 越权访问接口(需要记录日志)    |
| 1000 | LOGIN_ERROR    | 登录失败   | 账号或密码有误            | 登录时               |
| 1001 | USER_EXIST     | 用户已存在  | 系统已存在该用户名/邮箱/手机号   | 注册时(用户名/邮箱)/绑定手机时 |
| 1002 | USER_NOTFOUND  | 用户不存在  | 没找到该用户的信息          | 登录/所有登录后接口        |
| 1003 | NOT_FOUND      | 未找到信息  | 没有查询到信息            | 所有涉及到查询的接口        |


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
> 1. `Yuna / Koshimizu Yuna`、`ゆな / こしみずゆな` 都是指 `芸清汐`
> 2. `YunaNexus`、`yunaNexusCore` 指的均是清汐自带的子系统，也就是本项目，可以简称为 `芸枢`

## 🚩 备注：

