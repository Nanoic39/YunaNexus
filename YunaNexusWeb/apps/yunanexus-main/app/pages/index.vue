<script setup lang="ts">
import mascotImage from "~/assets/mascot/YunaImageMascot.jpg";
import { useMyProfile } from "~/composables/useMyProfile";

const { isLoggedIn } = useAuth();
const { profile } = useMyProfile();

const storage = ref({ used: 0, max: 0, unlimited: false });

function formatSize(bytes: number): string {
  if (bytes === 0) return "0 B";
  if (bytes < 0) return "--";
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + " KB";
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + " MB";
  return (bytes / 1073741824).toFixed(1) + " GB";
}

onMounted(async () => {
  if (!isLoggedIn.value) return;
  const { $fetch: _f } = useNuxtApp();
  const fetch = _f as typeof $fetch;
  try {
    const res = await fetch<{
      code: number;
      data: {
        usedStorage: number;
        maxTotalStorage: number;
        totalStorageUnlimited: boolean;
      };
    }>("/api/file/storage/summary");
    if (res.code === 200 && res.data) {
      storage.value = {
        used: res.data.usedStorage,
        max: res.data.maxTotalStorage,
        unlimited: res.data.totalStorageUnlimited,
      };
    }
  } catch {
    /* ignore */
  }
});
</script>

<template>
  <ClientOnly>
    <template #fallback>
      <div class="dashboard-loading">加载中…</div>
    </template>
    <div v-if="isLoggedIn" class="dashboard-grid">
      <section class="page-header fade-up">
        <div class="page-header-left">
          <div class="page-header-overline">Dashboard</div>
          <h1 class="page-header-title">仪表盘</h1>
          <p class="page-header-description">
            欢迎回来，{{ profile?.nickname || "用户" }}
          </p>
        </div>
      </section>

      <section class="stat-row fade-up">
        <div class="stat-card">
          <div class="stat-card-number">{{ profile?.exp ?? 0 }}</div>
          <div class="stat-card-label">经验值</div>
        </div>
        <div class="stat-card">
          <div class="stat-card-number">{{ profile?.coin ?? 0 }}</div>
          <div class="stat-card-label">硬币</div>
        </div>
        <div class="stat-card">
          <div class="stat-card-number">{{ formatSize(storage.used) }}</div>
          <div class="stat-card-label">已用空间</div>
        </div>
        <div class="stat-card">
          <div class="stat-card-number">
            {{ storage.unlimited ? "无限制" : formatSize(storage.max) }}
          </div>
          <div class="stat-card-label">总空间</div>
        </div>
      </section>

      <section class="panel-grid fade-up">
        <div class="panel-card">
          <div class="panel-card-header">快速入口</div>
          <div class="panel-card-body">
            <div style="opacity: 0.5; cursor: not-allowed">
              <strong>文件管理</strong>
              <span>即将上线</span>
            </div>
            <NuxtLink to="/profile" class="quick-link">
              <Icon name="lucide:user" size="16" />
              <strong>个人中心</strong>
              <span>账户资料与徽章</span>
            </NuxtLink>
            <NuxtLink to="/settings" class="quick-link">
              <Icon name="lucide:settings" size="16" />
              <strong>系统设置</strong>
              <span>偏好与安全</span>
            </NuxtLink>
          </div>
        </div>
        <div class="panel-card">
          <div class="panel-card-header">系统公告</div>
          <div class="panel-card-body">
            <div style="color: var(--color-font-assist)">暂无新公告</div>
          </div>
        </div>
      </section>
    </div>
    <div v-else class="home-wrapper">
      <section class="hero">
        <div class="hero-left">
          <div class="system-badge fade-up">
            <span class="badge-dot" />
            YunaNexus
          </div>
          <h1 class="fade-up">
            YunaNexus<br />
            <span style="color: var(--color-emphasis)">芸枢</span>
          </h1>
          <div class="hero-sub-name fade-up">
            UNIFIED IDENTITY &amp; FILE SYSTEM
          </div>
          <p class="hero-description fade-up">
            统一身份认证与文件管理中枢。<br />
            为各个服务提供标准账户体系、权限控制与独立文件空间。
          </p>
          <div class="hero-bubble fade-up">
            <div class="hero-bubble-avatar">
              <img :src="mascotImage" alt="Yuna" />
            </div>
            <div class="hero-bubble-content">化繁为简，万象互联。</div>
          </div>
        </div>
        <div class="hero-right fade-up">
          <div class="avatar-card">
            <img :src="mascotImage" alt="YunaNexus" />
            <div class="avatar-card-footer">
              <div>
                <div class="avatar-card-name">Fl4g{Yuuunaaa}</div>
              </div>
              <span class="avatar-card-tag">ROOT</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </ClientOnly>
</template>

<style scoped>
.dashboard-loading {
  text-align: center;
  padding: 64px 24px;
  color: var(--color-font-secondary);
}

.dashboard-grid {
  max-width: 100%;
  margin: 0 auto;
  width: 100%;
  display: grid;
  gap: 20px;
}

.quick-link {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  color: inherit;
}

.quick-link span {
  color: var(--color-font-secondary);
}

.home-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
}

@media (max-width: 768px) {
  .home-wrapper {
    align-items: flex-start;
    padding-top: 0;
  }
}
</style>
