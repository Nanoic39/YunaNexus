import { getCookie, getHeader, getQuery } from "h3";
import { proxyJsonRequest } from "../../../utils/backendProxy";

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig();
  const accessToken = getCookie(event, "yn-access-token");
  const tokenType = getCookie(event, "yn-token-type") || "Bearer";
  const authorization =
    getHeader(event, "authorization") ||
    (accessToken ? `${tokenType} ${accessToken}` : undefined);
  const query = getQuery(event);
  const shareCode = Array.isArray(query.shareCode) ? query.shareCode[0] : query.shareCode;

  return await proxyJsonRequest(event, {
    baseURL: runtimeConfig.public.fileBase,
    path: `/file/share/info?shareCode=${encodeURIComponent(String(shareCode || ""))}`,
    method: "GET",
    headers: authorization ? { Authorization: authorization } : {},
  });
});
