export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  uuid: string;
}

interface AuthStorageData extends AuthTokens {
  menus?: any[];
  buttons?: string[];
}

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
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(data));
}

function clearStorage() {
  localStorage.removeItem(AUTH_STORAGE_KEY);
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
    }
  }

  const isLoggedIn = computed(() => !!tokens.value?.accessToken);
  const currentUuid = computed(() => tokens.value?.uuid ?? "");

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
    saveToStorage({
      ...tokens.value,
      menus: menus.value,
      buttons: buttons.value,
    });
  }

  function logout() {
    tokens.value = null;
    menus.value = [];
    buttons.value = [];
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
    login,
    logout,
    getAccessToken,
    encryptPassword,
  };
}
