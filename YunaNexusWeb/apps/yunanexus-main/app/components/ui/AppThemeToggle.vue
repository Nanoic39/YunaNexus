<script setup lang="ts">
const props = defineProps<{
  mode: "light" | "dark" | "system";
}>();

const emit = defineEmits<{
  toggle: [origin: HTMLElement | null]
  requestMenu: [origin: HTMLElement | null, event: MouseEvent]
}>();

const buttonRef = ref<HTMLElement | null>(null);

const iconName = computed(() => {
  if (props.mode === "dark") {
    return "lucide:moon-star";
  }

  if (props.mode === "system") {
    return "lucide:monitor-cog";
  }

  return "lucide:sun-medium";
});

const label = computed(() => {
  if (props.mode === "dark") {
    return "当前为深色模式，点击切换到跟随系统";
  }

  if (props.mode === "system") {
    return "当前为跟随系统模式，点击切换到浅色";
  }

  return "当前为浅色模式，点击切换到深色";
});

const handleClick = () => {
  emit("toggle", buttonRef.value);
};

const handleContextMenu = (event: MouseEvent) => {
  event.preventDefault();
  event.stopPropagation();
  emit("requestMenu", buttonRef.value, event);
};
</script>

<template>
  <button
    ref="buttonRef"
    class="app-theme-toggle"
    type="button"
    :aria-label="label"
    :title="label"
    @click="handleClick"
    @contextmenu="handleContextMenu"
  >
    <Icon :name="iconName" class="app-theme-toggle-icon" />
  </button>
</template>

<style scoped lang="scss">
.app-theme-toggle {
  display: inline-flex;
  height: 40px;
  width: 40px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: 9999px;
  background: color-mix(in srgb, var(--yn-color-surface) 82%, transparent);
  color: var(--yn-color-text-secondary);
  box-shadow: var(--yn-shadow-card);
  cursor: pointer;
  backdrop-filter: blur(10px);
  transition:
    background 0.2s ease,
    color 0.2s ease,
    border-color 0.2s ease,
    transform 0.2s ease;
}

.app-theme-toggle:hover {
  background: var(--yn-color-surface-raised);
  color: var(--yn-color-text-primary);
  border-color: var(--yn-color-border-medium);
}

.app-theme-toggle:active {
  transform: scale(0.96);
}

.app-theme-toggle-icon {
  font-size: 18px;
}
</style>
