import {
  createError,
  getHeader,
  getQuery,
  setHeader,
  setResponseStatus,
} from "h3";

const joinUrl = (baseURL: string, path: string) => {
  const normalizedBase = baseURL.replace(/\/+$/, "");
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${normalizedBase}${normalizedPath}`;
};

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig();
  const authorization = getHeader(event, "authorization");
  const query = getQuery(event);
  const fileUuid = String(query.fileUuid || "");
  const start = String(query.start || "");
  const end = String(query.end || "");
  const fileBase = runtimeConfig.public.fileBase;

  if (!fileUuid || !start || !end) {
    throw createError({
      statusCode: 400,
      statusMessage: "Bad Request",
      data: { message: "下载分段参数不完整" },
    });
  }

  if (!fileBase) {
    throw createError({
      statusCode: 500,
      statusMessage: "Proxy target is not configured",
      data: { message: "文件服务地址未配置" },
    });
  }

  const response = await fetch(
    joinUrl(
      fileBase,
      `/file/download/chunk?fileUuid=${encodeURIComponent(fileUuid)}&start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}`,
    ),
    {
      method: "GET",
      headers: authorization ? { Authorization: authorization } : undefined,
    },
  );

  setResponseStatus(event, response.status, response.statusText);

  if (!response.ok || !response.body) {
    const rawText = await response.text();
    throw createError({
      statusCode: response.status,
      statusMessage: response.statusText,
      data: { message: rawText || "分段下载失败" },
    });
  }

  const contentType = response.headers.get("content-type");
  const contentLength = response.headers.get("content-length");
  const contentRange = response.headers.get("content-range");

  if (contentType) {
    setHeader(event, "Content-Type", contentType);
  }
  if (contentLength) {
    setHeader(event, "Content-Length", Number(contentLength));
  }
  if (contentRange) {
    setHeader(event, "Content-Range", contentRange);
  }

  return response.body;
});
