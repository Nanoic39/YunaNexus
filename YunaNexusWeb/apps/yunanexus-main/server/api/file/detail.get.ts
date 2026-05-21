import { getHeader, getQuery } from "h3";
import { proxyJsonRequest } from "../../utils/backendProxy";

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig();
  const authorization = getHeader(event, "authorization");
  const query = getQuery(event);
  const fileUuid = Array.isArray(query.fileUuid) ? query.fileUuid[0] : query.fileUuid;

  return await proxyJsonRequest(event, {
    baseURL: runtimeConfig.public.fileBase,
    path: `/file/detail?fileUuid=${encodeURIComponent(String(fileUuid || ""))}`,
    method: "GET",
    headers: authorization ? { Authorization: authorization } : {},
  });
});
