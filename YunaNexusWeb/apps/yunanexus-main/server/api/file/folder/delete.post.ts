import { getHeader, readBody } from "h3";
import { proxyJsonRequest } from "../../../utils/backendProxy";

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig();
  const authorization = getHeader(event, "authorization");
  const body = await readBody<Record<string, unknown>>(event);

  return await proxyJsonRequest(event, {
    baseURL: runtimeConfig.public.fileBase,
    path: "/file/folder/delete",
    method: "POST",
    body,
    headers: authorization ? { Authorization: authorization } : {},
  });
});
