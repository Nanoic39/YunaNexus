<script setup lang="ts">
/**
 * 头像裁剪组件 — canvas 裁剪 + 缩放滑块 + 拖拽。
 * 输入: File（图片文件） / 输出: @confirm 返回裁剪后的 Blob
 */
import { useToast } from "~/composables/useToast";

const props = defineProps<{
  file: File;
  /** 输出尺寸，默认 256x256 */
  outputSize?: number;
}>();

const emit = defineEmits<{
  confirm: [blob: Blob];
  cancel: [];
}>();

const toast = useToast();

const canvasRef = ref<HTMLCanvasElement>();
const imageRef = ref<HTMLImageElement>();
const imgUrl = ref("");
const imgLoaded = ref(false);

const CROP_SIZE = props.outputSize || 256;
const CANVAS_SIZE = 340; // 可视化区域尺寸
const canvasSizePx = CANVAS_SIZE + "px";


// 缩放状态
const scale = ref(1);
const minScale = 0.5;
const maxScale = 3;

// 图片实际绘制位置与尺寸
const imgX = ref(0);
const imgY = ref(0);
const imgW = ref(0);
const imgH = ref(0);

// 拖拽状态
const dragging = ref(false);
const dragStartX = ref(0);
const dragStartY = ref(0);
const dragStartImgX = ref(0);
const dragStartImgY = ref(0);

// --- 初始化：加载图片 ---
watch(
  () => props.file,
  (file) => {
    if (!file) return;
    if (imgUrl.value) URL.revokeObjectURL(imgUrl.value);
    imgUrl.value = URL.createObjectURL(file);
    imgLoaded.value = false;
    scale.value = 1;
  },
  { immediate: true },
);

function onImageLoad() {
  imgLoaded.value = true;
  fitImage();
}

function fitImage() {
  const img = imageRef.value;
  if (!img) return;

  const imgNaturalW = img.naturalWidth;
  const imgNaturalH = img.naturalHeight;

  // 计算缩放使图片至少填满裁剪框
  const fitScale = Math.max(CROP_SIZE / imgNaturalW, CROP_SIZE / imgNaturalH);
  scale.value = Math.max(minScale, Math.min(maxScale, fitScale));

  const w = imgNaturalW * scale.value;
  const h = imgNaturalH * scale.value;

  imgW.value = w;
  imgH.value = h;
  imgX.value = (CANVAS_SIZE - w) / 2;
  imgY.value = (CANVAS_SIZE - h) / 2;
}

// --- 裁剪区域 ---
const cropX = computed(() => (CANVAS_SIZE - CROP_SIZE) / 2);
const cropY = computed(() => (CANVAS_SIZE - CROP_SIZE) / 2);

// --- 渲染 ---
function draw() {
  const canvas = canvasRef.value;
  const img = imageRef.value;
  if (!canvas || !img || !imgLoaded.value) return;

  const ctx = canvas.getContext("2d")!;
  canvas.width = CANVAS_SIZE;
  canvas.height = CANVAS_SIZE;

  ctx.clearRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);

  // 绘制图片
  ctx.drawImage(img, imgX.value, imgY.value, imgW.value, imgH.value);

  // 绘制暗色遮罩（裁剪区域外）
  ctx.save();
  ctx.fillStyle = "rgba(0, 0, 0, 0.55)";
  ctx.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
  ctx.clearRect(cropX.value, cropY.value, CROP_SIZE, CROP_SIZE);
  ctx.restore();

  // 裁剪框边框
  ctx.save();
  ctx.strokeStyle = "#fff";
  ctx.lineWidth = 2;
  ctx.strokeRect(cropX.value, cropY.value, CROP_SIZE, CROP_SIZE);
  ctx.restore();

  // 角落辅助线
  ctx.save();
  ctx.strokeStyle = "rgba(255, 255, 255, 0.5)";
  ctx.lineWidth = 1;
  const cornerLen = 16;
  const corners: [number, number, number, number][] = [
    [cropX.value, cropY.value, -1, -1],
    [cropX.value + CROP_SIZE, cropY.value, 1, -1],
    [cropX.value, cropY.value + CROP_SIZE, -1, 1],
    [cropX.value + CROP_SIZE, cropY.value + CROP_SIZE, 1, 1],
  ];
  for (const [cx, cy, dx, dy] of corners) {
    ctx.beginPath();
    ctx.moveTo(cx, cy + dy * cornerLen);
    ctx.lineTo(cx, cy);
    ctx.lineTo(cx + dx * cornerLen, cy);
    ctx.stroke();
  }
  ctx.restore();
}

watch([imgLoaded, scale], () => {
  nextTick(draw);
});

watch([imgX, imgY, imgW, imgH], () => {
  if (imgLoaded.value) nextTick(draw);
});

// --- 缩放 ---
function onZoomChange(e: Event) {
  const val = Number((e.target as HTMLInputElement).value);
  const oldScale = scale.value;
  scale.value = val;

  // 保持裁剪中心不变
  const cx = cropX.value + CROP_SIZE / 2;
  const cy = cropY.value + CROP_SIZE / 2;
  const ratio = val / oldScale;
  imgX.value = cx - (cx - imgX.value) * ratio;
  imgY.value = cy - (cy - imgY.value) * ratio;
  imgW.value = imgW.value * ratio;
  imgH.value = imgH.value * ratio;
}

// --- 拖拽 ---
function onPointerDown(e: PointerEvent) {
  dragging.value = true;
  dragStartX.value = e.clientX;
  dragStartY.value = e.clientY;
  dragStartImgX.value = imgX.value;
  dragStartImgY.value = imgY.value;
  (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value) return;
  const dx = e.clientX - dragStartX.value;
  const dy = e.clientY - dragStartY.value;
  imgX.value = dragStartImgX.value + dx;
  imgY.value = dragStartImgY.value + dy;
}

function onPointerUp() {
  dragging.value = false;
}

// --- 裁剪输出 ---
function doCrop(): Promise<Blob | null> {
  return new Promise((resolve) => {
    const img = imageRef.value;
    if (!img || !imgLoaded.value) {
      resolve(null);
      return;
    }

    const canvas = document.createElement("canvas");
    canvas.width = CROP_SIZE;
    canvas.height = CROP_SIZE;
    const ctx = canvas.getContext("2d")!;

    const srcX = (cropX.value - imgX.value) / scale.value;
    const srcY = (cropY.value - imgY.value) / scale.value;
    const srcW = CROP_SIZE / scale.value;
    const srcH = CROP_SIZE / scale.value;

    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = "high";
    ctx.drawImage(
      img,
      Math.max(0, srcX),
      Math.max(0, srcY),
      Math.min(img.naturalWidth, srcW),
      Math.min(img.naturalHeight, srcH),
      0,
      0,
      CROP_SIZE,
      CROP_SIZE,
    );

    canvas.toBlob((blob) => {
      resolve(blob);
    }, "image/png");
  });
}

async function confirm() {
  const blob = await doCrop();
  if (!blob) {
    toast.error("裁剪失败，请重试");
    return;
  }
  emit("confirm", blob);
}
</script>

<template>
  <Teleport to="body">
    <div class="crop-overlay" @click.self="emit('cancel')">
      <div class="crop-modal">
        <div class="crop-modal-header">
          <h3>裁剪头像</h3>
          <p class="crop-modal-sub">拖拽图片调整裁剪区域，滑块调节缩放</p>
        </div>

        <div class="crop-body">
          <div class="crop-canvas-wrap">
            <canvas
              ref="canvasRef"
              class="crop-canvas"
              :style="{ width: CANVAS_SIZE + 'px', height: CANVAS_SIZE + 'px' }"
              @pointerdown="onPointerDown"
              @pointermove="onPointerMove"
              @pointerup="onPointerUp"
              @pointerleave="onPointerUp"
            />
            <!-- 隐藏的原图用于绘制 -->
            <img
              ref="imageRef"
              :src="imgUrl"
              class="crop-source-img"
              @load="onImageLoad"
            />
          </div>

          <div class="crop-controls">
            <div class="crop-zoom">
              <Icon name="lucide:zoom-out" size="14" />
              <input
                type="range"
                class="crop-zoom-slider"
                :min="minScale"
                :max="maxScale"
                :step="0.01"
                :value="scale"
                @input="onZoomChange"
              />
              <Icon name="lucide:zoom-in" size="14" />
            </div>
            <span class="crop-zoom-label">{{ Math.round(scale * 100) }}%</span>
          </div>
        </div>

        <div class="crop-footer">
          <button class="button" @click="emit('cancel')">取消</button>
          <button class="button button-primary" @click="confirm">确认裁剪</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.crop-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  animation: crop-fade-in 0.15s ease;
}

@keyframes crop-fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.crop-modal {
  background: var(--color-primary-background);
  border-radius: var(--radius-lg);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.3);
  width: 420px;
  max-width: 95vw;
  overflow: hidden;
}

.crop-modal-header {
  padding: 20px 24px 12px;
}

.crop-modal-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-font);
}

.crop-modal-sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--color-font-assist);
}

.crop-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 24px 16px;
  gap: 12px;
}

.crop-canvas-wrap {
  position: relative;
  width: v-bind(canvasSizePx);
  height: v-bind(canvasSizePx);
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: grab;
  background: #1a1a1a;
}

.crop-canvas-wrap:active {
  cursor: grabbing;
}

.crop-canvas {
  display: block;
}

.crop-source-img {
  display: none;
}

.crop-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  max-width: 320px;
}

.crop-zoom {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  color: var(--color-font-assist);
}

.crop-zoom-slider {
  flex: 1;
  height: 4px;
  -webkit-appearance: none;
  appearance: none;
  background: var(--color-border);
  border-radius: 2px;
  outline: none;
  cursor: pointer;
}

.crop-zoom-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--color-emphasis);
  cursor: pointer;
  border: 2px solid #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

.crop-zoom-slider::-moz-range-thumb {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--color-emphasis);
  cursor: pointer;
  border: 2px solid #fff;
}

.crop-zoom-label {
  font-size: 12px;
  color: var(--color-font-assist);
  min-width: 36px;
  text-align: right;
}

.crop-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 24px 20px;
  border-top: 1px solid var(--color-border);
}
</style>
