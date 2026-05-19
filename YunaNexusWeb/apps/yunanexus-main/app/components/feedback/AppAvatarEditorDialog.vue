<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import AppButton from "../form/AppButton.vue";

type TransformState = {
  x: number;
  y: number;
  scale: number;
  rotation: number;
};

const props = withDefaults(
  defineProps<{
    open: boolean;
    uploading?: boolean;
    errorMessage?: string;
  }>(),
  {
    uploading: false,
    errorMessage: "",
  },
);

const emit = defineEmits<{
  (e: "close"): void;
  (e: "confirm", file: File): void;
}>();

const cropSize = 320;
const previewSize = 176;
const outputSize = 512;
const maxClientFileSize = 10 * 1024 * 1024;

const fileInputRef = ref<HTMLInputElement | null>(null);
const imageRef = ref<HTMLImageElement | null>(null);
const emptyDropzoneRef = ref<HTMLElement | null>(null);
const sourceUrl = ref("");
const sourceFileName = ref("");
const editorError = ref("");
const previewUrl = ref("");
const dragOver = ref(false);
const processing = ref(false);

const naturalWidth = ref(0);
const naturalHeight = ref(0);

const transform = reactive<TransformState>({
  x: 0,
  y: 0,
  scale: 1,
  rotation: 0,
});

const zoomValue = ref(100);
const stageDisplaySize = ref(cropSize);
const cropFrameDisplaySize = computed(() => {
  if (stageDisplaySize.value >= 420) {
    return 320;
  }
  if (stageDisplaySize.value >= 380) {
    return 300;
  }
  if (stageDisplaySize.value >= 340) {
    return 280;
  }
  return Math.max(224, stageDisplaySize.value - 32);
});
const isCompactToolbar = computed(() => stageDisplaySize.value <= 320);

let previewFrameId: number | null = null;

const dragState = ref<{
  startClientX: number;
  startClientY: number;
  startX: number;
  startY: number;
} | null>(null);

const hasSourceImage = computed(() => !!sourceUrl.value);
const hasImage = computed(
  () =>
    hasSourceImage.value && naturalWidth.value > 0 && naturalHeight.value > 0,
);
const canConfirm = computed(
  () => hasImage.value && !processing.value && !props.uploading,
);

const cloneState = (): TransformState => ({
  x: transform.x,
  y: transform.y,
  scale: transform.scale,
  rotation: transform.rotation,
});

const revokeSource = () => {
  if (sourceUrl.value) {
    URL.revokeObjectURL(sourceUrl.value);
  }
};

const clearDragListeners = () => {
  if (!import.meta.client) {
    return;
  }

  window.removeEventListener("pointermove", handlePointerMove);
  window.removeEventListener("pointerup", handlePointerUp);
};

let removeEmptyDropzoneListeners: (() => void) | null = null;

const preventWindowFileDrop = (event: DragEvent) => {
  event.preventDefault();
};

const clearEmptyDropzoneListeners = () => {
  removeEmptyDropzoneListeners?.();
  removeEmptyDropzoneListeners = null;
};

const syncWindowDropProtection = (enabled: boolean) => {
  if (!import.meta.client) {
    return;
  }

  if (enabled) {
    window.addEventListener("dragover", preventWindowFileDrop);
    window.addEventListener("drop", preventWindowFileDrop);
    return;
  }

  window.removeEventListener("dragover", preventWindowFileDrop);
  window.removeEventListener("drop", preventWindowFileDrop);
};

const syncPageScrollLock = (locked: boolean) => {
  if (!import.meta.client) {
    return;
  }

  const overflow = locked ? "hidden" : "";
  document.documentElement.style.overflow = overflow;
  document.body.style.overflow = overflow;
};

const clearEditor = () => {
  clearDragListeners();
  revokeSource();
  sourceUrl.value = "";
  sourceFileName.value = "";
  editorError.value = "";
  previewUrl.value = "";
  naturalWidth.value = 0;
  naturalHeight.value = 0;
  zoomValue.value = 100;
  if (import.meta.client && previewFrameId !== null) {
    window.cancelAnimationFrame(previewFrameId);
    previewFrameId = null;
  }
  transform.x = 0;
  transform.y = 0;
  transform.scale = 1;
  transform.rotation = 0;
};

const syncStageDisplaySize = () => {
  if (!import.meta.client) {
    return;
  }
  const viewportWidth = window.innerWidth;
  if (viewportWidth >= 1680) {
    stageDisplaySize.value = 460;
    return;
  }
  if (viewportWidth >= 1400) {
    stageDisplaySize.value = 420;
    return;
  }
  if (viewportWidth >= 1200) {
    stageDisplaySize.value = 380;
    return;
  }
  if (viewportWidth >= 960) {
    stageDisplaySize.value = 340;
    return;
  }
  stageDisplaySize.value = Math.max(240, Math.min(320, viewportWidth - 72));
};

watch(
  () => props.open,
  async (open) => {
    syncWindowDropProtection(open);
    syncPageScrollLock(open);
    clearEmptyDropzoneListeners();
    if (open) {
      await nextTick();
      bindEmptyDropzoneListeners();
      return;
    }
    clearEditor();
  },
);

onMounted(() => {
  syncStageDisplaySize();
  if (import.meta.client) {
    window.addEventListener("resize", syncStageDisplaySize);
  }
});

onBeforeUnmount(() => {
  syncWindowDropProtection(false);
  syncPageScrollLock(false);
  clearEmptyDropzoneListeners();
  if (import.meta.client) {
    window.removeEventListener("resize", syncStageDisplaySize);
  }
  clearEditor();
});

const getBaseScale = () => {
  if (!naturalWidth.value || !naturalHeight.value) {
    return 1;
  }

  return Math.max(
    cropSize / naturalWidth.value,
    cropSize / naturalHeight.value,
  );
};

const clampTransform = (state: TransformState): TransformState => {
  if (!naturalWidth.value || !naturalHeight.value) {
    return state;
  }

  const normalizedRotation =
    (((Math.round(state.rotation / 90) * 90) % 360) + 360) % 360;
  const baseScale = getBaseScale();
  const rawWidth = naturalWidth.value * baseScale * state.scale;
  const rawHeight = naturalHeight.value * baseScale * state.scale;
  const rotatedWidth = normalizedRotation % 180 === 0 ? rawWidth : rawHeight;
  const rotatedHeight = normalizedRotation % 180 === 0 ? rawHeight : rawWidth;
  const limitX = Math.max(0, (rotatedWidth - cropSize) / 2);
  const limitY = Math.max(0, (rotatedHeight - cropSize) / 2);

  return {
    ...state,
    rotation: normalizedRotation,
    x: Math.min(limitX, Math.max(-limitX, state.x)),
    y: Math.min(limitY, Math.max(-limitY, state.y)),
  };
};

const renderCanvas = (size: number) => {
  if (!hasImage.value || !imageRef.value) {
    return null;
  }

  const canvas = document.createElement("canvas");
  canvas.width = size;
  canvas.height = size;

  const context = canvas.getContext("2d");
  if (!context) {
    return null;
  }

  const ratio = size / cropSize;
  const baseScale = getBaseScale() * transform.scale * ratio;

  context.imageSmoothingEnabled = true;
  context.imageSmoothingQuality = "high";
  context.translate(
    size / 2 + transform.x * ratio,
    size / 2 + transform.y * ratio,
  );
  context.rotate((transform.rotation * Math.PI) / 180);
  context.scale(baseScale, baseScale);
  context.drawImage(
    imageRef.value,
    -naturalWidth.value / 2,
    -naturalHeight.value / 2,
    naturalWidth.value,
    naturalHeight.value,
  );

  return canvas;
};

const updatePreview = () => {
  if (!import.meta.client || !hasImage.value) {
    previewUrl.value = "";
    return;
  }

  const canvas = renderCanvas(previewSize);
  previewUrl.value = canvas ? canvas.toDataURL("image/png") : "";
};

const schedulePreviewUpdate = () => {
  if (!import.meta.client || previewFrameId !== null) {
    return;
  }
  previewFrameId = window.requestAnimationFrame(() => {
    previewFrameId = null;
    updatePreview();
  });
};

const applyState = (nextState: TransformState, syncControls = true) => {
  const clamped = clampTransform(nextState);

  transform.x = clamped.x;
  transform.y = clamped.y;
  transform.scale = clamped.scale;
  transform.rotation = clamped.rotation;

  if (syncControls) {
    zoomValue.value = Math.round(clamped.scale * 100);
  }

  schedulePreviewUpdate();
};

const resetTransform = () => {
  applyState(
    {
      x: 0,
      y: 0,
      scale: 1,
      rotation: 0,
    },
    true,
  );
};

const resetEditor = () => {
  if (!hasImage.value) {
    return;
  }

  applyState(
    {
      x: 0,
      y: 0,
      scale: 1,
      rotation: 0,
    },
    true,
  );
};

const selectFile = (file: File | null | undefined) => {
  editorError.value = "";

  if (!file) {
    return;
  }

  if (!file.type.startsWith("image/")) {
    editorError.value = "请选择图片文件";
    return;
  }

  if (file.size > maxClientFileSize) {
    editorError.value = "图片过大，请选择 10MB 以内的文件";
    return;
  }

  revokeSource();
  sourceFileName.value = file.name;
  sourceUrl.value = URL.createObjectURL(file);
  previewUrl.value = "";
  naturalWidth.value = 0;
  naturalHeight.value = 0;
};

const openFilePicker = () => {
  fileInputRef.value?.click();
};

const handleFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  selectFile(file);
};

const resolveDraggedFile = (event: DragEvent) => {
  const item = Array.from(event.dataTransfer?.items ?? []).find(
    (entry) => entry.kind === "file",
  );
  return item?.getAsFile() ?? event.dataTransfer?.files?.[0] ?? null;
};

const handleDrop = (event: DragEvent) => {
  event.preventDefault();
  event.stopPropagation();
  dragOver.value = false;
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = "copy";
  }
  selectFile(resolveDraggedFile(event));
};

const handleDragEnter = (event: DragEvent) => {
  event.preventDefault();
  event.stopPropagation();
  dragOver.value = true;
};

const handleDragOver = (event: DragEvent) => {
  event.preventDefault();
  event.stopPropagation();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = "copy";
  }
  dragOver.value = true;
};

const handleDragLeave = (event: DragEvent) => {
  event.preventDefault();
  event.stopPropagation();
  const nextTarget = event.relatedTarget as Node | null;
  if (
    nextTarget &&
    (event.currentTarget as Node | null)?.contains(nextTarget)
  ) {
    return;
  }
  dragOver.value = false;
};

const bindEmptyDropzoneListeners = () => {
  if (!import.meta.client || !emptyDropzoneRef.value) {
    return;
  }

  const target = emptyDropzoneRef.value;
  const listeners: Array<[keyof HTMLElementEventMap, EventListener]> = [
    ["dragenter", handleDragEnter as EventListener],
    ["dragover", handleDragOver as EventListener],
    ["dragleave", handleDragLeave as EventListener],
    ["drop", handleDrop as EventListener],
  ];

  listeners.forEach(([name, listener]) => {
    target.addEventListener(name, listener);
  });

  removeEmptyDropzoneListeners = () => {
    listeners.forEach(([name, listener]) => {
      target.removeEventListener(name, listener);
    });
  };
};

const handleImageLoad = () => {
  if (!imageRef.value) {
    return;
  }

  naturalWidth.value = imageRef.value.naturalWidth;
  naturalHeight.value = imageRef.value.naturalHeight;
  resetTransform();
};

const applyZoomValue = (value: number) => {
  const nextZoom = Math.min(300, Math.max(100, value));
  if (nextZoom === zoomValue.value) {
    return;
  }
  zoomValue.value = nextZoom;
  applyState(
    {
      ...cloneState(),
      scale: nextZoom / 100,
    },
    false,
  );
};

const adjustZoom = (delta: number) => {
  applyZoomValue(zoomValue.value + delta);
};

const handleWheelZoom = (event: WheelEvent) => {
  if (!hasImage.value) {
    return;
  }
  event.preventDefault();
  adjustZoom(event.deltaY < 0 ? 8 : -8);
};

const rotateQuarter = () => {
  applyState(
    {
      ...cloneState(),
      rotation: (Math.round(transform.rotation / 90) * 90 + 90) % 360,
    },
    true,
  );
};

const startDrag = (event: PointerEvent) => {
  if (!hasImage.value || !import.meta.client) {
    return;
  }

  dragState.value = {
    startClientX: event.clientX,
    startClientY: event.clientY,
    startX: transform.x,
    startY: transform.y,
  };

  window.addEventListener("pointermove", handlePointerMove);
  window.addEventListener("pointerup", handlePointerUp);
};

function handlePointerMove(event: PointerEvent) {
  if (!dragState.value) {
    return;
  }

  applyState(
    {
      ...cloneState(),
      x: dragState.value.startX + event.clientX - dragState.value.startClientX,
      y: dragState.value.startY + event.clientY - dragState.value.startClientY,
    },
    false,
  );
}

function handlePointerUp() {
  if (!dragState.value) {
    return;
  }

  dragState.value = null;
  clearDragListeners();
}

const emitConfirm = async () => {
  if (!canConfirm.value || !import.meta.client) {
    return;
  }

  const canvas = renderCanvas(outputSize);
  if (!canvas) {
    editorError.value = "无法生成头像文件";
    return;
  }

  processing.value = true;
  editorError.value = "";

  canvas.toBlob(
    (blob) => {
      processing.value = false;

      if (!blob) {
        editorError.value = "头像生成失败，请重试";
        return;
      }

      const file = new File([blob], "avatar.png", {
        type: "image/png",
      });

      emit("confirm", file);
    },
    "image/png",
    1,
  );
};

const closeDialog = () => {
  if (props.uploading || processing.value) {
    return;
  }

  emit("close");
};

const editorImageStyle = computed(() => {
  const displayRatio = cropFrameDisplaySize.value / cropSize;
  const totalScale = getBaseScale() * transform.scale * displayRatio;

  return {
    width: `${naturalWidth.value || cropSize}px`,
    height: `${naturalHeight.value || cropSize}px`,
    left: "50%",
    top: "50%",
    transform: `translate(-50%, -50%) translate(${transform.x * displayRatio}px, ${transform.y * displayRatio}px) rotate(${transform.rotation}deg) scale(${totalScale})`,
  };
});

const stageStyle = computed(() => ({
  "--avatar-stage-size": `${stageDisplaySize.value}px`,
  "--avatar-crop-frame-size": `${cropFrameDisplaySize.value}px`,
}));
</script>

<template>
  <Teleport to="body">
    <Transition name="avatar-editor-dialog">
      <div
        v-if="props.open"
        class="avatar-editor-mask"
        @click.self="closeDialog"
      >
        <section class="avatar-editor-panel">
          <header class="avatar-editor-header">
            <div>
              <h3 class="avatar-editor-title">更换头像</h3>
            </div>
            <button
              class="avatar-editor-close"
              type="button"
              :disabled="props.uploading || processing"
              @click="closeDialog"
            >
              <Icon name="lucide:x" />
            </button>
          </header>

          <div class="avatar-editor-body">
            <input
              ref="fileInputRef"
              class="avatar-editor-hidden-input"
              type="file"
              accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
              @change="handleFileChange"
            />

            <div
              v-if="!hasSourceImage"
              ref="emptyDropzoneRef"
              class="avatar-editor-empty"
              :class="{ 'avatar-editor-empty-active': dragOver }"
            >
              <div
                class="avatar-editor-dropzone"
                role="button"
                tabindex="0"
                @click="openFilePicker"
                @keydown.enter.prevent="openFilePicker"
                @keydown.space.prevent="openFilePicker"
              >
                <Icon
                  name="lucide:image-plus"
                  class="avatar-editor-dropzone-icon"
                />
                <div class="avatar-editor-dropzone-copy">
                  <strong>拖拽上传头像</strong>
                  <span>或点击选择图片</span>
                  <small>支持 jpg / jpeg / png / webp</small>
                </div>
              </div>
            </div>

            <template v-else>
              <div
                class="avatar-editor-main"
                :class="{ 'avatar-editor-main-pending': !hasImage }"
              >
                <div class="avatar-editor-workbench">
                  <div class="avatar-editor-toolbar">
                    <AppButton
                      variant="secondary"
                      :disabled="props.uploading || processing"
                      @click="openFilePicker"
                    >
                      重新选择
                    </AppButton>
                    <AppButton
                      variant="secondary"
                      :disabled="props.uploading || processing"
                      @click="rotateQuarter"
                    >
                      旋转 90°
                    </AppButton>
                    <AppButton
                      variant="secondary"
                      :disabled="props.uploading || processing"
                      @click="resetEditor"
                    >
                      重置
                    </AppButton>
                    <template v-if="isCompactToolbar">
                      <AppButton
                        variant="secondary"
                        :disabled="
                          props.uploading || processing || zoomValue <= 100
                        "
                        @click="adjustZoom(-12)"
                      >
                        缩小
                      </AppButton>
                      <AppButton
                        variant="secondary"
                        :disabled="
                          props.uploading || processing || zoomValue >= 300
                        "
                        @click="adjustZoom(12)"
                      >
                        放大
                      </AppButton>
                    </template>
                  </div>

                  <div class="avatar-editor-stage-wrap" :style="stageStyle">
                    <div v-if="!hasImage" class="avatar-editor-stage-loading">
                      正在加载图片…
                    </div>
                    <div
                      class="avatar-editor-stage"
                      @pointerdown.prevent="startDrag"
                      @wheel.prevent="handleWheelZoom"
                    >
                      <img
                        ref="imageRef"
                        :src="sourceUrl"
                        alt="待编辑头像"
                        class="avatar-editor-image"
                        :style="editorImageStyle"
                        draggable="false"
                        @load="handleImageLoad"
                      />
                    </div>
                    <div class="avatar-editor-stage-overlay" aria-hidden="true">
                      <div class="avatar-editor-crop-frame" />
                    </div>
                  </div>
                </div>

                <aside class="avatar-editor-preview-panel">
                  <div class="avatar-editor-preview-card">
                    <div class="avatar-editor-preview-label">实时预览</div>
                    <div class="avatar-editor-preview-rect">
                      <img
                        v-if="previewUrl"
                        :src="previewUrl"
                        alt="头像预览"
                        class="avatar-editor-preview-image"
                      />
                    </div>
                  </div>

                  <div class="avatar-editor-file-meta">
                    <span class="avatar-editor-file-name">{{
                      sourceFileName
                    }}</span>
                  </div>
                </aside>
              </div>
            </template>

            <p
              v-if="editorError || props.errorMessage"
              class="avatar-editor-error"
            >
              {{ editorError || props.errorMessage }}
            </p>
          </div>

          <footer class="avatar-editor-footer">
            <AppButton
              variant="secondary"
              :disabled="props.uploading || processing"
              @click="closeDialog"
            >
              取消
            </AppButton>
            <AppButton
              :loading="props.uploading || processing"
              :disabled="!canConfirm"
              @click="emitConfirm"
            >
              确认上传
            </AppButton>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss">
.avatar-editor-mask {
  position: fixed;
  inset: 0;
  z-index: 1400;
  display: grid;
  place-items: center;
  background: rgba(15, 23, 42, 0.48);
  padding: clamp(10px, 2vw, 20px);
  backdrop-filter: blur(8px);
  overscroll-behavior: contain;
  touch-action: none;
}

.avatar-editor-panel {
  display: grid;
  gap: 18px;
  width: min(1180px, 100%);
  max-height: min(92vh, 920px);
  overflow: auto;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: var(--yn-color-surface);
  box-shadow: var(--yn-shadow-overlay);
  padding: 20px;
}

.avatar-editor-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.avatar-editor-title {
  margin: 0;
  color: var(--yn-color-text-primary);
  font-size: 20px;
  font-weight: 700;
}

.avatar-editor-close {
  display: inline-flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--yn-color-border-medium);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface-raised);
  color: var(--yn-color-text-secondary);
  cursor: pointer;
}

.avatar-editor-close:disabled {
  cursor: not-allowed;
}

.avatar-editor-body {
  display: grid;
  gap: 14px;
}

.avatar-editor-hidden-input {
  display: none;
}

.avatar-editor-empty {
  display: grid;
  min-height: 420px;
}

.avatar-editor-dropzone {
  display: grid;
  width: 100%;
  min-height: 420px;
  align-content: center;
  justify-items: center;
  gap: 16px;
  border: 1px dashed var(--yn-color-border-medium);
  border-radius: var(--yn-radius-large);
  background: color-mix(
    in srgb,
    var(--yn-color-surface-raised) 88%,
    var(--yn-color-surface)
  );
  color: var(--yn-color-text-secondary);
  text-align: center;
  padding: 24px;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease;
}

.avatar-editor-empty-active .avatar-editor-dropzone,
.avatar-editor-dropzone:hover,
.avatar-editor-dropzone:focus-visible {
  border-color: var(--yn-color-primary);
  color: var(--yn-color-text-primary);
  box-shadow: var(--yn-glow-subtle);
}

.avatar-editor-dropzone-copy {
  display: grid;
  gap: 4px;
}

.avatar-editor-dropzone strong {
  font-size: 20px;
  line-height: 1.2;
}

.avatar-editor-dropzone span {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.25;
}

.avatar-editor-dropzone small {
  font-size: 12px;
  line-height: 1.3;
}

.avatar-editor-dropzone-icon {
  font-size: 52px;
  color: var(--yn-color-primary);
}

.avatar-editor-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 248px;
  gap: clamp(16px, 2vw, 24px);
}

.avatar-editor-main-pending {
  opacity: 0.96;
}

.avatar-editor-workbench {
  display: grid;
  gap: 16px;
}

.avatar-editor-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.avatar-editor-stage-wrap {
  --avatar-stage-wrap-padding: clamp(12px, 2vw, 28px);
  position: relative;
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background:
    linear-gradient(45deg, rgba(148, 163, 184, 0.12) 25%, transparent 25%),
    linear-gradient(-45deg, rgba(148, 163, 184, 0.12) 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, rgba(148, 163, 184, 0.12) 75%),
    linear-gradient(-45deg, transparent 75%, rgba(148, 163, 184, 0.12) 75%);
  background-size: 24px 24px;
  background-position:
    0 0,
    0 12px,
    12px -12px,
    -12px 0;
}

.avatar-editor-stage-loading {
  position: absolute;
  inset: var(--avatar-stage-wrap-padding);
  display: grid;
  place-items: center;
  border-radius: var(--yn-radius-large);
  background: color-mix(in srgb, rgba(15, 23, 42, 0.72) 82%, transparent);
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  pointer-events: none;
  z-index: 1;
}

.avatar-editor-stage {
  position: relative;
  width: 100%;
  height: var(--avatar-stage-size);
  max-width: 100%;
  overflow: hidden;
  border-radius: var(--yn-radius-large);
  background:
    linear-gradient(45deg, rgba(148, 163, 184, 0.16) 25%, transparent 25%),
    linear-gradient(-45deg, rgba(148, 163, 184, 0.16) 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, rgba(148, 163, 184, 0.16) 75%),
    linear-gradient(-45deg, transparent 75%, rgba(148, 163, 184, 0.16) 75%),
    color-mix(in srgb, var(--yn-color-surface-raised) 88%, transparent);
  background-size: 24px 24px;
  background-position:
    0 0,
    0 12px,
    12px -12px,
    -12px 0;
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.avatar-editor-stage:active {
  cursor: grabbing;
}

.avatar-editor-image {
  position: absolute;
  transform-origin: center center;
  will-change: transform;
  pointer-events: none;
}

.avatar-editor-stage-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.avatar-editor-crop-frame {
  position: absolute;
  left: 50%;
  top: 50%;
  width: var(--avatar-crop-frame-size);
  height: var(--avatar-crop-frame-size);
  transform: translate(-50%, -50%);
  border: 2px solid rgba(255, 255, 255, 0.96);
  border-radius: calc(var(--yn-radius-large) - 2px);
  box-shadow: 0 0 0 9999px rgba(148, 163, 184, 0.34);
  pointer-events: none;
}

.avatar-editor-preview-panel {
  display: grid;
  align-content: start;
  gap: 14px;
}

.avatar-editor-preview-card,
.avatar-editor-file-meta {
  display: grid;
  gap: 10px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: var(--yn-color-surface-raised);
  padding: 16px;
}

.avatar-editor-preview-label {
  color: var(--yn-color-text-primary);
  font-size: 14px;
  font-weight: 700;
}

.avatar-editor-preview-rect {
  display: grid;
  width: 148px;
  height: 148px;
  place-self: center;
  overflow: hidden;
  border-radius: calc(var(--yn-radius-large) + 2px);
  background: rgba(15, 23, 42, 0.88);
  border: 1px solid var(--yn-color-border-medium);
}

.avatar-editor-preview-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-editor-preview-tip,
.avatar-editor-file-hint {
  margin: 0;
  color: var(--yn-color-text-tertiary);
  font-size: 12px;
  line-height: 1.6;
}

.avatar-editor-file-name {
  color: var(--yn-color-text-primary);
  font-size: 13px;
  font-weight: 600;
  word-break: break-all;
}

.avatar-editor-error {
  margin: 0;
  color: #b91c1c;
  font-size: 13px;
  line-height: 1.6;
}

.avatar-editor-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.avatar-editor-dialog-enter-active,
.avatar-editor-dialog-leave-active {
  transition: opacity 0.18s ease;
}

.avatar-editor-dialog-enter-from,
.avatar-editor-dialog-leave-to {
  opacity: 0;
}

@media (max-width: 1120px) {
  .avatar-editor-main {
    grid-template-columns: 1fr;
  }

  .avatar-editor-preview-panel {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
  }
}

@media (max-width: 720px) {
  .avatar-editor-panel {
    padding: 14px;
  }

  .avatar-editor-preview-panel {
    grid-template-columns: 1fr;
  }
}
</style>
