<script setup lang="ts">
defineProps<{
  currentTitle: string;
}>();

const emit = defineEmits<{
  "toggle-mobile": [];
  "toggle-theme": [{ x: number; y: number }];
}>();

const { isLoggedIn } = useAuth();

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
    <div v-if="isLoggedIn" class="topbar-right">
      <button
        ref="themeButtonRef"
        class="topbar-action-button"
        @click="onThemeClick"
      >
        <Icon name="lucide:sun" size="16" />
      </button>
      <div class="user-profile">
        <div class="user-avatar">清</div>
        <div>
          <div class="user-name">清汐</div>
          <div class="user-role">管理员</div>
        </div>
      </div>
    </div>

    <!-- 未登录：主题切换 + 开源地址 + 登录 -->
    <div v-else class="topbar-right">
      <button
        ref="themeButtonRef"
        class="topbar-action-button"
        @click="onThemeClick"
      >
        <Icon name="lucide:sun" size="16" />
      </button>
      <a
        class="topbar-action-button"
        href="https://github.com/Nanoic39/YunaNexus"
        target="_blank"
        rel="noopener noreferrer"
      >
        <Icon name="lucide:github" size="16" />
      </a>
      <NuxtLink to="/login" class="button button-primary button-small">
        登录
      </NuxtLink>
    </div>
  </header>
</template>
