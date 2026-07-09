export interface MenuItem {
  path: string;
  label: string;
  icon: string;
  children?: MenuItem[];
  /** 菜单分组：不填=用户区, "admin"=管理区 */
  group?: string;
}

/** 后端 ResourceVO 格式 */
interface ResourceVO {
  id: number;
  name: string;
  code: string;
  type: number;
  icon: string;
  path: string;
  redirect: string;
  component: string;
  sortNo: number;
  children: ResourceVO[];
}

/** 将后端 ResourceVO 转为前端 MenuItem，递归处理子菜单 */
function mapResources(list: ResourceVO[]): MenuItem[] {
  if (!list) return [];
  return list
    .sort((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0))
    .map((r) => ({
      path: r.path ?? "",
      label: r.name ?? "",
      icon: r.icon ?? "",
      children: mapResources(r.children),
    }));
}

/** 用户端菜单 */
const FALLBACK_MENU_AUTH: MenuItem[] = [
  { path: "/", label: "仪表盘", icon: "dashboard" },
  { path: "/files", label: "文件", icon: "folder" },
  { path: "/apps", label: "应用", icon: "box" },
  { path: "/profile", label: "个人中心", icon: "user" },
  { path: "/settings", label: "系统设置", icon: "settings" },
];

/** 管理端菜单（仅 SUPER_ADMIN 可见，通过 group="admin" 标识） */
const FALLBACK_MENU_ADMIN: MenuItem[] = [
  {
    path: "/admin",
    label: "应用管理",
    icon: "box",
    group: "admin",
    children: [
      { path: "/admin/apps", label: "审核管理", icon: "clipboard-check", group: "admin" },
    ],
  },
  {
    path: "/admin",
    label: "用户管理",
    icon: "users-round",
    group: "admin",
    children: [
      { path: "/admin/users", label: "用户列表", icon: "list", group: "admin" },
      { path: "/admin/roles", label: "角色管理", icon: "shield-check", group: "admin" },
    ],
  },
  {
    path: "/admin",
    label: "资源管理",
    icon: "database",
    group: "admin",
    children: [
      { path: "/admin/endpoints", label: "接口端点", icon: "plug", group: "admin" },
      { path: "/admin/resources", label: "前端资源", icon: "layout", group: "admin" },
    ],
  },
];

const FALLBACK_MENU_GUEST: MenuItem[] = [
  { path: "/", label: "首页", icon: "home" },
  { path: "/about", label: "关于", icon: "about" },
];

export function useMenu() {
  const { isLoggedIn, menus: rawMenus, buttons } = useAuth();

  const menuItems = computed<MenuItem[]>(() => {
    if (isLoggedIn.value && rawMenus.value.length > 0) {
      return mapResources(rawMenus.value as ResourceVO[]);
    }
    if (isLoggedIn.value) {
      // Fallback：管理员额外显示管理菜单
      const isAdmin = buttons.value?.includes("core:oauth:audit") || buttons.value?.includes("core:oauth:list:manage");
      return isAdmin ? [...FALLBACK_MENU_AUTH, ...FALLBACK_MENU_ADMIN] : FALLBACK_MENU_AUTH;
    }
    return FALLBACK_MENU_GUEST;
  });

  /** 获取所有允许访问的路径 */
  function collectPaths(items: MenuItem[]): string[] {
    const paths: string[] = [];
    for (const item of items) {
      if (item.path) paths.push(item.path);
      if (item.children) paths.push(...collectPaths(item.children));
    }
    return paths;
  }

  const allowedPaths = computed(() => collectPaths(menuItems.value));

  function isPathAllowed(path: string): boolean {
    if (
      path === "/forbidden" ||
      path.startsWith("/login") ||
      path.startsWith("/register")
    )
      return true;
    return allowedPaths.value.some(
      (p) => path === p || path.startsWith(p + "/"),
    );
  }

  return { menuItems, allowedPaths, isPathAllowed };
}
