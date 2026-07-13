<script setup lang="ts">
import { useToast } from "~/composables/useToast";

definePageMeta({ layout: "default" });

const { login } = useAuth();
const toast = useToast();
const route = useRoute();

const account = ref("");
const password = ref("");
const loading = ref(false);
const errorMsg = ref("");
const fieldErrors = ref<Record<string, string>>({});

function validateField(field: string) {
  const errs = { ...fieldErrors.value };
  if (field === "account") {
    if (account.value.trim().length < 3) {
      errs.account = "用户名至少需要 3 个字符";
    } else {
      delete errs.account;
    }
  } else if (field === "password") {
    if (password.value && password.value.length < 6) {
      errs.password = "密码长度不能少于 6 位";
    } else {
      delete errs.password;
    }
  }
  fieldErrors.value = errs;
}

function validate(): boolean {
  const errs: Record<string, string> = {};
  if (!account.value) {
    errs.account = "请输入用户名";
  } else if (account.value.trim().length < 3) {
    errs.account = "用户名至少需要 3 个字符";
  }
  if (!password.value) {
    errs.password = "请输入密码";
  } else if (password.value.length < 6) {
    errs.password = "密码长度不能少于 6 位";
  }
  fieldErrors.value = errs;
  return Object.keys(errs).length === 0;
}

async function handleLogin() {
  errorMsg.value = "";
  if (!validate()) {
    return;
  }
  loading.value = true;
  try {
    await login(account.value, password.value);
    toast.success("登录成功");
    navigateTo((route.query.redirect as string) || "/");
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
            :class="{ 'input-error': fieldErrors.account }"
            @blur="validateField('account')"
          />
          <span v-if="fieldErrors.account" class="field-error-text">{{ fieldErrors.account }}</span>
        </label>

        <label class="auth-field">
          <span class="auth-required">密码</span>
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            :class="{ 'input-error': fieldErrors.password }"
            @blur="validateField('password')"
          />
          <span v-if="fieldErrors.password" class="field-error-text">{{ fieldErrors.password }}</span>
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
