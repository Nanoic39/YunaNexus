import { existsSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const appRoot = dirname(fileURLToPath(import.meta.url));
const workspaceRoot = resolve(appRoot, "../..");

const parseEnvFile = (filePath: string) => {
  if (!existsSync(filePath)) {
    return {} as Record<string, string>;
  }

  return readFileSync(filePath, "utf8")
    .split(/\r?\n/)
    .reduce<Record<string, string>>((acc, line) => {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) {
        return acc;
      }

      const separatorIndex = trimmed.indexOf("=");
      if (separatorIndex < 0) {
        return acc;
      }

      const key = trimmed.slice(0, separatorIndex).trim();
      const value = trimmed
        .slice(separatorIndex + 1)
        .trim()
        .replace(/^(["'])(.*)\1$/, "$2");
      if (key) {
        acc[key] = value;
      }
      return acc;
    }, {});
};

const globalEnv = parseEnvFile(resolve(workspaceRoot, ".env"));
const localEnv = parseEnvFile(resolve(appRoot, ".env"));
const pickEnv = (key: string) =>
  process.env[key] ?? localEnv[key] ?? globalEnv[key] ?? "";

export default defineNuxtConfig({
  nitro: {
    experimental: {
      openAPI: false,
    },
  },
  compatibilityDate: "2025-07-15",
  modules: ["@nuxt/icon"],
  devtools: {
    enabled: true,
  },
  css: ["./app/assets/styles/app.scss"],
  app: {
    head: {
      title: pickEnv("NUXT_PUBLIC_SITE_TITLE") || "YunaNexus",
      titleTemplate: `%s | ${pickEnv("NUXT_PUBLIC_SITE_TITLE") || "YunaNexus"}`,
      script: [
        {
          key: "yn-theme-init",
          innerHTML: `(function(){try{var mode=localStorage.getItem('yn-theme-mode')||'light';var dark=window.matchMedia('(prefers-color-scheme: dark)').matches;var resolved=mode==='system'?(dark?'dark':'light'):mode;document.documentElement.dataset.theme=resolved;document.documentElement.style.colorScheme=resolved;}catch(e){}})();`,
        },
      ],
    },
  },
  runtimeConfig: {
    public: {
      siteTitle: pickEnv("NUXT_PUBLIC_SITE_TITLE"),
      apiBase: pickEnv("NUXT_PUBLIC_API_BASE"),
      authBase: pickEnv("NUXT_PUBLIC_AUTH_BASE"),
      oauthClientUuid: pickEnv("NUXT_PUBLIC_OAUTH_CLIENT_UUID"),
      oauthClientSecret: pickEnv("NUXT_PUBLIC_OAUTH_CLIENT_SECRET"),
      authAccessCookieMaxAge:
        pickEnv("NUXT_PUBLIC_AUTH_ACCESS_COOKIE_MAX_AGE") || "7200",
      authRefreshCookieMaxAge:
        pickEnv("NUXT_PUBLIC_AUTH_REFRESH_COOKIE_MAX_AGE") || "604800",
    },
  },
});
