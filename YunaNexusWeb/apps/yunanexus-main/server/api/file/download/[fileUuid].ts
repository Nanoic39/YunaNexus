import {
  createError,
  getHeader,
  getRouterParam,
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
  const fileUuid = getRouterParam(event, "fileUuid");
  const fileBase = runtimeConfig.public.fileBase;

  if (!fileUuid) {
    throw createError({
      statusCode: 400,
      statusMessage: "Bad Request",
      data: {
        message: "fileUuid 不能为空",
      },
    });
  }

  if (!fileBase) {
    throw createError({
      statusCode: 500,
      statusMessage: "Proxy target is not configured",
      data: {
        message: "文件服务地址未配置",
      },
    });
  }

  const response = await fetch(
    joinUrl(fileBase, `/file/download?fileUuid=${encodeURIComponent(String(fileUuid))}`),
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
      data: {
        message: rawText || "文件下载失败",
      },
    });
  }

  const contentType = response.headers.get("content-type");
  const contentLength = response.headers.get("content-length");
  const contentDisposition = response.headers.get("content-disposition");

  if (contentType) {
    setHeader(event, "Content-Type", contentType);
  }
  if (contentLength) {
    setHeader(event, "Content-Length", Number(contentLength));
  }
  if (contentDisposition) {
    setHeader(event, "Content-Disposition", contentDisposition);
  }

  return response.body;
});
