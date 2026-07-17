<script setup lang="ts">
import { useToast } from "~/composables/useToast";

definePageMeta({ layout: "default" });

const toast = useToast();
const { isLoggedIn } = useAuth();
const route = useRoute();

// ==================== 参数 ====================
const clientId = ref((route.query.client_id as string) || "");
const redirectUri = ref((route.query.redirect_uri as string) || "");
const responseType = ref((route.query.response_type as string) || "code");
const scope = ref((route.query.scope as string) || "read");
const state = ref((route.query.state as string) || "");
const codeChallenge = ref((route.query.code_challenge as string) || "");
const codeChallengeMethod = ref((route.query.code_challenge_method as string) || "S256");

// ==================== 状态 ====================
const loading = ref(true);
const authorizing = ref(false);
const error = ref("");
const appInfo = ref<{ clientName: string; description: string } | null>(null);

// ==================== 检查登录态 ====================
onMounted(async () => {
  if (!isLoggedIn.value) {
    // 未登录 → 保存当前页地址，跳转登录
    const currentUrl = route.fullPath;
    navigateTo(`/login?redirect=${encodeURIComponent(currentUrl)}`);
    return;
  }

  // 验证参数
  if (!clientId.value || !redirectUri.value) {
    error.value = "缺少必要参数 (client_id / redirect_uri)";
    loading.value = false;
    return;
  }

  // 尝试获取应用信息（可选，失败不影响流程）
  try {
    const { $fetch: _f } = useNuxtApp();
    const res = await (_f as typeof $fetch)<{ code: number; data: any }>(
      `/api/oauth/client/${clientId.value}`,
    );
    if (res.code === 200) {
      appInfo.value = res.data;
    }
  } catch {
    // 获取应用信息失败不阻塞流程
  }

  loading.value = false;
});

// ==================== 确认授权 ====================
async function confirmAuthorize() {
  authorizing.value = true;
  error.value = "";

  try {
    // 显式携带 Token（不依赖 auth-fetch 插件的隐式注入）
    let token = "";
    try {
      const raw = localStorage.getItem("user-auth-info");
      if (raw) token = JSON.parse(raw).accessToken || "";
    } catch {}
    if (!token) {
      error.value = "未登录，请先登录后再授权";
      authorizing.value = false;
      return;
    }

    const body: Record<string, string> = {
      clientId: clientId.value,
      redirectUri: redirectUri.value,
      responseType: responseType.value,
      scope: scope.value,
    };
    if (state.value) body.state = state.value;
    if (codeChallenge.value) {
      body.codeChallenge = codeChallenge.value;
      body.codeChallengeMethod = codeChallengeMethod.value;
    }

    const res = await $fetch<{ code: number; data: any; msg: string }>(
      "/api/oauth/authorize",
      {
        method: "POST",
        body,
        headers: { Authorization: `Bearer ${token}` },
      },
    );

    if (res.code === 200) {
      const data = res.data;
      let location = data.redirectUri || redirectUri.value;
      location += (location.includes("?") ? "&" : "?") + "code=" + data.code;
      if (data.state) {
        location += "&state=" + encodeURIComponent(data.state);
      }
      // 跳转到第三方回调（外部 URL，需用 location.href）
      window.location.href = location;
    } else {
      error.value = res.msg || "授权失败";
    }
  } catch (e: any) {
    error.value = e?.data?.msg || e?.message || "请求失败";
  } finally {
    authorizing.value = false;
  }
}

function cancelAuthorize() {
  toast.info("已取消授权");
  // 回到首页
  navigateTo("/");
}
</script>

<template>
  <div class="apps-page oauth-authorize-page fade-up">
    <!-- 加载中 -->
    <div v-if="loading" class="panel-card" style="text-align: center; padding: 64px 24px;">
      <Icon name="lucide:loader-2" size="32" style="animation: spin 1s linear infinite; opacity: 0.4;" />
      <p style="margin-top: 16px; color: var(--color-font-assist);">加载中…</p>
    </div>

    <!-- 参数错误 -->
    <div v-else-if="error && !appInfo" class="panel-card" style="text-align: center; padding: 48px 24px;">
      <Icon name="lucide:alert-circle" size="48" style="opacity: 0.3; margin-bottom: 16px; color: var(--color-error);" />
      <p style="font-size: 16px; font-weight: 600; margin-bottom: 8px;">授权请求无效</p>
      <p style="color: var(--color-font-assist); font-size: 13px;">{{ error }}</p>
    </div>

    <!-- 授权确认 -->
    <div v-else class="oauth-card panel-card" style="max-width: 460px; margin: 0 auto;">
      <div class="oauth-card-header">
        <div class="oauth-card-icon">
          <Icon name="lucide:shield-check" size="32" style="color: var(--color-emphasis);" />
        </div>
        <h2 style="font-size: 20px; font-weight: 600; margin: 0;">授权请求</h2>
        <p style="color: var(--color-font-assist); font-size: 13px; margin: 8px 0 0;">
          {{ appInfo?.clientName || clientId }} 请求访问你的账户
        </p>
      </div>

      <div class="oauth-card-body">
        <div class="oauth-info-row">
          <span class="oauth-info-label">应用</span>
          <span class="oauth-info-value">{{ appInfo?.clientName || clientId }}</span>
        </div>
        <div v-if="appInfo?.description" class="oauth-info-row">
          <span class="oauth-info-label">简介</span>
          <span class="oauth-info-value">{{ appInfo.description }}</span>
        </div>
        <div class="oauth-info-row">
          <span class="oauth-info-label">授权范围</span>
          <span class="oauth-info-value">{{ scope }}</span>
        </div>
        <div class="oauth-info-row">
          <span class="oauth-info-label">回调地址</span>
          <span class="oauth-info-value oauth-uri">{{ redirectUri }}</span>
        </div>
      </div>

      <div v-if="error" class="oauth-error">
        <Icon name="lucide:alert-circle" size="14" />
        <span>{{ error }}</span>
      </div>

      <div class="oauth-card-footer">
        <button class="button" @click="cancelAuthorize" :disabled="authorizing">拒绝</button>
        <button class="button button-primary" @click="confirmAuthorize" :disabled="authorizing">
          <Icon v-if="authorizing" name="lucide:loader-2" size="14" style="animation: spin 1s linear infinite;" />
          {{ authorizing ? "处理中…" : "授权" }}
        </button>
      </div>

      <p class="oauth-disclaimer">
        授权后，该应用将能访问你账户中授权范围内的资源。你可以随时在「应用管理」中撤销授权。
      </p>
    </div>
  </div>
</template>

<style scoped>
.oauth-authorize-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}

.oauth-card {
  padding: 0;
  overflow: hidden;
}

.oauth-card-header {
  text-align: center;
  padding: 28px 28px 20px;
  border-bottom: 1px solid var(--color-separator);
}

.oauth-card-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--color-emphasis-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 14px;
}

.oauth-card-body {
  padding: 20px 28px;
  display: grid;
  gap: 10px;
}

.oauth-info-row {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 12px;
  align-items: baseline;
}

.oauth-info-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-font-assist);
}

.oauth-info-value {
  font-size: 13px;
  color: var(--color-font);
  word-break: break-all;
}

.oauth-uri {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-font-secondary);
}

.oauth-error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 28px;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  background: var(--color-error-soft);
  color: var(--color-error);
  font-size: 13px;
}

.oauth-card-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 28px 20px;
}

.oauth-disclaimer {
  padding: 12px 28px 18px;
  font-size: 11px;
  color: var(--color-font-assist);
  text-align: center;
  line-height: 1.5;
  border-top: 1px solid var(--color-separator);
}
</style>
