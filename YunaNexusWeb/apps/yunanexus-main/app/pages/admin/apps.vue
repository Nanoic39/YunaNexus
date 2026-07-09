<script setup lang="ts">
import { useMockApps, type MockApp } from "~/composables/useMockApps";
import { useToast } from "~/composables/useToast";

definePageMeta({ layout: "default" });

const toast = useToast();
type AppVO = MockApp;

const apps = ref<AppVO[]>([]);
const loading = ref(true);
const error = ref("");
const filter = ref<number | null>(null);

const filtered = computed(() => {
  if (filter.value === null) return apps.value;
  return apps.value.filter((a) => a.auditStatus === filter.value);
});

// ---- 当前管理员名称（开发模式用 mock，生产从 auth 获取） ----
const currentAdmin = computed(() => "admin");

// ---- 审核详情面板 ----
const selectedApp = ref<AppVO | null>(null);
const auditOpinion = ref("");
const auditError = ref("");
const auditing = ref(false);

async function loadApps() {
  loading.value = true;
  error.value = "";
  if (import.meta.dev) {
    apps.value = [...useMockApps().value];
    loading.value = false;
    return;
  }
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; data: AppVO[]; msg: string }>("/api/oauth/client/list");
    if (res.code === 200) apps.value = res.data || [];
    else error.value = res.msg || "获取列表失败";
  } catch (e: any) {
    error.value = e?.data?.msg || e?.message || "请求失败";
  } finally {
    loading.value = false;
  }
}

// ---- 审核锁定 ----
function openDetail(app: AppVO) {
  // 检查是否已被其他管理员锁定
  if (app.reviewingBy && app.reviewingBy !== currentAdmin.value) {
    toast.error(`该应用正在被「${app.reviewingBy}」审核，请稍后再试`);
    return;
  }
  // 锁定应用
  if (import.meta.dev) {
    const mockApps = useMockApps();
    const target = mockApps.value.find((a) => a.uuid === app.uuid);
    if (target) target.reviewingBy = currentAdmin.value;
    apps.value = [...mockApps.value];
  }
  auditOpinion.value = "";
  auditError.value = "";
  selectedApp.value = apps.value.find((a) => a.uuid === app.uuid) || app;
}

function closeDetail() {
  // 释放锁定
  if (selectedApp.value && import.meta.dev) {
    const mockApps = useMockApps();
    const target = mockApps.value.find((a) => a.uuid === selectedApp.value!.uuid);
    if (target && target.reviewingBy === currentAdmin.value) target.reviewingBy = null;
    apps.value = [...mockApps.value];
  }
  selectedApp.value = null;
}

async function doAudit(auditStatus: number) {
  if (!selectedApp.value) return;
  auditing.value = true;
  auditError.value = "";

  if (import.meta.dev) {
    const mockApps = useMockApps();
    const target = mockApps.value.find((a) => a.uuid === selectedApp.value!.uuid);
    if (target) {
      target.auditStatus = auditStatus;
      target.auditOpinion = auditOpinion.value || "";
      target.status = auditStatus === 1 ? 1 : 0;
      target.reviewingBy = null;
    }
    toast.success(auditStatus === 1 ? "审核通过" : "已拒绝该申请");
    selectedApp.value = null;
    apps.value = [...mockApps.value];
    auditing.value = false;
    return;
  }
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; msg: string }>(
      `/api/oauth/client/${selectedApp.value.uuid}/audit?auditStatus=${auditStatus}&auditOpinion=${encodeURIComponent(auditOpinion.value)}`,
      { method: "POST" },
    );
    if (res.code === 200) {
      toast.success(auditStatus === 1 ? "审核通过" : "已拒绝该申请");
      selectedApp.value = null;
      await loadApps();
    } else {
      auditError.value = res.msg || "操作失败";
    }
  } catch (e: any) {
    auditError.value = e?.data?.msg || e?.message || "请求失败";
  } finally {
    auditing.value = false;
  }
}

async function toggleApp(app: AppVO) {
  if (import.meta.dev) {
    const mockApps = useMockApps();
    const target = mockApps.value.find((a) => a.uuid === app.uuid);
    if (target) target.status = target.status === 0 ? 1 : 0;
    apps.value = [...mockApps.value];
    return;
  }
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; msg: string }>(`/api/oauth/client/${app.uuid}/toggle`, { method: "POST" });
    if (res.code === 200) await loadApps();
  } catch { /* ignore */ }
}

// ---- 实时轮询 ----
const pollInterval = ref<ReturnType<typeof setInterval> | null>(null);

onMounted(() => {
  loadApps();
  // 每 15 秒自动刷新列表
  pollInterval.value = setInterval(() => {
    if (import.meta.dev) {
      apps.value = [...useMockApps().value];
    }
  }, 15000);
});

onBeforeUnmount(() => {
  if (pollInterval.value) clearInterval(pollInterval.value);
  // 离开页面时释放所有锁定
  if (import.meta.dev) {
    const mockApps = useMockApps();
    for (const app of mockApps.value) {
      if (app.reviewingBy === currentAdmin.value) app.reviewingBy = null;
    }
  }
});

// ---- 工具 ----
const statusLabel: Record<number, string> = { 0: "待审核", 1: "已通过", 2: "已拒绝" };
const statusClass: Record<number, string> = { 0: "pending", 1: "approved", 2: "rejected" };
const typeLabel: Record<number, string> = { 1: "官方", 2: "第三方" };
const filterLabels = ["待审核", "已通过", "已拒绝"];

function parseTags(str: string): string[] {
  return str.split(",").filter(Boolean).map((s) => s.trim());
}

function grantTypeLabel(gt: string): string {
  const map: Record<string, string> = {
    authorization_code: "Authorization Code", refresh_token: "Refresh Token", password: "Password",
  };
  return map[gt] || gt;
}

onUnmounted(() => {
  if (pollInterval.value) clearInterval(pollInterval.value);
});
</script>

<template>
  <div class="apps-page">
    <section class="page-header fade-up">
      <div class="page-header-left">
        <div class="page-header-overline">Admin Panel</div>
        <h1 class="page-header-title">应用审核</h1>
      </div>
    </section>

    <!-- 筛选栏 -->
    <div class="admin-filters fade-up">
      <div :class="['filter-chip', { active: filter === null }]" @click="filter = null">
        全部 ({{ apps.length }})
      </div>
      <div
        v-for="(label, i) in filterLabels"
        :key="i"
        :class="['filter-chip', { active: filter === i }]"
        @click="filter = i"
      >
        {{ label }} ({{ apps.filter((a) => a.auditStatus === i).length }})
      </div>
    </div>

    <!-- 加载 / 空态 -->
    <div v-if="loading" class="apps-empty">加载中…</div>
    <div v-else-if="error" class="apps-empty">
      <p>{{ error }}</p>
      <button class="button button-primary" @click="loadApps">重新加载</button>
    </div>
    <div v-else-if="filtered.length === 0" class="apps-empty">
      <p>暂无{{ filter !== null ? filterLabels[filter] : "" }}申请</p>
    </div>

    <!-- 列表与详情双栏 -->
    <div v-else class="admin-layout fade-up">
      <!-- 左栏：应用列表 -->
      <div class="admin-list-col">
        <div v-for="app in filtered" :key="app.uuid" class="admin-app-card" :class="{ selected: selectedApp?.uuid === app.uuid }">
          <div class="admin-app-main" @click="app.auditStatus === 0 ? openDetail(app) : void 0">
            <div class="admin-app-icon">
              <Icon name="lucide:box" size="18" />
            </div>
            <div class="admin-app-info">
              <div class="admin-app-name">{{ app.clientName }}</div>
              <div class="admin-app-meta">
                <span>{{ app.applicantName || "未知用户" }}</span>
                <span>&middot;</span>
                <span>{{ app.createdAt }}</span>
              </div>
            </div>
          </div>
          <div class="admin-app-actions">
            <span :class="['app-status-tag', statusClass[app.auditStatus]]">
              {{ statusLabel[app.auditStatus] }}
            </span>
            <template v-if="app.auditStatus === 0">
              <span v-if="app.reviewingBy && app.reviewingBy !== currentAdmin" class="app-lock-tag" :title="`${app.reviewingBy} 正在审核`">
                <Icon name="lucide:lock" size="11" /> {{ app.reviewingBy }}
              </span>
              <span v-else-if="app.reviewingBy === currentAdmin" class="app-lock-tag self" title="你正在审核">
                <Icon name="lucide:eye" size="11" /> 审核中
              </span>
              <button
                v-else
                class="button button-primary button-small"
                @click="openDetail(app)"
              >
                审核
              </button>
            </template>
            <button
              v-else
              class="button button-small"
              @click="toggleApp(app)"
            >
              {{ app.status === 0 ? "启用" : "禁用" }}
            </button>
          </div>
        </div>
      </div>

      <!-- 右栏：审核详情 -->
      <div v-if="selectedApp" class="admin-detail-col">
        <div class="admin-detail-card panel-card">
          <div class="panel-card-header">
            <Icon name="lucide:file-search" size="18" class="panel-card-header-icon" />
            <div class="panel-card-header-text">
              <h3>申请详情</h3>
              <p class="panel-card-header-sub">{{ selectedApp.clientName }}</p>
            </div>
          </div>
          <div class="panel-card-body" style="gap: 18px">
            <!-- 基本信息 -->
            <fieldset class="detail-fieldset">
              <legend><Icon name="lucide:info" size="13" /> 基本信息</legend>
              <div class="detail-grid">
                <div class="detail-item">
                  <span class="detail-label">应用名称</span>
                  <span class="detail-value">{{ selectedApp.clientName }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">应用类型</span>
                  <span class="detail-value">{{ typeLabel[selectedApp.clientType] || "第三方" }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">提交者</span>
                  <span class="detail-value">{{ selectedApp.applicantName || "未知" }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">提交时间</span>
                  <span class="detail-value">{{ selectedApp.createdAt }}</span>
                </div>
                <div class="detail-item" style="grid-column: 1 / -1">
                  <span class="detail-label">用途说明</span>
                  <span class="detail-value">{{ selectedApp.description || "无" }}</span>
                </div>
              </div>
            </fieldset>

            <!-- OAuth 配置 -->
            <fieldset class="detail-fieldset">
              <legend><Icon name="lucide:shield" size="13" /> OAuth 配置</legend>
              <div class="detail-grid">
                <div class="detail-item" style="grid-column: 1 / -1">
                  <span class="detail-label">回调地址</span>
                  <code class="detail-code-block">{{ selectedApp.redirectUri }}</code>
                </div>
                <div class="detail-item">
                  <span class="detail-label">授权模式</span>
                  <div class="detail-tags">
                    <span v-for="gt in parseTags(selectedApp.grantTypes)" :key="gt" class="detail-tag">
                      {{ grantTypeLabel(gt) }}
                    </span>
                  </div>
                </div>
                <div class="detail-item">
                  <span class="detail-label">授权范围</span>
                  <div class="detail-tags">
                    <code v-for="s in parseTags(selectedApp.scope)" :key="s" class="detail-scope-tag">{{ s }}</code>
                  </div>
                </div>
                <div class="detail-item">
                  <span class="detail-label">应用 UUID</span>
                  <code class="detail-code">{{ selectedApp.uuid }}</code>
                </div>
              </div>
            </fieldset>

            <!-- 审核操作 -->
            <fieldset class="detail-fieldset">
              <legend><Icon name="lucide:clipboard-check" size="13" /> 审核决定</legend>
              <div v-if="auditError" class="profile-edit-error" style="margin-bottom: 8px">{{ auditError }}</div>
              <div class="detail-field">
                <span class="detail-label">审核意见</span>
                <textarea v-model="auditOpinion" class="apply-textarea" rows="3" placeholder="可选，拒绝时建议附上原因…" />
              </div>
              <div class="audit-actions-row">
                <button class="button" @click="closeDetail">返回列表</button>
                <button class="button audit-reject-btn" :disabled="auditing" @click="doAudit(2)">
                  拒绝申请
                </button>
                <button class="button button-primary" :disabled="auditing" @click="doAudit(1)">
                  {{ auditing ? "处理中…" : "通过审核" }}
                </button>
              </div>
            </fieldset>
          </div>
        </div>
      </div>

      <!-- 空详情占位 -->
      <div v-else class="admin-detail-col admin-detail-placeholder">
        <div class="admin-detail-empty">
          <Icon name="lucide:arrow-left" size="24" style="opacity: 0.2" />
          <span>选择左侧应用查看详情并进行审核</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ---- 双栏布局 ---- */
.admin-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  align-items: start;
}

.admin-list-col {
  display: grid;
  gap: 8px;
}

/* ---- 应用卡片 ---- */
.admin-app-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  gap: 12px;
  transition: border-color 0.12s;
}

.admin-app-card:hover {
  border-color: var(--color-border-heavy);
}

.admin-app-card.selected {
  border-color: var(--color-emphasis);
  background: var(--color-emphasis-soft);
}

.admin-app-main {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
  border: none;
  background: none;
  padding: 0;
  cursor: default;
}

.admin-app-icon {
  width: 38px;
  height: 38px;
  border-radius: var(--radius-md);
  background: var(--color-primary-background);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--color-font-assist);
}

.admin-app-info {
  min-width: 0;
}

.admin-app-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-font);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-app-meta {
  font-size: 12px;
  color: var(--color-font-assist);
  display: flex;
  gap: 4px;
  margin-top: 2px;
}

.admin-app-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* ---- 锁定标签 ---- */
.app-lock-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--color-font-assist);
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-background);
  border: 1px solid var(--color-border);
}

.app-lock-tag.self {
  color: var(--color-emphasis);
  background: var(--color-emphasis-soft);
  border-color: rgba(22, 163, 74, 0.15);
}

/* ---- 详情面板 ---- */
.admin-detail-col {
  position: sticky;
  top: 20px;
}

.admin-detail-card {
  padding: 0;
}

.admin-detail-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}

.admin-detail-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--color-font-assist);
}

/* ---- 字段集 ---- */
.detail-fieldset {
  border: none;
  padding: 0;
  margin: 0;
}

.detail-fieldset legend {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-font-secondary);
  padding-bottom: 10px;
  width: 100%;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 10px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.detail-item {
  display: grid;
  gap: 2px;
}

.detail-label {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-font-assist);
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.detail-value {
  font-size: 13px;
  color: var(--color-font);
}

.detail-code {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-font-secondary);
  background: var(--color-primary-background);
  padding: 1px 4px;
  border-radius: 3px;
}

.detail-code-block {
  display: block;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-font-secondary);
  background: var(--color-primary-background);
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  word-break: break-all;
  margin-top: 2px;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.detail-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  background: var(--color-emphasis-soft);
  color: var(--color-emphasis);
  border: 1px solid rgba(22, 163, 74, 0.12);
}

.detail-scope-tag {
  font-family: var(--font-mono);
  font-size: 10px;
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-background);
  color: var(--color-font-secondary);
  border: 1px solid var(--color-border);
}

.detail-field {
  display: grid;
  gap: 4px;
  margin-top: 6px;
}

.detail-field .apply-textarea {
  width: 100%;
  min-height: 72px;
  padding: 8px 12px;
  font-size: 13px;
  font-family: inherit;
  color: var(--color-font);
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  outline: none;
  resize: vertical;
  box-sizing: border-box;
}

.detail-field .apply-textarea:focus {
  border-color: var(--color-emphasis);
  box-shadow: 0 0 0 2px var(--color-emphasis-soft);
}

/* ---- 审核操作栏 ---- */
.audit-actions-row {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 8px;
}

.audit-reject-btn {
  color: var(--color-error) !important;
}

/* 响应式 */
@media (max-width: 900px) {
  .admin-layout {
    grid-template-columns: 1fr;
  }

  .admin-detail-col {
    position: static;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
