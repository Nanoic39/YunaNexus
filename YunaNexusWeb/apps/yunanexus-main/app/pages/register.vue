<script setup lang="ts">
import { useToast } from "~/composables/useToast";

definePageMeta({ layout: "default" });

const { encryptPassword } = useAuth();
const toast = useToast();

const username = ref("");
const password = ref("");
const confirmPassword = ref("");
const email = ref("");
const verifyCode = ref("");
const nickname = ref("");
const gender = ref("");
const adminInitKey = ref("");
const loading = ref(false);
const sendingCode = ref(false);
const codeSent = ref(false);
const countdown = ref(0);
const errorMsg = ref("");
const fieldErrors = ref<Record<string, string>>({});

let countdownTimer: ReturnType<typeof setInterval> | null = null;

function validateField(field: string) {
  const errs = { ...fieldErrors.value };
  switch (field) {
    case "email":
      if (email.value && (!email.value.includes("@") || !email.value.includes("."))) {
        errs.email = "请输入有效的邮箱地址";
      } else {
        delete errs.email;
      }
      break;
    case "username":
      if (username.value && username.value.trim().length < 3) {
        errs.username = "用户名至少需要 3 个字符";
      } else {
        delete errs.username;
      }
      break;
    case "verifyCode":
      if (verifyCode.value && !/^\d{6}$/.test(verifyCode.value)) {
        errs.verifyCode = "验证码必须为 6 位数字";
      } else {
        delete errs.verifyCode;
      }
      break;
    case "password":
      if (password.value && password.value.length < 6) {
        errs.password = "密码长度不能少于 6 位";
      } else {
        delete errs.password;
      }
      break;
    case "confirmPassword":
      if (confirmPassword.value && confirmPassword.value !== password.value) {
        errs.confirmPassword = "两次输入的密码不一致";
      } else {
        delete errs.confirmPassword;
      }
      break;
    case "nickname":
      if (nickname.value && nickname.value.trim().length < 1) {
        errs.nickname = "昵称不能为空";
      } else {
        delete errs.nickname;
      }
      break;
  }
  fieldErrors.value = errs;
}

async function sendVerifyCode() {
  if (!email.value) {
    errorMsg.value = "请先输入邮箱地址";
    return;
  }
  errorMsg.value = "";
  sendingCode.value = true;
  try {
    const res = await $fetch<{ code: number; msg: string }>(
      "/api/register/send-code",
      {
        method: "POST",
        body: { email: email.value },
      },
    );
    if (res.code !== 200) {
      errorMsg.value = res.msg || "验证码发送失败";
      return;
    }
    codeSent.value = true;
    countdown.value = 60;
    countdownTimer = setInterval(() => {
      countdown.value--;
      if (countdown.value <= 0) {
        if (countdownTimer) clearInterval(countdownTimer);
        codeSent.value = false;
      }
    }, 1000);
  } catch {
    errorMsg.value = "网络错误，请稍后重试";
  } finally {
    sendingCode.value = false;
  }
}

async function handleRegister() {
  if (loading.value) return;
  errorMsg.value = "";
  if (
    !username.value ||
    !password.value ||
    !confirmPassword.value ||
    !email.value ||
    !verifyCode.value ||
    !nickname.value ||
    !gender.value
  ) {
    errorMsg.value = "请填写完整信息";
    return;
  }
  if (password.value !== confirmPassword.value) {
    errorMsg.value = "两次输入的密码不一致";
    return;
  }
  if (password.value.length < 6) {
    errorMsg.value = "密码长度不能少于 6 位";
    return;
  }
  loading.value = true;
  try {
    const encrypted = await encryptPassword(password.value);
    const res = await $fetch<{ code: number; msg: string }>("/api/register", {
      method: "POST",
      body: {
        username: username.value,
        password: encrypted,
        email: email.value,
        verifyCode: verifyCode.value,
        nickname: nickname.value,
        gender: gender.value,
        adminInitKey: adminInitKey.value || undefined,
      },
    });
    if (res.code !== 200) {
      errorMsg.value = res.msg || "注册失败";
      return;
    }
    toast.success("注册成功");
    navigateTo("/login");
  } catch {
    errorMsg.value = "网络错误，请稍后重试";
  } finally {
    loading.value = false;
  }
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer);
});
</script>

<template>
  <div class="auth-wrapper">
    <div class="auth-card auth-card-register">
      <div class="auth-header">
        <h1 class="lolifont auth-title">注册</h1>
        <p class="auth-subtitle">创建你的 YunaNexus 账号</p>
      </div>

      <form
        class="auth-form auth-form-columns"
        @submit.prevent="handleRegister"
      >
        <div v-if="errorMsg" class="auth-error auth-error-full">
          {{ errorMsg }}
        </div>

        <label class="auth-field">
          <span class="auth-required">用户名</span>
          <input
            v-model="username"
            type="text"
            autocomplete="username"
            placeholder="请输入用户名"
            :class="{ 'input-error': fieldErrors.username }"
            @blur="validateField('username')"
          />
          <span v-if="fieldErrors.username" class="field-error-text">{{ fieldErrors.username }}</span>
        </label>

        <label class="auth-field">
          <span class="auth-required">邮箱地址</span>
          <input
            v-model="email"
            type="email"
            autocomplete="email"
            placeholder="请输入邮箱地址"
            :class="{ 'input-error': fieldErrors.email }"
            @blur="validateField('email')"
          />
          <span v-if="fieldErrors.email" class="field-error-text">{{ fieldErrors.email }}</span>
        </label>

        <label class="auth-field auth-field-full">
          <span class="auth-required">邮箱验证码</span>
          <div class="auth-field-row">
            <input
              v-model="verifyCode"
              type="text"
              autocomplete="one-time-code"
              placeholder="请输入验证码"
              class="auth-field-code-input"
              :class="{ 'input-error': fieldErrors.verifyCode }"
              @blur="validateField('verifyCode')"
            />
            <button
              type="button"
              class="auth-sendcode-button"
              :disabled="sendingCode || codeSent"
              @click="sendVerifyCode"
            >
              {{
                sendingCode
                  ? "发送中…"
                  : codeSent
                    ? `${countdown}s 后重发`
                    : "发送验证码"
              }}
            </button>
          </div>
          <span v-if="fieldErrors.verifyCode" class="field-error-text">{{ fieldErrors.verifyCode }}</span>
        </label>

        <label class="auth-field">
          <div class="auth-field-label">
            <span class="auth-required">昵称</span>
            <span class="auth-field-hint">对外展示的名称</span>
          </div>
          <input
            v-model="nickname"
            type="text"
            placeholder="请输入昵称"
            :class="{ 'input-error': fieldErrors.nickname }"
            @blur="validateField('nickname')"
          />
          <span v-if="fieldErrors.nickname" class="field-error-text">{{ fieldErrors.nickname }}</span>
        </label>

        <label class="auth-field">
          <span class="auth-required">性别</span>
          <SelectInput v-model="gender" :options="['男', '女', '未知']" />
        </label>

        <label class="auth-field">
          <div class="auth-field-label">
            <span class="auth-required">密码</span>
            <span class="auth-field-hint">至少 6 位</span>
          </div>
          <input
            v-model="password"
            type="password"
            autocomplete="new-password"
            placeholder="请输入密码"
            :class="{ 'input-error': fieldErrors.password }"
            @blur="validateField('password')"
          />
          <span v-if="fieldErrors.password" class="field-error-text">{{ fieldErrors.password }}</span>
        </label>

        <label class="auth-field">
          <span class="auth-required">确认密码</span>
          <input
            v-model="confirmPassword"
            type="password"
            autocomplete="new-password"
            placeholder="请再次输入密码"
            :class="{ 'input-error': fieldErrors.confirmPassword }"
            @blur="validateField('confirmPassword')"
          />
          <span v-if="fieldErrors.confirmPassword" class="field-error-text">{{ fieldErrors.confirmPassword }}</span>
        </label>

        <details class="auth-advanced auth-error-full">
          <summary>高级选项</summary>
          <label class="auth-field" style="margin-top: 8px">
            <div class="auth-field-label">
              <span>管理员初始化密钥</span>
              <span class="auth-field-hint"
                >首次部署时使用，留空即注册为普通用户</span
              >
            </div>
            <input
              v-model="adminInitKey"
              type="text"
              autocomplete="off"
              placeholder="请输入初始化密钥"
            />
          </label>
        </details>

        <button
          type="submit"
          class="button button-primary auth-submit auth-error-full"
          :disabled="loading"
        >
          {{ loading ? "注册中…" : "注册" }}
        </button>
      </form>

      <div class="auth-footer">
        已有账号？
        <NuxtLink to="/login" class="auth-link">立即登录</NuxtLink>
      </div>
    </div>
  </div>
</template>
