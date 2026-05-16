// 文件: app/composables/useAuthApi.ts
import { JSEncrypt } from "jsencrypt";

type ResultEnvelope<T> = {
  code: number;
  msg: string;
  tip: string | null;
  timestamp: number;
  data: T;
};

type OAuthLoginToken = {
  tokenType: string;
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  scope: string;
};

type LoginPayload = {
  username: string;
  password: string;
};

type RegisterPayload = {
  username: string;
  nickname: string;
  gender: string;
  password: string;
  email: string;
  verifyCode: string;
};

type CurrentUser = {
  uuid?: string;
  nickname?: string | null;
  avatarUuid?: string | null;
};

type PermissionSnapshot = {
  userId?: number;
  maxRoleLevel?: number;
  roles?: string[];
  permissionCodes?: string[];
  fieldWhitelist?: Record<string, string[]>;
};

const SUCCESS_CODE = 200;
const EXPIRED_CODES = new Set([401, 4010, 4011, 4012]);
const REFRESH_ADVANCE_MS = 60_000;
let refreshTimer: ReturnType<typeof window.setTimeout> | null = null;
let refreshPromise: Promise<ResultEnvelope<OAuthLoginToken> | null> | null =
  null;

const resolveMessage = (
  input: Partial<Pick<ResultEnvelope<unknown>, "msg" | "tip">>,
  fallback: string,
) => {
  return input.tip?.trim() || input.msg?.trim() || fallback;
};

const resolveFetchErrorMessage = (error: unknown, fallback: string) => {
  if (!error || typeof error !== "object") {
    return fallback;
  }

  const maybeError = error as {
    data?: {
      message?: string;
      msg?: string;
      tip?: string;
      error?: string;
    };
    message?: string;
  };

  return (
    maybeError.data?.tip ||
    maybeError.data?.msg ||
    maybeError.data?.message ||
    maybeError.data?.error ||
    maybeError.message ||
    fallback
  );
};

const toPublicKeyPem = (base64Key: string) => {
  const normalized = base64Key.replace(/\s+/g, "");
  const body = normalized.match(/.{1,64}/g)?.join("\n") ?? normalized;
  return `-----BEGIN PUBLIC KEY-----\n${body}\n-----END PUBLIC KEY-----`;
};

export const useAuthApi = () => {
  const runtimeConfig = useRuntimeConfig();

  const accessToken = useCookie<string | null>("yn-access-token", {
    default: () => null,
    sameSite: "lax",
  });
  const refreshToken = useCookie<string | null>("yn-refresh-token", {
    default: () => null,
    sameSite: "lax",
  });
  const tokenType = useCookie<string | null>("yn-token-type", {
    default: () => null,
    sameSite: "lax",
  });
  const tokenExpiresIn = useCookie<number | null>("yn-token-expires-in", {
    default: () => null,
    sameSite: "lax",
  });
  const authScope = useCookie<string | null>("yn-auth-scope", {
    default: () => null,
    sameSite: "lax",
  });
  const tokenExpireAt = useCookie<number | null>("yn-token-expire-at", {
    default: () => null,
    sameSite: "lax",
  });

  const currentUser = useState<CurrentUser | null>(
    "yn-current-user",
    () => null,
  );
  const permissionSnapshot = useState<PermissionSnapshot | null>(
    "yn-permission-snapshot",
    () => null,
  );
  const sessionReady = useState<boolean>("yn-auth-ready", () => false);

  const requestResult = async <T>(
    url: string,
    options: Record<string, unknown> = {},
  ) => {
    try {
      return await $fetch<ResultEnvelope<T>>(url, options);
    } catch (error) {
      throw new Error(resolveFetchErrorMessage(error, "请求失败，请稍后重试"));
    }
  };

  const clearRefreshTimer = () => {
    if (import.meta.client && refreshTimer !== null) {
      window.clearTimeout(refreshTimer);
      refreshTimer = null;
    }
  };

  const saveTokens = (token: OAuthLoginToken) => {
    accessToken.value = token.accessToken;
    refreshToken.value = token.refreshToken;
    tokenType.value = token.tokenType;
    tokenExpiresIn.value = token.expiresIn;
    tokenExpireAt.value = Date.now() + token.expiresIn * 1000;
    authScope.value = token.scope;
  };

  const clearSession = () => {
    clearRefreshTimer();
    accessToken.value = null;
    refreshToken.value = null;
    tokenType.value = null;
    tokenExpiresIn.value = null;
    tokenExpireAt.value = null;
    authScope.value = null;
    currentUser.value = null;
    permissionSnapshot.value = null;
    sessionReady.value = true;
  };

  const getAuthorizationHeader = () => {
    if (!accessToken.value) {
      return "";
    }
    return `${tokenType.value || "Bearer"} ${accessToken.value}`;
  };

  const scheduleSilentRefresh = () => {
    if (!import.meta.client || !tokenExpireAt.value || !refreshToken.value) {
      return;
    }
    clearRefreshTimer();
    const delay = Math.max(
      tokenExpireAt.value - Date.now() - REFRESH_ADVANCE_MS,
      0,
    );
    refreshTimer = setTimeout(() => {
      void refreshSession();
    }, delay);
  };

  const refreshSession = async () => {
    if (!refreshToken.value || refreshPromise) {
      return refreshPromise;
    }
    refreshPromise = requestResult<OAuthLoginToken>("/api/auth/refresh", {
      method: "POST",
      body: {
        clientUuid: runtimeConfig.public.oauthClientUuid,
        clientSecret: runtimeConfig.public.oauthClientSecret,
        refreshToken: refreshToken.value,
      },
    })
      .then((result) => {
        if (result.code === SUCCESS_CODE && result.data) {
          saveTokens(result.data);
          scheduleSilentRefresh();
          return result;
        }
        clearSession();
        return result;
      })
      .finally(() => {
        refreshPromise = null;
      });
    return refreshPromise;
  };

  const fetchCurrentUser = async (allowRefresh = true) => {
    const authorization = getAuthorizationHeader();
    if (!authorization) {
      currentUser.value = null;
      return null;
    }
    const result = await requestResult<CurrentUser>("/api/user/me", {
      method: "GET",
      headers: { Authorization: authorization },
    });
    if (result.code === SUCCESS_CODE) {
      currentUser.value = result.data ?? null;
      return currentUser.value;
    }
    if (allowRefresh && EXPIRED_CODES.has(result.code)) {
      const refreshResult = await refreshSession();
      if (refreshResult?.code === SUCCESS_CODE) {
        return await fetchCurrentUser(false);
      }
    }
    return null;
  };

  const fetchPermissionSnapshot = async () => {
    const authorization = getAuthorizationHeader();
    if (!authorization) {
      permissionSnapshot.value = null;
      return null;
    }

    const result = await requestResult<PermissionSnapshot>(
      "/api/auth/permissionSnapshot",
      {
        method: "GET",
        headers: { Authorization: authorization },
      },
    );

    if (result.code === SUCCESS_CODE) {
      permissionSnapshot.value = result.data ?? null;
      return permissionSnapshot.value;
    }

    return null;
  };

  const fetchRsaPublicKey = async () => {
    const result = await requestResult<string>(
      "/api/user/security/rsa/publicKey",
      {
        method: "GET",
      },
    );

    if (result.code !== SUCCESS_CODE || !result.data) {
      throw new Error(resolveMessage(result, "获取 RSA 公钥失败"));
    }

    return result.data;
  };

  const encryptPassword = async (rawPassword: string) => {
    const publicKey = await fetchRsaPublicKey();
    const encryptor = new JSEncrypt();

    encryptor.setPublicKey(toPublicKeyPem(publicKey));

    const encrypted = encryptor.encrypt(rawPassword);

    if (!encrypted) {
      throw new Error("注册密码加密失败");
    }

    return encrypted;
  };

  const login = async (payload: LoginPayload) => {
    const clientUuid = runtimeConfig.public.oauthClientUuid;
    const clientSecret = runtimeConfig.public.oauthClientSecret;

    if (!clientUuid || !clientSecret) {
      throw new Error(
        "请先配置 NUXT_PUBLIC_OAUTH_CLIENT_UUID 和 NUXT_PUBLIC_OAUTH_CLIENT_SECRET",
      );
    }

    const result = await requestResult<OAuthLoginToken>("/api/auth/login", {
      method: "POST",
      body: {
        grantType: "password",
        clientUuid,
        clientSecret,
        username: payload.username,
        password: payload.password,
      },
    });

    if (result.code === SUCCESS_CODE && result.data) {
      saveTokens(result.data);
      await Promise.all([fetchCurrentUser(), fetchPermissionSnapshot()]);
      scheduleSilentRefresh();
      sessionReady.value = true;
    }

    return result;
  };

  const sendRegisterCode = async (email: string) => {
    return await requestResult<null>("/api/user/emailVerifySend", {
      method: "POST",
      body: {
        email,
      },
    });
  };

  const register = async (payload: RegisterPayload) => {
    const encryptedPassword = await encryptPassword(payload.password);

    return await requestResult<null>("/api/user/register", {
      method: "POST",
      body: {
        username: payload.username,
        nickname: payload.nickname,
        gender: payload.gender,
        password: encryptedPassword,
        email: payload.email,
        verifyCode: payload.verifyCode,
      },
    });
  };

  const logout = async () => {
    const authorization = getAuthorizationHeader();

    try {
      if (authorization) {
        await requestResult<null>("/api/auth/logout", {
          method: "POST",
          headers: {
            Authorization: authorization,
          },
        });
      }
    } finally {
      clearSession();
    }
  };

  const restoreSession = async () => {
    try {
      if (!accessToken.value) {
        sessionReady.value = true;
        currentUser.value = null;
        return null;
      }
      if (
        tokenExpireAt.value &&
        tokenExpireAt.value <= Date.now() + REFRESH_ADVANCE_MS
      ) {
        const refreshResult = await refreshSession();
        if (!refreshResult || refreshResult.code !== SUCCESS_CODE) {
          sessionReady.value = true;
          return null;
        }
      } else {
        scheduleSilentRefresh();
      }
      if (!currentUser.value || !permissionSnapshot.value) {
        await Promise.all([
          fetchCurrentUser(),
          permissionSnapshot.value
            ? Promise.resolve(permissionSnapshot.value)
            : fetchPermissionSnapshot(),
        ]);
      }
      sessionReady.value = true;
      return currentUser.value;
    } catch {
      clearSession();
      return null;
    }
  };

  return {
    accessToken,
    refreshToken,
    tokenType,
    tokenExpiresIn,
    authScope,
    tokenExpireAt,
    currentUser,
    permissionSnapshot,
    sessionReady,
    login,
    register,
    sendRegisterCode,
    fetchCurrentUser,
    fetchPermissionSnapshot,
    refreshSession,
    logout,
    restoreSession,
    clearSession,
  };
};
