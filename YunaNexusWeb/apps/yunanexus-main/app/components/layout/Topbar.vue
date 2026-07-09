<script setup lang="ts">
import mascot from "~/assets/mascot/YunaImageMascotQQ.jpg";
import { useMyProfile } from "~/composables/useMyProfile";

defineProps<{
  currentTitle: string;
}>();

const emit = defineEmits<{
  "toggle-mobile": [];
  "toggle-theme": [{ x: number; y: number }];
}>();

const { isLoggedIn, logout } = useAuth();
const { profile } = useMyProfile();

const themeButtonRef = ref<HTMLElement>();
const avatarMenuOpen = ref(false);

function onThemeClick() {
  const btn = themeButtonRef.value;
  const pos = btn
    ? {
        x: btn.getBoundingClientRect().left + btn.offsetWidth / 2,
        y: btn.getBoundingClientRect().top + btn.offsetHeight / 2,
      }
    : { x: window.innerWidth / 2, y: window.innerHeight / 2 };
  emit("toggle-theme", pos);
}

function avatarSrc(): string {
  if (profile.value?.avatarUuid) {
    return `/api/file/avatar/${profile.value.avatarUuid}`;
  }
  return mascot;
}

function toggleAvatarMenu() {
  avatarMenuOpen.value = !avatarMenuOpen.value;
}

function closeAvatarMenu() {
  avatarMenuOpen.value = false;
}

function handleLogoutClick() {
  avatarMenuOpen.value = false;
  logout();
}

function handleClickOutside(e: MouseEvent) {
  const target = e.target as HTMLElement;
  const menu = document.querySelector(".avatar-dropdown");
  const avatar = document.querySelector(".user-avatar-btn");
  if (menu && !menu.contains(target) && avatar && !avatar.contains(target)) {
    avatarMenuOpen.value = false;
  }
}

onMounted(() => document.addEventListener("click", handleClickOutside));
onUnmounted(() => document.removeEventListener("click", handleClickOutside));
</script>

<template>
  <header class="app-topbar">
    <div class="topbar-left">
      <button class="mobile-menu-button" @click="emit('toggle-mobile')">
        <Icon name="lucide:menu" size="16" />
      </button>
      <span class="topbar-title">{{ currentTitle }}</span>
    </div>

    <!-- 已登录：主题切换 + 用户头像（下拉菜单） -->
    <ClientOnly>
      <div v-if="isLoggedIn" class="topbar-right">
        <button
          ref="themeButtonRef"
          class="topbar-action-button"
          @click="onThemeClick"
        >
          <Icon name="lucide:sun" size="16" />
        </button>
        <div class="avatar-dropdown-wrapper">
          <button class="user-avatar-btn" @click="toggleAvatarMenu">
            <img :src="avatarSrc()" class="user-avatar" />
            <span class="user-avatar-label">{{ profile?.nickname || "用户" }}</span>
            <Icon
              :name="avatarMenuOpen ? 'lucide:chevron-up' : 'lucide:chevron-down'"
              size="12"
              class="user-avatar-chevron"
            />
          </button>
          <Transition name="fade-drop">
            <div v-if="avatarMenuOpen" class="avatar-dropdown">
              <NuxtLink to="/profile" class="avatar-dropdown-item" @click="closeAvatarMenu">
                <Icon name="lucide:circle-user" size="14" />
                <span>个人中心</span>
              </NuxtLink>
              <NuxtLink to="/settings" class="avatar-dropdown-item" @click="closeAvatarMenu">
                <Icon name="lucide:settings" size="14" />
                <span>设置</span>
              </NuxtLink>
              <div class="avatar-dropdown-divider" />
              <button class="avatar-dropdown-item avatar-dropdown-danger" @click="handleLogoutClick">
                <Icon name="lucide:log-out" size="14" />
                <span>登出</span>
              </button>
            </div>
          </Transition>
        </div>
      </div>
      <div v-else class="topbar-right">
        <button
          ref="themeButtonRef"
          class="topbar-action-button"
          @click="onThemeClick"
        >
          <Icon name="lucide:sun" size="16" />
        </button>
        <NuxtLink to="/login" class="button button-primary button-small">
          登录
        </NuxtLink>
      </div>
      <template #fallback>
        <div class="topbar-right" />
      </template>
    </ClientOnly>
  </header>
</template>

<style scoped>
.avatar-dropdown-wrapper {
  position: relative;
}

.user-avatar-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  border: none;
  background: none;
  border-radius: var(--radius-lg);
  cursor: pointer;
  font-family: inherit;
  color: var(--color-font);
  transition: background 0.1s;
}

.user-avatar-btn:hover {
  background: var(--color-card);
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-full);
  object-fit: cover;
}

.user-avatar-label {
  font-size: 13px;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-avatar-chevron {
  color: var(--color-font-assist);
  flex-shrink: 0;
}

.avatar-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 140px;
  padding: 4px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
  z-index: 100;
}

.avatar-dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--color-font);
  cursor: pointer;
  text-decoration: none;
  border: none;
  background: none;
  width: 100%;
  text-align: left;
  font-family: inherit;
  transition: background 0.1s;
}

.avatar-dropdown-item:hover {
  background: var(--color-primary-background);
  color: var(--color-emphasis);
}

.avatar-dropdown-divider {
  height: 1px;
  margin: 4px 8px;
  background: var(--color-border);
}

.avatar-dropdown-danger {
  color: #b31f1f !important;
}

.avatar-dropdown-danger:hover {
  background: rgba(216, 57, 49, 0.06);
}

.fade-drop-enter-active,
.fade-drop-leave-active {
  transition: opacity 0.12s, transform 0.12s;
}

.fade-drop-enter-from,
.fade-drop-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
