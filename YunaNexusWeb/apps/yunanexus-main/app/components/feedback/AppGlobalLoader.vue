<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";

const props = withDefaults(
  defineProps<{ scope?: "fullscreen" | "content" }>(),
  { scope: "fullscreen" },
);

const pageLoader = usePageLoader();
const visible = ref(false);
const loading = ref(false);

let showTimer: ReturnType<typeof window.setTimeout> | null = null;
let hideTimer: ReturnType<typeof window.setTimeout> | null = null;
let loadingStartedAt = 0;

const showDelayMs = 90;
const minVisibleMs = 220;
const shouldShow = computed(() =>
  props.scope === "fullscreen"
    ? pageLoader.showFullscreen.value
    : pageLoader.showContent.value,
);

const clearShowTimer = () => {
  if (showTimer !== null) {
    window.clearTimeout(showTimer);
    showTimer = null;
  }
};

const clearHideTimer = () => {
  if (hideTimer !== null) {
    window.clearTimeout(hideTimer);
    hideTimer = null;
  }
};

const revealLoader = () => {
  clearHideTimer();
  loading.value = true;
  visible.value = true;
  loadingStartedAt = window.performance.now();
};

const beginLoading = (immediate = false) => {
  clearShowTimer();
  clearHideTimer();

  if (visible.value) {
    loading.value = true;
    return;
  }

  if (immediate) {
    revealLoader();
    return;
  }

  showTimer = setTimeout(() => {
    revealLoader();
  }, showDelayMs);
};

const finishLoading = () => {
  clearShowTimer();
  if (!visible.value) {
    return;
  }

  loading.value = false;
  const elapsed = window.performance.now() - loadingStartedAt;
  const wait = Math.max(0, minVisibleMs - elapsed);

  hideTimer = setTimeout(() => {
    visible.value = false;
  }, wait + 90);
};

watch(
  shouldShow,
  (next) => {
    if (next) {
      beginLoading(props.scope === "fullscreen");
      return;
    }
    finishLoading();
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  clearShowTimer();
  clearHideTimer();
});
</script>

<template>
  <Transition name="app-global-loader">
    <div
      v-if="visible"
      class="app-global-loader"
      :class="[
        `app-global-loader-${props.scope}`,
        { 'app-global-loader-active': loading },
      ]"
      aria-live="polite"
      aria-busy="true"
    >
      <div class="app-global-loader-backdrop" />
      <div class="app-global-loader-graphic" aria-hidden="true">
        <span class="app-global-loader-track" />
        <span class="app-global-loader-dot app-global-loader-dot-primary" />
        <span class="app-global-loader-dot app-global-loader-dot-secondary" />
      </div>
    </div>
  </Transition>
</template>

<style scoped lang="scss">
.app-global-loader {
  display: grid;
  place-items: center;
  padding: 24px;
}

.app-global-loader-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 1200;
}

.app-global-loader-content {
  position: absolute;
  inset: 0;
  z-index: 12;
  border-radius: var(--yn-radius-large);
  overflow: hidden;
}

.app-global-loader-backdrop {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at top, rgba(22, 163, 74, 0.12), transparent 38%),
    linear-gradient(
      180deg,
      color-mix(in srgb, var(--yn-color-background) 88%, transparent) 0%,
      color-mix(in srgb, var(--yn-color-surface) 92%, transparent) 100%
    );
  backdrop-filter: blur(14px);
}

.app-global-loader-content .app-global-loader-backdrop {
  background: color-mix(in srgb, var(--yn-color-surface) 84%, transparent);
  backdrop-filter: blur(10px);
}

.app-global-loader-graphic {
  position: relative;
  width: 78px;
  height: 78px;
}

.app-global-loader-track {
  position: absolute;
  inset: 0;
  border: 2px solid
    color-mix(in srgb, var(--yn-color-border-medium) 72%, transparent);
  border-radius: 50%;
  opacity: 0.7;
}

.app-global-loader-dot {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 14px;
  height: 14px;
  margin-left: -7px;
  margin-top: -7px;
  border-radius: 50%;
}

.app-global-loader-dot-primary {
  background: var(--yn-color-primary);
  box-shadow: 0 0 0 6px
    color-mix(in srgb, var(--yn-color-primary) 14%, transparent);
  animation: yn-global-loader-orbit 1s linear infinite;
}

.app-global-loader-dot-secondary {
  background: color-mix(
    in srgb,
    var(--yn-color-text-tertiary) 82%,
    transparent
  );
  transform: rotate(180deg) translateX(39px);
  animation: yn-global-loader-orbit-reverse 1.25s linear infinite;
}

.app-global-loader-enter-active,
.app-global-loader-leave-active {
  transition:
    opacity 0.22s ease,
    visibility 0.22s ease;
}

.app-global-loader-enter-from,
.app-global-loader-leave-to {
  opacity: 0;
}

.app-global-loader-enter-from .app-global-loader-graphic,
.app-global-loader-leave-to .app-global-loader-graphic {
  transform: scale(0.92);
  opacity: 0;
}

.app-global-loader-enter-active .app-global-loader-graphic,
.app-global-loader-leave-active .app-global-loader-graphic {
  transition:
    transform 0.22s ease,
    opacity 0.22s ease;
}

@keyframes yn-global-loader-orbit {
  from {
    transform: rotate(0deg) translateX(39px);
  }
  to {
    transform: rotate(360deg) translateX(39px);
  }
}

@keyframes yn-global-loader-orbit-reverse {
  from {
    transform: rotate(180deg) translateX(39px);
  }
  to {
    transform: rotate(-180deg) translateX(39px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .app-global-loader-dot-primary,
  .app-global-loader-dot-secondary {
    animation: none;
  }

  .app-global-loader-enter-active,
  .app-global-loader-leave-active {
    transition: none;
  }
}

@media (max-width: 640px) {
  .app-global-loader-graphic {
    width: 64px;
    height: 64px;
  }
}
</style>
