<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    type?: "button" | "submit" | "reset";
    variant?: "primary" | "secondary" | "danger";
    block?: boolean;
    loading?: boolean;
    disabled?: boolean;
  }>(),
  {
    type: "button",
    variant: "primary",
    block: false,
    loading: false,
    disabled: false,
  },
);
</script>

<template>
  <button
    :type="props.type"
    class="app-button"
    :class="[
      `app-button-${props.variant}`,
      { 'app-button-block': props.block, 'app-button-loading': props.loading },
    ]"
    :disabled="props.disabled || props.loading"
  >
    <span v-if="props.loading" class="app-button-spinner" aria-hidden="true" />
    <span class="app-button-label">
      <slot />
    </span>
  </button>
</template>

<style scoped lang="scss">
.app-button {
  display: inline-flex;
  min-height: 46px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid transparent;
  border-radius: var(--yn-radius-medium);
  padding: 0 16px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease,
    opacity 0.2s ease;
}

.app-button:hover:not(:disabled) {
  filter: brightness(0.97);
}

.app-button:focus-visible {
  outline: none;
  box-shadow: var(--yn-glow-medium);
}

.app-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.app-button-block {
  width: 100%;
}

.app-button-primary {
  border-color: var(--yn-color-primary);
  background: var(--yn-color-primary);
  color: #ffffff;
}

.app-button-secondary {
  border-color: var(--yn-color-border-medium);
  background: var(--yn-color-surface-raised);
  color: var(--yn-color-text-primary);
}

.app-button-danger {
  border-color: rgba(220, 38, 38, 0.24);
  background: rgba(220, 38, 38, 0.08);
  color: #b91c1c;
}

.app-button-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: app-button-spin 0.65s linear infinite;
}

.app-button-label {
  line-height: 1;
}

@keyframes app-button-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
