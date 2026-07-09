/**
 * 开发环境 SSR mock 插件 — 在服务端注入与客户端一致的 mock 身份数据，
 * 确保 SSR 渲染的 HTML 与客户端水合一致，消除 hydration mismatch。
 * 仅在 import.meta.dev 时生效，与 dev-mock-auth.client.ts 配对使用。
 */
export default defineNuxtPlugin(() => {
  if (!import.meta.dev) return;

  const mockAuth = {
    accessToken: "dev-mock-token",
    refreshToken: "dev-mock-refresh",
    expiresIn: 86400,
    uuid: "DEV-MOCK-00000000",
  };

  const mockMenus = [
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
  ];

  const mockButtons = [
    "core:user:self:edit",
    "core:file:avatar:upload",
    "core:oauth:audit",
    "core:oauth:list:manage",
    "admin:users:read",
    "admin:users:write",
    "admin:users:status",
    "admin:users:roles",
    "admin:system:roles:read",
    "admin:system:roles:write",
    "admin:system:resources:read",
    "admin:system:resources:write",
    "admin:system:endpoints:read",
    "admin:system:endpoints:write",
  ];

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

  // 注入 auth state
  useState("auth-tokens", () => mockAuth);
  useState("auth-menus", () => mockMenus);
  useState("auth-buttons", () => mockButtons);
  useState("auth-roles", () => ["SUPER_ADMIN"]);
  // 注入 profile state
  useState("my-profile", () => mockProfile);
  // 预计算菜单允许路径
  useState("menu-allowed-paths", () => [
    "/", "/files", "/apps", "/profile", "/settings",
    "/admin", "/admin/apps", "/admin/users", "/admin/roles", "/admin/endpoints", "/admin/resources",
  ]);
});
