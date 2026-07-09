<script setup lang="ts">
definePageMeta({ layout: "default" });

const { isLoggedIn, logoutAll } = useAuth();
const confirming = ref(false);

function doLogoutAll() {
  confirming.value = true;
}
function confirmLogoutAll() {
  logoutAll();
}
function cancelLogoutAll() {
  confirming.value = false;
}
</script>

<template>
  <div class="apps-page">
    <section class="page-header fade-up">
      <div class="page-header-left">
        <div class="page-header-overline">Settings</div>
        <h1 class="page-header-title">设置</h1>
      </div>
    </section>

    <div class="panel-card fade-up">
      <div class="panel-card-header">安全设置</div>
      <div class="panel-card-body profile-info-grid">
        <div class="profile-info-item profile-info-item-wide" style="display: flex; align-items: center; justify-content: space-between">
          <div>
            <strong>多设备登出</strong>
            <p style="font-size: 12px; color: var(--color-font-assist); margin-top: 2px">
              退出所有已登录的设备
            </p>
          </div>
          <button v-if="!confirming" class="button" style="color: #b31f1f" @click="doLogoutAll">
            全部登出
          </button>
          <div v-else style="display: flex; gap: 8px">
            <button class="button button-primary" @click="confirmLogoutAll">确认</button>
            <button class="button" @click="cancelLogoutAll">取消</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
