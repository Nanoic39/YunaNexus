<template>
  <div class="app-root">
    <NuxtLoadingIndicator />
    <NuxtRouteAnnouncer />
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
    <div id="app-toast-root" />
    <div id="app-notice-root" />
    <div id="app-dialog-root" />
  </div>
</template>
