<script setup lang="ts">
withDefaults(
  defineProps<{
    show: boolean;
    title: string;
    message: string;
    width?: string;
    confirmText?: string;
    cancelText?: string;
    danger?: boolean;
  }>(),
  {
    width: "420px",
    confirmText: "确认",
    cancelText: "取消",
    danger: false,
  },
);

const emit = defineEmits<{
  confirm: [];
  cancel: [];
  close: [];
}>();

function onConfirm() {
  emit("confirm");
  emit("close");
}

function onCancel() {
  emit("cancel");
  emit("close");
}
</script>

<template>
  <Modal :show="show" :title="title" :width="width" @close="onCancel">
    <p>{{ message }}</p>
    <template #footer>
      <button class="button" @click="onCancel">{{ cancelText }}</button>
      <button
        class="button"
        :class="danger ? 'button-danger' : 'button-primary'"
        @click="onConfirm"
      >
        {{ confirmText }}
      </button>
    </template>
  </Modal>
</template>
