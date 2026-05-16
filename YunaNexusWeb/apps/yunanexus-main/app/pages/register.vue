<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import AppInputMenu from "../components/form/AppInputMenu.vue";
import { useAuthApi } from "~/composables/useAuthApi";

useHead({ title: "注册" });

const authApi = useAuthApi();
await authApi.restoreSession();

if (authApi.accessToken.value) {
  await navigateTo("/");
}

const genderItems = ["未知", "男", "女"];
const isSendingCode = ref(false);
const isSubmitting = ref(false);
const codeMessage = ref("");
const codeSuccess = ref(false);
const submitMessage = ref("");

const form = reactive({
  username: "",
  nickname: "",
  email: "",
  gender: "未知",
  password: "",
  confirmPassword: "",
  code: "",
  agreement: true,
});
const finalGender = computed(() => form.gender.trim());
const canSubmit = computed(
  () =>
    finalGender.value.length > 0 &&
    finalGender.value.length <= 10 &&
    form.username.trim().length >= 3 &&
    form.nickname.trim().length >= 2 &&
    form.email.trim().length > 0 &&
    form.password.trim().length >= 6 &&
    form.confirmPassword === form.password &&
    form.code.trim().length > 0 &&
    form.agreement,
);

const sendCode = async () => {
  if (!form.email.trim() || isSendingCode.value) return;
  codeMessage.value = "";
  codeSuccess.value = false;
  isSendingCode.value = true;
  try {
    const result = await authApi.sendRegisterCode(form.email.trim());
    codeSuccess.value = result.code === 200;
    codeMessage.value =
      result.tip ||
      result.msg ||
      (result.code === 200 ? "验证码已发送" : "发送失败，请稍后重试");
  } catch (error) {
    codeMessage.value =
      error instanceof Error ? error.message : "发送失败，请稍后重试";
  } finally {
    isSendingCode.value = false;
  }
};

const submitRegister = async () => {
  if (!canSubmit.value || isSubmitting.value) return;
  submitMessage.value = "";
  isSubmitting.value = true;
  try {
    const result = await authApi.register({
      username: form.username.trim(),
      nickname: form.nickname.trim(),
      email: form.email.trim(),
      gender: finalGender.value,
      password: form.password,
      verifyCode: form.code.trim(),
    });
    submitMessage.value =
      result.tip ||
      result.msg ||
      (result.code === 200 ? "注册成功，请前往登录" : "注册失败，请稍后重试");
    if (result.code === 200) await navigateTo("/login");
  } catch (error) {
    submitMessage.value =
      error instanceof Error ? error.message : "注册失败，请稍后重试";
  } finally {
    isSubmitting.value = false;
  }
};
</script>

<template>
  <div class="register-page">
    <section class="register-card">
      <div class="register-header">
        <p class="register-overline">Create Account</p>
        <h1 class="register-title">注册 YunaNexus 账号</h1>
        <p class="register-desc">填写基础信息并完成邮箱验证码验证。</p>
      </div>

      <form class="register-form" @submit.prevent="submitRegister">
        <div class="register-grid">
          <label class="register-field">
            <span class="register-label">用户名</span>
            <input
              v-model="form.username"
              class="register-input"
              type="text"
              placeholder="用于登录的唯一用户名"
              autocomplete="username"
            />
          </label>

          <label class="register-field">
            <span class="register-label">昵称</span>
            <input
              v-model="form.nickname"
              class="register-input"
              type="text"
              placeholder="请输入昵称"
            />
          </label>

          <label class="register-field">
            <span class="register-label">电子邮箱</span>
            <input
              v-model="form.email"
              class="register-input"
              type="email"
              placeholder="请输入可接收验证码的邮箱"
              autocomplete="email"
            />
          </label>

          <label class="register-field">
            <span class="register-label">性别</span>
            <AppInputMenu
              v-model="form.gender"
              :items="genderItems"
              :maxlength="10"
              placeholder="请选择或输入性别，最多10个字符"
            />
          </label>

          <label class="register-field">
            <span class="register-label">密码</span>
            <input
              v-model="form.password"
              class="register-input"
              type="password"
              placeholder="至少 6 位密码"
              autocomplete="new-password"
            />
          </label>

          <label class="register-field">
            <span class="register-label">确认密码</span>
            <input
              v-model="form.confirmPassword"
              class="register-input"
              type="password"
              placeholder="请再次输入密码"
              autocomplete="new-password"
            />
          </label>
        </div>

        <div class="register-code-row">
          <label class="register-field register-field-grow">
            <span class="register-label">邮箱验证码</span>
            <input
              v-model="form.code"
              class="register-input"
              type="text"
              placeholder="请输入邮箱验证码"
            />
          </label>

          <button
            class="register-code-button"
            type="button"
            :disabled="!form.email.trim() || isSendingCode"
            @click="sendCode"
          >
            {{ isSendingCode ? "发送中..." : "发送验证码" }}
          </button>
        </div>

        <p
          v-if="codeMessage"
          class="register-message"
          :class="
            codeSuccess ? 'register-message-success' : 'register-message-error'
          "
        >
          {{ codeMessage }}
        </p>

        <label class="register-check">
          <input v-model="form.agreement" type="checkbox" />
          <span>我已阅读并同意平台基础使用规则与账号安全说明</span>
        </label>

        <p v-if="submitMessage" class="register-message register-message-error">
          {{ submitMessage }}
        </p>

        <button
          class="register-submit"
          type="submit"
          :disabled="!canSubmit || isSubmitting"
        >
          {{ isSubmitting ? "提交中..." : "创建账号" }}
        </button>

        <div class="register-footer">
          <span>已经有账号？</span>
          <NuxtLink class="register-text-link" to="/login">前往登录</NuxtLink>
        </div>
      </form>
    </section>
  </div>
</template>

<style scoped lang="scss">
.register-page {
  display: flex;
  justify-content: center;
  padding: 24px 0;
}

.register-card {
  width: min(100%, 760px);
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: var(--yn-color-surface);
  box-shadow: var(--yn-shadow-card);
  padding: 32px;
}

.register-header {
  margin-bottom: 28px;
}

.register-overline {
  margin: 0 0 12px;
  color: var(--yn-color-primary);
  font-family: var(--yn-font-mono), monospace;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.register-title {
  margin: 0 0 12px;
  color: var(--yn-color-text-primary);
  font-family: var(--yn-font-mono), monospace;
  font-size: clamp(28px, 3vw, 36px);
  line-height: 1.2;
}

.register-desc {
  margin: 0;
  color: var(--yn-color-text-secondary);
  line-height: 1.8;
}

.register-form {
  display: grid;
  gap: 16px;
}

.register-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.register-field {
  display: grid;
  gap: 8px;
}

.register-field-grow {
  flex: 1;
}

.register-label {
  color: var(--yn-color-text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.register-input {
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

.register-input:focus {
  outline: none;
  border-color: var(--yn-color-primary);
  box-shadow: var(--yn-glow-medium);
}

.register-code-row {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.register-helper {
  color: var(--yn-color-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

.register-check {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--yn-color-text-secondary);
  font-size: 14px;
}

.register-submit,
.register-code-button {
  display: inline-flex;
  min-height: 46px;
  align-items: center;
  justify-content: center;
  border-radius: var(--yn-radius-medium);
  font-weight: 600;
  transition:
    background 0.2s ease,
    color 0.2s ease,
    border-color 0.2s ease;
}

.register-submit {
  width: 100%;
  border: 1px solid var(--yn-color-primary);
  background: var(--yn-color-primary);
  color: #ffffff;
  cursor: pointer;
}

.register-submit:hover:not(:disabled),
.register-code-button:hover:not(:disabled) {
  filter: brightness(0.96);
}

.register-submit:disabled,
.register-code-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.register-code-button {
  min-width: 140px;
  border: 1px solid var(--yn-color-border-medium);
  background: var(--yn-color-surface-raised);
  color: var(--yn-color-text-primary);
  padding: 0 16px;
}

.register-footer {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
}

.register-text-link {
  color: var(--yn-color-primary);
  font-weight: 600;
}

.register-message {
  margin: 0;
  border-radius: var(--yn-radius-medium);
  padding: 10px 12px;
  font-size: 14px;
  line-height: 1.6;
}

.register-message-success {
  border: 1px solid rgba(22, 163, 74, 0.18);
  background: rgba(22, 163, 74, 0.08);
  color: #15803d;
}

.register-message-error {
  border: 1px solid rgba(220, 38, 38, 0.18);
  background: rgba(220, 38, 38, 0.08);
  color: #b91c1c;
}

@media (max-width: 720px) {
  .register-card {
    padding: 20px;
  }

  .register-grid {
    grid-template-columns: 1fr;
  }

  .register-code-row,
  .register-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .register-code-button {
    width: 100%;
  }
}
</style>
