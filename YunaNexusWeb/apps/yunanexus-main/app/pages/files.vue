<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useAppToast } from "../composables/useAppToast";
import {
  useFileApi,
  type FileUploadProgress,
  type UserManagedFileItem,
} from "../composables/useFileApi";
import AppButton from "../components/form/AppButton.vue";

useHead({
  title: "文件管理",
});

const authApi = useAuthApi();
const fileApi = useFileApi();
const toast = useAppToast();
const route = useRoute();

type UploadTaskItem = {
  id: string;
  fileName: string;
  fileSize: number;
  status: "preparing" | "uploading" | "merging" | "done" | "error";
  percent: number;
  text: string;
  errorMessage?: string;
};

const pageReady = ref(false);
const activeTab = ref<"files" | "recycle">("files");
const uploadInputRef = ref<HTMLInputElement | null>(null);
const uploading = ref(false);
const uploadProgressText = ref("");
const uploadPanelOpen = ref(false);
const loadingCurrent = ref(false);
const loadingRecycle = ref(false);
const operatingUuid = ref("");

const currentFiles = ref<UserManagedFileItem[]>([]);
const recycleFiles = ref<UserManagedFileItem[]>([]);
const uploadTasks = ref<UploadTaskItem[]>([]);

const pageResolved = computed(() => pageReady.value && authApi.sessionReady.value);
const isAuthenticated = computed(() => !!authApi.accessToken.value);
const activeUploadCount = computed(
  () => uploadTasks.value.filter((item) => item.status === "preparing" || item.status === "uploading" || item.status === "merging").length,
);
const hasUploadTasks = computed(() => uploadTasks.value.length > 0);

watch(
  [() => authApi.sessionReady.value, () => authApi.accessToken.value],
  async ([ready, token]) => {
    if (import.meta.client && ready && !token && route.path === "/files") {
      await navigateTo("/login");
    }
  },
  { immediate: true },
);

watch(
  pageResolved,
  async (ready) => {
    if (ready && isAuthenticated.value) {
      await loadAll();
    }
  },
  { immediate: true },
);

onMounted(() => {
  pageReady.value = true;
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

  return date.toLocaleString("zh-CN", {
    hour12: false,
  });
};

const loadCurrentFiles = async () => {
  loadingCurrent.value = true;
  try {
    const result = await fileApi.listFiles();
    currentFiles.value = result.data || [];
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "文件列表加载失败");
  } finally {
    loadingCurrent.value = false;
  }
};

const loadRecycleFiles = async () => {
  loadingRecycle.value = true;
  try {
    const result = await fileApi.listRecycleFiles();
    recycleFiles.value = result.data || [];
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "回收站加载失败");
  } finally {
    loadingRecycle.value = false;
  }
};

const loadAll = async () => {
  await Promise.all([loadCurrentFiles(), loadRecycleFiles()]);
};

const openUploadPicker = () => {
  uploadInputRef.value?.click();
};

const createUploadTask = (file: File) => {
  const task: UploadTaskItem = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    fileName: file.name,
    fileSize: file.size,
    status: "preparing",
    percent: 0,
    text: "准备上传…",
  };
  uploadTasks.value = [task, ...uploadTasks.value].slice(0, 12);
  uploadPanelOpen.value = true;
  return task.id;
};

const updateUploadTask = (taskId: string, progress: FileUploadProgress) => {
  uploadTasks.value = uploadTasks.value.map((item) =>
    item.id === taskId
      ? {
          ...item,
          status: progress.phase,
          percent: progress.percent,
          text: progress.text,
        }
      : item,
  );
};

const finishUploadTask = (taskId: string) => {
  uploadTasks.value = uploadTasks.value.map((item) =>
    item.id === taskId
      ? {
          ...item,
          status: "done",
          percent: 100,
          text: "上传完成",
        }
      : item,
  );
};

const failUploadTask = (taskId: string, message: string) => {
  uploadTasks.value = uploadTasks.value.map((item) =>
    item.id === taskId
      ? {
          ...item,
          status: "error",
          text: "上传失败",
          errorMessage: message,
        }
      : item,
  );
};

const clearFinishedUploadTasks = () => {
  uploadTasks.value = uploadTasks.value.filter((item) => item.status !== "done");
};

const handleUploadChange = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";

  if (!file) {
    return;
  }

  const taskId = createUploadTask(file);
  uploading.value = true;
  uploadProgressText.value = "准备上传…";
  try {
    const result = await fileApi.uploadFile(file, undefined, (progress) => {
      uploadProgressText.value = progress.text;
      updateUploadTask(taskId, progress);
    });
    finishUploadTask(taskId);
    toast.success(result.msg || "文件上传成功");
    activeTab.value = "files";
    await loadCurrentFiles();
  } catch (error) {
    const message = error instanceof Error ? error.message : "文件上传失败";
    failUploadTask(taskId, message);
    toast.error(message);
  } finally {
    uploading.value = false;
    uploadProgressText.value = "";
  }
};

const handleDownload = async (item: UserManagedFileItem) => {
  operatingUuid.value = item.fileUuid;
  try {
    await fileApi.downloadFile(item, (progress) => {
      uploadProgressText.value = progress;
    });
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "文件下载失败");
  } finally {
    operatingUuid.value = "";
    uploadProgressText.value = "";
  }
};

const handleDelete = async (item: UserManagedFileItem) => {
  operatingUuid.value = item.fileUuid;
  try {
    const result = await fileApi.deleteFile(item.fileUuid);
    toast.success(result.msg || "文件已移入回收站");
    await Promise.all([loadCurrentFiles(), loadRecycleFiles()]);
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "文件删除失败");
  } finally {
    operatingUuid.value = "";
  }
};

const handleRestore = async (item: UserManagedFileItem) => {
  operatingUuid.value = item.fileUuid;
  try {
    const result = await fileApi.restoreFile(item.fileUuid);
    toast.success(result.msg || "文件已恢复");
    await Promise.all([loadCurrentFiles(), loadRecycleFiles()]);
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "文件恢复失败");
  } finally {
    operatingUuid.value = "";
  }
};
</script>

<template>
  <section class="files-page">
    <header class="files-page-header">
      <div>
        <h1 class="files-page-title">文件管理</h1>
      </div>

      <div class="files-page-actions">
        <input
          ref="uploadInputRef"
          class="files-hidden-input"
          type="file"
          @change="handleUploadChange"
        />
        <AppButton
          variant="secondary"
          :loading="loadingCurrent || loadingRecycle"
          @click="loadAll"
        >
          刷新
        </AppButton>
        <AppButton :loading="uploading" @click="openUploadPicker">
          {{ uploadProgressText || "上传文件" }}
        </AppButton>
      </div>
    </header>

    <div v-if="pageResolved" class="files-panel">
      <div class="files-tabs">
        <button
          class="files-tab"
          :class="{ 'files-tab-active': activeTab === 'files' }"
          type="button"
          @click="activeTab = 'files'"
        >
          我的文件
          <span class="files-tab-count">{{ currentFiles.length }}</span>
        </button>
        <button
          class="files-tab"
          :class="{ 'files-tab-active': activeTab === 'recycle' }"
          type="button"
          @click="activeTab = 'recycle'"
        >
          回收站
          <span class="files-tab-count">{{ recycleFiles.length }}</span>
        </button>
      </div>

      <div v-if="activeTab === 'files'" class="files-list">
        <article
          v-for="item in currentFiles"
          :key="item.fileUuid"
          class="file-card"
        >
          <div class="file-card-main">
            <strong class="file-card-name">
              {{ item.originName || item.fileName }}
            </strong>
            <div class="file-card-meta">
              <span>{{ formatBytes(item.fileSize) }}</span>
              <span>{{ item.fileExt || "未知类型" }}</span>
              <span>{{ formatDateTime(item.createTime) }}</span>
            </div>
          </div>

          <div class="file-card-actions">
            <AppButton
              variant="secondary"
              :loading="operatingUuid === item.fileUuid"
              @click="handleDownload(item)"
            >
              下载
            </AppButton>
            <AppButton
              variant="secondary"
              :loading="operatingUuid === item.fileUuid"
              @click="handleDelete(item)"
            >
              删除
            </AppButton>
          </div>
        </article>

        <div
          v-if="!loadingCurrent && !currentFiles.length"
          class="files-empty-state"
        >
          还没有文件，先上传一个试试。
        </div>
      </div>

      <div v-else class="files-list">
        <article
          v-for="item in recycleFiles"
          :key="item.fileUuid"
          class="file-card"
        >
          <div class="file-card-main">
            <strong class="file-card-name">
              {{ item.originName || item.fileName }}
            </strong>
            <div class="file-card-meta">
              <span>{{ formatBytes(item.fileSize) }}</span>
              <span>删除时间：{{ formatDateTime(item.deletedAt) }}</span>
              <span>到期：{{ formatDateTime(item.recycleExpireAt) }}</span>
            </div>
          </div>

          <div class="file-card-actions">
            <AppButton
              variant="secondary"
              :loading="operatingUuid === item.fileUuid"
              @click="handleRestore(item)"
            >
              恢复
            </AppButton>
          </div>
        </article>

        <div
          v-if="!loadingRecycle && !recycleFiles.length"
          class="files-empty-state"
        >
          回收站当前为空。
        </div>
      </div>
    </div>

    <div v-else class="files-skeleton-grid" aria-hidden="true">
      <article class="files-skeleton-card" />
      <article class="files-skeleton-card" />
      <article class="files-skeleton-card" />
    </div>

    <button
      class="files-upload-fab"
      type="button"
      @click="uploadPanelOpen = !uploadPanelOpen"
    >
      上传进度
      <span class="files-upload-fab-badge">{{ activeUploadCount || 0 }}</span>
    </button>

    <section
      v-if="uploadPanelOpen"
      class="files-upload-panel"
    >
      <header class="files-upload-panel-header">
        <strong>上传列表</strong>
        <div class="files-upload-panel-actions">
          <button type="button" @click="clearFinishedUploadTasks">清除已完成</button>
          <button type="button" @click="uploadPanelOpen = false">收起</button>
        </div>
      </header>

      <div v-if="uploadTasks.length" class="files-upload-panel-list">
        <article
          v-for="task in uploadTasks"
          :key="task.id"
          class="files-upload-item"
        >
          <div class="files-upload-item-header">
            <strong class="files-upload-item-name">{{ task.fileName }}</strong>
            <span class="files-upload-item-percent">{{ task.percent }}%</span>
          </div>
          <div class="files-upload-progress-track">
            <span class="files-upload-progress-fill" :style="{ width: `${task.percent}%` }" />
          </div>
          <div class="files-upload-item-meta">
            <span>{{ formatBytes(task.fileSize) }}</span>
            <span>{{ task.text }}</span>
          </div>
          <p v-if="task.errorMessage" class="files-upload-item-error">{{ task.errorMessage }}</p>
        </article>
      </div>
      <div v-else class="files-upload-empty-state">当前没有上传任务</div>
    </section>
  </section>
</template>

<style scoped lang="scss">
.files-page {
  display: grid;
  gap: 20px;
}

.files-page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.files-page-title {
  margin: 0;
  color: var(--yn-color-text-primary);
  font-size: 24px;
  font-weight: 700;
}

.files-page-subtitle {
  margin: 6px 0 0;
  color: var(--yn-color-text-secondary);
  font-size: 14px;
}

.files-page-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.files-hidden-input {
  display: none;
}

.files-panel,
.file-card,
.files-skeleton-card {
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: var(--yn-color-surface);
  box-shadow: var(--yn-shadow-card);
}

.files-panel {
  display: grid;
  gap: 16px;
  padding: 20px;
}

.files-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.files-tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid var(--yn-color-border-medium);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface-raised);
  color: var(--yn-color-text-secondary);
  cursor: pointer;
}

.files-tab-active {
  border-color: color-mix(in srgb, var(--yn-color-primary) 40%, transparent);
  background: color-mix(in srgb, var(--yn-color-primary) 12%, var(--yn-color-surface));
  color: var(--yn-color-text-primary);
}

.files-tab-count {
  color: var(--yn-color-text-tertiary);
  font-size: 12px;
}

.files-list {
  display: grid;
  gap: 12px;
}

.file-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
}

.file-card-main {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.file-card-name {
  color: var(--yn-color-text-primary);
  font-size: 15px;
  font-weight: 700;
  word-break: break-all;
}

.file-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  color: var(--yn-color-text-tertiary);
  font-size: 12px;
}

.file-card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.files-empty-state,
.files-upload-empty-state {
  border: 1px dashed var(--yn-color-border-medium);
  border-radius: var(--yn-radius-large);
  padding: 28px 16px;
  color: var(--yn-color-text-tertiary);
  text-align: center;
}

.files-skeleton-grid {
  display: grid;
  gap: 12px;
}

.files-skeleton-card {
  min-height: 84px;
  background: color-mix(in srgb, var(--yn-color-surface-raised) 72%, var(--yn-color-surface));
}

.files-upload-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 40;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 16px;
  border: 1px solid color-mix(in srgb, var(--yn-color-primary) 24%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--yn-color-surface) 92%, transparent);
  box-shadow: var(--yn-shadow-overlay);
  color: var(--yn-color-text-primary);
  cursor: pointer;
}

.files-upload-fab-badge {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--yn-color-primary);
  color: #fff;
  font-size: 12px;
  line-height: 20px;
  text-align: center;
}

.files-upload-panel {
  position: fixed;
  right: 24px;
  bottom: 80px;
  z-index: 40;
  width: min(360px, calc(100vw - 24px));
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: color-mix(in srgb, var(--yn-color-surface) 96%, transparent);
  box-shadow: var(--yn-shadow-overlay);
  backdrop-filter: blur(12px);
}

.files-upload-panel-header,
.files-upload-item-header,
.files-upload-item-meta,
.files-upload-panel-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.files-upload-panel-actions button {
  border: 0;
  background: transparent;
  color: var(--yn-color-text-secondary);
  cursor: pointer;
}

.files-upload-panel-list {
  display: grid;
  gap: 10px;
  max-height: 320px;
  overflow: auto;
}

.files-upload-item {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface);
}

.files-upload-item-name {
  min-width: 0;
  color: var(--yn-color-text-primary);
  font-size: 13px;
  font-weight: 700;
  word-break: break-all;
}

.files-upload-item-percent,
.files-upload-item-meta {
  color: var(--yn-color-text-tertiary);
  font-size: 12px;
}

.files-upload-progress-track {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--yn-color-surface-raised);
}

.files-upload-progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--yn-color-primary), color-mix(in srgb, var(--yn-color-primary) 60%, #ffffff));
}

.files-upload-item-error {
  margin: 0;
  color: #dc2626;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 960px) {
  .files-page-header,
  .file-card {
    align-items: stretch;
    flex-direction: column;
  }

  .files-page-actions,
  .file-card-actions {
    width: 100%;
  }
}

@media (max-width: 640px) {
  .files-upload-fab {
    right: 12px;
    bottom: 12px;
  }

  .files-upload-panel {
    right: 12px;
    bottom: 68px;
    left: 12px;
    width: auto;
  }
}
</style>
