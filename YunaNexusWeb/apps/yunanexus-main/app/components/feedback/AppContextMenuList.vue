<script setup lang="ts">
import { nextTick, reactive } from "vue";
import type { AppContextMenuItem } from "../../composables/useAppContextMenu";

defineOptions({
  name: "AppContextMenuList",
});

const props = defineProps<{
  items: AppContextMenuItem[];
}>();

const emit = defineEmits<{
  run: [item: AppContextMenuItem];
}>();

const submenuDirectionMap = reactive<Record<string, "left" | "right">>({});
const submenuOffset = 12;
const submenuWidth = 188;
const viewportPadding = 12;

const updateSubmenuDirection = async (key: string, event: MouseEvent) => {
  await nextTick();
  const node = event.currentTarget as HTMLElement | null;
  if (!node || !import.meta.client) {
    return;
  }

  const rect = node.getBoundingClientRect();
  const shouldOpenLeft =
    rect.right + submenuOffset + submenuWidth + viewportPadding >
    window.innerWidth;
  submenuDirectionMap[key] = shouldOpenLeft ? "left" : "right";
};

const handleRun = (item: AppContextMenuItem) => {
  if (item.disabled || item.type === "separator" || item.children?.length) {
    return;
  }

  emit("run", item);
};
</script>

<template>
  <div class="app-context-menu-list">
    <template v-for="item in props.items" :key="item.key">
      <div
        v-if="item.type === 'separator'"
        class="app-context-menu-separator"
      />
      <div
        v-else
        class="app-context-menu-node"
        :class="{
          'app-context-menu-node-disabled': item.disabled,
          'app-context-menu-node-danger': item.danger,
          'app-context-menu-node-left':
            submenuDirectionMap[item.key] === 'left',
        }"
        @mouseenter="
          item.children?.length && updateSubmenuDirection(item.key, $event)
        "
      >
        <button
          class="app-context-menu-item"
          type="button"
          :disabled="item.disabled"
          @click.stop="handleRun(item)"
        >
          <span class="app-context-menu-item-main">
            <Icon
              v-if="item.icon"
              :name="item.icon"
              class="app-context-menu-icon"
            />
            <span>{{ item.label }}</span>
          </span>
          <span class="app-context-menu-item-side">
            <Icon
              v-if="item.checked"
              name="lucide:check"
              class="app-context-menu-check"
            />
            <Icon
              v-if="item.children?.length"
              name="lucide:chevron-right"
              class="app-context-menu-arrow"
            />
          </span>
        </button>

        <div v-if="item.children?.length" class="app-context-submenu">
          <AppContextMenuList
            :items="item.children"
            @run="emit('run', $event)"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.app-context-menu-list {
  display: grid;
  gap: 4px;
}

.app-context-menu-node {
  position: relative;
}

.app-context-menu-item {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 0;
  border-radius: var(--yn-radius-medium);
  background: transparent;
  color: var(--yn-color-text-secondary);
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  transition:
    background 0.2s ease,
    color 0.2s ease;
}

.app-context-menu-item:hover {
  background: var(--yn-color-surface-raised);
  color: var(--yn-color-text-primary);
}

.app-context-menu-node-disabled .app-context-menu-item {
  opacity: 0.45;
  cursor: not-allowed;
}

.app-context-menu-node-danger .app-context-menu-item {
  color: #dc2626;
}

html[data-theme="dark"] .app-context-menu-node-danger .app-context-menu-item {
  color: #f87171;
}

.app-context-menu-item-main,
.app-context-menu-item-side {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.app-context-menu-icon,
.app-context-menu-check,
.app-context-menu-arrow {
  font-size: 16px;
}

.app-context-menu-separator {
  height: 1px;
  margin: 4px 8px;
  background: var(--yn-color-border-subtle);
}

.app-context-submenu {
  position: absolute;
  top: -6px;
  left: calc(100% + 12px);
  z-index: 2;
  display: none;
  min-width: 188px;
  padding: 6px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: color-mix(in srgb, var(--yn-color-surface) 94%, transparent);
  box-shadow: var(--yn-shadow-overlay);
  backdrop-filter: blur(12px);
}

.app-context-menu-node-left > .app-context-submenu {
  right: calc(100% + 12px);
  left: auto;
}

.app-context-submenu::before {
  content: "";
  position: absolute;
  top: 0;
  right: 100%;
  width: 18px;
  height: 100%;
  background: transparent;
}

.app-context-menu-node-left > .app-context-submenu::before {
  right: auto;
  left: 100%;
}

.app-context-menu-node:hover > .app-context-submenu {
  display: block;
}
</style>
