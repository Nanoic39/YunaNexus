import { createError, setResponseStatus, type H3Event } from "h3";

type ProxyOptions = {
  baseURL: string;
  path: string;
  method?: string;
  body?: unknown;
  headers?: Record<string, string>;
};

const joinUrl = (baseURL: string, path: string) => {
  const normalizedBase = baseURL.replace(/\/+$/, "");
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${normalizedBase}${normalizedPath}`;
};

const parseResponseBody = (rawText: string, contentType: string | null) => {
  if (!rawText) {
    return null;
  }

  if (contentType?.includes("application/json")) {
    return JSON.parse(rawText);
  }

  try {
    return JSON.parse(rawText);
  } catch {
    return {
      message: rawText,
    };
  }
};

export const proxyJsonRequest = async <T = unknown>(
  event: H3Event,
  options: ProxyOptions,
): Promise<T> => {
  if (!options.baseURL) {
    throw createError({
      statusCode: 500,
      statusMessage: "Proxy target is not configured",
      data: {
        message: "后端服务地址未配置",
      },
    });
  }

  try {
    const response = await fetch(joinUrl(options.baseURL, options.path), {
      method: options.method ?? "GET",
      headers: {
        Accept: "application/json",
        ...(options.body === undefined
          ? {}
          : { "Content-Type": "application/json" }),
        ...(options.headers ?? {}),
      },
      body:
        options.body === undefined ? undefined : JSON.stringify(options.body),
    });

    const rawText = await response.text();
    const contentType = response.headers.get("content-type");
    const data = parseResponseBody(rawText, contentType) as T;

    setResponseStatus(event, response.status, response.statusText);

    if (!response.ok) {
      throw createError({
        statusCode: response.status,
        statusMessage: response.statusText,
        data,
      });
    }

    return data;
  } catch (error) {
    if (error && typeof error === "object" && "statusCode" in error) {
      throw error;
    }

    throw createError({
      statusCode: 502,
      statusMessage: "Bad Gateway",
      data: {
        message: error instanceof Error ? error.message : "上游服务请求失败",
      },
    });
  }
};
