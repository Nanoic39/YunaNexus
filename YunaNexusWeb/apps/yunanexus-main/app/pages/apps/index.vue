<script setup lang="ts">
import { useMockApps, type MockApp } from "~/composables/useMockApps";
import { useToast } from "~/composables/useToast";

definePageMeta({ layout: "default" });

type AppVO = MockApp;

const { isLoggedIn } = useAuth();
const toast = useToast();
const apps = ref<AppVO[]>([]);
const loading = ref(true);
const error = ref("");

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
    else error.value = res.msg || "获取应用列表失败";
  } catch (e: any) {
    error.value = e?.data?.msg || e?.message || "请求失败";
  } finally {
    loading.value = false;
  }
}

async function toggleApp(app: AppVO, e: Event) {
  e.stopPropagation(); e.preventDefault();
  if (import.meta.dev) {
    const mockApps = useMockApps();
    const t = mockApps.value.find(a => a.uuid === app.uuid);
    if (t) t.status = t.status === 0 ? 1 : 0;
    apps.value = [...mockApps.value];
    toast.success(apps.value.find(a => a.uuid === app.uuid)!.status === 1 ? "已启用" : "已禁用");
    return;
  }
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; msg: string }>(`/api/oauth/client/${app.uuid}/toggle`, { method: "POST" });
    if (res.code === 200) { await loadApps(); toast.success("操作成功"); }
  } catch { /* ignore */ }
}

const statusLabel: Record<number, string> = { 0: "待审核", 1: "已通过", 2: "已拒绝" };
const statusClass: Record<number, string> = { 0: "pending", 1: "approved", 2: "rejected" };
const statusIcon: Record<number, string> = { 0: "lucide:clock", 1: "lucide:check-circle", 2: "lucide:x-circle" };

onMounted(loadApps);
</script>

<template>
  <div class="apps-page">
    <section class="page-header fade-up">
      <div class="page-header-left">
        <div class="page-header-overline">OAuth Applications</div>
        <h1 class="page-header-title">我的应用</h1>
        <p class="page-header-description">管理已接入 OAuth 认证的应用</p>
      </div>
    </section>

    <!-- 创建应用卡片 -->
    <NuxtLink to="/apps/apply" class="app-card app-card-create fade-up">
      <div class="app-card-icon" style="background: var(--color-emphasis-soft); color: var(--color-emphasis)">
        <Icon name="lucide:plus" size="20" />
      </div>
      <div class="app-card-body">
        <div class="app-card-name">创建应用</div>
        <div class="app-card-desc">申请接入 OAuth 认证服务</div>
      </div>
      <Icon name="lucide:arrow-right" size="16" style="color: var(--color-font-assist)" />
    </NuxtLink>

    <!-- 加载 / 错误 / 空 -->
    <div v-if="loading" class="panel-card fade-up">
      <div class="panel-card-body" style="padding: 32px; text-align: center; color: var(--color-font-assist)">加载中…</div>
    </div>
    <div v-else-if="error" class="panel-card fade-up">
      <div class="panel-card-body" style="padding: 32px; text-align: center">
        <p style="color: var(--color-error); margin-bottom: 12px">{{ error }}</p>
        <button class="button button-primary" @click="loadApps">重新加载</button>
      </div>
    </div>
    <div v-else-if="apps.length === 0" class="panel-card fade-up">
      <div class="panel-card-body" style="text-align: center; padding: 48px 24px; color: var(--color-font-assist)">
        <Icon name="lucide:inbox" size="48" style="margin-bottom: 16px; opacity: 0.3" />
        <p style="font-size: 16px; margin-bottom: 8px">还没有创建任何应用</p>
        <p style="font-size: 13px; margin-bottom: 20px">点击上方「创建应用」开始申请 OAuth 接入</p>
      </div>
    </div>

    <!-- 应用列表 -->
    <div v-else class="apps-list fade-up">
      <div v-for="app in apps" :key="app.uuid" class="app-card-wrapper">
        <NuxtLink :to="`/apps/${app.uuid}`" class="app-card">
          <div class="app-card-icon">
            <Icon name="lucide:box" size="18" />
          </div>
          <div class="app-card-body">
            <div class="app-card-name">{{ app.clientName }}</div>
            <div class="app-card-desc">{{ app.description || "暂无说明" }}</div>
          </div>
          <div class="app-card-meta">
            <span class="app-card-date">{{ app.createdAt?.substring(0, 10) }}</span>
            <span :class="['app-status-tag', statusClass[app.auditStatus]]">
              <Icon :name="statusIcon[app.auditStatus]" size="11" />
              {{ statusLabel[app.auditStatus] }}
            </span>
          </div>
        </NuxtLink>

        <!-- 行尾快捷操作 -->
        <div class="app-card-actions">
          <!-- 待审核：无快捷操作 -->
          <span v-if="app.auditStatus === 0" class="app-card-actions-hint">等待审核</span>

          <!-- 已通过：启停开关 -->
          <template v-if="app.auditStatus === 1">
            <span v-if="app.status === 1" class="app-card-state enabled">运行中</span>
            <span v-else class="app-card-state disabled">已禁用</span>
            <button class="button button-small" @click="toggleApp(app, $event)">
              {{ app.status === 1 ? "禁用" : "启用" }}
            </button>
          </template>

          <!-- 已拒绝：重新申请 -->
          <template v-if="app.auditStatus === 2">
            <span class="app-card-state rejected" :title="app.auditOpinion || ''">
              {{ app.auditOpinion ? app.auditOpinion.substring(0, 20) + (app.auditOpinion.length > 20 ? '…' : '') : "未通过" }}
            </span>
            <NuxtLink :to="`/apps/${app.uuid}?edit=1`" class="button button-primary button-small" @click.stop>
              重新申请
            </NuxtLink>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.apps-page {
  max-width: 100%; margin: 0 auto; width: 100%;
}

.app-card-create {
  border-style: dashed; border-color: var(--color-emphasis); opacity: 0.8;
}
.app-card-create:hover { opacity: 1; border-style: solid; }

/* 卡片行 */
.app-card-wrapper {
  display: flex; align-items: center; gap: 12px;
}
.app-card-wrapper .app-card { flex: 1; min-width: 0; }
.app-card-wrapper:hover .app-card { border-color: var(--color-border-heavy); }

.app-card-actions {
  display: flex; align-items: center; gap: 8px; flex-shrink: 0;
}

.app-card-actions-hint {
  font-size: 12px; color: var(--color-font-assist);
}

.app-card-state {
  font-size: 11px; padding: 2px 8px; border-radius: var(--radius-sm); font-weight: 500;
}
.app-card-state.enabled { background: var(--color-success-soft); color: var(--color-success); }
.app-card-state.disabled { background: var(--color-primary-background); color: var(--color-font-assist); }
.app-card-state.rejected {
  background: var(--color-error-soft); color: var(--color-error);
  max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.app-card-date { font-size: 11px; color: var(--color-font-assist); }

@media (max-width: 640px) {
  .app-card-wrapper { flex-direction: column; gap: 8px; }
  .app-card-actions { align-self: flex-end; }
}
</style>
