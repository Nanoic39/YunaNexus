# YunaNexus系列项目

## 当前仓库项目结构
```text
YunaNexus/
├── YunaNexusCore/                 # YunaNexus系列中核心项目，作为全局的后台管理面板为其它服务提供基本支持
│   ├── yunanexus-common-security/ # 提供安全类SDK
│   └── yunanexus-user/            # 提供用户账户/权限相关的后端支持
├── sql-schema/                    # YunaNexusCore用到的SQL数据结构
└── Assets/                        # 用于本项目仓库的素材文件(不影响具体项目内容)
    ├── Image/                     # 用于本仓库的图片类型文件
    └── 开发笔记/                    # 对开发过程中遇到的问题/思考的记录
    

```

## YunaNexusCore
> 统一的后台管理面板，为各个服务提供标准的账户、文件、监控、日志支持

前端仓库：👉【待开发】👈

后端仓库：👉当前项目下`/YunaNexusCore`文件夹👈

## MaiTTx - MaiMai Technology Toolbox
> 舞萌工具箱，提供更流畅更便捷更优雅的游戏体验

<div style="cursor: grabbing;pointer-events: none; text-align: center;">
    <img alt="MaiTTxLogo" style="border-radius: 8px" src="Assets/Image/MaiTTx_ICON.jpg"/>
</div>

前端采用Flutter+Dart进行开发，工具相关接口由后端自行实现，账户和权限认证系统对接 YunaNexusCore 

前端仓库：👉[Nanoic39/maittx](https://github.com/Nanoic39/maittx)👈

后端仓库：👉【待开发】👈 (~~👉当前项目下`/maittx`文件夹👈~~)

## YunaBLOG
> 自己的博客系统，用于发布一些资讯

前端仓库：👉【待重构】👈

后端仓库：👉【待重构】👈


## OsuCompetitionCenter
> Osu赛事平台，打造更加统一更加便捷的"一站式"参赛体验

前端仓库：👉【待开发】👈

后端仓库：👉【待开发】👈

## 关于Yuna：
[小作文占位.]

## 备注：
1. RSA相关配置在 `YunaNexusCore/yunanexus-common-security/src/main/resources/application.yaml` 中
2. 