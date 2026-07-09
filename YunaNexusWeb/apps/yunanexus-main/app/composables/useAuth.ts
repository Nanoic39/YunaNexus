export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  uuid: string;
}

interface AuthStorageData extends AuthTokens {
  menus?: any[];
  buttons?: string[];
  roles?: string[];
}

const ROLE_LEVEL_MAP: Record<string, number> = {
  USER: 1,
  VIP: 10,
  ADMIN: 60,
  SUPER_ADMIN: 99,
};

const AUTH_STORAGE_KEY = "user-auth-info";

function loadFromStorage(): AuthStorageData | null {
  if (import.meta.server) return null;
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function saveToStorage(data: AuthStorageData) {
  try {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(data));
  } catch { /* storage full */ }
}

function clearStorage() {
  try {
    localStorage.removeItem(AUTH_STORAGE_KEY);
  } catch { /* ignore */ }
}

async function encryptPassword(plain: string): Promise<string> {
  const { JSEncrypt } = await import("jsencrypt");
  const keyRes = await $fetch<{ code: number; data: { publicKey: string } }>(
    "/api/key/public",
  );
  if (keyRes.code !== 200 || !keyRes.data?.publicKey) {
    throw new Error("获取加密密钥失败");
  }
  const encrypt = new JSEncrypt();
  encrypt.setPublicKey(keyRes.data.publicKey);
  const encrypted = encrypt.encrypt(plain);
  if (!encrypted) throw new Error("密码加密失败");
  return encrypted;
}

export function useAuth() {
  const tokens = useState<AuthTokens | null>("auth-tokens", () => null);
  const menus = useState<any[]>("auth-menus", () => []);
  const buttons = useState<string[]>("auth-buttons", () => []);
  const roles = useState<string[]>("auth-roles", () => []);

  if (import.meta.client && !tokens.value) {
    const stored = loadFromStorage();
    if (stored) {
      tokens.value = {
        accessToken: stored.accessToken,
        refreshToken: stored.refreshToken,
        expiresIn: stored.expiresIn,
        uuid: stored.uuid,
      };
      menus.value = stored.menus ?? [];
      buttons.value = stored.buttons ?? [];
      roles.value = stored.roles ?? [];
    }
  }

  const isLoggedIn = computed(() => !!tokens.value?.accessToken);
  const currentUuid = computed(() => tokens.value?.uuid ?? "");

  const maxRoleLevel = computed(() => {
    if (roles.value.length === 0) return 1;
    return Math.max(...roles.value.map((r) => ROLE_LEVEL_MAP[r] ?? 1));
  });

  async function login(username: string, password: string) {
    const encrypted = await encryptPassword(password);
    const res = await $fetch<{
      code: number;
      msg: string;
      tip: string;
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
      body: { username, password: encrypted },
    });
    if (res.code !== 200) {
      throw new Error(res.tip || res.msg || "登录失败");
    }
    tokens.value = {
      accessToken: res.data.accessToken,
      refreshToken: res.data.refreshToken,
      expiresIn: res.data.expiresIn,
      uuid: res.data.uuid,
    };
    menus.value = res.data.menus ?? [];
    buttons.value = res.data.buttons ?? [];
    roles.value = (res.data as any).roles ?? [];
    saveToStorage({
      ...tokens.value,
      menus: menus.value,
      buttons: buttons.value,
      roles: roles.value,
    });
  }

  function logout() {
    const refreshToken = tokens.value?.refreshToken;
    tokens.value = null;
    menus.value = [];
    buttons.value = [];
    roles.value = [];
    clearStorage();
    if (refreshToken) {
      const nuxtApp = useNuxtApp();
      (nuxtApp.$fetch as typeof $fetch)("/api/login/logout", {
        method: "POST",
        body: { refreshToken },
      }).catch(() => {});
    }
    navigateTo("/login");
  }

  async function logoutAll() {
    try {
      const { $fetch: _f } = useNuxtApp();
      await (_f as typeof $fetch)("/api/login/logout-all", { method: "POST" });
    } catch {}
    tokens.value = null;
    menus.value = [];
    buttons.value = [];
    roles.value = [];
    clearStorage();
    navigateTo("/login");
  }

  async function getAccessToken(): Promise<string | null> {
    if (!tokens.value) return null;
    return tokens.value.accessToken;
  }

  return {
    tokens,
    isLoggedIn,
    currentUuid,
    menus,
    buttons,
    roles,
    maxRoleLevel,
    login,
    logout,
    logoutAll,
    getAccessToken,
    encryptPassword,
  };
}
