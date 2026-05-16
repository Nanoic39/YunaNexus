<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from "vue";
import AppButton from "../components/form/AppButton.vue";
import AppCheckbox from "../components/form/AppCheckbox.vue";
import AppFormField from "../components/form/AppFormField.vue";
import AppInput from "../components/form/AppInput.vue";
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
const codeCountdown = ref(0);
const codeMessage = ref("");
const codeSuccess = ref(false);
const submitMessage = ref("");
let codeCountdownTimer: ReturnType<typeof window.setInterval> | null = null;

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
const codeButtonText = computed(() =>
  codeCountdown.value > 0 ? `${codeCountdown.value} 秒后重发` : "发送验证码",
);
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

const clearCodeCountdown = () => {
  if (codeCountdownTimer !== null) {
    window.clearInterval(codeCountdownTimer);
    codeCountdownTimer = null;
  }
};

const startCodeCountdown = () => {
  clearCodeCountdown();
  codeCountdown.value = 60;
  codeCountdownTimer = setInterval(() => {
    if (codeCountdown.value <= 1) {
      codeCountdown.value = 0;
      clearCodeCountdown();
      return;
    }
    codeCountdown.value -= 1;
  }, 1000);
};

const sendCode = async () => {
  if (!form.email.trim() || isSendingCode.value || codeCountdown.value > 0)
    return;
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
    if (result.code === 200) {
      startCodeCountdown();
    }
  } catch (error) {
    codeMessage.value =
      error instanceof Error ? error.message : "发送失败，请稍后重试";
  } finally {
    isSendingCode.value = false;
  }
};

onBeforeUnmount(() => {
  clearCodeCountdown();
});

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
          <AppFormField label="用户名" required>
            <AppInput
              v-model="form.username"
              type="text"
              placeholder="用于登录的唯一用户名"
              autocomplete="username"
            />
          </AppFormField>

          <AppFormField label="昵称" required>
            <AppInput
              v-model="form.nickname"
              type="text"
              placeholder="请输入昵称"
            />
          </AppFormField>

          <AppFormField label="电子邮箱" required>
            <AppInput
              v-model="form.email"
              type="email"
              placeholder="请输入可接收验证码的邮箱"
              autocomplete="email"
            />
          </AppFormField>

          <AppFormField label="性别" required>
            <AppInputMenu
              v-model="form.gender"
              :items="genderItems"
              :maxlength="10"
              placeholder="请选择或输入性别，最多10个字符"
            />
          </AppFormField>

          <AppFormField label="密码" required>
            <AppInput
              v-model="form.password"
              type="password"
              placeholder="至少 6 位密码"
              autocomplete="new-password"
            />
          </AppFormField>

          <AppFormField label="确认密码" required>
            <AppInput
              v-model="form.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              autocomplete="new-password"
            />
          </AppFormField>
        </div>

        <div class="register-code-row">
          <AppFormField class="register-field-grow" label="邮箱验证码" required>
            <AppInput
              v-model="form.code"
              type="text"
              placeholder="请输入邮箱验证码"
            />
          </AppFormField>

          <AppButton
            class="register-code-button"
            type="button"
            variant="secondary"
            :loading="isSendingCode"
            :disabled="!form.email.trim() || isSendingCode || codeCountdown > 0"
            @click="sendCode"
          >
            {{ codeButtonText }}
          </AppButton>
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

        <AppCheckbox v-model="form.agreement">
          我已阅读并同意平台基础使用规则与账号安全说明
        </AppCheckbox>

        <p v-if="submitMessage" class="register-message register-message-error">
          {{ submitMessage }}
        </p>

        <AppButton
          class="register-submit"
          type="submit"
          block
          :loading="isSubmitting"
          :disabled="!canSubmit || isSubmitting"
        >
          创建账号
        </AppButton>

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

.register-field-grow {
  flex: 1;
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

.register-code-button {
  min-width: 140px;
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
