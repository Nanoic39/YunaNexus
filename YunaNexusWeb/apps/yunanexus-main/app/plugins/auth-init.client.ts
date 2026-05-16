// 文件: app/plugins/auth-init.client.ts
import {useAuthApi} from "../composables/useAuthApi";

export default defineNuxtPlugin(async () => {
  const authApi = useAuthApi();
  await authApi.restoreSession();
});
