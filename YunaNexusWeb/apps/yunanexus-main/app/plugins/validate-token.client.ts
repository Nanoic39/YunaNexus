/**
 * 页面加载时校验 Token 有效性。
 * Token 无效 → 尝试刷新 → 刷新失败 → 自动登出。
 */
export default defineNuxtPlugin(async () => {
  // SSR 期间不执行
  if (import.meta.server) return;

  const auth = useAuth();

  // 没有 token 则跳过
  if (!auth.isLoggedIn.value) return;

  try {
    // 用 /user/me 校验 token 是否仍然有效（手动设 header，不走 auth-fetch 拦截器避免双重处理）
    const res = await $fetch<{ code: number }>("/api/user/me", {
      headers: { Authorization: `Bearer ${auth.tokens.value?.accessToken}` },
    });

    // code 200 → token 有效，无需处理
    if (res.code === 200) return;

    // code 非 200 → token 无效，尝试刷新
    await tryRefresh(auth);
  } catch {
    // /user/me 请求本身失败（如网络错误），不强制登出
  }
});

async function tryRefresh(auth: ReturnType<typeof useAuth>) {
  const refreshToken = auth.tokens.value?.refreshToken;
  if (!refreshToken) {
    auth.logout();
    return;
  }

  try {
    const refreshRes = await $fetch<{
      code: number;
      data: { accessToken: string; refreshToken: string; uuid: string };
    }>("/api/login/refresh", {
      method: "POST",
      body: { refreshToken },
    });

    if (refreshRes.code === 200 && refreshRes.data?.accessToken) {
      // 刷新成功：更新内存状态 + localStorage
      auth.tokens.value = {
        accessToken: refreshRes.data.accessToken,
        refreshToken: refreshRes.data.refreshToken || refreshToken,
        expiresIn: auth.tokens.value?.expiresIn ?? 86400,
        uuid: refreshRes.data.uuid || auth.tokens.value?.uuid || "",
      };

      try {
        const raw = localStorage.getItem("user-auth-info");
        if (raw) {
          const cached = JSON.parse(raw);
          cached.accessToken = refreshRes.data.accessToken;
          cached.refreshToken = refreshRes.data.refreshToken || refreshToken;
          if (refreshRes.data.uuid) cached.uuid = refreshRes.data.uuid;
          localStorage.setItem("user-auth-info", JSON.stringify(cached));
        }
      } catch { /* ignore */ }
      return;
    }

    // 刷新也失败了 → 登出
    auth.logout();
  } catch {
    auth.logout();
  }
}
