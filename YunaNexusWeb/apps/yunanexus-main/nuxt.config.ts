export default defineNuxtConfig({
  compatibilityDate: "2025-07-15",
  devtools: { enabled: true },
  modules: ["@nuxt/icon"],
  css: [
    "~/assets/styles/theme.css",
    "~/assets/styles/fonts.css",
    "~/assets/styles/base.css",
    "~/assets/styles/components.css",
    "~/assets/styles/pages/hero.css",
    "~/assets/styles/pages/about.css",
    "~/assets/styles/pages/login.css",
  ],
  components: [{ path: "~/components", pathPrefix: false }],
  app: {
    head: {
      script: [
        {
          innerHTML: `try{var d=localStorage.getItem('user-theme-info');if(!d){d='{"dark":false}';localStorage.setItem('user-theme-info',d)}d=JSON.parse(d);if(d.dark)document.body.setAttribute('data-theme','dark')}catch(e){}`,
          tagPosition: "bodyOpen",
        },
      ],
    },
  },
  routeRules: {
    "/api/**": { proxy: "http://localhost:8000/**" },
  },
});
