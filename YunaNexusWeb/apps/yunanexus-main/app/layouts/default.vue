<script setup lang="ts">
const { menuItems } = useMenu();
const { toggle: toggleTheme, sidebarCollapsed } = useTheme();

const mobileOpen = ref(false);
const currentTitle = ref("首页");

const route = useRoute();

// 同步菜单路径给权限中间件
const allowedPaths = useState<string[]>("menu-allowed-paths", () => []);

watch(
  menuItems,
  (items) => {
    allowedPaths.value = items.map((i) => i.path);
  },
  { immediate: true },
);

// 同步当前页面标题
watch(
  route,
  () => {
    const item = menuItems.value.find(
      (i) => route.path === i.path || route.path.startsWith(i.path + "/"),
    );
    currentTitle.value = item?.label ?? (route.meta.title as string) ?? "";
  },
  { immediate: true },
);
</script>

<template>
  <div class="app-shell">
    <Sidebar
      :menu-items="menuItems"
      :collapsed="sidebarCollapsed"
      :mobile-open="mobileOpen"
      @update:collapsed="sidebarCollapsed = $event"
      @update:mobile-open="mobileOpen = $event"
    />
    <div class="app-main">
      <Topbar
        :current-title="currentTitle"
        @toggle-mobile="mobileOpen = !mobileOpen"
        @toggle-theme="toggleTheme"
      />
      <div class="page-content">
        <slot />
      </div>
    </div>
  </div>
</template>
