<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";

const props = withDefaults(
  defineProps<{
    modelValue: string;
    items: string[];
    placeholder?: string;
    maxlength?: number;
    disabled?: boolean;
  }>(),
  {
    placeholder: "",
    maxlength: 10,
    disabled: false,
  },
);

const emit = defineEmits<{
  "update:modelValue": [value: string];
}>();

const rootRef = ref<HTMLElement | null>(null);
const inputRef = ref<HTMLInputElement | null>(null);
const open = ref(false);
const highlightedIndex = ref(-1);
const isComposing = ref(false);
const draftValue = ref("");

const localValue = computed({
  get: () => props.modelValue ?? "",
  set: (value: string) => {
    emit("update:modelValue", value.slice(0, props.maxlength));
  },
});

const displayValue = computed(() =>
  isComposing.value ? draftValue.value : localValue.value,
);

const visibleItems = computed(() => props.items);

const closeMenu = () => {
  open.value = false;
  highlightedIndex.value = -1;
};

const openMenu = () => {
  if (props.disabled) {
    return;
  }

  open.value = true;
};

const selectItem = (item: string) => {
  emit("update:modelValue", item.slice(0, props.maxlength));
  closeMenu();
};

const handleInput = (event: Event) => {
  const target = event.target as HTMLInputElement;
  if (isComposing.value) {
    draftValue.value = target.value;
    openMenu();
    return;
  }

  emit("update:modelValue", target.value.slice(0, props.maxlength));
  openMenu();
};

const handleCompositionStart = (event: CompositionEvent) => {
  isComposing.value = true;
  draftValue.value = (event.target as HTMLInputElement).value;
};

const handleCompositionEnd = (event: CompositionEvent) => {
  isComposing.value = false;
  const value = (event.target as HTMLInputElement).value.slice(
    0,
    props.maxlength,
  );
  draftValue.value = value;
  emit("update:modelValue", value);
};

const handleKeydown = (event: KeyboardEvent) => {
  if (!open.value && ["ArrowDown", "ArrowUp", "Enter"].includes(event.key)) {
    openMenu();
  }

  if (!open.value) {
    return;
  }

  if (event.key === "ArrowDown") {
    event.preventDefault();
    highlightedIndex.value =
      highlightedIndex.value >= visibleItems.value.length - 1
        ? 0
        : highlightedIndex.value + 1;
  }

  if (event.key === "ArrowUp") {
    event.preventDefault();
    highlightedIndex.value =
      highlightedIndex.value <= 0
        ? visibleItems.value.length - 1
        : highlightedIndex.value - 1;
  }

  if (event.key === "Enter" && highlightedIndex.value >= 0) {
    event.preventDefault();
    const selectedItem = visibleItems.value[highlightedIndex.value];
    if (selectedItem) {
      selectItem(selectedItem);
    }
  }

  if (event.key === "Escape") {
    event.preventDefault();
    closeMenu();
  }
};

const handleClickOutside = (event: MouseEvent) => {
  const target = event.target as Node | null;
  if (!rootRef.value?.contains(target)) {
    closeMenu();
  }
};

watch(
  () => props.disabled,
  (disabled) => {
    if (disabled) {
      closeMenu();
    }
  },
);

window.addEventListener("mousedown", handleClickOutside);

onBeforeUnmount(() => {
  window.removeEventListener("mousedown", handleClickOutside);
});
</script>

<template>
  <div
    ref="rootRef"
    class="app-input-menu"
    :class="{
      'app-input-menu-open': open,
      'app-input-menu-disabled': disabled,
    }"
  >
    <div class="app-input-menu-control" @click="inputRef?.focus()">
      <input
        ref="inputRef"
        :value="displayValue"
        class="app-input-menu-input"
        type="text"
        :placeholder="placeholder"
        :disabled="disabled"
        @focus="openMenu"
        @input="handleInput"
        @keydown="handleKeydown"
        @compositionstart="handleCompositionStart"
        @compositionend="handleCompositionEnd"
      />

      <span class="app-input-menu-arrow">
        <Icon name="lucide:chevrons-up-down" />
      </span>
    </div>

    <transition name="app-input-menu-panel">
      <div v-if="open && visibleItems.length" class="app-input-menu-panel">
        <button
          v-for="(item, index) in visibleItems"
          :key="item"
          class="app-input-menu-option"
          :class="{
            'app-input-menu-option-active': item === localValue,
            'app-input-menu-option-highlighted': highlightedIndex === index,
          }"
          type="button"
          @mouseenter="highlightedIndex = index"
          @click="selectItem(item)"
        >
          <span>{{ item }}</span>
          <Icon
            v-if="item === localValue"
            name="lucide:check"
            class="app-input-menu-check"
          />
        </button>
      </div>
    </transition>
  </div>
</template>

<style scoped lang="scss">
.app-input-menu {
  position: relative;
  width: 100%;
}

.app-input-menu-control {
  display: flex;
  min-height: 46px;
  align-items: center;
  border: 1px solid var(--yn-color-border-medium);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface-raised);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

.app-input-menu-open .app-input-menu-control,
.app-input-menu-control:focus-within {
  border-color: var(--yn-color-primary);
  box-shadow: var(--yn-glow-medium);
}

.app-input-menu-input {
  flex: 1;
  min-width: 0;
  height: 44px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--yn-color-text-primary);
  padding: 0 14px;
}

.app-input-menu-input::placeholder {
  color: var(--yn-color-text-tertiary);
}

.app-input-menu-arrow {
  display: inline-flex;
  height: 36px;
  width: 36px;
  align-items: center;
  justify-content: center;
  color: var(--yn-color-text-tertiary);
}

.app-input-menu-panel {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  z-index: 30;
  width: 100%;
  padding: 6px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface);
  box-shadow: var(--yn-shadow-overlay);
}

.app-input-menu-option {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 0;
  border-radius: calc(var(--yn-radius-medium) - 2px);
  background: transparent;
  color: var(--yn-color-text-secondary);
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  transition:
    background 0.2s ease,
    color 0.2s ease;
}

.app-input-menu-option:hover,
.app-input-menu-option-highlighted {
  background: var(--yn-color-surface-raised);
  color: var(--yn-color-text-primary);
}

.app-input-menu-option-active {
  color: var(--yn-color-text-primary);
}

.app-input-menu-check {
  font-size: 16px;
}

.app-input-menu-disabled .app-input-menu-control {
  opacity: 0.6;
  cursor: not-allowed;
}

.app-input-menu-panel-enter-active,
.app-input-menu-panel-leave-active {
  transition: all 0.18s ease;
}

.app-input-menu-panel-enter-from,
.app-input-menu-panel-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
