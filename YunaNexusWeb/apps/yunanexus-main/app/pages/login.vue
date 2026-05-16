<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useAuthApi } from "~/composables/useAuthApi";

useHead({
  title: "登录",
});

const authApi = useAuthApi();
await authApi.restoreSession();

if (authApi.accessToken.value) {
  await navigateTo("/");
}

const isSubmitting = ref("");ref(false);
const submitError = ref("");

const form = reactive({
  account: "",
  password: "",
  remember: true,
});

const canSubmit = computed(() => {
  return form.account.trim().length > 0 && form.password.trim().length >= 6;
});

const submitLogin = async () => {
  if (!canSubmit.value || isSubmitting.value) {
    return;
  }

  submitError.value = "";
  isSubmitting.value = "登录中";

  try {
    const result = await authApi.login({
      username: form.account.trim(),
      password: form.password,
    });

    if (result.code !== 200) {
      submitError.value = result.tip || result.msg || "登录失败，请稍后重试";
      return;
    }

    await navigateTo("/");
  } catch (error) {
    submitError.value =
      error instanceof Error ? error.message : "登录失败，请稍后重试";
  } finally {
    isSubmitting.value = "";
  }
};
</script>

<template>
  <div class="auth-page">
    <section class="auth-card">
      <div class="auth-header">
        <p class="auth-overline">Account Access</p>
        <h1 class="auth-title">登录 YunaNexus</h1>
        <p class="auth-desc">使用用户名与密码登录主站。</p>
      </div>

      <form class="auth-form" @submit.prevent="submitLogin">
        <label class="auth-field">
          <span class="auth-label">用户名</span>
          <input
            v-model="form.account"
            class="auth-input"
            type="text"
            placeholder="请输入用户名"
            autocomplete="username"
          />
        </label>

        <label class="auth-field">
          <span class="auth-label">密码</span>
          <input
            v-model="form.password"
            class="auth-input"
            type="password"
            placeholder="请输入登录密码"
            autocomplete="current-password"
          />
        </label>

        <div class="auth-row">
          <label class="auth-check">
            <input v-model="form.remember" type="checkbox" />
            <span>记住当前设备</span>
          </label>

          <NuxtLink class="auth-text-link" to="/appeal">
            账号封禁申诉
          </NuxtLink>
        </div>

        <p v-if="submitError" class="auth-message auth-message-error">
          {{ submitError }}
        </p>

        <button
          class="auth-submit"
          type="submit"
          :disabled="!canSubmit || !!isSubmitting"
        >
          {{ isSubmitting ? "登录中..." : "登录" }}
        </button>

        <div class="auth-footer">
          <span>还没有账号？</span>
          <NuxtLink class="auth-text-link" to="/register">立即注册</NuxtLink>
        </div>
      </form>
    </section>
  </div>
</template>

<style scoped lang="scss">
.auth-page {
  display: flex;
  justify-content: center;
  padding: 24px 0;
}

.auth-card {
  width: min(100%, 520px);
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: var(--yn-color-surface);
  box-shadow: var(--yn-shadow-card);
  padding: 32px;
}

.auth-header {
  margin-bottom: 28px;
}

.auth-overline {
  margin: 0 0 12px;
  color: var(--yn-color-primary);
  font-family: var(--yn-font-mono), monospace;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.auth-title {
  margin: 0 0 12px;
  color: var(--yn-color-text-primary);
  font-family: var(--yn-font-mono), monospace;
  font-size: clamp(28px, 3vw, 36px);
  line-height: 1.2;
}

.auth-desc {
  margin: 0;
  color: var(--yn-color-text-secondary);
  line-height: 1.8;
}

.auth-form {
  display: grid;
  gap: 16px;
}

.auth-field {
  display: grid;
  gap: 8px;
}

.auth-label {
  color: var(--yn-color-text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.auth-input {
  height: 46px;
  width: 100%;
  border: 1px solid var(--yn-color-border-medium);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface-raised);
  color: var(--yn-color-text-primary);
  padding: 0 14px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

.auth-input:focus {
  outline: none;
  border-color: var(--yn-color-primary);
  box-shadow: var(--yn-glow-medium);
}

.auth-row,
.auth-footer,
.auth-check {
  display: flex;
  align-items: center;
}

.auth-row {
  justify-content: space-between;
  gap: 12px;
}

.auth-footer {
  justify-content: flex-start;
  gap: 12px;
}

.auth-check {
  gap: 8px;
  color: var(--yn-color-text-secondary);
  font-size: 14px;
}

.auth-submit {
  width: 100%;
  min-height: 46px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--yn-color-primary);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-primary);
  color: #ffffff;
  font-weight: 600;
  cursor: pointer;
  transition: filter 0.2s ease;
}

.auth-submit:hover:not(:disabled) {
  filter: brightness(0.96);
}

.auth-submit:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.auth-text-link {
  color: var(--yn-color-primary);
  font-weight: 600;
}

.auth-message {
  margin: 0;
  border-radius: var(--yn-radius-medium);
  padding: 10px 12px;
  font-size: 14px;
  line-height: 1.6;
}

.auth-message-error {
  border: 1px solid rgba(220, 38, 38, 0.18);
  background: rgba(220, 38, 38, 0.08);
  color: #b91c1c;
}

@media (max-width: 640px) {
  .auth-card {
    padding: 20px;
  }

  .auth-row,
  .auth-footer {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
