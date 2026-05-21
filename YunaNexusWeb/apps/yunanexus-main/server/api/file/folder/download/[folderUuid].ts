import {
  createError,
  getCookie,
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
  const accessToken = getCookie(event, "yn-access-token");
  const tokenType = getCookie(event, "yn-token-type") || "Bearer";
  const authorization =
    getHeader(event, "authorization") ||
    (accessToken ? `${tokenType} ${accessToken}` : undefined);
  const folderUuid = getRouterParam(event, "folderUuid");
  const fileBase = runtimeConfig.public.fileBase;

  if (!folderUuid) {
    throw createError({
      statusCode: 400,
      statusMessage: "Bad Request",
      data: { message: "folderUuid 不能为空" },
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
      `/file/folder/download?folderUuid=${encodeURIComponent(String(folderUuid))}`,
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
      data: { message: rawText || "目录下载失败" },
    });
  }

  const contentType = response.headers.get("content-type");
  const contentDisposition = response.headers.get("content-disposition");

  if (contentType) {
    setHeader(event, "Content-Type", contentType);
  }
  if (contentDisposition) {
    setHeader(event, "Content-Disposition", contentDisposition);
  }

  return response.body;
});
