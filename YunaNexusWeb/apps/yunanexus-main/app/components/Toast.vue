<script setup lang="ts">
const { toasts } = useToast();

const iconMap: Record<string, string> = {
  success: "lucide:check-circle",
  error: "lucide:alert-circle",
  warning: "lucide:alert-triangle",
  info: "lucide:info",
};

const colorMap: Record<string, string> = {
  success: "#389e0d",
  error: "#b31f1f",
  warning: "#d48806",
  info: "var(--color-emphasis)",
};
</script>

<template>
  <Teleport to="body">
    <TransitionGroup name="toast" tag="div" class="toast-container">
      <div
        v-for="t in toasts"
        :key="t.id"
        class="toast-item"
        :style="{ borderColor: colorMap[t.type] }"
      >
        <Icon :name="iconMap[t.type]" :style="{ color: colorMap[t.type] }" size="16" />
        <span class="toast-message">{{ t.message }}</span>
      </div>
    </TransitionGroup>
  </Teleport>
</template>

<style scoped>
.toast-container {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  pointer-events: none;
}

.toast-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: var(--radius-md);
  font-size: 13px;
  background: var(--color-card);
  box-shadow: var(--shadow-overlay);
  border-left: 3px solid;
  pointer-events: auto;
  max-width: 420px;
}

.toast-message {
  color: var(--color-font);
}

.toast-enter-active {
  transition: all 0.25s ease-out;
}

.toast-leave-active {
  transition: all 0.2s ease-in;
}

.toast-enter-from {
  opacity: 0;
  transform: translateY(12px);
}

.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
