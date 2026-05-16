import type { RouteLocationNormalizedLoaded } from "vue-router";
import type { PageLoaderScope } from "../composables/usePageLoader";

const resolveLoaderScope = (
  to: RouteLocationNormalizedLoaded,
  from: RouteLocationNormalizedLoaded,
): PageLoaderScope => {
  const metaScope = to.meta.loaderScope;

  if (metaScope === "fullscreen" || metaScope === "content") {
    return metaScope;
  }

  const toLayout =
    typeof to.meta.layout === "string" ? to.meta.layout : "default";
  const fromLayout =
    typeof from.meta.layout === "string" ? from.meta.layout : "default";

  if (!from.name || toLayout !== fromLayout) {
    return "fullscreen";
  }

  return "content";
};

export default defineNuxtPlugin((nuxtApp) => {
  const router = useRouter();
  const pageLoader = usePageLoader();

  let pendingScope: PageLoaderScope | null = null;

  pageLoader.begin("fullscreen");

  nuxtApp.hook("app:mounted", () => {
    pageLoader.finish("fullscreen");
  });

  nuxtApp.hook("app:error", () => {
    pendingScope = null;
    pageLoader.finishAll();
  });

  router.beforeEach((to, from) => {
    pendingScope = resolveLoaderScope(to, from);
    pageLoader.begin(pendingScope);
  });

  router.afterEach(() => {
    if (!pendingScope) {
      return;
    }

    pageLoader.finish(pendingScope);
    pendingScope = null;
  });
});
