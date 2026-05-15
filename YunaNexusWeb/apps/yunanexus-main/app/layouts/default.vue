<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from "vue";
import { sidebarMenus } from "../mocks/navigation";
import AppThemeToggle from "../components/ui/AppThemeToggle.vue";

const runtimeConfig = useRuntimeConfig();
const route = useRoute();
const siteTitle = runtimeConfig.public.siteTitle || "YunaNexus";
const collapsed = useState<boolean>("app-sidebar-collapsed", () => false);
const openKeys = useState<string[]>("app-sidebar-open-keys", () => [
  "account",
  "user",
]);
const savedOpenKeys = useState<string[]>("app-sidebar-saved-open-keys", () => [
  "account",
  "user",
]);
const hoverKey = ref("");
const mobileDrawerOpen = useState<boolean>(
  "app-mobile-drawer-open",
  () => false,
);
const menus = sidebarMenus;
useHead({ titleTemplate: `${siteTitle} | %s` });
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
};
const toggleGroup = (key: string) => {
  if (collapsed.value) return;
  openKeys.value = isOpen(key)
    ? openKeys.value.filter((item) => item !== key)
    : [...openKeys.value, key];
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
  hoverCloseTimer = window.setTimeout(() => {
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
});

const closeMobileDrawer = () => {
  mobileDrawerOpen.value = false;
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
  const saved = window.localStorage.getItem("yn-theme-mode");
  if (saved === "light" || saved === "dark" || saved === "system") {
    themeMode.value = saved;
  }
  mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
  const syncTheme = () => themeMode.value === "system" && applyTheme("system");
  mediaQuery.addEventListener("change", syncTheme);
  cleanupThemeListener = () =>
    mediaQuery?.removeEventListener("change", syncTheme);
  applyTheme(themeMode.value);
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
          <div class="app-brand-subtitle">Main Workspace</div>
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
          <a href="/login">登录</a>
        </div>
      </header>
      <main class="app-content">
        <div class="app-content-inner"><slot /></div>
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
.app-topbar-actions a {
  color: var(--yn-color-primary);
  font-size: 14px;
  font-weight: 600;
}
.app-content {
  padding: 24px;
}
.app-content-inner {
  min-height: calc(100vh - 112px);
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
