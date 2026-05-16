<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    modelValue?: boolean;
    disabled?: boolean;
  }>(),
  {
    modelValue: false,
    disabled: false,
  },
);

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
}>();

const toggleChecked = () => {
  if (props.disabled) {
    return;
  }

  emit("update:modelValue", !props.modelValue);
};
</script>

<template>
  <label
    class="app-checkbox"
    :class="{ 'app-checkbox-disabled': props.disabled }"
  >
    <input
      class="app-checkbox-native"
      type="checkbox"
      :checked="props.modelValue"
      :disabled="props.disabled"
      @change="toggleChecked"
    />
    <span
      class="app-checkbox-box"
      :class="{ 'app-checkbox-box-checked': props.modelValue }"
      aria-hidden="true"
    >
      <svg viewBox="0 0 16 16" class="app-checkbox-icon">
        <path
          d="M3.5 8.5 6.5 11.5 12.5 4.5"
          fill="none"
          stroke="currentColor"
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="2"
        />
      </svg>
    </span>
    <span class="app-checkbox-label">
      <slot />
    </span>
  </label>
</template>

<style scoped lang="scss">
.app-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--yn-color-text-secondary);
  font-size: 14px;
  line-height: 1.6;
  cursor: pointer;
}

.app-checkbox-disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.app-checkbox-native {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.app-checkbox-box {
  display: inline-flex;
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--yn-color-border-medium);
  border-radius: 4px;
  background: var(--yn-color-surface-raised);
  color: transparent;
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease;
}

.app-checkbox-box-checked {
  border-color: var(--yn-color-primary);
  background: var(--yn-color-primary);
  color: #ffffff;
  box-shadow: var(--yn-glow-subtle);
}

.app-checkbox-icon {
  width: 12px;
  height: 12px;
}

.app-checkbox-label {
  min-width: 0;
}
</style>
