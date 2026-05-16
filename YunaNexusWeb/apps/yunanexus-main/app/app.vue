<script setup lang="ts">
import AppContextMenuHost from "./components/feedback/AppContextMenuHost.vue";
import AppGlobalLoader from "./components/feedback/AppGlobalLoader.vue";
</script>

<template>
  <div class="app-root">
    <AppGlobalLoader scope="fullscreen" />
    <NuxtRouteAnnouncer />
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
    <AppContextMenuHost />
    <div id="app-toast-root" />
    <div id="app-notice-root" />
    <div id="app-dialog-root" />
  </div>
</template>
