export interface SidebarMenuChild {
  key: string;
  label: string;
  icon: string;
  to: string;
}

export interface SidebarMenuItem {
  key: string;
  label: string;
  icon: string;
  to?: string;
  children?: SidebarMenuChild[];
}

export const sidebarMenus: SidebarMenuItem[] = [
  {
    key: "dashboard",
    label: "仪表盘",
    icon: "lucide:layout-dashboard",
    to: "/",
  },
  {
    key: "account",
    label: "账户",
    icon: "lucide:book-user",
    children: [
      {
        key: "login",
        label: "登录",
        icon: "lucide:log-in",
        to: "/login",
      },
      {
        key: "register",
        label: "注册",
        icon: "lucide:user-plus",
        to: "/register",
      },
      {
        key: "appeal",
        label: "申诉",
        icon: "lucide:file-warning",
        to: "/appeal",
      },
    ],
  },
  {
    key: "profile",
    label: "个人资料",
    icon: "lucide:user-round",
    to: "/profile",
  },
];
