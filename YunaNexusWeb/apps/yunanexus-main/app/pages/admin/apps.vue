<script setup lang="ts">
definePageMeta({ layout: "default" });

interface AppVO {
  uuid: string;
  clientName: string;
  clientType: number;
  grantTypes: string;
  scope: string;
  redirectUri: string;
  description: string;
  auditStatus: number;
  auditOpinion: string;
  applicantGlobalId: string;
  status: number;
  createdAt: string;
}

const apps = ref<AppVO[]>([]);
const loading = ref(true);
const error = ref("");
const filter = ref<number | null>(null); // null = 全部, 0 = 待审, 1 = 已通过, 2 = 已拒绝

const filtered = computed(() => {
  if (filter.value === null) return apps.value;
  return apps.value.filter((a) => a.auditStatus === filter.value);
});

async function loadApps() {
  loading.value = true;
  error.value = "";
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

const auditing = ref(false);
const auditTarget = ref<AppVO | null>(null);
const auditForm = ref({ opinion: "" });
const auditError = ref("");

function openAudit(app: AppVO) {
  auditTarget.value = app;
  auditForm.value.opinion = "";
  auditError.value = "";
}

function closeAudit() {
  auditTarget.value = null;
}

async function doAudit(auditStatus: number) {
  if (!auditTarget.value) return;
  auditing.value = true;
  auditError.value = "";
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; msg: string }>(
      `/api/oauth/client/${auditTarget.value.uuid}/audit?auditStatus=${auditStatus}&auditOpinion=${encodeURIComponent(auditForm.value.opinion)}`,
      { method: "POST" },
    );
    if (res.code === 200) {
      closeAudit();
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
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; msg: string }>(`/api/oauth/client/${app.uuid}/toggle`, { method: "POST" });
    if (res.code === 200) await loadApps();
  } catch { /* ignore */ }
}

const statusLabel: Record<number, string> = { 0: "待审核", 1: "已通过", 2: "已拒绝" };
const statusClass: Record<number, string> = { 0: "pending", 1: "approved", 2: "rejected" };
const filterLabels = ["待审核", "已通过", "已拒绝"];

onMounted(loadApps);
</script>

<template>
  <div class="apps-page">
    <section class="page-header fade-up">
      <div class="page-header-left">
        <div class="page-header-overline">Admin Panel</div>
        <h1 class="page-header-title">应用审核</h1>
      </div>
    </section>

    <div class="admin-filters fade-up">
      <div
        :class="['filter-chip', { active: filter === null }]"
        @click="filter = null"
      >
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

    <div v-if="loading" class="apps-empty">加载中…</div>
    <div v-else-if="error" class="apps-empty">
      <p>{{ error }}</p>
      <button class="button button-primary" @click="loadApps">重新加载</button>
    </div>
    <div v-else-if="filtered.length === 0" class="apps-empty">
      <p>暂无{{ filter !== null ? filterLabels[filter] : "" }}申请</p>
    </div>

    <div v-else class="apps-list fade-up">
      <div v-for="app in filtered" :key="app.uuid" class="app-card" style="cursor: default">
        <div class="app-card-icon">
          <Icon name="lucide:box" size="18" />
        </div>
        <div class="app-card-body">
          <div class="app-card-name">{{ app.clientName }}</div>
          <div class="app-card-desc">{{ app.description || "暂无说明" }}</div>
        </div>
        <div class="app-card-meta">
          <span :class="['app-status-tag', statusClass[app.auditStatus]]">
            {{ statusLabel[app.auditStatus] }}
          </span>
          <button
            v-if="app.auditStatus === 0"
            class="button button-primary button-small"
            @click="openAudit(app)"
          >
            审核
          </button>
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

    <!-- 审核弹窗 -->
    <div v-if="auditTarget" class="modal-overlay" @click.self="closeAudit">
      <div class="modal-card audit-modal">
        <h4>审核申请 - {{ auditTarget.clientName }}</h4>
        <div style="font-size: 13px; color: var(--color-font-secondary); margin-bottom: 12px">
          {{ auditTarget.description || "无申请说明" }}
        </div>
        <div v-if="auditError" class="profile-edit-error" style="margin-bottom: 12px">{{ auditError }}</div>
        <div class="auth-field" style="margin-bottom: 16px">
          <span>审核意见（可选）</span>
          <textarea v-model="auditForm.opinion" rows="3" placeholder="输入审核意见..." />
        </div>
        <div class="audit-actions">
          <button class="button" @click="closeAudit">取消</button>
          <button class="button" style="color: #b31f1f" :disabled="auditing" @click="doAudit(2)">
            拒绝
          </button>
          <button class="button button-primary" :disabled="auditing" @click="doAudit(1)">
            {{ auditing ? "处理中…" : "通过" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
