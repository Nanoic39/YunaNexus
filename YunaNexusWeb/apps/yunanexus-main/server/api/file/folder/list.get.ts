import { getHeader, getQuery } from "h3";
import { proxyJsonRequest } from "../../../utils/backendProxy";

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig();
  const authorization = getHeader(event, "authorization");
  const query = getQuery(event);
  const parentId = Array.isArray(query.parentId)
    ? query.parentId[0]
    : query.parentId;

  const path = parentId
    ? `/file/folder/list?parentId=${encodeURIComponent(String(parentId))}`
    : "/file/folder/list";

  return await proxyJsonRequest(event, {
    baseURL: runtimeConfig.public.fileBase,
    path,
    method: "GET",
    headers: authorization ? { Authorization: authorization } : {},
  });
});
