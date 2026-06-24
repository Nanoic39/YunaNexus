<script setup lang="ts">
definePageMeta({ layout: "blank" });

const router = useRouter();
</script>

<template>
  <div class="modal-card" style="text-align: center; min-width: 360px">
    <div
      style="
        font-size: 48px;
        margin-bottom: 12px;
        font-weight: 700;
        color: var(--color-font-assist);
      "
    >
      403
    </div>
    <h4>NOT_PERMISSION</h4>
    <p>你没有权限访问此页面，或该页面不存在。</p>
    <div style="display: flex; gap: 8px; justify-content: center">
      <button class="button button-small" @click="router.back()">
        返回上一页
      </button>
      <NuxtLink to="/" class="button button-primary button-small">
        回到首页
      </NuxtLink>
    </div>
  </div>
</template>
