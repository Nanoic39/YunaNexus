<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import AppButton from "../components/form/AppButton.vue";
import AppCheckbox from "../components/form/AppCheckbox.vue";
import AppFormField from "../components/form/AppFormField.vue";
import AppInput from "../components/form/AppInput.vue";
import { useAuthApi } from "~/composables/useAuthApi";

useHead({
  title: "登录",
});

const authApi = useAuthApi();
await authApi.restoreSession();

if (authApi.accessToken.value) {
  await navigateTo("/");
}

const isSubmitting = ref(false);
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
  isSubmitting.value = true;

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
    isSubmitting.value = false;
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
        <AppFormField label="用户名" required>
          <AppInput
            v-model="form.account"
            type="text"
            placeholder="请输入用户名"
            autocomplete="username"
          />
        </AppFormField>

        <AppFormField label="密码" required>
          <AppInput
            v-model="form.password"
            type="password"
            placeholder="请输入登录密码"
            autocomplete="current-password"
          />
        </AppFormField>

        <div class="auth-row">
          <AppCheckbox v-model="form.remember">记住登录</AppCheckbox>
        </div>

        <p v-if="submitError" class="auth-message auth-message-error">
          {{ submitError }}
        </p>

        <AppButton
          class="auth-submit"
          type="submit"
          block
          :loading="isSubmitting"
          :disabled="!canSubmit || isSubmitting"
        >
          登录
        </AppButton>

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

.auth-row,
.auth-footer {
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
