<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useAuthApi } from "../composables/useAuthApi";
import { useFileApi, type UserFolderItem, type UserManagedFileItem } from "../composables/useFileApi";

useHead({
  title: "首页",
});

const authApi = useAuthApi();
const fileApi = useFileApi();

const loading = ref(false);
const dashboardError = ref("");
const rootFolders = ref<UserFolderItem[]>([]);
const rootFiles = ref<UserManagedFileItem[]>([]);
const recycleFiles = ref<UserManagedFileItem[]>([]);
const storageSummary = ref<{
  totalBytes: number;
  usedBytes: number;
  remainingBytes: number;
  usagePercent: number;
  fileCount: number;
} | null>(null);

const isLoggedIn = computed(() => !!authApi.accessToken.value);
const sessionReady = computed(() => authApi.sessionReady.value);
const currentUser = computed(() => authApi.currentUser.value);
const permissionSnapshot = computed(() => authApi.permissionSnapshot.value);
const permissionCount = computed(
  () => permissionSnapshot.value?.permissionCodes?.length || 0,
);
const roleCount = computed(() => permissionSnapshot.value?.roles?.length || 0);
const tokenExpireAtLabel = computed(() => {
  if (!authApi.tokenExpireAt.value) {
    return "未建立会话";
  }
  const date = new Date(authApi.tokenExpireAt.value);
  return Number.isNaN(date.getTime())
    ? "未知"
    : date.toLocaleString("zh-CN", { hour12: false });
});

const formatBytes = (value?: number | null) => {
  const size = Number(value || 0);
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  if (size < 1024 * 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
  }
  return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`;
};

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return "暂无";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString("zh-CN", { hour12: false });
};

const dashboardStats = computed(() => {
  if (!isLoggedIn.value || !storageSummary.value) {
    return [
      { label: "当前会话", value: sessionReady.value ? "未登录" : "初始化中" },
      { label: "公开入口", value: "3" },
      { label: "默认网关", value: "8000" },
      { label: "访问模式", value: "Public" },
    ];
  }
  return [
    {
      label: "已用空间",
      value: `${storageSummary.value.usagePercent}%`,
      desc: `${formatBytes(storageSummary.value.usedBytes)} / ${formatBytes(storageSummary.value.totalBytes)}`,
    },
    {
      label: "文件总数",
      value: String(storageSummary.value.fileCount),
      desc: "当前账户有效文件",
    },
    {
      label: "根目录内容",
      value: String(rootFolders.value.length + rootFiles.value.length),
      desc: `${rootFolders.value.length} 个目录 · ${rootFiles.value.length} 个文件`,
    },
    {
      label: "回收站",
      value: String(recycleFiles.value.length),
      desc: "待恢复或待过期文件",
    },
  ];
});

const loadDashboard = async () => {
  if (!isLoggedIn.value) {
    dashboardError.value = "";
    rootFolders.value = [];
    rootFiles.value = [];
    recycleFiles.value = [];
    storageSummary.value = null;
    return;
  }
  loading.value = true;
  dashboardError.value = "";
  try {
    const [summaryResult, folderResult, fileResult, recycleResult] =
      await Promise.all([
        fileApi.getStorageSummary(),
        fileApi.listFolders(null),
        fileApi.listFiles(null),
        fileApi.listRecycleFiles(),
      ]);
    storageSummary.value = summaryResult.data;
    rootFolders.value = folderResult.data || [];
    rootFiles.value = fileResult.data || [];
    recycleFiles.value = recycleResult.data || [];
  } catch (error) {
    dashboardError.value =
      error instanceof Error ? error.message : "仪表盘数据加载失败";
  } finally {
    loading.value = false;
  }
};

watch(
  [() => authApi.sessionReady.value, () => authApi.accessToken.value],
  async ([ready]) => {
    if (!ready) {
      return;
    }
    await loadDashboard();
  },
  { immediate: true },
);

onMounted(async () => {
  if (authApi.sessionReady.value) {
    await loadDashboard();
  }
});
</script>

<template>
  <div class="dashboard-page">
    <section class="page-header-card">
      <div>
        <p class="section-overline">Dashboard</p>
        <h1 class="page-title">
          {{ isLoggedIn ? "YunaNexus 控制台总览" : "YunaNexus 公共入口" }}
        </h1>
        <p class="page-desc">
          {{
            isLoggedIn
              ? "文件空间、账户状态、权限信息一览。"
              : "登录后可查看个人控制台。"
          }}
        </p>
      </div>
    </section>

    <section class="stats-grid">
      <article v-for="item in dashboardStats" :key="item.label" class="stat-card">
        <span>{{ item.value }}</span>
        <p>{{ item.label }}</p>
        <small v-if="'desc' in item">{{ item.desc }}</small>
      </article>
    </section>

    <section class="panel-grid">
      <article class="panel-card">
        <h2>会话状态</h2>
        <ul>
          <li>
            登录状态：{{ isLoggedIn ? "已登录" : sessionReady ? "未登录" : "初始化中" }}
          </li>
          <li>当前用户：{{ currentUser?.nickname || "访客" }}</li>
          <li>会话过期：{{ tokenExpireAtLabel }}</li>
          <li>资料更新时间：{{ formatDateTime(currentUser?.updateTime) }}</li>
        </ul>
      </article>

      <article class="panel-card">
        <h2>空间概览</h2>
        <template v-if="isLoggedIn && storageSummary">
          <div class="progress-block">
            <div class="progress-head">
              <strong>{{ storageSummary.usagePercent }}%</strong>
              <span>剩余 {{ formatBytes(storageSummary.remainingBytes) }}</span>
            </div>
            <div class="progress-track">
              <span
                class="progress-fill"
                :style="{ width: `${storageSummary.usagePercent}%` }"
              />
            </div>
          </div>
          <ul>
            <li>总空间：{{ formatBytes(storageSummary.totalBytes) }}</li>
            <li>已使用：{{ formatBytes(storageSummary.usedBytes) }}</li>
            <li>文件总数：{{ storageSummary.fileCount }}</li>
          </ul>
        </template>
        <p v-else class="panel-tip">登录后显示当前账户的真实文件空间数据。</p>
      </article>

      <article class="panel-card">
        <h2>文件概览</h2>
        <template v-if="isLoggedIn">
          <ul>
            <li>文件夹：{{ rootFolders.length }} 个</li>
            <li>文件：{{ rootFiles.length }} 个</li>
            <li>回收站：{{ recycleFiles.length }} 个</li>
            <li v-if="rootFiles[0]">
              最近更新：{{ rootFiles[0].originName || rootFiles[0].fileName }}
            </li>
          </ul>
        </template>
        <p v-else class="panel-tip">登录后查看文件信息。</p>
      </article>

      <article class="panel-card">
        <h2>权限快照</h2>
        <template v-if="isLoggedIn">
          <ul>
            <li>角色数量：{{ roleCount }}</li>
            <li>最大角色级别：{{ permissionSnapshot?.maxRoleLevel ?? "暂无" }}</li>
            <li>权限总数：{{ permissionCount }}</li>
            <li>
              角色列表：
              {{ permissionSnapshot?.roles?.join(" / ") || "暂无角色信息" }}
            </li>
          </ul>
        </template>
        <p v-else class="panel-tip">登录后展示当前用户的角色和权限快照。</p>
      </article>

      <article class="panel-card">
        <h2>快捷入口</h2>
        <div class="link-list">
          <NuxtLink v-if="isLoggedIn" to="/files">文件管理</NuxtLink>
          <NuxtLink v-if="isLoggedIn" to="/profile">个人资料</NuxtLink>
          <NuxtLink v-if="!isLoggedIn" to="/login">登录</NuxtLink>
          <NuxtLink v-if="!isLoggedIn" to="/register">注册</NuxtLink>
        </div>
      </article>

      <article class="panel-card">
        <h2>近期动态</h2>
        <p v-if="loading" class="panel-tip">加载中…</p>
        <p v-else-if="dashboardError" class="panel-error">{{ dashboardError }}</p>
        <ul v-else-if="isLoggedIn">
          <li v-if="rootFiles.length > 0">
            最近文件：{{ rootFiles[0].originName || rootFiles[0].fileName }}
          </li>
          <li v-if="rootFolders.length > 0">
            共有 {{ rootFolders.length }} 个文件夹
          </li>
          <li v-if="recycleFiles.length > 0">
            回收站有 {{ recycleFiles.length }} 个待处理文件
          </li>
          <li v-if="rootFiles.length === 0 && rootFolders.length === 0">
            暂未上传任何文件
          </li>
        </ul>
        <p v-else class="panel-tip">登录后显示文件动态。</p>
      </article>
    </section>
  </div>
</template>

<style scoped lang="scss">
.dashboard-page,
.stats-grid,
.panel-grid,
.link-list {
  display: grid;
  gap: 16px;
}

.dashboard-page {
  gap: 24px;
}

.section-overline {
  margin: 0 0 12px;
  color: var(--yn-color-primary);
  font-family: var(--yn-font-mono), monospace;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.1em;
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
  max-width: 760px;
  color: var(--yn-color-text-secondary);
  line-height: 1.8;
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
  font-size: 28px;
  font-weight: 700;
}

.stat-card p,
.panel-card li,
.panel-tip,
.panel-error,
.stat-card small {
  margin: 0;
  color: var(--yn-color-text-secondary);
}

.stat-card small {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
}

.panel-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.panel-card {
  display: grid;
  gap: 16px;
  padding: 24px;
}

.panel-card h2 {
  margin: 0;
  color: var(--yn-color-text-primary);
  font-family: var(--yn-font-mono), monospace;
  font-size: 20px;
}

.panel-card ul {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 12px;
}

.link-list {
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
}

.link-list a {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 0 16px;
  border: 1px solid var(--yn-color-border-medium);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface-raised);
  color: var(--yn-color-text-primary);
  font-weight: 600;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    color 0.2s ease;
}

.link-list a:hover {
  border-color: color-mix(in srgb, var(--yn-color-primary) 28%, transparent);
  color: var(--yn-color-primary);
  box-shadow: 0 0 8px rgba(74, 222, 128, 0.08);
}

.progress-block {
  display: grid;
  gap: 10px;
}

.progress-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.progress-head strong {
  color: var(--yn-color-text-primary);
  font-family: var(--yn-font-mono), monospace;
  font-size: 24px;
}

.progress-head span {
  color: var(--yn-color-text-secondary);
}

.progress-track {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--yn-color-background);
}

.progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(
    90deg,
    var(--yn-color-primary),
    color-mix(in srgb, var(--yn-color-primary) 70%, #ffffff)
  );
}

.panel-error {
  color: var(--yn-color-error);
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
