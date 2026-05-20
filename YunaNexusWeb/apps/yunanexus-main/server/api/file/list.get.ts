import { getHeader, getQuery } from "h3";
import { proxyJsonRequest } from "../../utils/backendProxy";

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig();
  const authorization = getHeader(event, "authorization");
  const query = getQuery(event);
  const folderId = Array.isArray(query.folderId)
    ? query.folderId[0]
    : query.folderId;

  const path = folderId
    ? `/file/list?folderId=${encodeURIComponent(String(folderId))}`
    : "/file/list";

  return await proxyJsonRequest(event, {
    baseURL: runtimeConfig.public.fileBase,
    path,
    method: "GET",
    headers: authorization ? { Authorization: authorization } : {},
  });
});
