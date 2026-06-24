<script setup lang="ts">
const model = defineModel<string>({ required: true });
const props = defineProps<{
  options: string[];
  placeholder?: string;
}>();

const open = ref(false);
const wrapperRef = ref<HTMLElement>();

function select(value: string) {
  model.value = value;
  open.value = false;
}

function onClickOutside(e: MouseEvent) {
  if (wrapperRef.value && !wrapperRef.value.contains(e.target as Node)) {
    open.value = false;
  }
}

onMounted(() => document.addEventListener("click", onClickOutside));
onUnmounted(() => document.removeEventListener("click", onClickOutside));
</script>

<template>
  <div ref="wrapperRef" class="select-input">
    <input
      type="text"
      class="select-input-field"
      :value="model"
      :placeholder="props.placeholder || '选择或输入'"
      @focus="open = true"
      @input="model = ($event.target as HTMLInputElement).value"
    />
    <button
      type="button"
      class="select-input-arrow"
      :class="{ open }"
      @click="open = !open"
    >
      <Icon name="lucide:chevron-down" size="14" />
    </button>
    <ul v-if="open" class="select-input-dropdown">
      <li
        v-for="opt in props.options"
        :key="opt"
        class="select-input-dropdown-item"
        @click="select(opt)"
      >
        {{ opt }}
      </li>
    </ul>
  </div>
</template>
