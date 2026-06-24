export default defineNuxtPlugin(() => {
  const { getAccessToken } = useAuth();

  globalThis.$fetch = $fetch.create({
    onRequest({ options }) {
      const token = getAccessToken();
      if (token) {
        (options.headers as any)["Authorization"] = `Bearer ${token}`;
      }
    },
  });
});
