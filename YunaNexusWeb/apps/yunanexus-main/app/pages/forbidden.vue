<script setup lang="ts">
definePageMeta({ layout: "blank" });
</script>

<template>
  <div class="forbidden-card">
    <div class="forbidden-code">403</div>
    <h1 class="forbidden-title">访问被拒绝</h1>
    <p class="forbidden-desc">你没有权限访问此页面，或该页面不存在。</p>
    <div class="forbidden-actions">
      <NuxtLink to="/" class="button button-primary"> 回到首页 </NuxtLink>
    </div>
  </div>
</template>

<style scoped>
.forbidden-card {
  text-align: center;
  max-width: 400px;
  width: 100%;
  padding: 48px 32px;
  background: var(--color-card);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-overlay);
  border: 1px solid var(--color-border);
}

.forbidden-code {
  font-size: 64px;
  font-weight: 700;
  color: var(--color-font-assist);
  line-height: 1;
  margin-bottom: 16px;
}

.forbidden-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--color-font);
}

.forbidden-desc {
  font-size: 14px;
  color: var(--color-font-secondary);
  margin-bottom: 28px;
}

.forbidden-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
}
</style>
