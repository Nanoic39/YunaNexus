<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    label?: string;
    required?: boolean;
    helper?: string;
    error?: string;
  }>(),
  {
    label: "",
    required: false,
    helper: "",
    error: "",
  },
);
</script>

<template>
  <div class="app-form-field">
    <div v-if="props.label" class="app-form-field-head">
      <span class="app-form-field-label">{{ props.label }}</span>
      <span v-if="props.required" class="app-form-field-required">*</span>
    </div>

    <div class="app-form-field-body">
      <slot />
    </div>

    <p v-if="props.error" class="app-form-field-error">
      {{ props.error }}
    </p>
    <p v-else-if="props.helper" class="app-form-field-helper">
      {{ props.helper }}
    </p>
  </div>
</template>

<style scoped lang="scss">
.app-form-field {
  display: grid;
  gap: 8px;
}

.app-form-field-head {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.app-form-field-label {
  color: var(--yn-color-text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.app-form-field-required {
  color: #dc2626;
  font-size: 13px;
  line-height: 1;
}

.app-form-field-body {
  min-width: 0;
}

.app-form-field-helper,
.app-form-field-error {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
}

.app-form-field-helper {
  color: var(--yn-color-text-tertiary);
}

.app-form-field-error {
  color: #b91c1c;
}
</style>
