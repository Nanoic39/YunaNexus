import {
  createError,
  getCookie,
  getHeader,
  getQuery,
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
  const query = getQuery(event);
  const extractCode = Array.isArray(query.extractCode)
    ? query.extractCode[0]
    : query.extractCode;
  const shareCode = getRouterParam(event, "shareCode");
  const fileBase = runtimeConfig.public.fileBase;

  if (!shareCode) {
    throw createError({
      statusCode: 400,
      statusMessage: "Bad Request",
      data: { message: "shareCode 不能为空" },
    });
  }

  if (!fileBase) {
    throw createError({
      statusCode: 500,
      statusMessage: "Proxy target is not configured",
      data: { message: "文件服务地址未配置" },
    });
  }

  const downloadPath = extractCode
    ? `/file/share/download?shareCode=${encodeURIComponent(String(shareCode))}&extractCode=${encodeURIComponent(String(extractCode))}`
    : `/file/share/download?shareCode=${encodeURIComponent(String(shareCode))}`;

  const range = getHeader(event, "range");

  const response = await fetch(joinUrl(fileBase, downloadPath), {
    method: "GET",
    headers: {
      ...(authorization ? { Authorization: authorization } : {}),
      ...(range ? { Range: range } : {}),
    },
  });

  setResponseStatus(event, response.status, response.statusText);

  if (!response.ok || !response.body) {
    const rawText = await response.text();
    throw createError({
      statusCode: response.status,
      statusMessage: response.statusText,
      data: { message: rawText || "分享文件下载失败" },
    });
  }

  const contentType = response.headers.get("content-type");
  const contentLength = response.headers.get("content-length");
  const contentDisposition = response.headers.get("content-disposition");
  const contentRange = response.headers.get("content-range");
  const acceptRanges = response.headers.get("accept-ranges");

  if (contentType) {
    setHeader(event, "Content-Type", contentType);
  }
  if (contentLength) {
    setHeader(event, "Content-Length", Number(contentLength));
  }
  if (contentDisposition) {
    setHeader(event, "Content-Disposition", contentDisposition);
  }
  if (contentRange) {
    setHeader(event, "Content-Range", contentRange);
  }
  if (acceptRanges) {
    setHeader(event, "Accept-Ranges", acceptRanges);
  }

  return response.body;
});
