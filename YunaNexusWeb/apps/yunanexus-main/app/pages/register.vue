<script setup lang="ts">
definePageMeta({ layout: "default" });

const { encryptPassword } = useAuth();

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

let countdownTimer: ReturnType<typeof setInterval> | null = null;

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
          />
        </label>

        <label class="auth-field">
          <span class="auth-required">邮箱地址</span>
          <input
            v-model="email"
            type="email"
            autocomplete="email"
            placeholder="请输入邮箱地址"
          />
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
        </label>

        <label class="auth-field">
          <div class="auth-field-label">
            <span class="auth-required">昵称</span>
            <span class="auth-field-hint">对外展示的名称</span>
          </div>
          <input v-model="nickname" type="text" placeholder="请输入昵称" />
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
          />
        </label>

        <label class="auth-field">
          <span class="auth-required">确认密码</span>
          <input
            v-model="confirmPassword"
            type="password"
            autocomplete="new-password"
            placeholder="请再次输入密码"
          />
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
