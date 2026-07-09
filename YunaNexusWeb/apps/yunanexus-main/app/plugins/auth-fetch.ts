export default defineNuxtPlugin((nuxtApp) => {
  const auth = useAuth();

  nuxtApp.$fetch = $fetch.create({
    async onRequest({ options }) {
      if (import.meta.server) return;
      try {
        const raw = localStorage.getItem("user-auth-info");
        if (raw) {
          const token = JSON.parse(raw)?.accessToken;
          if (token) {
            const headers = new Headers(options.headers);
            headers.set("Authorization", "Bearer " + token);
            options.headers = headers;
          }
        }
      } catch { /* ignore */ }
    },
    async onResponseError({ response, options }) {
      if (response.status !== 401 || import.meta.server) return;

      // 排除认证相关请求本身，避免死循环
      const url = typeof (options as any).url === "string" ? (options as any).url : "";
      if (url.includes("/api/login") || url.includes("/api/key/public") || url.includes("/api/user/register")) return;

      try {
        const raw = localStorage.getItem("user-auth-info");
        if (!raw) { autoLogout(); return; }
        const cached = JSON.parse(raw);
        if (!cached.refreshToken) { autoLogout(); return; }

        const refreshRes = await $fetch<{
          code: number;
          data?: { accessToken: string; refreshToken: string };
        }>("/api/login/refresh", {
          method: "POST",
          body: { refreshToken: cached.refreshToken } as Record<string, string>,
        });

        if (refreshRes?.data?.accessToken) {
          // 更新 localStorage
          cached.accessToken = refreshRes.data.accessToken;
          cached.refreshToken = refreshRes.data.refreshToken || cached.refreshToken;
          localStorage.setItem("user-auth-info", JSON.stringify(cached));

          // 同步更新内存状态
          if (auth.tokens.value) {
            auth.tokens.value.accessToken = refreshRes.data.accessToken;
            auth.tokens.value.refreshToken = refreshRes.data.refreshToken || auth.tokens.value.refreshToken;
          }

          // 重试原请求
          const headers = new Headers(options.headers as any);
          headers.set("Authorization", "Bearer " + cached.accessToken);
          options.headers = headers;
          return;
        }
        // refresh token 也过期了
        autoLogout();
      } catch {
        // 刷新请求本身失败（网络/其他错误）
        autoLogout();
      }
    },
  }) as typeof $fetch;
});

function autoLogout() {
  try { localStorage.removeItem("user-auth-info"); } catch {}
  // 只在非登录页时跳转，避免循环
  if (typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
    window.location.href = "/login";
  }
}
