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
const expandedGroups = ref<Set<string>>(new Set());

/* 折叠态悬停提示 */
const hoveredItem = ref<MenuItem | null>(null);
const hoveredPath = ref("");
const tooltipStyle = ref<{ top: string; left: string }>({ top: "0px", left: "0px" });
let hoverTimer: ReturnType<typeof setTimeout> | null = null;

function onItemEnter(item: MenuItem, e: MouseEvent) {
  if (!isCollapsed.value) return;
  clearHoverTimer();
  hoveredItem.value = item;
  hoveredPath.value = item.path;
  // 计算 tooltip/flyout 位置：出现在 sidebar 右侧
  const sidebar = (e.currentTarget as HTMLElement).closest(".app-sidebar");
  const sidebarRect = sidebar?.getBoundingClientRect();
  const targetRect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  tooltipStyle.value = {
    top: `${targetRect.top}px`,
    left: `${(sidebarRect?.right ?? targetRect.right) + 6}px`,
  };
}

function onItemLeave() {
  if (!isCollapsed.value) return;
  hoverTimer = setTimeout(() => {
    hoveredItem.value = null;
    hoveredPath.value = "";
  }, 150);
}

function clearHoverTimer() {
  if (hoverTimer) { clearTimeout(hoverTimer); hoverTimer = null; }
}

function onFlyoutEnter() {
  clearHoverTimer();
}

function onFlyoutLeave() {
  hoveredItem.value = null;
  hoveredPath.value = "";
}

/* 折叠态点击：有子项的切换展开状态 */
function onCollapsedParentClick(item: MenuItem) {
  if (!isCollapsed.value) return;
  toggleSubmenu(item.path + "::" + item.label);
}

function onResize() {
  isDesktop.value = window.innerWidth > 768;
}

onMounted(() => {
  isDesktop.value = window.innerWidth > 768;
  window.addEventListener("resize", onResize);
});

onUnmounted(() => {
  window.removeEventListener("resize", onResize);
  clearHoverTimer();
});

const isCollapsed = computed(() => props.collapsed && isDesktop.value);

function isActive(item: MenuItem): boolean {
  if (item.children?.length) {
    return item.children.some((c) => isActive(c));
  }
  if (item.path === "/") return route.path === "/";
  return route.path === item.path || route.path.startsWith(item.path + "/");
}

function hasActiveChild(item: MenuItem): boolean {
  return item.children?.some((c) => isActive(c)) ?? false;
}

function onNavClick() {
  if (!isDesktop.value) {
    emit("update:mobileOpen", false);
  }
}

function toggleSubmenu(path: string) {
  const set = new Set(expandedGroups.value);
  if (set.has(path)) set.delete(path);
  else set.add(path);
  expandedGroups.value = set;
}

function toggleCollapse() {
  emit("update:collapsed", !props.collapsed);
}

const sidebarClasses = computed(() => ({
  "app-sidebar": true,
  "sidebar-collapsed": props.collapsed && isDesktop.value,
  open: props.mobileOpen,
}));

const iconMap: Record<string, string> = {
  dashboard: "lucide:layout-dashboard",
  folder: "lucide:folder",
  box: "lucide:box",
  user: "lucide:circle-user",
  settings: "lucide:settings",
  monitor: "lucide:activity",
  about: "lucide:info",
  home: "lucide:home",
  shield: "lucide:shield-check",
  "shield-check": "lucide:shield-check",
  plus: "lucide:plus",
  "clipboard-check": "lucide:clipboard-check",
  users: "lucide:users-round",
  "users-round": "lucide:users-round",
  list: "lucide:list",
  database: "lucide:database",
  plug: "lucide:plug",
  layout: "lucide:layout-grid",
};

function getIconName(key: string): string {
  return iconMap[key] || "lucide:circle";
}

const userItems = computed(() => props.menuItems.filter((i) => i.group !== "admin"));
const adminItems = computed(() => props.menuItems.filter((i) => i.group === "admin"));
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

    <!-- 导航：用户功能区 -->
    <nav class="sidebar-nav">
      <template v-for="item in userItems" :key="item.path">
        <!-- 有子菜单 -->
        <div v-if="item.children?.length" class="sidebar-nav-group">
          <button
            class="sidebar-nav-item sidebar-nav-parent"
            :class="{ active: hasActiveChild(item) }"
            @mouseenter="onItemEnter(item, $event)"
            @mouseleave="onItemLeave()"
            @click="isCollapsed ? onCollapsedParentClick(item) : toggleSubmenu(item.path + '::' + item.label)"
          >
            <span class="nav-icon">
              <Icon :name="getIconName(item.icon)" size="16" />
            </span>
            <span class="nav-label">{{ item.label }}</span>
            <span class="nav-arrow-slot">
              <Icon
                v-if="!isCollapsed"
                :name="expandedGroups.has(item.path + '::' + item.label) ? 'lucide:chevron-down' : 'lucide:chevron-right'"
                size="12"
              />
            </span>
          </button>
          <Transition name="submenu-slide">
            <div v-if="expandedGroups.has(item.path + '::' + item.label) && !isCollapsed" class="sidebar-subnav">
              <NuxtLink
                v-for="child in item.children"
                :key="child.path"
                :to="child.path"
                class="sidebar-nav-item sidebar-nav-child"
                :class="{ active: isActive(child) }"
                @click="onNavClick"
              >
                <span class="nav-icon">
                  <Icon :name="getIconName(child.icon)" size="14" />
                </span>
                <span>{{ child.label }}</span>
              </NuxtLink>
            </div>
          </Transition>
        </div>

        <NuxtLink
          v-else
          :to="item.path"
          class="sidebar-nav-item"
          :class="{ active: isActive(item) }"
          @mouseenter="onItemEnter(item, $event)"
          @mouseleave="onItemLeave()"
          @click="onNavClick"
        >
          <span class="nav-icon">
            <Icon :name="getIconName(item.icon)" size="16" />
          </span>
          <span class="nav-label">{{ item.label }}</span>
          <span class="nav-arrow-slot" />
        </NuxtLink>
      </template>
    </nav>

    <!-- 管理功能分隔 -->
    <div v-if="adminItems.length > 0" class="sidebar-divider" />

    <!-- 导航：管理功能区 -->
    <nav v-if="adminItems.length > 0" class="sidebar-nav sidebar-nav-admin">
      <template v-for="item in adminItems" :key="item.path">
        <div v-if="item.children?.length" class="sidebar-nav-group">
          <button
            class="sidebar-nav-item sidebar-nav-parent"
            :class="{ active: hasActiveChild(item) }"
            @mouseenter="onItemEnter(item, $event)"
            @mouseleave="onItemLeave()"
            @click="isCollapsed ? onCollapsedParentClick(item) : toggleSubmenu(item.path + '::' + item.label)"
          >
            <span class="nav-icon">
              <Icon :name="getIconName(item.icon)" size="16" />
            </span>
            <span class="nav-label">{{ item.label }}</span>
            <span class="nav-arrow-slot">
              <Icon
                v-if="!isCollapsed"
                :name="expandedGroups.has(item.path + '::' + item.label) ? 'lucide:chevron-down' : 'lucide:chevron-right'"
                size="12"
              />
            </span>
          </button>
          <Transition name="submenu-slide">
            <div v-if="expandedGroups.has(item.path + '::' + item.label) && !isCollapsed" class="sidebar-subnav">
              <NuxtLink
                v-for="child in item.children"
                :key="child.path"
                :to="child.path"
                class="sidebar-nav-item sidebar-nav-child"
                :class="{ active: isActive(child) }"
                @click="onNavClick"
              >
                <span class="nav-icon">
                  <Icon :name="getIconName(child.icon)" size="14" />
                </span>
                <span>{{ child.label }}</span>
              </NuxtLink>
            </div>
          </Transition>
        </div>

        <NuxtLink
          v-else
          :to="item.path"
          class="sidebar-nav-item"
          :class="{ active: isActive(item) }"
          @mouseenter="onItemEnter(item, $event)"
          @mouseleave="onItemLeave()"
          @click="onNavClick"
        >
          <span class="nav-icon">
            <Icon :name="getIconName(item.icon)" size="16" />
          </span>
          <span class="nav-label">{{ item.label }}</span>
          <span class="nav-arrow-slot" />
        </NuxtLink>
      </template>
    </nav>

    <!-- 底部操作 -->
    <div class="sidebar-footer">
      <button v-if="isDesktop" class="sidebar-footer-item" @click="toggleCollapse">
        <span class="footer-icon">
          <Icon
            :name="props.collapsed ? 'lucide:panel-left-open' : 'lucide:panel-left-close'"
            size="16"
          />
        </span>
        <span>{{ props.collapsed ? "展开" : "折叠" }}</span>
      </button>
    </div>

    <!-- ========== 折叠态悬停提示 ========== -->
    <Teleport to="body">
      <!-- 普通菜单项 tooltip -->
      <Transition name="nav-tip">
        <div
          v-if="isCollapsed && hoveredItem && !hoveredItem.children?.length"
          class="nav-tooltip"
          :style="tooltipStyle"
          @mouseenter="onFlyoutEnter"
          @mouseleave="onFlyoutLeave"
        >
          <span>{{ hoveredItem.label }}</span>
        </div>
      </Transition>

      <!-- 父菜单 flyout 面板 -->
      <Transition name="nav-tip">
        <div
          v-if="isCollapsed && hoveredItem && hoveredItem.children?.length"
          class="nav-flyout"
          :style="tooltipStyle"
          @mouseenter="onFlyoutEnter"
          @mouseleave="onFlyoutLeave"
        >
          <div class="nav-flyout-header">{{ hoveredItem.label }}</div>
          <div class="nav-flyout-divider" />
          <NuxtLink
            v-for="child in hoveredItem.children"
            :key="child.path"
            :to="child.path"
            class="nav-flyout-item"
            :class="{ active: isActive(child) }"
            @click="onNavClick"
          >
            <Icon :name="getIconName(child.icon)" size="13" />
            <span>{{ child.label }}</span>
          </NuxtLink>
        </div>
      </Transition>
    </Teleport>
  </aside>
</template>

<style scoped>
.sidebar-nav-group {
  display: contents;
}

.sidebar-nav-parent {
  padding: 0 10px;
  height: 36px;
  border: none;
  background: none;
  color: var(--color-font-secondary);
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.1s, color 0.1s;
}

.sidebar-nav-parent:hover,
.sidebar-nav-parent.active {
  background: var(--color-card);
  color: var(--color-font);
}

.sidebar-nav-parent.active {
  background: var(--color-emphasis-soft);
  color: var(--color-emphasis);
}

/* 箭头槽：所有菜单项右侧统一预留固定宽度，保证文本居中 */
.nav-arrow-slot {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--color-font-assist);
  opacity: 0.4;
  transition: transform 0.18s;
}

.nav-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  text-align: left;
}

/* 子菜单滑动动画 */
.submenu-slide-enter-active,
.submenu-slide-leave-active {
  transition: all 0.18s ease;
  overflow: hidden;
}

.submenu-slide-enter-from,
.submenu-slide-leave-to {
  opacity: 0;
  max-height: 0;
}

.submenu-slide-enter-to,
.submenu-slide-leave-from {
  opacity: 1;
  max-height: 200px;
}

.sidebar-subnav {
  display: grid;
  gap: 1px;
  padding-left: 30px;
}

.sidebar-nav-child {
  font-size: 13px;
  height: 32px;
  padding: 0 10px;
}

.sidebar-nav-child .nav-icon {
  opacity: 0.7;
}

.sidebar-divider {
  height: 1px;
  margin: 8px 14px;
  background: var(--color-border);
  opacity: 0.6;
}

.sidebar-nav-admin {
  flex: 0 0 auto;
  padding-top: 0;
}

/* ========== 折叠态下父菜单按钮与普通项一致 ========== */
:deep(.sidebar-collapsed) .sidebar-nav-parent {
  justify-content: center;
  padding: 0;
  width: 100%;
}

:deep(.sidebar-collapsed) .sidebar-nav-parent .nav-icon {
  margin-right: 0;
}

/* ========== 折叠态 tooltip ========== */
.nav-tooltip {
  position: fixed;
  z-index: 1200;
  padding: 6px 12px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--color-font);
  white-space: nowrap;
  box-shadow: var(--shadow-card);
  pointer-events: none;
}

/* ========== 折叠态 flyout 面板 ========== */
.nav-flyout {
  position: fixed;
  z-index: 1200;
  min-width: 150px;
  padding: 6px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
}

.nav-flyout-header {
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-font-assist);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.nav-flyout-divider {
  height: 1px;
  margin: 4px 8px;
  background: var(--color-border);
}

.nav-flyout-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--color-font);
  cursor: pointer;
  text-decoration: none;
  transition: background 0.1s;
}

.nav-flyout-item:hover {
  background: var(--color-primary-background);
  color: var(--color-emphasis);
}

.nav-flyout-item.active {
  background: var(--color-emphasis-soft);
  color: var(--color-emphasis);
  font-weight: 500;
}

/* Transition */
.nav-tip-enter-active,
.nav-tip-leave-active {
  transition: opacity 0.12s, transform 0.12s;
}

.nav-tip-enter-from,
.nav-tip-leave-to {
  opacity: 0;
  transform: translateX(-4px);
}
</style>
