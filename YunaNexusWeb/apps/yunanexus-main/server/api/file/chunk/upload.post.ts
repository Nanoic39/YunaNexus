import {
  createError,
  getHeader,
  readFormData,
  setResponseStatus,
} from "h3";

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
    return { message: rawText };
  }
};

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig();
  const authorization = getHeader(event, "authorization");
  const formData = await readFormData(event);
  const fileBase = runtimeConfig.public.fileBase;

  if (!fileBase) {
    throw createError({
      statusCode: 500,
      statusMessage: "Proxy target is not configured",
      data: { message: "文件服务地址未配置" },
    });
  }

  const response = await fetch(joinUrl(fileBase, "/file/chunk/upload"), {
    method: "POST",
    headers: authorization ? { Authorization: authorization } : undefined,
    body: formData,
  });

  const rawText = await response.text();
  const contentType = response.headers.get("content-type");
  const data = parseResponseBody(rawText, contentType);

  setResponseStatus(event, response.status, response.statusText);

  if (!response.ok) {
    throw createError({
      statusCode: response.status,
      statusMessage: response.statusText,
      data,
    });
  }

  return data;
});
