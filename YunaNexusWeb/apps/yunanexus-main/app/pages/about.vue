<script setup lang="ts">
import mascotImage from "~/assets/mascot/YunaImageMascot.jpg";

const names = ["芸清汐", "Koshimizu Yuna", "こしみずゆな"];

const currentIndex = ref(0);
const displayName = computed(() => names[currentIndex.value]);

let timer: ReturnType<typeof setInterval> | null = null;

onMounted(() => {
  timer = setInterval(() => {
    currentIndex.value = (currentIndex.value + 1) % names.length;
  }, 3000);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div class="about-wrapper fade-up">
    <!-- OC 信息 -->
    <div class="about-card fade-up">
      <div class="about-section-title lolifont">✦ 角色信息</div>
      <div class="about-hero">
        <img class="about-avatar" :src="mascotImage" alt="Yuna" />
        <div>
          <div
            class="name-rotator lolifont"
            style="font-size: 16px; font-weight: 600"
          >
            <Transition name="name-slide">
              <span :key="currentIndex">{{ displayName }}</span>
            </Transition>
          </div>
          <div
            style="
              font-size: 12px;
              font-family: var(--font-family-mono);
              color: var(--color-font-assist);
            "
          >
            Yuna (· ω ·) Nanoic39
          </div>
          <p
            class="lolifont"
            style="
              font-size: 13px;
              color: var(--color-font-secondary);
              margin: 8px 0 0;
              line-height: 1.7;
            "
          >
            Nexus 系统的守护者与管理界面化身。以人类形象承载系统意志，
            在虚拟与现实的边界维护秩序，给与每一位用户便利与指引。
          </p>
        </div>
      </div>
      <div class="about-tags">
        <span class="tag tag-green">音游领域高手</span>
        <span class="tag tag-amber">写写码~</span>
        <span class="tag tag-blue">二次元重度依赖</span>
      </div>
    </div>

    <!-- 项目信息 -->
    <div class="about-card fade-up">
      <div class="about-section-title lolifont">✦ 项目信息</div>
      <h1
        class="lolifont"
        style="font-size: 24px; font-weight: 600; margin-bottom: 4px"
      >
        YunaNexus
        <span style="color: var(--color-emphasis); font-weight: 400">芸枢</span>
      </h1>
      <div
        style="
          font-size: 12px;
          font-family: var(--font-family-mono);
          color: var(--color-font-assist);
          margin-bottom: 14px;
        "
      >
        UNIFIED IDENTITY &amp; FILE SYSTEM
      </div>
      <p
        style="
          font-size: 14px;
          color: var(--color-font-secondary);
          line-height: 1.8;
        "
      >
        完全重构的V2版本 YunaNexus <br />
        在保证系统性能的情况下做了合适的模块拆分与业务精简<br />
        相较于V1更加具备代码可读性和配置便捷性<br />
        预期未来发展成为「YunaNexus」生态的核心
      </p>
      <div class="about-tags">
        <span class="tag tag-green">Spring Cloud</span>
        <span class="tag tag-blue">Nuxt</span>
        <span class="tag">MyBatisPlus</span>
      </div>
      <div class="about-meta">
        <div><strong>版本</strong> v0.1.0-alpha</div>
        <div><strong>许可证</strong> MIT</div>
        <div>
          <strong>源码：</strong>
          <a
            href="https://github.com/Nanoic39/YunaNexus"
            target="_blank"
            rel="noopener noreferrer"
            style="color: var(--color-emphasis); text-decoration: none"
            >Nanoic39/YunaNexus</a
          >
        </div>
      </div>
    </div>

    <!-- 个人简历 -->
    <Watermark
      text="个人简历"
      color="rgba(22, 163, 74, 0.08)"
      :font-size="13"
      :font-weight="500"
      :rotate="-18"
      :gap-x="160"
      :gap-y="120"
    >
      <div class="about-card fade-up">
        <div class="about-section-title">✦ 个人简历</div>

        <!-- 基础信息 -->
        <div style="font-size: 16px; font-weight: 600; margin-bottom: 2px">
          王维康
          <span
            style="
              font-size: 11px;
              color: var(--color-font-assist);
              margin-left: 6px;
              font-weight: 400;
            "
          >
            nanoic
          </span>
        </div>
        <div
          style="
            font-size: 12px;
            color: var(--color-font-assist);
            margin-bottom: 14px;
          "
        >
          意向：后端开发 · 准备实习
        </div>

        <div class="about-meta">
          <div>
            <strong>联系电话</strong>
            13935763072
          </div>
          <div>
            <strong>电子邮箱</strong>
            <a
              href="mailto:nanoic39@gmail.com"
              style="color: var(--color-emphasis); text-decoration: none"
            >
              nanoic39@gmail.com
            </a>
          </div>
          <div>
            <strong>居住地址</strong>
            扬州 · 邗江区
          </div>
        </div>

        <!-- 教育背景 -->
        <div style="margin-top: 22px">
          <div
            style="
              font-size: 12px;
              font-weight: 600;
              color: var(--color-font-assist);
              text-transform: uppercase;
              letter-spacing: 0.06em;
              margin-bottom: 12px;
            "
          >
            教育背景
          </div>
          <div class="about-timeline">
            <div class="about-timeline-item">
              <div class="date">2023.09 – 2027.06</div>
              <div class="title">南京邮电大学通达学院 · 软件工程（本科）</div>
              <div class="desc">
                主修：高级语言程序设计、Linux 编程、Python
                语言程序设计、数据结构与算法、Web
                技术、编译原理、算法分析与设计等。
              </div>
            </div>
          </div>
        </div>

        <!-- 核心能力 -->
        <div style="margin-top: 22px">
          <div
            style="
              font-size: 12px;
              font-weight: 600;
              color: var(--color-font-assist);
              text-transform: uppercase;
              letter-spacing: 0.06em;
              margin-bottom: 12px;
            "
          >
            核心能力
          </div>
          <div class="about-skill-list">
            <div>
              <strong>后端开发</strong> Java，Spring Boot / Spring Cloud 微服务
            </div>
            <div>
              <strong>微服务组件</strong> Nacos / OpenFeign / Gateway，SSE
              实时推送
            </div>
            <div>
              <strong>数据库</strong> MySQL 建模设计，索引优化与慢查询分析
            </div>
            <div>
              <strong>消息与缓存</strong> RocketMQ 异步消息，Redisson 分布式锁
            </div>
            <div>
              <strong>运维与工具</strong> Docker 容器化，CI/CD 持续集成，Linux /
              Shell，Git
            </div>
            <div><strong>前端</strong> Vue 3 / Nuxt 3，支持前后端联调</div>
            <div><strong>效率工具</strong> Trae / Copilot</div>
          </div>
        </div>

        <!-- 项目经历 --- 待填写 -->
        <div style="margin-top: 22px">
          <div
            style="
              font-size: 12px;
              font-weight: 600;
              color: var(--color-font-assist);
              text-transform: uppercase;
              letter-spacing: 0.06em;
              margin-bottom: 12px;
            "
          >
            项目经历
          </div>
          <div class="about-project-card">
            <div class="about-project-head">
              <div class="about-project-name">YunaNexus</div>
              <div class="about-project-period">2025.05 – 至今</div>
            </div>
            <div class="about-project-role">后端架构负责人 · 独立全栈开发</div>
            <p class="about-project-desc">
              针对多服务场景下账户体系割裂、权限管理分散的痛点，从零设计并实现统一身份认证与文件管理平台。
              采用 Spring Cloud 微服务架构，基于 Nacos
              实现服务注册与配置中心，通过 Gateway 统一入口与 OpenFeign
              完成服务间通信。 设计 RBAC
              细粒度权限模型与独立文件空间隔离方案，并开发 Nuxt 3 管理前端，搭建
              CI/CD 流水线实现容器化部署。
              当前已构建完整的基础设施层，为后续服务扩展提供标准化基座。
            </p>
            <div class="about-tags">
              <span class="tag tag-green">Spring Boot 4</span>
              <span class="tag tag-blue">Spring Cloud</span>
              <span class="tag">Nacos</span>
              <span class="tag">Gateway</span>
              <span class="tag">Nuxt 3</span>
              <span class="tag">Docker</span>
            </div>
          </div>

          <div class="about-project-card about-project-placeholder">
            <div class="about-project-head">
              <div class="about-project-name">YunaBot</div>
              <div class="about-project-period">待补充</div>
            </div>
            <div class="about-project-role">基于 NoneBot 的 QQ Bot</div>
            <p class="about-project-desc about-project-pending">
              项目详情待补充……
            </p>
          </div>

          <div class="about-project-card about-project-placeholder">
            <div class="about-project-head">
              <div class="about-project-name">YunaBlog</div>
              <div class="about-project-period">待补充</div>
            </div>
            <div class="about-project-role">个人博客</div>
            <p class="about-project-desc about-project-pending">
              项目详情待补充……
            </p>
          </div>
        </div>

        <!-- 获奖经历 -->
        <div style="margin-top: 22px">
          <div
            style="
              font-size: 12px;
              font-weight: 600;
              color: var(--color-font-assist);
              text-transform: uppercase;
              letter-spacing: 0.06em;
              margin-bottom: 12px;
            "
          >
            获奖经历
          </div>
          <div class="about-timeline">
            <div class="about-timeline-item">
              <div class="date">2025.11</div>
              <div class="title">第四届中国高校计算机大赛 — 智能交互创新赛</div>
              <div class="desc">全国总决赛优胜奖</div>
            </div>
            <div class="about-timeline-item">
              <div class="date">2025.05</div>
              <div class="title">"新大陆杯" 江苏省大学生计算机设计大赛</div>
              <div class="desc">三等奖</div>
            </div>
            <div class="about-timeline-item">
              <div class="date">2025.05</div>
              <div class="title">第十六届蓝桥杯 Web 应用开发</div>
              <div class="desc">大学组省级一等奖</div>
            </div>
            <div class="about-timeline-item">
              <div class="date">2024.04</div>
              <div class="title">第十五届蓝桥杯 Web 应用开发</div>
              <div class="desc">大学组省级二等奖</div>
            </div>
            <div class="about-timeline-item">
              <div class="date">2023.12</div>
              <div class="title">第五届全国高校计算机能力挑战赛</div>
              <div class="desc">华东赛区本研组三等奖</div>
            </div>
          </div>
        </div>

        <!-- 自我评价 -->
        <div style="margin-top: 22px">
          <div
            style="
              font-size: 12px;
              font-weight: 600;
              color: var(--color-font-assist);
              text-transform: uppercase;
              letter-spacing: 0.06em;
              margin-bottom: 12px;
            "
          >
            自我评价
          </div>
          <p
            style="
              font-size: 13px;
              color: var(--color-font-secondary);
              line-height: 1.8;
              margin: 0;
            "
          >
            具备 Java
            后端完整开发经验，可独立完成需求分析、接口设计、数据库建模、联调与部署；熟悉
            Spring Boot / MyBatis / MySQL / Redis
            技术栈，关注代码质量与性能优化，能快速定位并闭环解决问题。
          </p>
        </div>
      </div>
    </Watermark>
  </div>
</template>
