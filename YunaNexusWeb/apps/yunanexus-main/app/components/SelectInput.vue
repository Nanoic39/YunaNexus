<script setup lang="ts">
const props = withDefaults(defineProps<{
  modelValue: string;
  options: string[];
  placeholder?: string;
  disabled?: boolean;
  error?: string;
}>(), {
  placeholder: '请选择',
});

const emit = defineEmits<{
  'update:modelValue': [value: string];
}>();

const open = ref(false);
const wrapperRef = ref<HTMLElement>();
const inputRef = ref<HTMLInputElement>();
const searchText = ref('');
const highlightIndex = ref(-1);

const filteredOptions = computed(() => {
  if (!searchText.value) return props.options;
  const q = searchText.value.toLowerCase();
  return props.options.filter(opt => opt.toLowerCase().includes(q));
});

const displayValue = computed(() => {
  return open.value ? searchText.value : props.modelValue;
});

function toggleOpen() {
  if (props.disabled) return;
  if (open.value) {
    close();
  } else {
    openDropdown();
  }
}

function openDropdown() {
  if (props.disabled) return;
  open.value = true;
  searchText.value = '';
  highlightIndex.value = -1;
  nextTick(() => inputRef.value?.focus());
}

function close() {
  open.value = false;
  searchText.value = '';
  highlightIndex.value = -1;
}

function select(value: string) {
  emit('update:modelValue', value);
  close();
}

function onKeydown(e: KeyboardEvent) {
  if (props.disabled) return;

  if (!open.value) {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp' || e.key === 'Enter') {
      e.preventDefault();
      openDropdown();
    }
    return;
  }

  switch (e.key) {
    case 'ArrowDown':
      e.preventDefault();
      if (filteredOptions.value.length === 0) break;
      highlightIndex.value = highlightIndex.value < filteredOptions.value.length - 1
        ? highlightIndex.value + 1
        : 0;
      break;
    case 'ArrowUp':
      e.preventDefault();
      if (filteredOptions.value.length === 0) break;
      highlightIndex.value = highlightIndex.value > 0
        ? highlightIndex.value - 1
        : filteredOptions.value.length - 1;
      break;
    case 'Enter':
      e.preventDefault();
      if (highlightIndex.value >= 0 && highlightIndex.value < filteredOptions.value.length) {
        select(filteredOptions.value[highlightIndex.value]);
      }
      break;
    case 'Escape':
      e.preventDefault();
      close();
      break;
  }
}

function onClickOutside(e: MouseEvent) {
  if (wrapperRef.value && !wrapperRef.value.contains(e.target as Node)) {
    close();
  }
}

onMounted(() => document.addEventListener('click', onClickOutside));
onUnmounted(() => document.removeEventListener('click', onClickOutside));
</script>

<template>
  <div ref="wrapperRef" class="select-input">
    <input
      ref="inputRef"
      type="text"
      class="select-input-field"
      role="combobox"
      :aria-expanded="open"
      :aria-activedescendant="highlightIndex >= 0 ? `select-option-${highlightIndex}` : undefined"
      :value="displayValue"
      :placeholder="placeholder"
      :disabled="disabled"
      autocomplete="off"
      @click="openDropdown"
      @input="searchText = ($event.target as HTMLInputElement).value"
      @keydown="onKeydown"
    />
    <button
      type="button"
      class="select-input-arrow"
      :class="{ open }"
      :disabled="disabled"
      @click.stop="toggleOpen"
    >
      <Icon name="lucide:chevron-down" size="14" />
    </button>
    <ul
      v-if="open"
      class="select-input-dropdown"
      role="listbox"
    >
      <template v-if="props.options.length === 0">
        <li class="select-input-empty">
          暂无可选项
        </li>
      </template>
      <template v-else-if="filteredOptions.length === 0">
        <li class="select-input-empty">
          无匹配结果
        </li>
      </template>
      <template v-else>
        <li
          v-for="(opt, idx) in filteredOptions"
          :key="opt"
          :id="`select-option-${idx}`"
          class="select-input-dropdown-item"
          :class="{
            active: idx === highlightIndex,
            selected: opt === modelValue,
          }"
          role="option"
          :aria-selected="opt === modelValue"
          @click="select(opt)"
          @mouseenter="highlightIndex = idx"
        >
          {{ opt }}
        </li>
      </template>
    </ul>
    <p v-if="error" class="select-input-error">{{ error }}</p>
  </div>
</template>
