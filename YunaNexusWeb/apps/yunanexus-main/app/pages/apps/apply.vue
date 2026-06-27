<script setup lang="ts">
definePageMeta({ layout: "default" });

const form = ref({
  clientName: "",
  redirectUri: "",
  description: "",
  grantTypes: "authorization_code",
  scope: "read",
});

const saving = ref(false);
const error = ref("");
const success = ref("");

async function submit() {
  saving.value = true;
  error.value = "";
  success.value = "";
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; data: any; msg: string }>("/api/oauth/client/register", {
      method: "POST",
      body: form.value,
    });
    if (res.code === 200) {
      success.value = "应用提交成功！";
      form.value = { clientName: "", redirectUri: "", description: "", grantTypes: "authorization_code", scope: "read" };
    } else {
      error.value = res.msg || "提交失败";
    }
  } catch (e: any) {
    error.value = e?.data?.msg || e?.message || "请求失败";
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="apps-page">
    <section class="page-header fade-up">
      <div class="page-header-left">
        <div class="page-header-overline">Applications</div>
        <h1 class="page-header-title">创建应用</h1>
        <p class="page-header-description">
          申请接入 OAuth 认证服务
        </p>
      </div>
    </section>

    <div class="apply-form panel-card fade-up">
      <div v-if="success" class="profile-edit-error" style="background: rgba(82,196,26,0.08); color: #389e0d; margin-bottom: 12px">
        {{ success }}
        <NuxtLink to="/apps" class="button button-primary" style="margin-left: 12px">返回列表</NuxtLink>
      </div>
      <div v-if="error" class="profile-edit-error" style="margin-bottom: 12px">{{ error }}</div>

      <div class="profile-edit-fields">
        <label class="auth-field">
          <span>应用名称</span>
          <input v-model="form.clientName" maxlength="32" placeholder="你的应用名称" />
        </label>
        <label class="auth-field">
          <span>回调地址</span>
          <input v-model="form.redirectUri" placeholder="https://example.com/oauth/callback" />
          <div class="apply-hint">OAuth 授权后的回调 URL，必须使用 HTTPS</div>
        </label>
        <label class="auth-field profile-edit-field-wide">
          <span>申请说明</span>
          <textarea v-model="form.description" rows="3" maxlength="500" placeholder="简要说明你的应用用途和需要获取的用户信息" />
        </label>
        <label class="auth-field">
          <span>授权模式</span>
          <SelectInput v-model="form.grantTypes" :options="['authorization_code', 'password', 'authorization_code,refresh_token']" />
          <div class="apply-hint">建议使用 authorization_code 模式</div>
        </label>
        <label class="auth-field">
          <span>授权范围</span>
          <SelectInput v-model="form.scope" :options="['read', 'read,write']" />
        </label>
      </div>

      <div class="profile-edit-actions" style="margin-top: 16px">
        <NuxtLink to="/apps" class="button">取消</NuxtLink>
        <button class="button button-primary" :disabled="saving" @click="submit">
          {{ saving ? "提交中…" : "提交申请" }}
        </button>
      </div>
    </div>
  </div>
</template>
