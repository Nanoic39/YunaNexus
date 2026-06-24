<script setup lang="ts">
import mascotImage from "~/assets/mascot/YunaImageMascot.jpg";
</script>

<template>
  <div class="home-wrapper">
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
</template>

<style scoped>
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
