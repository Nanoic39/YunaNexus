import { readBody } from "h3";
import { proxyJsonRequest } from "../../utils/backendProxy";

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig();
  const body = await readBody(event);

  return await proxyJsonRequest(event, {
    baseURL: runtimeConfig.public.apiBase,
    path: "/email-verify-send",
    method: "POST",
    body,
  });
});
