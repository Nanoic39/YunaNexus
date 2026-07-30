<script setup lang="ts">
import { useToast } from "~/composables/useToast";

definePageMeta({ layout: "default" });

const toast = useToast();
const { isLoggedIn, getAccessToken } = useAuth();
const route = useRoute();

// ==================== 参数 ====================
// 新流程：服务端已验证的授权会话 ID
const sessionId = ref((route.query.session as string) || "");

// 旧流程兼容：直接接收原始 OAuth 参数（将在后续版本移除）
const legacyClientId = ref((route.query.client_id as string) || "");
const legacyRedirectUri = ref((route.query.redirect_uri as string) || "");
const legacyScope = ref((route.query.scope as string) || "read");
const legacyState = ref((route.query.state as string) || "");
const legacyCodeChallenge = ref((route.query.code_challenge as string) || "");
const legacyCodeChallengeMethod = ref((route.query.code_challenge_method as string) || "S256");

const isLegacyMode = computed(() => !sessionId.value && !!legacyClientId.value);

// ==================== 状态 ====================
const loading = ref(true);
const authorizing = ref(false);
const error = ref("");
const appInfo = ref<{
  clientName: string;
  description: string;
  scope: string;
  redirectUri: string;
} | null>(null);

// ==================== 初始化 ====================
onMounted(async () => {
  if (!isLoggedIn.value) {
    const currentUrl = route.fullPath;
    navigateTo(`/login?redirect=${encodeURIComponent(currentUrl)}`);
    return;
  }

  if (isLegacyMode.value) {
    // 旧流程：兼容模式
    await initLegacy();
  } else if (sessionId.value) {
    // 新流程：通过 session ID 获取授权详情
    await initSession();
  } else {
    error.value = "缺少必要参数";
    loading.value = false;
  }
});

/** 新流程：从后端获取授权会话详情 */
async function initSession() {
  try {
    const { $fetch: _f } = useNuxtApp();
    const res = await (_f as typeof $fetch)<{
      code: number;
      data: {
        clientName: string;
        description: string;
        scope: string;
        redirectUri: string;
        isLoggedIn: boolean;
      };
      msg: string;
    }>(`/api/oauth2/authorize/${sessionId.value}`);

    if (res.code === 200) {
      appInfo.value = res.data;
      if (!res.data.isLoggedIn) {
        navigateTo(`/login?redirect=${encodeURIComponent(route.fullPath)}`);
        return;
      }
    } else {
      error.value = res.msg || "授权会话已过期或不存在";
    }
  } catch (e: any) {
    error.value = e?.data?.msg || e?.message || "获取授权信息失败";
  } finally {
    loading.value = false;
  }
}

/** 旧流程兼容：直接使用 URL 参数 */
async function initLegacy() {
  if (!legacyClientId.value || !legacyRedirectUri.value) {
    error.value = "缺少必要参数 (client_id / redirect_uri)";
    loading.value = false;
    return;
  }

  try {
    const { $fetch: _f } = useNuxtApp();
    const res = await (_f as typeof $fetch)<{ code: number; data: any }>(
      `/api/oauth/client/${legacyClientId.value}`,
    );
    if (res.code === 200) {
      appInfo.value = {
        clientName: res.data.clientName || legacyClientId.value,
        description: res.data.description || "",
        scope: legacyScope.value,
        redirectUri: legacyRedirectUri.value,
      };
    } else {
      appInfo.value = {
        clientName: legacyClientId.value,
        description: "",
        scope: legacyScope.value,
        redirectUri: legacyRedirectUri.value,
      };
    }
  } catch {
    appInfo.value = {
      clientName: legacyClientId.value,
      description: "",
      scope: legacyScope.value,
      redirectUri: legacyRedirectUri.value,
    };
  }

  loading.value = false;
}

// ==================== 确认授权 ====================
async function confirmAuthorize() {
  authorizing.value = true;
  error.value = "";

  try {
    const token = await getAccessToken();
    if (!token) {
      error.value = "未登录，请先登录后再授权";
      authorizing.value = false;
      return;
    }

    if (isLegacyMode.value) {
      await confirmLegacy(token);
    } else {
      await confirmSession(token);
    }
  } catch (e: any) {
    error.value = e?.data?.msg || e?.message || "请求失败";
  } finally {
    authorizing.value = false;
  }
}

/** 新流程：POST session action */
async function confirmSession(token: string) {
  const { $fetch: _f } = useNuxtApp();
  const res = await (_f as typeof $fetch)<{ code: number; data: any; msg: string }>(
    `/api/oauth2/authorize/${sessionId.value}`,
    {
      method: "POST",
      body: { action: "approve" },
      headers: { Authorization: `Bearer ${token}` },
    },
  );

  if (res.code === 200) {
    const data = res.data;
    const redirectUrl = data.redirectUri
      ? `${data.redirectUri}${data.redirectUri.includes("?") ? "&" : "?"}code=${data.code}${data.state ? "&state=" + encodeURIComponent(data.state) : ""}`
      : null;

    if (redirectUrl) {
      window.location.href = redirectUrl;
    } else {
      error.value = "授权成功但未获取到回调地址";
    }
  } else {
    error.value = res.msg || "授权失败";
  }
}

/** 旧流程兼容 */
async function confirmLegacy(token: string) {
  const { $fetch: _f } = useNuxtApp();

  const body: Record<string, string> = {
    clientId: legacyClientId.value,
    redirectUri: legacyRedirectUri.value,
    responseType: "code",
    scope: legacyScope.value,
  };
  if (legacyState.value) body.state = legacyState.value;
  if (legacyCodeChallenge.value) {
    body.codeChallenge = legacyCodeChallenge.value;
    body.codeChallengeMethod = legacyCodeChallengeMethod.value;
  }

  const res = await (_f as typeof $fetch)<{ code: number; data: any; msg: string }>(
    "/api/oauth/authorize",
    {
      method: "POST",
      body,
      headers: { Authorization: `Bearer ${token}` },
    },
  );

  if (res.code === 200) {
    const data = res.data;
    let location = data.redirectUri || legacyRedirectUri.value;
    location += (location.includes("?") ? "&" : "?") + "code=" + data.code;
    if (data.state) {
      location += "&state=" + encodeURIComponent(data.state);
    }
    window.location.href = location;
  } else {
    error.value = res.msg || "授权失败";
  }
}

// ==================== 拒绝授权 ====================
async function cancelAuthorize() {
  if (isLegacyMode.value) {
    toast.info("已取消授权");
    navigateTo("/");
    return;
  }

  // 新流程：通知后端用户拒绝
  try {
    const token = await getAccessToken();
    const { $fetch: _f } = useNuxtApp();
    const res = await (_f as typeof $fetch)<{ code: number; data: any }>(
      `/api/oauth2/authorize/${sessionId.value}`,
      {
        method: "POST",
        body: { action: "deny" },
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      },
    );

    if (res.code === 200 && res.data?.redirectUrl) {
      window.location.href = res.data.redirectUrl;
      return;
    }
  } catch {
    // ignore
  }

  toast.info("已取消授权");
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
          {{ appInfo?.clientName || "未知应用" }} 请求访问你的账户
        </p>
      </div>

      <div class="oauth-card-body">
        <div class="oauth-info-row">
          <span class="oauth-info-label">应用</span>
          <span class="oauth-info-value">{{ appInfo?.clientName || "未知" }}</span>
        </div>
        <div v-if="appInfo?.description" class="oauth-info-row">
          <span class="oauth-info-label">简介</span>
          <span class="oauth-info-value">{{ appInfo.description }}</span>
        </div>
        <div class="oauth-info-row">
          <span class="oauth-info-label">授权范围</span>
          <span class="oauth-info-value">{{ appInfo?.scope || "read" }}</span>
        </div>
        <div class="oauth-info-row">
          <span class="oauth-info-label">回调地址</span>
          <span class="oauth-info-value oauth-uri">{{ appInfo?.redirectUri }}</span>
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
