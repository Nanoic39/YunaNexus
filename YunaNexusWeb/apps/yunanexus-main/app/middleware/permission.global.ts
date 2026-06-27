export default defineNuxtRouteMiddleware((to) => {
  if (
    to.path === "/forbidden" ||
    to.path.startsWith("/login") ||
    to.path.startsWith("/register") ||
    to.path.startsWith("/admin/")
  )
    return;

  const allowedPaths = useState<string[]>("menu-allowed-paths", () => []);

  // 菜单尚未加载完成，放行（SSR / 首次渲染时）
  if (allowedPaths.value.length === 0) return;

  const isAllowed = allowedPaths.value.some(
    (p) => to.path === p || to.path.startsWith(p + "/"),
  );

  if (!isAllowed) {
    return navigateTo("/forbidden");
  }
});
