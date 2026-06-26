<script setup lang="ts">
definePageMeta({ layout: "default" });

const { login } = useAuth();

const account = ref("");
const password = ref("");
const loading = ref(false);
const errorMsg = ref("");

async function handleLogin() {
  errorMsg.value = "";
  if (!account.value || !password.value) {
    errorMsg.value = "请填写完整信息";
    return;
  }
  loading.value = true;
  try {
    await login(account.value, password.value);
    navigateTo("/");
  } catch (e: any) {
    errorMsg.value = e.message || "登录失败，请检查用户名或密码";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="auth-wrapper">
    <div class="auth-card">
      <div class="auth-header">
        <h1 class="lolifont auth-title">登录</h1>
        <p class="auth-subtitle">欢迎回到 YunaNexus</p>
      </div>

      <form class="auth-form" @submit.prevent="handleLogin">
        <div v-if="errorMsg" class="auth-error">{{ errorMsg }}</div>

        <label class="auth-field">
          <span class="auth-required">用户名</span>
          <input
            v-model="account"
            type="text"
            autocomplete="username"
            placeholder="请输入用户名"
          />
        </label>

        <label class="auth-field">
          <span class="auth-required">密码</span>
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
          />
        </label>

        <button
          type="submit"
          class="button button-primary auth-submit"
          :disabled="loading"
        >
          {{ loading ? "登录中…" : "登录" }}
        </button>
      </form>

      <div class="auth-footer">
        还没有账号？
        <NuxtLink to="/register" class="auth-link">立即注册</NuxtLink>
      </div>
    </div>
  </div>
</template>
