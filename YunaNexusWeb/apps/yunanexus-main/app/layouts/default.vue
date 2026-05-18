<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from "vue";
import { sidebarMenus } from "../mocks/navigation";
import AppThemeToggle from "../components/ui/AppThemeToggle.vue";
import AppGlobalLoader from "../components/feedback/AppGlobalLoader.vue";

const runtimeConfig = useRuntimeConfig();
const route = useRoute();
const siteTitle = runtimeConfig.public.siteTitle || "YunaNexus";
const defaultOpenKeys = ["account", "user"];
const sidebarCollapsedCookie = useCookie<string>("yn-sidebar-collapsed", {
  default: () => "0",
  sameSite: "lax",
});
const sidebarOpenKeysCookie = useCookie<string>("yn-sidebar-open-keys", {
  default: () => JSON.stringify(defaultOpenKeys),
  sameSite: "lax",
});
const sidebarSavedOpenKeysCookie = useCookie<string>(
  "yn-sidebar-saved-open-keys",
  {
    default: () => JSON.stringify(defaultOpenKeys),
    sameSite: "lax",
  },
);
const parseSidebarKeys = (value?: string | null) => {
  try {
    const parsed = value ? JSON.parse(value) : defaultOpenKeys;
    return Array.isArray(parsed)
      ? parsed.filter((item): item is string => typeof item === "string")
      : [...defaultOpenKeys];
  } catch {
    return [...defaultOpenKeys];
  }
};
const collapsed = useState<boolean>(
  "app-sidebar-collapsed",
  () => sidebarCollapsedCookie.value === "1",
);
const openKeys = useState<string[]>("app-sidebar-open-keys", () =>
  parseSidebarKeys(sidebarOpenKeysCookie.value),
);
const savedOpenKeys = useState<string[]>("app-sidebar-saved-open-keys", () =>
  parseSidebarKeys(sidebarSavedOpenKeysCookie.value),
);
const hoverKey = ref("");
const mobileDrawerOpen = useState<boolean>(
  "app-mobile-drawer-open",
  () => false,
);
const authApi = useAuthApi();
const isAuthenticated = computed(() => !!authApi.accessToken.value);
const menus = computed(() =>
  sidebarMenus
    .map((menu) => ({
      ...menu,
      children: menu.children?.filter((child) =>
        isAuthenticated.value
          ? child.key !== "login" && child.key !== "register"
          : true,
      ),
    }))
    .filter((menu) => menu.to || menu.children?.length),
);
const userName = computed(
  () =>
    authApi.currentUser.value?.nickname?.trim() ||
    authApi.currentUser.value?.uuid ||
    "已登录用户",
);
const userAvatarText = computed(() => userName.value.slice(0, 1).toUpperCase());
const userAvatarUuid = computed(() => authApi.currentUser.value?.avatarUuid || "");
const userAvatarUrl = computed(() =>
  userAvatarUuid.value ? `/api/file/avatar/${userAvatarUuid.value}` : "",
);
const userUuid = computed(() => authApi.currentUser.value?.uuid || "");
const userHint = computed(() => (userUuid.value ? "个人中心" : "当前已登录"));
const authGroups = computed(
  () => authApi.permissionSnapshot.value?.roles?.filter(Boolean) || [],
);
const userEntryOpen = ref(false);
const userEntryRef = ref<HTMLElement | null>(null);
useHead({ titleTemplate: `${siteTitle} | %s` });
const persistSidebarState = () => {
  sidebarCollapsedCookie.value = collapsed.value ? "1" : "0";
  sidebarOpenKeysCookie.value = JSON.stringify(openKeys.value);
  sidebarSavedOpenKeysCookie.value = JSON.stringify(savedOpenKeys.value);
};
const isOpen = (key: string) => openKeys.value.includes(key);
const isActive = (to?: string) => !!to && route.path === to;
const toggleSidebar = () => {
  if (import.meta.client && window.innerWidth <= 960) {
    return;
  }
  if (!collapsed.value) {
    savedOpenKeys.value = [...openKeys.value];
    openKeys.value = [];
    hoverKey.value = "";
  } else {
    openKeys.value = [...savedOpenKeys.value];
  }
  collapsed.value = !collapsed.value;
  persistSidebarState();
};
const toggleGroup = (key: string) => {
  if (collapsed.value) return;
  openKeys.value = isOpen(key)
    ? openKeys.value.filter((item) => item !== key)
    : [...openKeys.value, key];
  savedOpenKeys.value = [...openKeys.value];
  persistSidebarState();
};
let hoverCloseTimer: ReturnType<typeof window.setTimeout> | null = null;

const clearHoverCloseTimer = () => {
  if (hoverCloseTimer !== null) {
    window.clearTimeout(hoverCloseTimer);
    hoverCloseTimer = null;
  }
};

const openHoverMenu = (key: string) => {
  if (!collapsed.value) {
    return;
  }
  clearHoverCloseTimer();
  hoverKey.value = key;
};

const closeHoverMenu = (key: string, event?: MouseEvent) => {
  const nextTarget = event?.relatedTarget;
  if (
    nextTarget instanceof HTMLElement &&
    nextTarget.closest(`[data-hover-menu-key="${key}"]`)
  ) {
    return;
  }

  clearHoverCloseTimer();
  hoverCloseTimer = setTimeout(() => {
    if (hoverKey.value === key) {
      hoverKey.value = "";
    }
  }, 140);
};
const setDocumentScrollLocked = (locked: boolean) => {
  if (!import.meta.client) {
    return;
  }
  const overflow = locked ? "hidden" : "";
  document.documentElement.style.overflow = overflow;
  document.body.style.overflow = overflow;
};

watch(
  mobileDrawerOpen,
  (opened) => {
    setDocumentScrollLocked(opened);
  },
  { immediate: true },
);

let cleanupThemeListener: (() => void) | null = null;

onBeforeUnmount(() => {
  setDocumentScrollLocked(false);
  cleanupThemeListener?.();
  clearHoverCloseTimer();
  if (import.meta.client) {
    document.removeEventListener("pointerdown", handleDocumentPointerDown);
  }
});

const closeMobileDrawer = () => {
  mobileDrawerOpen.value = false;
};
const closeUserEntry = () => {
  userEntryOpen.value = false;
};
const toggleUserEntry = () => {
  userEntryOpen.value = !userEntryOpen.value;
};
const logout = async () => {
  await authApi.logout();
  closeUserEntry();
  await navigateTo("/login");
};
const handleDocumentPointerDown = (event: MouseEvent) => {
  const target = event.target;
  if (target instanceof Node && !userEntryRef.value?.contains(target)) {
    closeUserEntry();
  }
};
const themeMode = useState<"light" | "dark" | "system">(
  "app-theme-mode",
  () => "light",
);
let mediaQuery: MediaQueryList | null = null;

type DocumentWithViewTransition = Document & {
  startViewTransition?: (callback: () => void | Promise<void>) => {
    finished: Promise<void>;
  };
};

const getResolvedTheme = (mode: "light" | "dark" | "system") =>
  mode === "system" &&
  import.meta.client &&
  window.matchMedia("(prefers-color-scheme: dark)").matches
    ? "dark"
    : mode === "system"
      ? "light"
      : mode;

const setThemeOrigin = (target?: HTMLElement | null) => {
  if (!import.meta.client || !target) {
    return;
  }
  const root = document.documentElement;
  const rect = target.getBoundingClientRect();
  root.style.setProperty(
    "--yn-theme-origin-x",
    `${rect.left + rect.width / 2}px`,
  );
  root.style.setProperty(
    "--yn-theme-origin-y",
    `${rect.top + rect.height / 2}px`,
  );
};

const persistTheme = (mode: "light" | "dark" | "system") => {
  if (!import.meta.client) {
    return;
  }
  window.localStorage.setItem("yn-theme-mode", mode);
};

const applyTheme = async (
  mode: "light" | "dark" | "system",
  origin?: HTMLElement | null,
  options?: { skipTransition?: boolean },
) => {
  if (!import.meta.client) return;
  const root = document.documentElement;
  const next = getResolvedTheme(mode);
  const doc = document as DocumentWithViewTransition;
  setThemeOrigin(origin);

  const commitTheme = () => {
    root.dataset.theme = next;
    root.style.colorScheme = next;
  };

  if (
    !options?.skipTransition &&
    !window.matchMedia("(prefers-reduced-motion: reduce)").matches &&
    doc.startViewTransition
  ) {
    root.classList.remove("yn-theme-transitioning");
    void root.offsetWidth;
    root.classList.add("yn-theme-transitioning");
    const transition = doc.startViewTransition(() => {
      commitTheme();
    });
    await transition.finished.finally(() => {
      root.classList.remove("yn-theme-transitioning");
    });
  } else {
    commitTheme();
  }

  persistTheme(mode);
};

const cycleTheme = async (origin?: HTMLElement | null) => {
  const nextMode =
    themeMode.value === "light"
      ? "dark"
      : themeMode.value === "dark"
        ? "system"
        : "light";
  themeMode.value = nextMode;
  await applyTheme(nextMode, origin);
};

onMounted(() => {
  if (!import.meta.client) return;
  document.addEventListener("pointerdown", handleDocumentPointerDown);
  const saved = window.localStorage.getItem("yn-theme-mode");
  if (saved === "light" || saved === "dark" || saved === "system") {
    themeMode.value = saved;
  }
  mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
  const syncTheme = () => themeMode.value === "system" && applyTheme("system");
  mediaQuery.addEventListener("change", syncTheme);
  cleanupThemeListener = () =>
    mediaQuery?.removeEventListener("change", syncTheme);
  applyTheme(themeMode.value, null, { skipTransition: true });
});

const contextMenu = useAppContextMenu();

const setThemeMode = async (
  mode: "light" | "dark" | "system",
  origin?: HTMLElement | null,
) => {
  themeMode.value = mode;
  await applyTheme(mode, origin);
};

const handleThemeToggle = async (origin: HTMLElement | null) => {
  await cycleTheme(origin);
};

const handleThemeMenu = (origin: HTMLElement | null, event: MouseEvent) => {
  contextMenu.open(
    event,
    [
      {
        key: "theme-light",
        label: "浅色",
        icon: "lucide:sun-medium",
        checked: themeMode.value === "light",
        action: () => setThemeMode("light", origin),
      },
      {
        key: "theme-dark",
        label: "深色",
        icon: "lucide:moon-star",
        checked: themeMode.value === "dark",
        action: () => setThemeMode("dark", origin),
      },
      {
        key: "theme-system",
        label: "跟随系统",
        icon: "lucide:monitor-cog",
        checked: themeMode.value === "system",
        action: () => setThemeMode("system", origin),
      },
    ],
    origin,
  );
};
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell-collapsed': collapsed }">
    <div
      v-if="mobileDrawerOpen"
      class="app-sidebar-overlay"
      @click="closeMobileDrawer"
    />
    <aside
      class="app-sidebar"
      :class="{ 'app-sidebar-mobile-open': mobileDrawerOpen }"
    >
      <div class="app-brand">
        <span class="app-brand-mark">YN</span>
        <div v-if="!collapsed" class="app-brand-meta">
          <div class="app-brand-text">YunaNexus</div>
          <div class="app-brand-subtitle">工作台</div>
        </div>
      </div>
      <nav class="app-sidebar-nav">
        <div
          v-for="menu in menus"
          :key="menu.key"
          class="app-menu-item"
          :data-hover-menu-key="menu.key"
          @mouseenter="openHoverMenu(menu.key)"
          @mouseleave="closeHoverMenu(menu.key, $event)"
        >
          <NuxtLink
            v-if="menu.to"
            :to="menu.to"
            class="app-nav-link"
            :class="{ 'app-nav-link-active': isActive(menu.to) }"
            @click="closeMobileDrawer"
          >
            <Icon :name="menu.icon" class="app-nav-icon" />
            <span v-if="!collapsed">{{ menu.label }}</span>
          </NuxtLink>
          <template v-else>
            <button
              class="app-nav-group-toggle"
              type="button"
              @click="toggleGroup(menu.key)"
            >
              <span class="app-nav-group-main">
                <Icon :name="menu.icon" class="app-nav-icon" />
                <span v-if="!collapsed">{{ menu.label }}</span>
              </span>
              <Icon
                v-if="!collapsed"
                name="lucide:chevron-down"
                class="app-nav-arrow"
                :class="{ 'app-nav-arrow-open': isOpen(menu.key) }"
              />
            </button>
            <Transition name="nav-children">
              <div
                v-if="!collapsed && isOpen(menu.key)"
                class="app-nav-children"
              >
                <NuxtLink
                  v-for="child in menu.children"
                  :key="child.key"
                  :to="child.to"
                  class="app-nav-link app-nav-link-child"
                  :class="{ 'app-nav-link-active': isActive(child.to) }"
                  @click="closeMobileDrawer"
                >
                  <Icon :name="child.icon" class="app-nav-icon" />
                  <span>{{ child.label }}</span>
                </NuxtLink>
              </div>
            </Transition>
          </template>
          <Transition name="nav-flyout">
            <div
              v-if="collapsed && hoverKey === menu.key"
              class="app-nav-flyout"
              :data-hover-menu-key="menu.key"
              @mouseenter="openHoverMenu(menu.key)"
              @mouseleave="closeHoverMenu(menu.key, $event)"
            >
              <div class="app-nav-flyout-title">{{ menu.label }}</div>
              <div v-if="menu.children?.length" class="app-nav-flyout-list">
                <NuxtLink
                  v-for="child in menu.children"
                  :key="child.key"
                  :to="child.to"
                  class="app-nav-flyout-link"
                  @click="closeMobileDrawer"
                >
                  <Icon :name="child.icon" class="app-nav-icon" />
                  <span>{{ child.label }}</span>
                </NuxtLink>
              </div>
            </div>
          </Transition>
        </div>
      </nav>
      <div class="app-sidebar-footer">
        <button class="app-sidebar-toggle" type="button" @click="toggleSidebar">
          <Icon
            :name="
              collapsed ? 'lucide:panel-left-open' : 'lucide:panel-left-close'
            "
          />
          <span v-if="!collapsed">折叠菜单</span>
        </button>
      </div>
    </aside>
    <div class="app-main">
      <header class="app-topbar">
        <div class="app-topbar-main">
          <button
            class="app-topbar-menu-button"
            type="button"
            @click="mobileDrawerOpen = true"
          >
            <Icon name="lucide:menu" />
          </button>
          <div class="app-topbar-title">仪表盘</div>
        </div>
        <div class="app-topbar-actions">
          <AppThemeToggle
            :mode="themeMode"
            @toggle="handleThemeToggle"
            @request-menu="handleThemeMenu"
          />
          <NuxtLink
            v-if="!isAuthenticated"
            class="app-topbar-login-link"
            to="/login"
            >登录</NuxtLink
          >
          <div v-else ref="userEntryRef" class="app-user-entry-wrap">
            <button
              class="app-user-entry"
              type="button"
              @click="toggleUserEntry"
            >
              <span class="app-user-avatar">
                <img
                  v-if="userAvatarUrl"
                  :src="userAvatarUrl"
                  alt="用户头像"
                  class="app-user-avatar-image"
                />
                <template v-else>{{ userAvatarText }}</template>
              </span>
              <span class="app-user-meta">
                <strong class="app-user-name">{{ userName }}</strong>
                <small class="app-user-hint">{{ userHint }}</small>
              </span>
              <Icon
                name="lucide:chevron-down"
                class="app-user-entry-arrow"
                :class="{ 'app-user-entry-arrow-open': userEntryOpen }"
              />
            </button>
            <Transition name="user-card-panel">
              <section v-if="userEntryOpen" class="app-user-card">
                <div class="app-user-card-header">
                  <span class="app-user-avatar app-user-avatar-large">
                    <img
                      v-if="userAvatarUrl"
                      :src="userAvatarUrl"
                      alt="用户头像"
                      class="app-user-avatar-image"
                    />
                    <template v-else>{{ userAvatarText }}</template>
                  </span>
                  <div class="app-user-card-meta">
                    <strong class="app-user-card-name">{{ userName }}</strong>
                    <span class="app-user-card-id">{{
                      userUuid || "未获取到用户 UUID"
                    }}</span>
                  </div>
                </div>
                <div class="app-user-card-section">
                  <div class="app-user-card-label">身份组</div>
                  <div class="app-user-groups">
                    <span
                      v-for="group in authGroups.length
                        ? authGroups
                        : ['暂无身份组']"
                      :key="group"
                      class="app-user-group-tag"
                      >{{ group }}</span
                    >
                  </div>
                </div>
                <div class="app-user-card-actions">
                  <NuxtLink
                    class="app-user-card-link"
                    to="/profile"
                    @click="closeUserEntry"
                    >查看个人资料</NuxtLink
                  >
                  <button
                    class="app-user-card-logout"
                    type="button"
                    @click="logout"
                  >
                    退出登录
                  </button>
                </div>
              </section>
            </Transition>
          </div>
        </div>
      </header>
      <main class="app-content">
        <div class="app-content-inner">
          <AppGlobalLoader scope="content" />
          <slot />
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.app-shell {
  display: grid;
  min-height: 100vh;
  grid-template-columns: 264px minmax(0, 1fr);
  transition: grid-template-columns 0.22s ease;
}
.app-shell-collapsed {
  grid-template-columns: 96px minmax(0, 1fr);
}
.app-sidebar {
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: visible;
  border-right: 1px solid var(--yn-color-border-subtle);
  background: var(--yn-color-surface);
  padding: 20px 12px;
  transition: padding 0.22s ease;
}
.app-sidebar-overlay {
  position: fixed;
  inset: 0;
  z-index: 29;
  touch-action: none;
  overscroll-behavior: contain;
  background: rgba(15, 23, 42, 0.42);
  backdrop-filter: blur(2px);
}
.app-sidebar::after {
  content: "";
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 88px;
  pointer-events: none;
  background: linear-gradient(
    to top,
    color-mix(in srgb, var(--yn-color-surface) 96%, transparent),
    color-mix(in srgb, var(--yn-color-surface) 0%, transparent)
  );
}
.app-brand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}
.app-brand-meta {
  min-width: 0;
  flex: 1;
}
.app-shell-collapsed .app-brand {
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 14px;
}
.app-brand-mark {
  display: inline-flex;
  flex-shrink: 0;
  height: 40px;
  width: 40px;
  align-items: center;
  justify-content: center;
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-primary);
  color: #fff;
  font-family: var(--yn-font-mono), monospace;
  font-weight: 700;
}
.app-brand-text {
  color: var(--yn-color-text-primary);
  font-family: var(--yn-font-mono), monospace;
  font-weight: 700;
}
.app-brand-subtitle {
  margin-top: 2px;
  color: var(--yn-color-text-tertiary);
  font-size: 12px;
}
.app-sidebar-toggle,
.app-nav-group-toggle {
  border: 0;
  background: transparent;
  cursor: pointer;
}
.app-sidebar-toggle {
  display: inline-flex;
  width: 100%;
  min-height: 44px;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
  padding: 0 12px;
  border-radius: var(--yn-radius-medium);
  border: 1px solid var(--yn-color-border-medium);
  background: color-mix(
    in srgb,
    var(--yn-color-surface-raised) 88%,
    var(--yn-color-surface)
  );
  color: var(--yn-color-text-secondary);
  font-size: 14px;
  font-weight: 600;
  box-shadow: var(--yn-shadow-card);
  backdrop-filter: blur(10px);
}
.app-shell-collapsed .app-sidebar-toggle {
  justify-content: center;
  padding-inline: 0;
}
.app-sidebar-toggle :deep(svg) {
  font-size: 20px;
}
.app-sidebar-toggle:hover {
  background: color-mix(
    in srgb,
    var(--yn-color-surface-raised) 100%,
    var(--yn-color-surface)
  );
  color: var(--yn-color-text-primary);
  border-color: var(--yn-color-border-strong);
}
.app-sidebar-nav,
.app-nav-children,
.app-nav-flyout-list {
  display: grid;
  gap: 8px;
}
.app-sidebar-nav {
  flex: 1;
  align-content: start;
}
.app-sidebar-footer {
  position: sticky;
  bottom: 0;
  z-index: 1;
  margin-top: auto;
  padding-top: 16px;
  padding-bottom: 4px;
}
.app-menu-item {
  position: relative;
}
.app-nav-link,
.app-nav-group-toggle {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border-radius: var(--yn-radius-medium);
  color: var(--yn-color-text-secondary);
  font-size: 14px;
  font-weight: 600;
  text-align: left;
  transition:
    background 0.2s ease,
    color 0.2s ease;
}
.app-shell-collapsed .app-nav-link,
.app-shell-collapsed .app-nav-group-toggle {
  justify-content: center;
  padding-inline: 0;
}
.app-shell-collapsed .app-nav-group-main {
  width: 100%;
  justify-content: center;
}
.app-nav-group-main {
  display: flex;
  align-items: center;
  gap: 12px;
}
.app-nav-arrow {
  margin-left: auto;
  transform: rotate(90deg);
  transition: transform 0.2s ease;
}
.app-nav-link-active,
.app-nav-link:hover,
.app-nav-group-toggle:hover,
.app-nav-flyout-link:hover {
  background: var(--yn-color-surface-raised);
  color: var(--yn-color-text-primary);
}
.app-nav-link-child {
  padding-left: 16px;
}
.app-nav-icon {
  font-size: 18px;
}
.app-nav-arrow-open {
  transform: rotate(0deg);
}
.app-nav-flyout {
  position: absolute;
  left: calc(100% + 16px);
  top: -4px;
  z-index: 40;
  min-width: 220px;
  padding: 12px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: var(--yn-color-surface);
  box-shadow: var(--yn-shadow-overlay);
}
.app-nav-flyout::before {
  content: "";
  position: absolute;
  top: 0;
  right: 100%;
  width: 16px;
  height: 100%;
}
.nav-children-enter-active,
.nav-children-leave-active,
.nav-flyout-enter-active,
.nav-flyout-leave-active {
  transition: all 0.2s ease;
}

.nav-children-enter-from,
.nav-children-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
.nav-flyout-enter-from,
.nav-flyout-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}
.app-nav-flyout-title {
  margin-bottom: 8px;
  color: var(--yn-color-text-primary);
  font-size: 13px;
  font-weight: 700;
}
.app-nav-flyout-link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--yn-radius-medium);
  color: var(--yn-color-text-secondary);
  font-size: 14px;
  font-weight: 600;
}
.app-main {
  min-width: 0;
}
.app-topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  min-height: 64px;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--yn-color-border-subtle);
  background: var(--yn-color-surface);
  padding: 0 24px;
  backdrop-filter: blur(12px);
}
.app-topbar-main {
  display: flex;
  align-items: center;
  gap: 12px;
}
.app-topbar-menu-button {
  display: none;
  height: 40px;
  width: 40px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--yn-color-border-medium);
  border-radius: var(--yn-radius-medium);
  background: color-mix(
    in srgb,
    var(--yn-color-surface-raised) 88%,
    var(--yn-color-surface)
  );
  color: var(--yn-color-text-secondary);
  cursor: pointer;
  box-shadow: var(--yn-shadow-card);
  backdrop-filter: blur(10px);
}
.app-topbar-menu-button:hover {
  background: color-mix(
    in srgb,
    var(--yn-color-surface-raised) 100%,
    var(--yn-color-surface)
  );
  color: var(--yn-color-text-primary);
  border-color: var(--yn-color-border-strong);
}
.app-topbar-menu-button :deep(svg) {
  font-size: 20px;
}
.app-topbar-title {
  color: var(--yn-color-text-primary);
  font-size: 16px;
  font-weight: 700;
}
.app-topbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.app-topbar-login-link {
  color: var(--yn-color-primary);
  font-size: 14px;
  font-weight: 600;
}
.app-user-entry-wrap {
  position: relative;
}
.app-user-entry {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--yn-color-border-medium);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface-raised);
  padding: 5px 10px 5px 5px;
  color: var(--yn-color-text-primary);
}
.app-user-avatar {
  display: inline-flex;
  height: 28px;
  width: 28px;
  align-items: center;
  justify-content: center;
  border-radius: var(--yn-radius-small);
  background: var(--yn-color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}
.app-user-avatar-large {
  height: 44px;
  width: 44px;
  font-size: 16px;
}
.app-user-avatar-image {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}
.app-user-entry-arrow {
  font-size: 16px;
  color: var(--yn-color-text-tertiary);
  transition: transform 0.2s ease;
}
.app-user-entry-arrow-open {
  transform: rotate(180deg);
}
.app-user-card {
  position: absolute;
  right: 0;
  top: calc(100% + 10px);
  z-index: 30;
  width: 320px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: var(--yn-color-surface);
  box-shadow: var(--yn-shadow-overlay);
  padding: 14px;
}
.app-user-meta,
.app-user-card-meta {
  display: grid;
  text-align: left;
}
.app-user-name,
.app-user-card-name {
  font-size: 13px;
  line-height: 1.2;
}
.app-user-hint,
.app-user-card-id {
  color: var(--yn-color-text-tertiary);
  font-size: 12px;
  line-height: 1.35;
}
.app-user-card-id {
  word-break: break-all;
}
.app-user-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--yn-color-border-subtle);
}
.app-user-card-section {
  padding: 12px 0;
}
.app-user-card-label {
  margin-bottom: 8px;
  color: var(--yn-color-text-secondary);
  font-size: 12px;
  font-weight: 600;
}
.app-user-groups {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.app-user-group-tag {
  border: 1px solid var(--yn-color-border-medium);
  border-radius: var(--yn-radius-small);
  background: var(--yn-color-surface-raised);
  padding: 4px 8px;
  color: var(--yn-color-text-secondary);
  font-size: 12px;
  font-weight: 600;
}
.app-user-card-actions {
  display: grid;
  gap: 10px;
}
.app-user-card-link,
.app-user-card-logout {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  justify-content: center;
  border-radius: var(--yn-radius-medium);
  font-size: 14px;
  font-weight: 600;
}
.app-user-card-link {
  border: 1px solid var(--yn-color-border-medium);
  color: var(--yn-color-text-primary);
  background: var(--yn-color-surface-raised);
}
.app-user-card-logout {
  border: 1px solid rgba(220, 38, 38, 0.18);
  background: rgba(220, 38, 38, 0.08);
  color: #b91c1c;
}
.app-content {
  padding: 24px;
}
.app-content-inner {
  position: relative;
  min-height: calc(100vh - 112px);
  isolation: isolate;
}
.user-card-panel-enter-active,
.user-card-panel-leave-active {
  transition: all 0.18s ease;
}
.user-card-panel-enter-from,
.user-card-panel-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media (max-width: 960px) {
  .app-shell,
  .app-shell-collapsed {
    grid-template-columns: 1fr;
  }
  .app-topbar-menu-button {
    display: inline-flex;
  }
  .app-topbar-actions {
    gap: 8px;
  }
  .app-user-entry {
    padding-right: 6px;
  }
  .app-user-meta {
    display: none;
  }
  .app-sidebar {
    position: fixed;
    top: 0;
    left: 0;
    z-index: 30;
    height: 100vh;
    width: min(82vw, 320px);
    transform: translateX(-100%);
    transition: transform 0.22s ease;
    box-shadow: var(--yn-shadow-overlay);
  }
  .app-sidebar-mobile-open {
    transform: translateX(0);
  }
  .app-sidebar-footer {
    display: none;
  }
}
@media (min-width: 961px) {
  .app-sidebar-overlay {
    display: none;
  }
}
</style>
