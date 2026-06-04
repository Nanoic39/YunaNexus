<script setup lang="ts">
import { ref } from "vue";

useHead({ title: "风格预览" });

const activeTab = ref<"all" | "image" | "doc" | "other">("all");
const toggleOn = ref(true);
const switchChecked = ref(false);

const statItems = [
  { label: "文件总数", value: "128", unit: "个", trend: "+12%" },
  { label: "已用空间", value: "45.2", unit: "GB", trend: "+3.1%" },
  { label: "今日上传", value: "7", unit: "个", trend: null },
  { label: "活跃会话", value: "3", unit: "台", trend: null },
];

const files = [
  {
    name: "2026-Q2-工作总结.docx",
    size: "2.4 MB",
    type: "doc",
    updated: "10 分钟前",
    shared: true,
  },
  {
    name: "产品原型图-v3.fig",
    size: "18.7 MB",
    type: "other",
    updated: "2 小时前",
    shared: false,
  },
  {
    name: "会议纪要-0525.md",
    size: "36 KB",
    type: "doc",
    updated: "昨天",
    shared: true,
  },
  {
    name: "系统架构设计.pdf",
    size: "4.1 MB",
    type: "doc",
    updated: "3 天前",
    shared: true,
  },
  {
    name: "首页 Banner 方案.png",
    size: "8.3 MB",
    type: "image",
    updated: "5 天前",
    shared: false,
  },
  {
    name: "团队合影-final.jpg",
    size: "12.6 MB",
    type: "image",
    updated: "1 周前",
    shared: true,
  },
];

const tablePeople = [
  {
    name: "林一",
    role: "管理员",
    storage: "12.4 GB",
    files: 247,
    status: "active",
  },
  {
    name: "陈二",
    role: "编辑者",
    storage: "3.1 GB",
    files: 89,
    status: "active",
  },
  {
    name: "张三",
    role: "观察者",
    storage: "0.8 GB",
    files: 12,
    status: "inactive",
  },
];

const alertItems = [
  { type: "success", text: "文件上传成功 — 2026-Q2-工作总结.docx" },
  { type: "warning", text: "存储空间已使用 89%，建议清理回收站" },
  { type: "info", text: "新版本 v2.5.1 已发布，查看更新日志" },
  { type: "error", text: "共享链接已过期，请重新生成" },
];

const shareRecords = [
  { target: "开发团队", code: "xJ3kP9", downloads: 42, expire: "2026-06-25" },
  { target: "外部客户", code: "aB7mQ2", downloads: 8, expire: "2026-05-30" },
];
</script>

<template>
  <div class="preview-page">
    <header class="preview-header">
      <h1>YunaNexus 设计风格预览</h1>
      <p>
        主色
        <span class="color-chip" style="--c: #a0d8ef">#A0D8EF</span>
        &nbsp;·&nbsp; 点缀
        <span class="color-chip" style="--c: #80ffc0">#80FFC0</span>
        &nbsp;·&nbsp; 背景
        <span class="color-chip" style="--c: #f5f6f8">#F5F6F8</span>
      </p>
    </header>

    <section class="style-section style-d">
      <div class="style-header">
        <h2>方案 D · 轻盈毛玻璃</h2>
        <p>半透明卡片 · 浅景深 · 点缀克制用于功能性信号</p>
      </div>

      <!-- 1. 统计卡片 -->
      <h3 class="section-subtitle">统计卡片</h3>
      <div class="stats-row">
        <article v-for="item in statItems" :key="item.label" class="stat-card">
          <span class="stat-value">
            {{ item.value }}<span class="stat-unit">{{ item.unit }}</span>
          </span>
          <span class="stat-label">{{ item.label }}</span>
          <span v-if="item.trend" class="stat-trend">{{ item.trend }}</span>
        </article>
      </div>

      <!-- 2. 文件列表 -->
      <h3 class="section-subtitle">文件列表</h3>
      <div class="file-panel">
        <div class="tab-bar">
          <button
            v-for="t in [['all','全部'],['image','图片'],['doc','文档'],['other','其他']] as const"
            :key="t[0]"
            class="tab-btn"
            :class="{ 'tab-btn-active': activeTab === t[0] }"
            @click="activeTab = t[0]"
          >{{ t[1] }}</button>
          <div class="tab-bar-spacer" />
          <div class="input-wrap input-wrap-sm">
            <input class="text-input" type="text" placeholder="搜索文件..." />
          </div>
        </div>
        <div class="file-list">
          <div v-for="(f, idx) in files" :key="f.name" class="file-row-item" :style="{ animationDelay: `${idx * 0.04}s` }">
            <span class="file-icon" :class="`file-icon-${f.type}`" />
            <div class="file-info">
              <span class="file-name-text">{{ f.name }}</span>
              <span class="file-meta">{{ f.size }} · {{ f.type === 'doc' ? '文档' : f.type === 'image' ? '图片' : '其他' }} · {{ f.updated }}</span>
            </div>
            <div class="file-actions">
              <span v-if="f.shared" class="shared-badge">已共享</span>
              <button class="row-btn">&#8942;</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 3. 按钮 & 开关 -->
      <h3 class="section-subtitle">按钮 &amp; 开关</h3>
      <div class="action-row">
        <button class="btn btn-primary">主要操作</button>
        <button class="btn btn-secondary">次要操作</button>
        <span class="action-sep" />
        <button class="btn btn-danger">危险操作</button>
        <span class="action-sep" />
        <label class="toggle-wrap">
          <span class="toggle-label">Toggle</span>
          <button
            class="toggle-track"
            :class="{ 'toggle-on': toggleOn }"
            @click="toggleOn = !toggleOn"
          >
            <span class="toggle-thumb" />
          </button>
        </label>
        <label class="switch-wrap">
          <span class="switch-label">Switch</span>
          <div
            class="switch-track"
            :class="{ 'switch-on': switchChecked }"
            @click="switchChecked = !switchChecked"
          >
            <span class="switch-node" />
          </div>
        </label>
      </div>

      <!-- 4. 提示条 -->
      <h3 class="section-subtitle">提示条</h3>
      <div class="alert-list">
        <div
          v-for="a in alertItems"
          :key="a.text"
          class="alert-item"
          :class="`alert-${a.type}`"
        >
          <svg class="alert-icon" viewBox="0 0 20 20" fill="none">
            <template v-if="a.type === 'success'">
              <circle cx="10" cy="10" r="9" stroke="currentColor" stroke-width="1.5" />
              <path d="M6 10.5l2.5 2.5 5.5-5.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </template>
            <template v-else-if="a.type === 'warning'">
              <path d="M10 1.5l8.5 15.5H1.5L10 1.5z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
              <path d="M10 7.5v3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              <circle cx="10" cy="14" r="1" fill="currentColor" />
            </template>
            <template v-else-if="a.type === 'info'">
              <circle cx="10" cy="10" r="9" stroke="currentColor" stroke-width="1.5" />
              <path d="M10 6v5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              <circle cx="10" cy="14.5" r="1" fill="currentColor" />
            </template>
            <template v-else>
              <circle cx="10" cy="10" r="9" stroke="currentColor" stroke-width="1.5" />
              <path d="M7 7l6 6M13 7l-6 6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </template>
          </svg>
          <span>{{ a.text }}</span>
        </div>
      </div>

      <!-- 5. 空间使用 -->
      <h3 class="section-subtitle">空间使用</h3>
      <div class="progress-panel">
        <svg width="0" height="0">
          <defs>
            <linearGradient id="ringGrad" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stop-color="#a0d8ef" />
              <stop offset="100%" stop-color="#80ffc0" />
            </linearGradient>
          </defs>
        </svg>
        <div class="progress-ring-col">
          <svg class="progress-ring" viewBox="0 0 120 120">
            <circle class="ring-bg" cx="60" cy="60" r="52" />
            <circle class="ring-fill" cx="60" cy="60" r="52"
              stroke-dasharray="326.7"
              stroke-dashoffset="35.9"
            />
          </svg>
          <div class="ring-center">
            <span class="ring-value">89<small>%</small></span>
            <span class="ring-label">已用</span>
          </div>
        </div>
        <div class="progress-detail">
          <div class="progress-detail-row">
            <span class="progress-detail-label">总容量</span>
            <span class="progress-detail-val">50 GB</span>
          </div>
          <div class="progress-detail-row">
            <span class="progress-detail-label">已使用</span>
            <span class="progress-detail-val accent-blue">44.5 GB</span>
          </div>
          <div class="progress-detail-row">
            <span class="progress-detail-label">剩余</span>
            <span class="progress-detail-val accent-green">5.5 GB</span>
          </div>
          <div class="progress-bar-small">
            <div class="progress-bar-small-fill" style="width:89%" />
          </div>
        </div>
      </div>

      <!-- 6. 分享列表 -->
      <h3 class="section-subtitle">分享记录</h3>
      <div class="share-grid">
        <article v-for="s in shareRecords" :key="s.code" class="share-card">
          <div class="share-card-head">
            <span class="share-card-code">{{ s.code }}</span>
            <span class="share-card-status">有效</span>
          </div>
          <p>
            分享至 <strong>{{ s.target }}</strong>
          </p>
          <div class="share-card-meta">
            <span>{{ s.downloads }} 次下载</span>
            <span>过期 {{ s.expire }}</span>
          </div>
        </article>
      </div>

      <!-- 7. 成员 -->
      <h3 class="section-subtitle">成员</h3>
      <div class="member-grid">
        <article v-for="p in tablePeople" :key="p.name" class="member-card">
          <div class="member-card-top">
            <span class="member-avatar md">{{ p.name[0] }}</span>
            <div>
              <strong>{{ p.name }}</strong>
              <span class="member-role-tag">{{ p.role }}</span>
            </div>
            <span class="status-dot" :class="p.status === 'active' ? 'status-active' : 'status-inactive'" />
          </div>
          <div class="member-card-stats">
            <div class="member-stat">
              <span class="member-stat-val">{{ p.storage }}</span>
              <span class="member-stat-label">存储</span>
            </div>
            <div class="member-stat">
              <span class="member-stat-val">{{ p.files }}</span>
              <span class="member-stat-label">文件</span>
            </div>
          </div>
        </article>
      </div>

      <!-- 8. 分页 -->
      <h3 class="section-subtitle">分页</h3>
      <nav class="pagination-row">
        <button class="page-btn nav">
          <svg viewBox="0 0 16 16" fill="none"><path d="M10 3L5 8l5 5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
          上一页
        </button>
        <div class="page-numbers">
          <span class="page-num active">1</span>
          <span class="page-num">2</span>
          <span class="page-num">3</span>
          <span class="page-dots">···</span>
          <span class="page-num">12</span>
        </div>
        <button class="page-btn nav">
          下一页
          <svg viewBox="0 0 16 16" fill="none"><path d="M6 3l5 5-5 5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </button>
      </nav>

      <!-- 9. 时间线 -->
      <h3 class="section-subtitle">最近动态</h3>
      <div class="timeline-panel">
        <div
          v-for="(item, idx) in [
            { icon: 'upload', text: '上传了 ', strong: '系统架构设计.pdf', time: '3 天前' },
            { icon: 'share', text: '创建共享链接 ', strong: 'xJ3kP9', time: '昨天' },
            { icon: 'delete', text: '删除了 ', strong: '临时文件.zip', time: '2 小时前' },
          ]"
          :key="idx"
          class="timeline-row"
        >
          <div class="timeline-dot-col">
            <div class="timeline-dot" :class="{ accent: idx === 0 }">
              <svg v-if="item.icon === 'upload'" viewBox="0 0 14 14" fill="none"><path d="M7 2v7M4 5l3-3 3 3M2.5 11v1.5h9V11" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
              <svg v-else-if="item.icon === 'share'" viewBox="0 0 14 14" fill="none"><circle cx="4" cy="7" r="2" stroke="currentColor" stroke-width="1.3"/><circle cx="10" cy="4" r="2" stroke="currentColor" stroke-width="1.3"/><circle cx="10" cy="10" r="2" stroke="currentColor" stroke-width="1.3"/><path d="M5.5 6l3-1M5.5 8l3 1" stroke="currentColor" stroke-width="1.3"/></svg>
              <svg v-else viewBox="0 0 14 14" fill="none"><path d="M3 4h8M5.5 2.5v9M3 10h8" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/></svg>
            </div>
            <div v-if="idx < 2" class="timeline-line" />
          </div>
          <div class="timeline-body">
            <span class="timeline-text">{{ item.text }}<strong>{{ item.strong }}</strong></span>
            <span class="timeline-time">{{ item.time }}</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.preview-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px 160px;
  font-family: "Noto Sans SC", Inter, "Segoe UI", sans-serif;
}

.preview-header {
  text-align: center;
  margin-bottom: 48px;

  h1 {
    margin: 0 0 12px;
    font-family: "JetBrains Mono", "Fira Code", monospace;
    font-size: 32px;
    font-weight: 700;
    color: #1a2a3a;
  }

  p {
    margin: 0;
    color: #64748b;
    font-size: 15px;
  }
}

.color-chip {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 3px;
  background: var(--c);
  vertical-align: middle;
  margin: 0 2px;
  border: 1px solid rgba(0, 0, 0, 0.08);
}

.style-section {
  padding: 32px;
  border-radius: 16px;
  background: linear-gradient(135deg, #f5f6f8 0%, #ecf3f8 100%);
}

.style-header {
  margin-bottom: 36px;

  h2 {
    margin: 0 0 8px;
    font-family: "JetBrains Mono", "Fira Code", monospace;
    font-size: 24px;
    font-weight: 700;
    color: #1a2a3a;
  }

  p {
    margin: 0;
    color: #64748b;
  }
}

.section-subtitle {
  margin: 36px 0 16px;
  font-family: "JetBrains Mono", "Fira Code", monospace;
  font-size: 13px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

/* ======== LAYOUT ======== */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.action-row {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 24px;
}

.action-sep {
  width: 1px;
  height: 28px;
  background: rgba(0, 0, 0, 0.06);
}

/* ======== STAT CARD ======== */
.stat-card {
  display: flex;
  flex-direction: column;
  padding: 24px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(12px);
  box-shadow: 0 4px 20px rgba(160, 216, 239, 0.12);
  transition: all 0.3s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.88);
    box-shadow: 0 8px 28px rgba(160, 216, 239, 0.2);
    transform: translateY(-1px);
  }
}

.stat-value {
  font-size: 36px;
  font-weight: 800;
  color: #1a2a3a;
  font-family: "JetBrains Mono", "Fira Code", monospace;
  line-height: 1;
}

.stat-unit {
  color: #64748b;
  font-size: 14px;
  margin-left: 4px;
  font-weight: 600;
}

.stat-label {
  margin-top: 12px;
  font-family: "JetBrains Mono", "Fira Code", monospace;
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
}

.stat-trend {
  margin-top: 10px;
  font-size: 12px;
  font-weight: 700;
  font-family: "JetBrains Mono", "Fira Code", monospace;
  color: #059669;
}

/* ======== FILE LIST ======== */
.file-panel {
  border-radius: 12px;
  border: 1px solid rgba(255,255,255,.6);
  background: rgba(255,255,255,.72);
  backdrop-filter: blur(12px);
  box-shadow: 0 4px 20px rgba(160,216,239,.12);
  overflow: hidden;
}

.tab-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 20px;
  border-bottom: 1px solid rgba(0,0,0,.05);
}

.tab-bar-spacer { flex: 1; }

.tab-btn {
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  font-family: "JetBrains Mono", "Fira Code", monospace;
  cursor: pointer;
  transition: all .2s ease;
  &:hover { background: rgba(160,216,239,.15); color: #1a2a3a; }
}

.tab-btn-active { background: rgba(160,216,239,.18); color: #1a2a3a; }

.file-list {
  padding: 8px 0;
}

.file-row-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 20px;
  transition: background .2s ease;
  animation: fileSlideIn .35s ease both;

  &:hover { background: rgba(160,216,239,.08); }
  & + & { border-top: 1px solid rgba(0,0,0,.03); }
}

@keyframes fileSlideIn {
  from { opacity: 0; transform: translateY(4px); }
  to   { opacity: 1; transform: translateY(0); }
}

.file-icon {
  width: 40px; height: 40px;
  border-radius: 10px;
  flex-shrink: 0;
  &::before {
    content: "";
    display: block;
    width: 100%; height: 100%;
    border-radius: inherit;
  }
}

.file-icon-doc::before {
  background: rgba(160,216,239,.25);
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z'/%3E%3C/svg%3E");
  mask-size: 22px; mask-position: center; mask-repeat: no-repeat;
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z'/%3E%3C/svg%3E");
  -webkit-mask-size: 22px; -webkit-mask-position: center; -webkit-mask-repeat: no-repeat;
}

.file-icon-image::before {
  background: rgba(160,216,239,.25);
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Crect x='3' y='3' width='18' height='18' rx='2'/%3E%3C/svg%3E");
  mask-size: 22px; mask-position: center; mask-repeat: no-repeat;
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Crect x='3' y='3' width='18' height='18' rx='2'/%3E%3C/svg%3E");
  -webkit-mask-size: 22px; -webkit-mask-position: center; -webkit-mask-repeat: no-repeat;
}

.file-icon-other::before {
  background: rgba(160,216,239,.18);
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M13 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V9z'/%3E%3Cpolyline points='13 2 13 9 20 9'/%3E%3C/svg%3E");
  mask-size: 22px; mask-position: center; mask-repeat: no-repeat;
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M13 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V9z'/%3E%3Cpolyline points='13 2 13 9 20 9'/%3E%3C/svg%3E");
  -webkit-mask-size: 22px; -webkit-mask-position: center; -webkit-mask-repeat: no-repeat;
}

.file-info { flex: 1; min-width: 0; }
.file-name-text { display: block; font-weight: 600; color: #1a2a3a; font-size: 14px; margin-bottom: 4px; }
.file-meta { display: block; color: #94a3b8; font-size: 12px; font-family: "JetBrains Mono","Fira Code",monospace; }

.file-actions { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }

.shared-badge {
  padding: 3px 10px;
  border-radius: 10px;
  background: rgba(0,0,0,.04);
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  font-family: "JetBrains Mono","Fira Code",monospace;
}

.row-btn {
  width: 32px; height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #94a3b8;
  font-size: 18px;
  cursor: pointer;
  transition: all .2s ease;
  &:hover { background: rgba(160,216,239,.12); color: #1a2a3a; }
}

/* ======== BUTTONS / TOGGLE / SWITCH ======== */
.btn {
  padding: 12px 24px;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  font-family: "JetBrains Mono", "Fira Code", monospace;
  transition: all 0.25s ease;
}

.btn-primary {
  background: #a0d8ef;
  color: #1a2a3a;
  box-shadow: 0 4px 16px rgba(160, 216, 239, 0.35);

  &:hover {
    background: #89cde6;
    transform: translateY(-1px);
    box-shadow: 0 6px 20px rgba(160, 216, 239, 0.45);
  }
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  color: #475569;

  &:hover {
    background: rgba(255, 255, 255, 0.9);
    transform: translateY(-1px);
  }
}

.btn-danger {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;

  &:hover {
    background: rgba(239, 68, 68, 0.18);
    transform: translateY(-1px);
  }
}

.toggle-wrap,
.switch-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.toggle-label,
.switch-label {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  font-family: "JetBrains Mono", "Fira Code", monospace;
}

.toggle-track {
  width: 44px;
  height: 24px;
  border-radius: 24px;
  border: none;
  background: #dde1e6;
  position: relative;
  cursor: pointer;
  transition: background 0.25s ease;
  padding: 0;
}

.toggle-on {
  background: #80ffc0;
}

.toggle-thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
  transition: transform 0.25s ease;
}

.toggle-on .toggle-thumb {
  transform: translateX(20px);
}

.switch-track {
  width: 40px;
  height: 22px;
  border-radius: 22px;
  background: #dde1e6;
  position: relative;
  cursor: pointer;
  transition: background 0.25s ease;
}

.switch-on {
  background: #80ffc0;
}

.switch-node {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
  transition: transform 0.25s ease;
}

.switch-on .switch-node {
  transform: translateX(18px);
}

/* ======== ALERTS ======== */
.alert-list { display: grid; gap: 10px; }

.alert-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 18px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  backdrop-filter: blur(8px);
}

.alert-icon {
  width: 20px; height: 20px;
  flex-shrink: 0;
  margin-top: 1px;
}

.alert-success {
  background: rgba(128, 255, 192, 0.14);
  border: 1px solid rgba(128, 255, 192, 0.25);
  color: #1a6b3a;
}

.alert-warning {
  background: rgba(251, 191, 36, 0.1);
  border: 1px solid rgba(251, 191, 36, 0.22);
  color: #92400e;
}

.alert-info {
  background: rgba(160, 216, 239, 0.15);
  border: 1px solid rgba(160, 216, 239, 0.3);
  color: #1e40af;
}

.alert-error {
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.18);
  color: #b91c1c;
}

/* ======== PROGRESS RING ======== */
.progress-panel {
  display: flex;
  gap: 32px;
  align-items: center;
  padding: 28px 32px;
  border-radius: 12px;
  border: 1px solid rgba(255,255,255,.6);
  background: rgba(255,255,255,.72);
  backdrop-filter: blur(12px);
  box-shadow: 0 4px 20px rgba(160,216,239,.12);
}

.progress-ring-col {
  position: relative;
  width: 120px; height: 120px;
  flex-shrink: 0;
}

.progress-ring {
  width: 100%; height: 100%;
  transform: rotate(-90deg);
}

.ring-bg {
  fill: none;
  stroke: rgba(0,0,0,.04);
  stroke-width: 8;
}

.ring-fill {
  fill: none;
  stroke: url(#ringGrad);
  stroke-width: 8;
  stroke-linecap: round;
  transition: stroke-dashoffset .8s ease;
}

.ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.ring-value {
  font-size: 28px;
  font-weight: 800;
  color: #1a2a3a;
  font-family: "JetBrains Mono","Fira Code",monospace;
  line-height: 1;
  small { font-size: 14px; font-weight: 600; color: #64748b; }
}

.ring-label { font-size: 11px; color: #94a3b8; margin-top: 4px; font-family: "JetBrains Mono","Fira Code",monospace; }

.progress-detail { flex: 1; }

.progress-detail-row {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  & + & { border-top: 1px solid rgba(0,0,0,.03); }
}

.progress-detail-label { font-size: 14px; color: #64748b; font-weight: 500; }
.progress-detail-val { font-size: 14px; font-weight: 700; font-family: "JetBrains Mono","Fira Code",monospace; color: #1a2a3a; }
.progress-detail-val.accent-blue { color: #3b82b6; }
.progress-detail-val.accent-green { color: #059669; }

.progress-bar-small {
  height: 6px;
  border-radius: 6px;
  background: rgba(0,0,0,.04);
  margin-top: 10px;
  overflow: hidden;
}

.progress-bar-small-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #a0d8ef, #80ffc0);
  transition: width .6s ease;
}

/* ======== SHARE CARDS ======== */
.share-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.share-card {
  padding: 20px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(12px);
  box-shadow: 0 4px 20px rgba(160, 216, 239, 0.12);
  transition: all 0.3s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.88);
    box-shadow: 0 8px 28px rgba(160, 216, 239, 0.2);
    transform: translateY(-1px);
  }

  p {
    margin: 8px 0;
    color: #475569;
    font-size: 14px;
  }
}

.share-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.share-card-code {
  font-family: "JetBrains Mono", "Fira Code", monospace;
  font-weight: 800;
  font-size: 20px;
  color: #1a2a3a;
}

.share-card-status {
  padding: 3px 10px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.04);
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  font-family: "JetBrains Mono", "Fira Code", monospace;
}

.share-card-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #94a3b8;
}

/* ======== MEMBER CARDS ======== */
.member-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.member-card {
  padding: 22px;
  border-radius: 12px;
  border: 1px solid rgba(255,255,255,.6);
  background: rgba(255,255,255,.72);
  backdrop-filter: blur(12px);
  box-shadow: 0 4px 20px rgba(160,216,239,.12);
  transition: all .3s ease;

  &:hover {
    background: rgba(255,255,255,.88);
    box-shadow: 0 8px 28px rgba(160,216,239,.2);
    transform: translateY(-1px);
  }
}

.member-card-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;

  strong { font-size: 14px; color: #1a2a3a; }
}

.member-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: #a0d8ef;
  color: #1a2a3a;
  font-weight: 800;
  font-family: "JetBrains Mono","Fira Code",monospace;
  font-size: 14px;
  vertical-align: middle;
}

.member-avatar.md {
  width: 40px; height: 40px;
  border-radius: 12px;
  font-size: 16px;
}

.member-role-tag {
  display: block;
  font-size: 12px;
  color: #64748b;
  font-family: "JetBrains Mono","Fira Code",monospace;
  font-weight: 600;
}

.member-card-stats {
  display: flex;
  gap: 24px;
  padding-top: 14px;
  border-top: 1px solid rgba(0,0,0,.04);
}

.member-stat { display: flex; flex-direction: column; gap: 2px; }

.member-stat-val {
  font-size: 20px;
  font-weight: 800;
  color: #1a2a3a;
  font-family: "JetBrains Mono","Fira Code",monospace;
}

.member-stat-label {
  font-size: 11px;
  color: #94a3b8;
  font-family: "JetBrains Mono","Fira Code",monospace;
  font-weight: 600;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}

.status-active {
  background: #80ffc0;
  box-shadow: 0 0 6px rgba(128, 255, 192, 0.4);
}
.status-inactive {
  background: #dde1e6;
}

/* ======== PAGINATION ======== */
.pagination-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.page-btn.nav {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 38px;
  padding: 0 14px;
  border: 1px solid rgba(255,255,255,.5);
  border-radius: 10px;
  background: rgba(255,255,255,.65);
  backdrop-filter: blur(8px);
  color: #475569;
  font-size: 13px;
  font-weight: 600;
  font-family: "JetBrains Mono","Fira Code",monospace;
  cursor: pointer;
  transition: all .2s ease;

  svg { width: 14px; height: 14px; }

  &:hover { background: rgba(160,216,239,.2); color: #1a2a3a; }
}

.page-numbers {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 6px;
  border-radius: 10px;
  background: rgba(255,255,255,.65);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255,255,255,.5);
}

.page-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px; height: 34px;
  border-radius: 8px;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  font-family: "JetBrains Mono","Fira Code",monospace;
  cursor: pointer;
  transition: all .2s ease;

  &:hover { background: rgba(160,216,239,.15); color: #1a2a3a; }
  &.active { background: #a0d8ef; color: #1a2a3a; font-weight: 700; }
}

.page-dots {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px; height: 34px;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 2px;
}

/* ======== TIMELINE ======== */
.timeline-panel {
  padding: 8px 0;
}

.timeline-row {
  display: flex;
  gap: 16px;
  padding: 4px 0;
}

.timeline-dot-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 28px;
  flex-shrink: 0;
}

.timeline-dot {
  width: 28px; height: 28px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0,0,0,.04);
  flex-shrink: 0;

  svg { width: 14px; height: 14px; color: #94a3b8; }

  &.accent {
    background: rgba(128,255,192,.18);
    svg { color: #059669; }
  }
}

.timeline-line {
  width: 2px;
  flex: 1;
  min-height: 20px;
  background: rgba(0,0,0,.06);
  border-radius: 1px;
  margin: 4px 0;
}

.timeline-body {
  flex: 1;
  padding: 4px 0;
}

.timeline-text {
  font-size: 14px;
  color: #475569;
  display: block;
  margin-bottom: 3px;
  strong { color: #1a2a3a; }
}

.timeline-time {
  font-size: 12px;
  color: #94a3b8;
  font-family: "JetBrains Mono","Fira Code",monospace;
}

/* ======== INPUT ======== */
.input-wrap {
  flex: 1;
  max-width: 360px;
}
.input-wrap-sm {
  max-width: 200px;
}

.text-input {
  width: 100%;
  height: 44px;
  padding: 0 16px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(8px);
  color: #1a2a3a;
  font-size: 14px;
  font-family: "JetBrains Mono", "Fira Code", monospace;
  transition: all 0.2s ease;
  outline: none;

  &::placeholder {
    color: #94a3b8;
  }
  &:focus {
    background: rgba(255, 255, 255, 0.92);
    border-color: #a0d8ef;
    box-shadow: 0 0 0 3px rgba(160, 216, 239, 0.2);
  }
}

/* ======== RESPONSIVE ======== */
@media (max-width: 900px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .share-grid { grid-template-columns: 1fr; }
  .member-grid { grid-template-columns: repeat(2, 1fr); }
  .progress-panel { flex-direction: column; align-items: stretch; }
  .action-row { .input-wrap { max-width: 100%; } }
}

@media (max-width: 600px) {
  .stats-row { grid-template-columns: 1fr; }
  .member-grid { grid-template-columns: 1fr; }
  .pagination-row { flex-direction: column; gap: 8px; }
}
</style>
