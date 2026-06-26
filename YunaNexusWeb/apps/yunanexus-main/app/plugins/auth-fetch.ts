export default defineNuxtPlugin((nuxtApp) => {
  nuxtApp.$fetch = $fetch.create({
    onRequest({ options }) {
      if (import.meta.server) return;
      try {
        const raw = localStorage.getItem("user-auth-info");
        if (raw) {
          const token = JSON.parse(raw)?.accessToken;
          if (token) {
            const headers = new Headers(options.headers);
            headers.set("Authorization", "Bearer " + token);
            options.headers = headers;
          }
        }
      } catch {
        /* ignore */
      }
    },
  }) as typeof $fetch;
});
