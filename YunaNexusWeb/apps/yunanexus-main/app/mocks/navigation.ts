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
    label: "首页",
    icon: "lucide:layout-dashboard",
    to: "/",
  },
  {
    key: "files",
    label: "文件",
    icon: "lucide:folder-kanban",
    to: "/files",
  },
  {
    key: "profile",
    label: "我的",
    icon: "lucide:user-round",
    to: "/profile",
  },
];
