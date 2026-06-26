export interface MenuItem {
  path: string;
  label: string;
  icon: string;
  children?: MenuItem[];
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

const FALLBACK_MENU_AUTH: MenuItem[] = [
  { path: "/", label: "仪表盘", icon: "dashboard" },
  { path: "/files", label: "文件", icon: "folder" },
  { path: "/profile", label: "我的", icon: "user" },
  { path: "/settings", label: "设置", icon: "settings" },
];

const FALLBACK_MENU_GUEST: MenuItem[] = [
  { path: "/", label: "首页", icon: "home" },
  { path: "/about", label: "关于", icon: "about" },
];

export function useMenu() {
  const { isLoggedIn, menus: rawMenus } = useAuth();

  const menuItems = computed<MenuItem[]>(() => {
    if (isLoggedIn.value && rawMenus.value.length > 0) {
      return mapResources(rawMenus.value as ResourceVO[]);
    }
    return isLoggedIn.value ? FALLBACK_MENU_AUTH : FALLBACK_MENU_GUEST;
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
