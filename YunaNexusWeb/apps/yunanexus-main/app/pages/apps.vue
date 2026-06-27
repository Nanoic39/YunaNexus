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
  status: number;
  createdAt: string;
}

const apps = ref<AppVO[]>([]);
const loading = ref(true);
const error = ref("");

async function loadApps() {
  loading.value = true;
  error.value = "";
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

const statusLabel: Record<number, string> = { 0: "待审核", 1: "已通过", 2: "已拒绝" };
const statusClass: Record<number, string> = { 0: "pending", 1: "approved", 2: "rejected" };

onMounted(loadApps);
</script>

<template>
  <div class="apps-page">
    <section class="page-header fade-up">
      <div class="page-header-left">
        <div class="page-header-overline">Applications</div>
        <h1 class="page-header-title">应用管理</h1>
      </div>
      <NuxtLink to="/apps/apply" class="button button-primary">创建应用</NuxtLink>
    </section>

    <div v-if="loading" class="apps-empty">加载中…</div>

    <div v-else-if="error" class="apps-empty">
      <p>{{ error }}</p>
      <button class="button button-primary" @click="loadApps">重新加载</button>
    </div>

    <div v-else-if="apps.length === 0" class="apps-empty">
      <p>还没有创建任何应用</p>
      <NuxtLink to="/apps/apply" class="button button-primary">创建第一个应用</NuxtLink>
    </div>

    <div v-else class="apps-list fade-up">
      <NuxtLink
        v-for="app in apps"
        :key="app.uuid"
        :to="`/apps/${app.uuid}`"
        class="app-card"
      >
        <div class="app-card-icon">
          <Icon name="lucide:box" size="18" />
        </div>
        <div class="app-card-body">
          <div class="app-card-name">{{ app.clientName }}</div>
          <div class="app-card-desc">
            {{ app.description || "暂无说明" }}
          </div>
        </div>
        <div class="app-card-meta">
          <span v-if="app.status === 0" style="color: var(--color-font-assist)">已禁用</span>
          <span :class="['app-status-tag', statusClass[app.auditStatus]]">
            {{ statusLabel[app.auditStatus] }}
          </span>
        </div>
      </NuxtLink>
    </div>
  </div>
</template>
