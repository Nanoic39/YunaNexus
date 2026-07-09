<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    show: boolean;
    title: string;
    width?: string;
  }>(),
  {
    width: "420px",
  },
);

const emit = defineEmits<{
  close: [];
}>();

const modalRef = ref<HTMLDivElement>();

function onOverlayClick(e: MouseEvent) {
  if (e.target === e.currentTarget) {
    emit("close");
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") {
    emit("close");
  }
}

function trapFocus() {
  if (!modalRef.value) return;
  const focusable = modalRef.value.querySelectorAll<HTMLElement>(
    'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
  );
  if (focusable.length > 0) {
    focusable[0].focus();
  }
}

watch(
  () => props.show,
  (val) => {
    if (val) {
      document.addEventListener("keydown", onKeydown);
      nextTick(() => trapFocus());
    } else {
      document.removeEventListener("keydown", onKeydown);
    }
  },
);

onUnmounted(() => {
  document.removeEventListener("keydown", onKeydown);
});
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="show" class="modal-overlay" @click="onOverlayClick">
        <div ref="modalRef" class="modal-panel" :style="{ width }" role="dialog" aria-modal="true" :aria-label="title">
          <div class="modal-header">
            <h3 class="modal-title">{{ title }}</h3>
            <button class="modal-close" @click="emit('close')">
              <Icon name="lucide:x" size="16" />
            </button>
          </div>
          <div class="modal-body">
            <slot />
          </div>
          <div v-if="$slots.footer" class="modal-footer">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 9998;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(2px);
}

.modal-panel {
  background: var(--color-card);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-overlay);
  display: flex;
  flex-direction: column;
  max-height: 90vh;
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 0;
  flex-shrink: 0;
}

.modal-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-font);
}

.modal-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  color: var(--color-font-assist);
  cursor: pointer;
  transition: all 0.1s;
}

.modal-close:hover {
  background: var(--color-primary-background);
  color: var(--color-font);
}

.modal-body {
  padding: 16px 20px;
  overflow-y: auto;
  color: var(--color-font-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 0 20px 18px;
  flex-shrink: 0;
}

.modal-enter-active {
  transition: all 0.2s ease-out;
}

.modal-leave-active {
  transition: all 0.15s ease-in;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .modal-panel {
  transform: scale(0.96);
}

.modal-leave-to .modal-panel {
  transform: scale(0.96);
}
</style>
