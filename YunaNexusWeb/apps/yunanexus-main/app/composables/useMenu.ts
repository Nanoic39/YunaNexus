export interface MenuItem {
  path: string;
  label: string;
  icon: "dashboard" | "folder" | "user" | "settings" | "monitor" | "about";
}

const FALLBACK_MENU_AUTH: MenuItem[] = [
  { path: "/", label: "首页", icon: "dashboard" },
  { path: "/files", label: "文件", icon: "folder" },
  { path: "/profile", label: "我的", icon: "user" },
  { path: "/settings", label: "设置", icon: "settings" },
  { path: "/about", label: "关于", icon: "about" },
];

const FALLBACK_MENU_GUEST: MenuItem[] = [
  { path: "/", label: "首页", icon: "dashboard" },
  { path: "/about", label: "关于", icon: "about" },
];

export function useMenu() {
  const { isLoggedIn, menus: authMenus } = useAuth();

  const menuItems = computed<MenuItem[]>(() => {
    if (isLoggedIn.value && authMenus.value.length > 0) {
      return authMenus.value;
    }
    return isLoggedIn.value ? FALLBACK_MENU_AUTH : FALLBACK_MENU_GUEST;
  });

  const allowedPaths = computed(() => menuItems.value.map((item) => item.path));

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
