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

const { isLoggedIn } = useAuth();
const { profile } = useMyProfile();

const themeButtonRef = ref<HTMLElement>();

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
</script>

<template>
  <header class="app-topbar">
    <div class="topbar-left">
      <button class="mobile-menu-button" @click="emit('toggle-mobile')">
        <Icon name="lucide:menu" size="16" />
      </button>
      <span class="topbar-title">{{ currentTitle }}</span>
    </div>

    <!-- 已登录：主题切换 + 用户信息 -->
    <ClientOnly>
      <div v-if="isLoggedIn" class="topbar-right">
        <button
          ref="themeButtonRef"
          class="topbar-action-button"
          @click="onThemeClick"
        >
          <Icon name="lucide:sun" size="16" />
        </button>
        <NuxtLink to="/profile" class="user-profile">
          <img :src="avatarSrc()" class="user-avatar" />
          <div class="user-profile-main">
            <div class="user-name">{{ profile?.nickname || "用户" }}</div>
          </div>
        </NuxtLink>
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
