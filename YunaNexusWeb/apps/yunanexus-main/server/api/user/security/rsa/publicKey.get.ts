import { proxyJsonRequest } from "../../../../utils/backendProxy";

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig();

  return await proxyJsonRequest(event, {
    baseURL: runtimeConfig.public.apiBase,
    path: "/security/rsa/public-key",
    method: "GET",
  });
});
