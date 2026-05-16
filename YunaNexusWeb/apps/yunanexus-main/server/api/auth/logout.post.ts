import { getHeader } from "h3";
import { proxyJsonRequest } from "../../utils/backendProxy";

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig();
  const authorization = getHeader(event, "authorization");

  return await proxyJsonRequest(event, {
    baseURL: runtimeConfig.public.authBase as string,
    path: "/oauth/logout",
    method: "POST",
    headers: authorization ? { Authorization: authorization } : {},
  });
});
