<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useFileApi, type UserFileShareItem } from "../../composables/useFileApi";
import { useAuthApi } from "../../composables/useAuthApi";
import AppButton from "../../components/form/AppButton.vue";

useHead({
  title: "文件分享",
});

const route = useRoute();
const authApi = useAuthApi();
const fileApi = useFileApi();

const shareCode = computed(() => String(route.params.shareCode || ""));
const shareInfo = ref<UserFileShareItem | null>(null);
const loadingInfo = ref(true);
const accessLoading = ref(false);
const accessGranted = ref(false);
const accessError = ref("");
const extractCode = ref("");
const previewLoading = ref(false);
const previewUrl = ref("");
const previewText = ref("");
const previewError = ref("");

const previewKind = computed(() => {
  const mime = shareInfo.value?.fileMime || "";
  if (mime.startsWith("image/")) {
    return "image";
  }
  if (mime.startsWith("video/")) {
    return "video";
  }
  if (mime.startsWith("audio/")) {
    return "audio";
  }
  if (
    mime.startsWith("text/") ||
    mime.includes("json") ||
    mime.includes("xml") ||
    mime.includes("javascript")
  ) {
    return "text";
  }
  return "unknown";
});

const isLoggedIn = computed(() => !!authApi.accessToken.value);
const requiresLoginForView = computed(() => shareInfo.value?.viewAuthMode === 1);
const requiresLoginForDownload = computed(
  () => shareInfo.value?.downloadAuthMode === 1,
);
const canAttemptAutoAccess = computed(() => {
  if (!shareInfo.value) {
    return false;
  }
  if (shareInfo.value.hasExtractCode && !extractCode.value.trim()) {
    return false;
  }
  if (requiresLoginForView.value && !isLoggedIn.value) {
    return false;
  }
  return true;
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
    return "不限";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString("zh-CN", { hour12: false });
};

const revokePreviewUrl = () => {
  if (
    previewUrl.value &&
    import.meta.client &&
    !previewUrl.value.startsWith("/api/file/share/download/")
  ) {
    URL.revokeObjectURL(previewUrl.value);
  }
  previewUrl.value = "";
};

const resetPreview = () => {
  revokePreviewUrl();
  previewText.value = "";
  previewError.value = "";
  previewLoading.value = false;
};

const buildDownloadUrl = () => {
  const query = extractCode.value.trim()
    ? `?extractCode=${encodeURIComponent(extractCode.value.trim().toUpperCase())}`
    : "";
  return `/api/file/share/download/${encodeURIComponent(shareCode.value)}${query}`;
};

const loadPreview = async () => {
  resetPreview();
  if (!accessGranted.value || !shareInfo.value) {
    return;
  }
  if (previewKind.value === "unknown") {
    previewError.value = "当前文件类型暂不支持在线预览";
    return;
  }
  if (
    previewKind.value === "image" ||
    previewKind.value === "video" ||
    previewKind.value === "audio"
  ) {
    previewUrl.value = buildDownloadUrl();
    return;
  }
  previewLoading.value = true;
  try {
    const response = await fetch(buildDownloadUrl(), { method: "GET" });
    if (!response.ok) {
      const rawText = await response.text();
      throw new Error(rawText || "分享预览加载失败");
    }
    previewText.value = await response.text();
  } catch (error) {
    previewError.value =
      error instanceof Error ? error.message : "分享预览加载失败";
  } finally {
    previewLoading.value = false;
  }
};

const loadShareInfo = async () => {
  loadingInfo.value = true;
  accessError.value = "";
  try {
    const result = await fileApi.getShareInfo(shareCode.value);
    shareInfo.value = result.data;
  } catch (error) {
    accessError.value =
      error instanceof Error ? error.message : "分享信息加载失败";
  } finally {
    loadingInfo.value = false;
  }
};

const accessShare = async () => {
  accessLoading.value = true;
  accessError.value = "";
  try {
    const result = await fileApi.accessShare(
      shareCode.value,
      extractCode.value.trim().toUpperCase() || null,
    );
    shareInfo.value = result.data;
    accessGranted.value = true;
    await loadPreview();
  } catch (error) {
    accessGranted.value = false;
    resetPreview();
    accessError.value =
      error instanceof Error ? error.message : "分享访问失败，请稍后重试";
  } finally {
    accessLoading.value = false;
  }
};

const triggerDownload = () => {
  if (!import.meta.client) {
    return;
  }
  const anchor = document.createElement("a");
  anchor.href = buildDownloadUrl();
  anchor.download = shareInfo.value?.originName || shareInfo.value?.fileName || "shared-file";
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
};

watch(
  [shareInfo, () => authApi.sessionReady.value, () => authApi.accessToken.value],
  async () => {
    if (!shareInfo.value || accessGranted.value || accessLoading.value) {
      return;
    }
    if (canAttemptAutoAccess.value) {
      await accessShare();
    }
  },
);

onMounted(async () => {
  await loadShareInfo();
  if (canAttemptAutoAccess.value) {
    await accessShare();
  }
});

onBeforeUnmount(() => {
  revokePreviewUrl();
});
</script>

<template>
  <section class="share-page">
    <header class="share-hero">
      <p class="share-overline">File Share</p>
      <h1>分享文件访问</h1>
      <p>
        通过提取码、登录要求、时效和下载次数控制共享范围，页面遵循系统终端风格并保留在线预览。
      </p>
    </header>

    <div class="share-layout">
      <section class="share-panel">
        <template v-if="loadingInfo">
          <div class="share-empty">正在加载分享信息…</div>
        </template>

        <template v-else-if="shareInfo">
          <div class="share-meta">
            <div class="share-meta-row">
              <span>文件名称</span>
              <strong>{{ shareInfo.originName || shareInfo.fileName }}</strong>
            </div>
            <div class="share-meta-row">
              <span>文件大小</span>
              <strong>{{ formatBytes(shareInfo.fileSize) }}</strong>
            </div>
            <div class="share-meta-row">
              <span>过期时间</span>
              <strong>{{ formatDateTime(shareInfo.expireAt) }}</strong>
            </div>
            <div class="share-meta-row">
              <span>查看次数</span>
              <strong>{{ shareInfo.viewCount }}</strong>
            </div>
            <div class="share-meta-row">
              <span>下载次数</span>
              <strong>
                {{ shareInfo.downloadCount }}
                <template v-if="shareInfo.maxDownloadCount">
                  / {{ shareInfo.maxDownloadCount }}
                </template>
              </strong>
            </div>
          </div>

          <div class="share-rules">
            <span class="share-chip">
              {{ shareInfo.hasExtractCode ? "需要提取码" : "无需提取码" }}
            </span>
            <span class="share-chip">
              {{ requiresLoginForView ? "登录后查看" : "免登录查看" }}
            </span>
            <span class="share-chip">
              {{ requiresLoginForDownload ? "登录后下载" : "免登录下载" }}
            </span>
          </div>

          <div class="share-access-card">
            <template v-if="shareInfo.hasExtractCode">
              <label class="share-field">
                <span>提取码</span>
                <input
                  v-model="extractCode"
                  type="text"
                  maxlength="8"
                  placeholder="请输入分享提取码"
                />
              </label>
            </template>

            <div v-if="requiresLoginForView && !isLoggedIn" class="share-tip">
              当前分享需要登录后查看内容。
            </div>

            <div v-if="accessError" class="share-error">
              {{ accessError }}
            </div>

            <div class="share-actions">
              <AppButton
                v-if="requiresLoginForView && !isLoggedIn"
                @click="navigateTo('/login')"
              >
                立即登录
              </AppButton>
              <AppButton
                v-else
                :loading="accessLoading"
                @click="accessShare"
              >
                验证并查看
              </AppButton>
              <AppButton
                variant="secondary"
                :disabled="
                  !accessGranted ||
                  (requiresLoginForDownload && !isLoggedIn) ||
                  !!shareInfo.downloadLimitReached
                "
                @click="triggerDownload"
              >
                下载文件
              </AppButton>
            </div>
          </div>
        </template>

        <template v-else>
          <div class="share-empty">
            {{ accessError || "当前分享不存在或已失效" }}
          </div>
        </template>
      </section>

      <section class="share-panel share-preview-panel">
        <div v-if="!accessGranted" class="share-empty">
          通过访问校验后即可在此处预览文件内容。
        </div>
        <div v-else-if="previewLoading" class="share-empty">
          正在加载预览…
        </div>
        <div v-else-if="previewError" class="share-empty">
          {{ previewError }}
        </div>
        <img
          v-else-if="previewKind === 'image' && previewUrl"
          :src="previewUrl"
          class="share-preview-image"
          alt="分享图片预览"
        />
        <video
          v-else-if="previewKind === 'video' && previewUrl"
          :src="previewUrl"
          class="share-preview-media"
          controls
          preload="metadata"
          playsinline
        />
        <audio
          v-else-if="previewKind === 'audio' && previewUrl"
          :src="previewUrl"
          class="share-preview-audio"
          controls
          preload="metadata"
        />
        <pre
          v-else-if="previewKind === 'text' && previewText"
          class="share-preview-text"
          >{{ previewText }}</pre
        >
        <div v-else class="share-empty">
          当前文件类型暂不支持在线预览。
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped lang="scss">
.share-page,
.share-layout,
.share-meta,
.share-actions,
.share-rules {
  display: grid;
  gap: 16px;
}

.share-page {
  gap: 24px;
}

.share-hero,
.share-panel {
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: var(--yn-color-surface);
  box-shadow: var(--yn-shadow-card);
}

.share-hero {
  padding: 32px;
}

.share-overline {
  margin: 0 0 12px;
  color: var(--yn-color-primary);
  font-family: var(--yn-font-mono), monospace;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.share-hero h1 {
  margin: 0 0 12px;
  color: var(--yn-color-text-primary);
  font-family: var(--yn-font-mono), monospace;
  font-size: clamp(28px, 3vw, 36px);
  line-height: 1.2;
}

.share-hero p {
  margin: 0;
  color: var(--yn-color-text-secondary);
  line-height: 1.8;
}

.share-layout {
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  align-items: start;
}

.share-panel {
  padding: 24px;
}

.share-meta-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid var(--yn-color-border-subtle);
}

.share-meta-row span {
  color: var(--yn-color-text-tertiary);
  font-size: 12px;
}

.share-meta-row strong {
  min-width: 0;
  color: var(--yn-color-text-primary);
  text-align: right;
  word-break: break-word;
}

.share-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  border: 1px solid var(--yn-color-border-medium);
  border-radius: var(--yn-radius-small);
  background: color-mix(in srgb, var(--yn-color-primary) 8%, var(--yn-color-surface));
  color: var(--yn-color-text-primary);
  font-family: var(--yn-font-mono), monospace;
  font-size: 12px;
}

.share-access-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface-raised);
}

.share-field {
  display: grid;
  gap: 8px;
}

.share-field span {
  color: var(--yn-color-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.share-field input {
  min-height: 42px;
  border: 1px solid var(--yn-color-border-medium);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-background);
  color: var(--yn-color-text-primary);
  padding: 0 14px;
}

.share-actions {
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
}

.share-tip {
  color: var(--yn-color-warning);
  font-size: 13px;
  line-height: 1.6;
}

.share-error {
  color: var(--yn-color-error);
  font-size: 13px;
  line-height: 1.6;
}

.share-preview-panel {
  min-height: 520px;
  background:
    linear-gradient(
      180deg,
      color-mix(in srgb, var(--yn-color-surface-raised) 82%, transparent),
      transparent
    ),
    #05070b;
}

.share-empty {
  display: grid;
  place-items: center;
  min-height: 220px;
  border: 1px dashed var(--yn-color-border-medium);
  border-radius: var(--yn-radius-medium);
  color: var(--yn-color-text-tertiary);
  text-align: center;
  padding: 24px;
}

.share-preview-image,
.share-preview-media {
  width: 100%;
  max-height: min(72vh, 840px);
  object-fit: contain;
}

.share-preview-audio {
  width: 100%;
  align-self: center;
}

.share-preview-text {
  margin: 0;
  max-height: min(72vh, 840px);
  overflow: auto;
  color: var(--yn-color-text-secondary);
  font-family: var(--yn-font-mono), monospace;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 960px) {
  .share-layout {
    grid-template-columns: 1fr;
  }

  .share-preview-panel {
    min-height: 360px;
  }
}

@media (max-width: 640px) {
  .share-hero,
  .share-panel {
    padding: 18px;
  }

  .share-meta-row {
    flex-direction: column;
  }

  .share-meta-row strong {
    text-align: left;
  }
}
</style>
