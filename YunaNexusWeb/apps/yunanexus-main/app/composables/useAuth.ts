export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  uuid: string;
}

const AUTH_STORAGE_KEY = "user-auth-info";

function loadFromStorage(): AuthTokens | null {
  if (import.meta.server) return null;
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function saveToStorage(tokens: AuthTokens) {
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(tokens));
}

function clearStorage() {
  localStorage.removeItem(AUTH_STORAGE_KEY);
}

export function useAuth() {
  const tokens = useState<AuthTokens | null>("auth-tokens", () => null);
  const menus = useState<any[]>("auth-menus", () => []);
  const buttons = useState<string[]>("auth-buttons", () => []);

  // 客户端初始化：从 localStorage 恢复
  if (import.meta.client && !tokens.value) {
    tokens.value = loadFromStorage();
  }

  const isLoggedIn = computed(() => !!tokens.value?.accessToken);
  const currentUuid = computed(() => tokens.value?.uuid ?? "");

  async function login(username: string, password: string) {
    const res = await $fetch<{
      code: number;
      data: {
        accessToken: string;
        refreshToken: string;
        expiresIn: number;
        uuid: string;
        menus: any[];
        buttons: string[];
      };
    }>("/api/login", {
      method: "POST",
      body: { username, password },
    });
    if (res.code !== 200) throw new Error("登录失败");
    tokens.value = {
      accessToken: res.data.accessToken,
      refreshToken: res.data.refreshToken,
      expiresIn: res.data.expiresIn,
      uuid: res.data.uuid,
    };
    menus.value = res.data.menus ?? [];
    buttons.value = res.data.buttons ?? [];
    saveToStorage(tokens.value);
  }

  function logout() {
    tokens.value = null;
    menus.value = [];
    buttons.value = [];
    clearStorage();
    navigateTo("/login");
  }

  /** 获取当前 accessToken，过期时自动刷新 */
  async function getAccessToken(): Promise<string | null> {
    if (!tokens.value) return null;
    // TODO: 检查过期并 refresh
    return tokens.value.accessToken;
  }

  return {
    tokens,
    isLoggedIn,
    currentUuid,
    menus,
    buttons,
    login,
    logout,
    getAccessToken,
  };
}
