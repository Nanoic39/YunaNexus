<script setup lang="ts">
definePageMeta({ layout: "default" });

const route = useRoute();
const uuid = route.params.uuid as string;

interface AppDetail {
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
  updatedAt: string;
}

const app = ref<AppDetail | null>(null);
const loading = ref(true);
const error = ref("");

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; data: AppDetail; msg: string }>(`/api/oauth/client/${uuid}`);
    if (res.code === 200) app.value = res.data;
    else error.value = res.msg || "获取详情失败";
  } catch (e: any) {
    error.value = e?.data?.msg || e?.message || "请求失败";
  } finally {
    loading.value = false;
  }
}

const statusLabel: Record<number, string> = { 0: "待审核", 1: "已通过", 2: "已拒绝" };
const typeLabel: Record<number, string> = { 1: "官方", 2: "第三方" };

onMounted(load);
</script>

<template>
  <div class="apps-page">
    <section class="page-header fade-up">
      <div class="page-header-left">
        <div class="page-header-overline">Application Detail</div>
        <h1 class="page-header-title">{{ app?.clientName || "应用详情" }}</h1>
      </div>
      <NuxtLink to="/apps" class="button">返回列表</NuxtLink>
    </section>

    <div v-if="loading" class="apps-empty">加载中…</div>
    <div v-else-if="error" class="apps-empty">
      <p>{{ error }}</p>
      <button class="button button-primary" @click="load">重新加载</button>
    </div>

    <template v-else-if="app">
      <div class="panel-card fade-up">
        <div class="panel-card-header">基本信息</div>
        <div class="panel-card-body profile-info-grid">
          <div class="profile-info-item">
            <span class="profile-info-label">应用名称</span>
            <strong>{{ app.clientName }}</strong>
          </div>
          <div class="profile-info-item">
            <span class="profile-info-label">应用类型</span>
            <strong>{{ typeLabel[app.clientType] }}</strong>
          </div>
          <div class="profile-info-item">
            <span class="profile-info-label">审核状态</span>
            <strong>{{ statusLabel[app.auditStatus] }}</strong>
          </div>
          <div class="profile-info-item">
            <span class="profile-info-label">启用状态</span>
            <strong :style="{ color: app.status === 0 ? 'var(--color-font-assist)' : 'inherit' }">
              {{ app.status === 0 ? "已禁用" : "已启用" }}
            </strong>
          </div>
          <div class="profile-info-item">
            <span class="profile-info-label">授权模式</span>
            <strong>{{ app.grantTypes }}</strong>
          </div>
          <div class="profile-info-item">
            <span class="profile-info-label">授权范围</span>
            <strong>{{ app.scope }}</strong>
          </div>
          <div class="profile-info-item">
            <span class="profile-info-label">回调地址</span>
            <strong style="word-break: break-all">{{ app.redirectUri }}</strong>
          </div>
          <div class="profile-info-item">
            <span class="profile-info-label">创建时间</span>
            <strong>{{ app.createdAt }}</strong>
          </div>
          <div class="profile-info-item profile-info-item-wide" v-if="app.description">
            <span class="profile-info-label">申请说明</span>
            <strong>{{ app.description }}</strong>
          </div>
          <div class="profile-info-item profile-info-item-wide" v-if="app.auditOpinion">
            <span class="profile-info-label">审核意见</span>
            <strong>{{ app.auditOpinion }}</strong>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
