<script setup lang="ts">
import type { MenuItem } from "~/composables/useMenu";
import brandLogo from "~/assets/mascot/YunaImageMascot.jpg";

const props = defineProps<{
  menuItems: MenuItem[];
  collapsed: boolean;
  mobileOpen: boolean;
}>();

const emit = defineEmits<{
  "update:collapsed": [value: boolean];
  "update:mobileOpen": [value: boolean];
}>();

const route = useRoute();

const isDesktop = ref(true);

onMounted(() => {
  isDesktop.value = window.innerWidth > 768;
  window.addEventListener("resize", () => {
    isDesktop.value = window.innerWidth > 768;
  });
});

function isActive(path: string): boolean {
  if (path === "/") return route.path === "/";
  return route.path === path || route.path.startsWith(path + "/");
}

function onNavClick() {
  if (!isDesktop.value) {
    emit("update:mobileOpen", false);
  }
}

function toggleCollapse() {
  emit("update:collapsed", !props.collapsed);
}

const sidebarClasses = computed(() => ({
  "app-sidebar": true,
  "sidebar-collapsed": props.collapsed && isDesktop.value,
  open: props.mobileOpen,
}));

const iconMap: Record<MenuItem["icon"], string> = {
  dashboard: "lucide:layout-dashboard",
  folder: "lucide:folder",
  user: "lucide:circle-user",
  settings: "lucide:settings",
  monitor: "lucide:activity",
  about: "lucide:info",
};
</script>

<template>
  <div
    class="sidebar-overlay"
    :class="{ hidden: !mobileOpen }"
    @click="emit('update:mobileOpen', false)"
  />

  <aside :class="sidebarClasses">
    <!-- 品牌区 -->
    <div class="sidebar-brand">
      <img class="sidebar-brand-icon" :src="brandLogo" alt="YunaNexus" />
      <div class="sidebar-brand-text">
        <div class="sidebar-brand-name">YunaNexus</div>
        <div class="sidebar-brand-subtitle">芸枢</div>
      </div>
    </div>

    <!-- 导航 -->
    <nav class="sidebar-nav">
      <NuxtLink
        v-for="item in menuItems"
        :key="item.path"
        :to="item.path"
        class="sidebar-nav-item"
        :class="{ active: isActive(item.path) }"
        @click="onNavClick"
      >
        <span class="nav-icon"
          ><Icon :name="iconMap[item.icon]" size="16"
        /></span>
        <span>{{ item.label }}</span>
      </NuxtLink>
    </nav>

    <!-- 底部操作 -->
    <div v-if="isDesktop" class="sidebar-footer">
      <button class="sidebar-footer-item" @click="toggleCollapse">
        <span class="footer-icon">
          <Icon
            :name="
              props.collapsed
                ? 'lucide:panel-left-open'
                : 'lucide:panel-left-close'
            "
            size="16"
          />
        </span>
        <span>{{ props.collapsed ? "展开" : "折叠" }}</span>
      </button>
    </div>
  </aside>
</template>
