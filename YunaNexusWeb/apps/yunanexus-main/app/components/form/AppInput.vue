<script setup lang="ts">
import type { InputHTMLAttributes } from "vue";

const props = withDefaults(
  defineProps<{
    modelValue?: string | number;
    type?: string;
    placeholder?: string;
    autocomplete?: string;
    maxlength?: number;
    disabled?: boolean;
    name?: string;
    inputmode?: InputHTMLAttributes["inputmode"];
  }>(),
  {
    modelValue: "",
    type: "text",
    placeholder: "",
    autocomplete: undefined,
    maxlength: undefined,
    disabled: false,
    name: undefined,
    inputmode: undefined,
  },
);

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
  (e: "blur", event: FocusEvent): void;
  (e: "focus", event: FocusEvent): void;
}>();

const updateValue = (event: Event) => {
  emit("update:modelValue", (event.target as HTMLInputElement).value);
};
</script>

<template>
  <div class="app-input-wrap">
    <span v-if="$slots.prefix" class="app-input-prefix">
      <slot name="prefix" />
    </span>
    <input
      class="app-input"
      :type="props.type"
      :value="props.modelValue"
      :placeholder="props.placeholder"
      :autocomplete="props.autocomplete"
      :maxlength="props.maxlength"
      :disabled="props.disabled"
      :name="props.name"
      :inputmode="props.inputmode"
      @input="updateValue"
      @blur="emit('blur', $event)"
      @focus="emit('focus', $event)"
    />
    <span v-if="$slots.suffix" class="app-input-suffix">
      <slot name="suffix" />
    </span>
  </div>
</template>

<style scoped lang="scss">
.app-input-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 46px;
  width: 100%;
  border: 1px solid var(--yn-color-border-medium);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface-raised);
  padding: 0 14px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

.app-input-wrap:focus-within {
  border-color: var(--yn-color-primary);
  box-shadow: var(--yn-glow-medium);
}

.app-input {
  flex: 1;
  min-width: 0;
  height: 44px;
  border: 0;
  background: transparent;
  color: var(--yn-color-text-primary);
  padding: 0;
}

.app-input::placeholder {
  color: var(--yn-color-text-tertiary);
}

.app-input:focus {
  outline: none;
}

.app-input:disabled {
  cursor: not-allowed;
}

.app-input-wrap:has(.app-input:disabled) {
  opacity: 0.66;
}

.app-input-prefix,
.app-input-suffix {
  display: inline-flex;
  align-items: center;
  color: var(--yn-color-text-tertiary);
}
</style>
