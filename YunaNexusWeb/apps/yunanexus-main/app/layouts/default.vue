<script setup lang="ts">
import { useMyProfile } from "~/composables/useMyProfile";

const { menuItems, allowedPaths: menuAllowedPaths } = useMenu();
const { toggle: toggleTheme, sidebarCollapsed } = useTheme();

const mobileOpen = ref(false);
const currentTitle = ref("首页");

const route = useRoute();

// 同步菜单路径给权限中间件
const allowedPaths = useState<string[]>("menu-allowed-paths", () => []);

// 仅在客户端同步路径，避免 SSR 下用访客 fallback 误拦已登录页面
if (import.meta.client) {
  watch(
    menuAllowedPaths,
    (paths) => {
      allowedPaths.value = paths;
    },
    { immediate: true },
  );
}

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

const { fetch: fetchProfile } = useMyProfile();

onMounted(() => {
  fetchProfile();
});
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
