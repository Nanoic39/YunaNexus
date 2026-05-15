export default defineNuxtConfig({
  compatibilityDate: "2025-07-15",
  modules: ["@nuxt/icon"],
  devtools: {
    enabled: true,
  },
  css: ["./app/assets/styles/app.scss"],
  app: {
    head: {
      title: "YunaNexus",
      titleTemplate: "%s | YunaNexus",
    },
  },
  runtimeConfig: {
    public: {
      siteTitle: "",
      apiBase: "",
      authBase: "",
    },
  },
});
