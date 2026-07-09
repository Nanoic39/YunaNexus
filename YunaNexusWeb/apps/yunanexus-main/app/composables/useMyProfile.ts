export interface MyProfile {
  nickname: string;
  avatarUuid: string;
  gender: string;
  birthday: string;
  bio: string;
  showcaseBadges: string;
  exp: number;
  coin: number;
  updatedAt: string;
}

export function useMyProfile() {
  const { isLoggedIn } = useAuth();
  const authFetch = useNuxtApp().$fetch as typeof $fetch;
  const profile = useState<MyProfile | null>("my-profile", () => null);
  const loading = ref(false);
  const error = ref("");

  async function fetch(force = false) {
    if (!isLoggedIn.value) return;
    if (!force && profile.value) return;
    loading.value = true;
    error.value = "";
    try {
      const res = await authFetch<{
        code: number;
        msg: string;
        data: MyProfile;
      }>("/api/user/me");
      if (res.code !== 200) {
        error.value = res.msg || "获取用户信息失败";
        return;
      }
      profile.value = res.data;
    } catch {
      error.value = "网络错误";
    } finally {
      loading.value = false;
    }
  }

  function refreshProfile() {
    return fetch(true);
  }

  return { profile, loading, error, fetch, refreshProfile };
}
