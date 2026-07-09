const API_TARGET = process.env.NUXT_API_PROXY_TARGET || "http://127.0.0.1:8000";

const API_PATHS = [
  "/api/login",
  "/api/register",
  "/api/auth",
  "/api/key",
  "/api/oauth",
  "/api/admin",
  "/api/file",
  "/api/user",
];

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
    "~/assets/styles/pages/profile.css",
    "~/assets/styles/pages/apps.css",
  ],
  components: [{ path: "~/components", pathPrefix: false }],
  app: {
    head: {
      script: [
        {
          innerHTML: `try{var d=localStorage.getItem('user-theme-info');if(!d){d='{"dark":false,"sidebarCollapsed":false}';localStorage.setItem('user-theme-info',d)}d=JSON.parse(d);if(d.dark)document.body.setAttribute('data-theme','dark');if(d.sidebarCollapsed)document.body.setAttribute('data-sidebar-collapsed','true')}catch(e){}`,
          tagPosition: "bodyOpen",
        },
      ],
    },
  },
  routeRules: {
    ...Object.fromEntries(
      API_PATHS.map((p) => [
        p + "/**",
        { proxy: API_TARGET + p.replace("/api", "") + "/**" },
      ]),
    ),
    "/_nuxt_icon/**": { proxy: undefined },
    "/.well-known/**": { proxy: undefined },
  },
});
