<script setup lang="ts">
useHead({
  title: "首页",
});
</script>

<template>
  <div class="dashboard-page">
    <section class="page-header-card">
      <div>
        <p class="section-overline">Dashboard</p>
        <h1 class="page-title">YunaNexus 公开工作台</h1>
        <p class="page-desc">
          所有公开入口与系统概览统一在内容区域展示，登录后延续同一套B端布局逻辑。
        </p>
      </div>
    </section>
    <section class="stats-grid">
      <article class="stat-card">
        <span>2</span>
        <p>已落地核心服务</p>
      </article>
      <article class="stat-card">
        <span>3</span>
        <p>公开入口页面</p>
      </article>
      <article class="stat-card">
        <span>1</span>
        <p>资料编辑链路</p>
      </article>
      <article class="stat-card">
        <span>Next</span>
        <p>文件系统与主题系统</p>
      </article>
    </section>
    <section class="panel-grid">
      <article class="panel-card">
        <h2>系统公告</h2>
        <ul>
          <li>用户登录、注册、资料编辑链路已具备骨架。</li>
          <li>权限地图基础链路已完成，管理员能力后续补充。</li>
          <li>首页已纳入统一B端内容布局。</li>
        </ul>
      </article>
      <article class="panel-card">
        <h2>快捷入口</h2>
        <div class="link-list">
          <a href="/login">登录</a><a href="/register">注册</a
          ><a href="/appeal">账户封禁申诉</a>
        </div>
      </article>
      <article class="panel-card">
        <h2>版本摘要</h2>
        <ul>
          <li>主站前端：B端骨架进行中</li>
          <li>User/Auth：核心能力可联调</li>
          <li>全局反馈组件：待抽离</li>
        </ul>
      </article>
      <article class="panel-card">
        <h2>下一步规划</h2>
        <ul>
          <li>抽离全局通知、弹窗、提示组件</li>
          <li>补齐 Login / Register / Appeal 页面</li>
          <li>实现登录后侧栏折叠与多级菜单</li>
        </ul>
      </article>
    </section>
  </div>
</template>

<style scoped lang="scss">
.section-overline {
  margin: 0 0 12px;
  color: var(--yn-color-primary);
  font-family: var(--yn-font-mono), monospace;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.page-header-card,
.panel-card,
.stat-card {
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: var(--yn-color-surface);
  box-shadow: var(--yn-shadow-card);
}
.page-header-card {
  padding: 28px;
}
.page-title {
  margin: 0 0 12px;
  color: var(--yn-color-text-primary);
  font-family: var(--yn-font-mono), monospace;
  font-size: clamp(28px, 3vw, 36px);
  line-height: 1.2;
}
.page-desc {
  margin: 0;
  max-width: 720px;
  color: var(--yn-color-text-secondary);
  line-height: 1.8;
}
.stats-grid,
.panel-grid,
.link-list,
.dashboard-page {
  display: grid;
  gap: 16px;
}
.dashboard-page {
  gap: 24px;
}
.stats-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.stat-card {
  padding: 18px;
}
.stat-card span {
  display: block;
  margin-bottom: 8px;
  color: var(--yn-color-text-primary);
  font-family: var(--yn-font-mono), monospace;
  font-size: 24px;
  font-weight: 700;
}
.stat-card p,
.panel-card li {
  margin: 0;
  color: var(--yn-color-text-secondary);
}
.panel-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.panel-card {
  padding: 24px;
}
.panel-card h2 {
  margin: 0 0 16px;
  color: var(--yn-color-text-primary);
  font-size: 18px;
}
.panel-card ul {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 12px;
}
.link-list a {
  padding: 14px 16px;
  border: 1px solid var(--yn-color-border-medium);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface-raised);
  color: var(--yn-color-text-primary);
  font-weight: 600;
}
@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 960px) {
  .panel-grid,
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
