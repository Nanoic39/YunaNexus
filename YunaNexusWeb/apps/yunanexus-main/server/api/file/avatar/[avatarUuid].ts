import { createError, getRouterParam, setHeader, setResponseStatus } from "h3";

const joinUrl = (baseURL: string, path: string) => {
  const normalizedBase = baseURL.replace(/\/+$/, "");
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${normalizedBase}${normalizedPath}`;
};

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig();
  const avatarUuid = getRouterParam(event, "avatarUuid");

  if (!avatarUuid) {
    throw createError({
      statusCode: 400,
      statusMessage: "Bad Request",
      data: {
        message: "avatarUuid 不能为空",
      },
    });
  }

  const apiBase = runtimeConfig.public.apiBase;
  if (!apiBase) {
    throw createError({
      statusCode: 500,
      statusMessage: "Proxy target is not configured",
      data: {
        message: "用户服务地址未配置",
      },
    });
  }

  const response = await fetch(joinUrl(apiBase, `/file/avatar/${avatarUuid}`));
  setResponseStatus(event, response.status, response.statusText);

  if (!response.ok || !response.body) {
    const rawText = await response.text();
    throw createError({
      statusCode: response.status,
      statusMessage: response.statusText,
      data: {
        message: rawText || "头像读取失败",
      },
    });
  }

  const contentType = response.headers.get("content-type");
  const contentLength = response.headers.get("content-length");
  const cacheControl = response.headers.get("cache-control");

  if (contentType) {
    setHeader(event, "Content-Type", contentType);
  }
  if (contentLength) {
    setHeader(event, "Content-Length", Number(contentLength));
  }
  if (cacheControl) {
    setHeader(event, "Cache-Control", cacheControl);
  }

  return response.body;
});

