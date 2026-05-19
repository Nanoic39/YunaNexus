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

  const fileBase = runtimeConfig.public.fileBase;
  if (!fileBase) {
    throw createError({
      statusCode: 500,
      statusMessage: "Proxy target is not configured",
      data: {
        message: "文件服务地址未配置",
      },
    });
  }

  // 确保两个路径参数都是字符串类型，避免类型不匹配错误
  const avatarUuidStr = String(avatarUuid);
  const response = await fetch(joinUrl(fileBase, `/file/avatar/${avatarUuidStr}`));
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

