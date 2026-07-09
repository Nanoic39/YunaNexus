/**
 * 开发环境模拟登录 — 仅在 nuxi dev 下生效。
 * 往 localStorage 写入一个 fake SUPER_ADMIN 身份，
 * 并拦截 API 请求返回模拟数据，所有页面无需后端即可直接预览。
 *
 * 注意：OAuth 应用相关数据已由 useMockApps() composable 统一管理。
 * 此插件主要负责 auth 身份注入和通用 API mock。
 */
export default defineNuxtPlugin((_nuxtApp) => {
  if (!import.meta.dev) return;

  const STORAGE_KEY = "user-auth-info";
  const PROFILE_KEY = "my-profile";
  const MOCK_UUID = "DEV-MOCK-00000000";

  /* ====== 挂载 $fetch mock 拦截器 ====== */
  const nuxtApp = useNuxtApp();
  const originalFetch = nuxtApp.$fetch as typeof $fetch;

  nuxtApp.$fetch = ((url: string, opts?: any) => {
    if (typeof url === "string") {
      // GET /api/file/storage/summary — 模拟存储数据
      if (url === "/api/file/storage/summary") {
        return Promise.resolve({
          code: 200,
          data: { usedStorage: 128 * 1024 * 1024, maxTotalStorage: 1024 * 1024 * 1024, totalStorageUnlimited: false },
          msg: "ok",
        });
      }
      // GET /api/user/me — 返回已注入的 mock profile
      if (url === "/api/user/me") {
        const profileState = useState<any>(PROFILE_KEY);
        if (profileState.value) {
          return Promise.resolve({ code: 200, data: profileState.value, msg: "ok" });
        }
      }
    }
    return originalFetch(url, opts);
  }) as typeof $fetch;

  /* ====== 写入模拟身份数据 ====== */
  const existing = localStorage.getItem(STORAGE_KEY);
  if (existing) {
    try {
      const parsed = JSON.parse(existing);
      if (parsed.uuid && !parsed.uuid.includes("MOCK")) return;
    } catch { /* ignore */ }
  }

  const mockAuth = {
    accessToken: "dev-mock-token",
    refreshToken: "dev-mock-refresh",
    expiresIn: 86400,
    uuid: MOCK_UUID,
    menus: [
      { path: "/", name: "仪表盘", icon: "dashboard", type: 0, sortNo: 10, children: [] },
      { path: "/files", name: "文件", icon: "folder", type: 0, sortNo: 20, children: [] },
      { path: "/apps", name: "应用", icon: "box", type: 0, sortNo: 30, children: [] },
      { path: "/profile", name: "个人中心", icon: "user", type: 0, sortNo: 40, children: [] },
      { path: "/settings", name: "系统设置", icon: "settings", type: 0, sortNo: 50, children: [] },
      {
        path: "/admin",
        name: "应用管理",
        icon: "box",
        type: 0,
        sortNo: 100,
        children: [
          { path: "/admin/apps", name: "审核管理", icon: "clipboard-check", type: 1, sortNo: 10, children: [] },
        ],
      },
      {
        path: "/admin",
        name: "用户管理",
        icon: "users-round",
        type: 0,
        sortNo: 110,
        children: [
          { path: "/admin/users", name: "用户列表", icon: "list", type: 1, sortNo: 10, children: [] },
          { path: "/admin/roles", name: "角色管理", icon: "shield-check", type: 1, sortNo: 20, children: [] },
        ],
      },
      {
        path: "/admin",
        name: "资源管理",
        icon: "database",
        type: 0,
        sortNo: 120,
        children: [
          { path: "/admin/endpoints", name: "接口端点", icon: "plug", type: 1, sortNo: 10, children: [] },
          { path: "/admin/resources", name: "前端资源", icon: "layout", type: 1, sortNo: 20, children: [] },
        ],
      },
    ],
    buttons: ["*:*:*:*"],
    roles: ["SUPER_ADMIN"],
  };

  const mockProfile = {
    nickname: "DevAdmin",
    avatarUuid: "",
    gender: "未知",
    birthday: "",
    bio: "本地开发模拟账号 (SUPER_ADMIN)",
    showcaseBadges: "[]",
    exp: 9999,
    coin: 9999,
    updatedAt: new Date().toISOString(),
  };

  localStorage.setItem(STORAGE_KEY, JSON.stringify(mockAuth));

  const profileState = useState<typeof mockProfile | null>(PROFILE_KEY, () => null);
  if (!profileState.value) {
    profileState.value = mockProfile;
  }
});
