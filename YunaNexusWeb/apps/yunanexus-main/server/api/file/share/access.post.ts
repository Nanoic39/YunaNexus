import { getCookie, getHeader, readBody } from "h3";
import { proxyJsonRequest } from "../../../utils/backendProxy";

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig();
  const accessToken = getCookie(event, "yn-access-token");
  const tokenType = getCookie(event, "yn-token-type") || "Bearer";
  const authorization =
    getHeader(event, "authorization") ||
    (accessToken ? `${tokenType} ${accessToken}` : undefined);
  const body = await readBody(event);

  return await proxyJsonRequest(event, {
    baseURL: runtimeConfig.public.fileBase,
    path: "/file/share/access",
    method: "POST",
    body,
    headers: authorization ? { Authorization: authorization } : {},
  });
});
