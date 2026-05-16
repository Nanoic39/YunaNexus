import { computed } from "vue";

export type PageLoaderScope = "fullscreen" | "content";

export const usePageLoader = () => {
  const fullscreenCount = useState<number>(
    "yn-loader-fullscreen-count",
    () => 0,
  );
  const contentCount = useState<number>("yn-loader-content-count", () => 0);

  const begin = (scope: PageLoaderScope) => {
    if (scope === "fullscreen") {
      fullscreenCount.value += 1;
      return;
    }

    contentCount.value += 1;
  };

  const finish = (scope: PageLoaderScope) => {
    if (scope === "fullscreen") {
      fullscreenCount.value = Math.max(0, fullscreenCount.value - 1);
      return;
    }

    contentCount.value = Math.max(0, contentCount.value - 1);
  };

  const finishAll = () => {
    fullscreenCount.value = 0;
    contentCount.value = 0;
  };

  const showFullscreen = computed(() => fullscreenCount.value > 0);
  const showContent = computed(
    () => fullscreenCount.value === 0 && contentCount.value > 0,
  );

  return {
    fullscreenCount,
    contentCount,
    showFullscreen,
    showContent,
    begin,
    finish,
    finishAll,
  };
};
