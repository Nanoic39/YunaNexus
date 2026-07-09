<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    text: string;
    color?: string;
    fontSize?: number;
    fontWeight?: number;
    rotate?: number;
    gapX?: number;
    gapY?: number;
    fontFamily?: string;
  }>(),
  {
    color: "rgba(0, 0, 0, 0.06)",
    fontSize: 14,
    fontWeight: 400,
    rotate: -22,
    gapX: 200,
    gapY: 160,
    fontFamily: "inherit",
  },
);

const containerRef = ref<HTMLDivElement>();

function generatePattern(): string | null {
  if (!props.text || import.meta.server) return null;

  const canvas = document.createElement("canvas");
  const lines = props.text.split("\\n").flatMap((l) => l.split("\n"));

  canvas.width = props.gapX;
  canvas.height = props.gapY;

  const ctx = canvas.getContext("2d");
  if (!ctx) return null;

  ctx.fillStyle = props.color;
  ctx.font = `${props.fontWeight} ${props.fontSize}px ${props.fontFamily}`;
  ctx.textAlign = "center";
  ctx.textBaseline = "top";
  ctx.rotate((props.rotate * Math.PI) / 180);

  const lineHeight = props.fontSize * 1.5;
  const totalHeight = lines.length * lineHeight;
  const startY = (props.gapY - totalHeight) / 2;
  const x = props.gapX / 2;

  lines.forEach((line, i) => {
    ctx.fillText(line, x, startY + i * lineHeight);
  });

  return canvas.toDataURL();
}

const patternDataUrl = ref<string | null>(null);

onMounted(() => {
  patternDataUrl.value = generatePattern();
});

const overlayRef = ref<HTMLDivElement>();
let observer: MutationObserver | null = null;

// 防移除：MutationObserver 监听水印层被删除后自动恢复
onMounted(() => {
  const container = containerRef.value;
  const overlay = overlayRef.value;
  if (!container || !overlay) return;

  observer = new MutationObserver((mutations) => {
    for (const m of mutations) {
      for (const node of m.removedNodes) {
        if (node === overlay || overlay.contains(node as Node)) {
          try {
            container.insertBefore(overlay, container.firstChild);
          } catch {
            /* container may have been detached */
          }
        }
      }
    }
  });

  observer.observe(container, { childList: true, subtree: false });

  // 防 style 被覆盖：在 style 属性上设置 !important 关键属性
  overlay.style.setProperty("position", "absolute", "important");
  overlay.style.setProperty("inset", "0", "important");
  overlay.style.setProperty("pointer-events", "none", "important");
  overlay.style.setProperty("z-index", "9999", "important");
});

onUnmounted(() => {
  observer?.disconnect();
  observer = null;
});
</script>

<template>
  <div ref="containerRef" style="position: relative">
    <div
      ref="overlayRef"
      class="watermark-overlay"
      :style="{
        backgroundImage: patternDataUrl ? `url(${patternDataUrl})` : 'none',
      }"
    />
    <slot />
  </div>
</template>

<style scoped>
.watermark-overlay {
  pointer-events: none;
  position: absolute;
  inset: 0;
  z-index: 9999;
  background-repeat: repeat;
}
</style>
