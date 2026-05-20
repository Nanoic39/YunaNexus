import { getHeader } from "h3";
import { proxyJsonRequest } from "../../../utils/backendProxy";

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig();
  const authorization = getHeader(event, "authorization");

  return await proxyJsonRequest(event, {
    baseURL: runtimeConfig.public.fileBase,
    path: "/file/recycle/list",
    method: "GET",
    headers: authorization ? { Authorization: authorization } : {},
  });
});
