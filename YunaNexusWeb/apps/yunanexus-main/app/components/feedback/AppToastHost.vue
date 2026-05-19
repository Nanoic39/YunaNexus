<script setup lang="ts">
import { computed } from "vue";
import { useAppToast, type AppToastItem, type AppToastType } from "../../composables/useAppToast";

const toast = useAppToast();

const iconMap: Record<AppToastType, string> = {
  success: "lucide:check-circle-2",
  error: "lucide:circle-alert",
  info: "lucide:info",
};

const titleMap: Record<AppToastType, string> = {
  success: "操作成功",
  error: "操作失败",
  info: "提示",
};

const isToastType = (value: unknown): value is AppToastType =>
  value === "success" || value === "error" || value === "info";

const toastItems = computed(() => {
  const source = toast.toasts.value;
  if (!Array.isArray(source)) {
    return [] as AppToastItem[];
  }
  return source.filter(
    (item): item is AppToastItem =>
      !!item &&
      typeof item.id === "string" &&
      typeof item.message === "string" &&
      isToastType(item.type),
  );
});

const resolveToastIcon = (type: AppToastType) => iconMap[type] || iconMap.info;
const resolveToastTitle = (item: AppToastItem) =>
  item.title || titleMap[item.type] || titleMap.info;
</script>

<template>
  <Teleport to="body">
    <div class="app-toast-host" aria-live="polite" aria-atomic="true">
      <TransitionGroup name="app-toast">
        <section
          v-for="item in toastItems"
          :key="item.id"
          class="app-toast-item"
          :class="`app-toast-${item.type}`"
        >
          <div class="app-toast-icon">
            <Icon :name="resolveToastIcon(item.type)" />
          </div>
          <div class="app-toast-content">
            <strong class="app-toast-title">
              {{ resolveToastTitle(item) }}
            </strong>
            <p class="app-toast-message">{{ item.message }}</p>
          </div>
          <button
            class="app-toast-close"
            type="button"
            aria-label="关闭提示"
            @click="toast.remove(item.id)"
          >
            <Icon name="lucide:x" />
          </button>
        </section>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped lang="scss">
.app-toast-host {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 1600;
  display: grid;
  gap: 10px;
  width: min(360px, calc(100vw - 24px));
  pointer-events: none;
}

.app-toast-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: color-mix(in srgb, var(--yn-color-surface) 94%, transparent);
  box-shadow: var(--yn-shadow-overlay);
  backdrop-filter: blur(14px);
  padding: 14px;
  pointer-events: auto;
}

.app-toast-success {
  border-color: rgba(22, 163, 74, 0.24);
}

.app-toast-error {
  border-color: rgba(220, 38, 38, 0.22);
}

.app-toast-info {
  border-color: rgba(59, 130, 246, 0.22);
}

.app-toast-icon {
  display: inline-flex;
  width: 20px;
  height: 20px;
  align-items: center;
  justify-content: center;
  margin-top: 1px;
  font-size: 18px;
}

.app-toast-success .app-toast-icon {
  color: #15803d;
}

.app-toast-error .app-toast-icon {
  color: #b91c1c;
}

.app-toast-info .app-toast-icon {
  color: #2563eb;
}

.app-toast-content {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.app-toast-title {
  color: var(--yn-color-text-primary);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.25;
}

.app-toast-message {
  margin: 0;
  color: var(--yn-color-text-secondary);
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}

.app-toast-close {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: var(--yn-radius-small);
  background: transparent;
  color: var(--yn-color-text-tertiary);
  cursor: pointer;
}

.app-toast-close:hover {
  background: var(--yn-color-surface-raised);
  color: var(--yn-color-text-primary);
}

.app-toast-enter-active,
.app-toast-leave-active {
  transition: all 0.2s ease;
}

.app-toast-enter-from,
.app-toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media (max-width: 640px) {
  .app-toast-host {
    top: 12px;
    right: 12px;
    left: 12px;
    width: auto;
  }
}
</style>
