<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from "vue";
import AppContextMenuList from "./AppContextMenuList.vue";
import type { AppContextMenuItem } from "../../composables/useAppContextMenu";

const contextMenu = useAppContextMenu();
const menuState = contextMenu.state;
const menuRef = ref<HTMLElement | null>(null);
const menuSize = ref({ width: 188, height: 0 });
const viewportPadding = 12;

const updateMenuSize = async () => {
  await nextTick();
  if (!menuRef.value) {
    return;
  }
  const rect = menuRef.value.getBoundingClientRect();
  menuSize.value = {
    width: rect.width || 188,
    height: rect.height || 0,
  };
};

watch(
  () => [
    menuState.value.visible,
    menuState.value.x,
    menuState.value.y,
    menuState.value.items.length,
  ],
  async ([visible]) => {
    if (visible) {
      await updateMenuSize();
    }
  },
  { flush: "post" },
);

const menuPositionStyle = computed(() => {
  const { x, y } = menuState.value;
  const { width, height } = menuSize.value;
  const viewportWidth = import.meta.client ? window.innerWidth : x + width;
  const viewportHeight = import.meta.client ? window.innerHeight : y + height;

  const showLeft = x + width + viewportPadding > viewportWidth;
  const showTop = y + height + viewportPadding > viewportHeight;

  const left = showLeft
    ? Math.max(viewportPadding, x - width)
    : Math.min(x, viewportWidth - width - viewportPadding);
  const top = showTop
    ? Math.max(viewportPadding, y - height)
    : Math.min(y, viewportHeight - height - viewportPadding);

  return {
    left: `${left}px`,
    top: `${top}px`,
    transformOrigin: `${showTop ? "bottom" : "top"} ${showLeft ? "right" : "left"}`,
  };
});

const isEditableTarget = (target: HTMLElement | null) => {
  if (!target) {
    return false;
  }

  if (
    target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement
  ) {
    return !target.readOnly && !target.disabled;
  }

  return target.isContentEditable;
};

const getTargetText = (target: HTMLElement | null) => {
  const selectionText = window.getSelection?.()?.toString().trim();
  if (selectionText) {
    return selectionText;
  }

  if (!target) {
    return "";
  }

  if (
    target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement
  ) {
    const { selectionStart, selectionEnd, value } = target;
    if (
      typeof selectionStart === "number" &&
      typeof selectionEnd === "number" &&
      selectionStart !== selectionEnd
    ) {
      return value.slice(selectionStart, selectionEnd);
    }

    return value;
  }

  return target.innerText?.trim() ?? "";
};

const pasteToTarget = async (target: HTMLElement | null) => {
  if (!target || !isEditableTarget(target) || !navigator.clipboard?.readText) {
    return;
  }

  const text = await navigator.clipboard.readText();

  if (
    target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement
  ) {
    const start = target.selectionStart ?? target.value.length;
    const end = target.selectionEnd ?? target.value.length;
    target.setRangeText(text, start, end, "end");
    target.dispatchEvent(new Event("input", { bubbles: true }));
    return;
  }

  target.focus();
  document.execCommand("insertText", false, text);
};

const clearTarget = (target: HTMLElement | null) => {
  if (!target || !isEditableTarget(target)) {
    return;
  }

  if (
    target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement
  ) {
    target.value = "";
    target.dispatchEvent(new Event("input", { bubbles: true }));
    return;
  }

  target.textContent = "";
};

const buildDefaultItems = (
  target: HTMLElement | null,
): AppContextMenuItem[] => {
  const items: AppContextMenuItem[] = [
    {
      key: "copy",
      label: "复制",
      icon: "lucide:copy",
      disabled: !getTargetText(target),
      action: async () => {
        const text = getTargetText(target);
        if (!text || !navigator.clipboard?.writeText) {
          return;
        }
        await navigator.clipboard.writeText(text);
      },
    },
    {
      key: "paste",
      label: "粘贴",
      icon: "lucide:clipboard-paste",
      disabled: !isEditableTarget(target),
      action: async () => {
        await pasteToTarget(target);
      },
    },
  ];

  if (isEditableTarget(target)) {
    items.push(
      { key: "sep-edit", type: "separator" },
      {
        key: "clear",
        label: "清空内容",
        icon: "lucide:eraser",
        danger: true,
        action: () => clearTarget(target),
      },
    );
  }

  return items;
};

const runItem = async (item: AppContextMenuItem) => {
  if (item.disabled) {
    return;
  }

  contextMenu.close();
  await nextTick();
  await item.action?.();
};

const handleNativeContextMenu = (event: MouseEvent) => {
  const target = event.target as HTMLElement | null;
  if (target?.closest(".app-context-menu")) {
    event.preventDefault();
    return;
  }
  contextMenu.open(event, buildDefaultItems(target), target);
};

const closeContextMenu = () => {
  contextMenu.close();
};

onMounted(() => {
  document.addEventListener("contextmenu", handleNativeContextMenu, true);
  document.addEventListener("click", closeContextMenu);
  window.addEventListener("resize", closeContextMenu);
  window.addEventListener("scroll", closeContextMenu, true);
});

onBeforeUnmount(() => {
  document.removeEventListener("contextmenu", handleNativeContextMenu, true);
  document.removeEventListener("click", closeContextMenu);
  window.removeEventListener("resize", closeContextMenu);
  window.removeEventListener("scroll", closeContextMenu, true);
});
</script>

<template>
  <Teleport to="body">
    <div
      v-if="menuState.visible"
      ref="menuRef"
      class="app-context-menu"
      :style="menuPositionStyle"
    >
      <AppContextMenuList :items="menuState.items" @run="runItem" />
    </div>
  </Teleport>
</template>

<style scoped lang="scss">
.app-context-menu {
  position: fixed;
  z-index: 999;
  min-width: 188px;
  padding: 6px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: color-mix(in srgb, var(--yn-color-surface) 94%, transparent);
  box-shadow: var(--yn-shadow-overlay);
  backdrop-filter: blur(12px);
}
</style>
