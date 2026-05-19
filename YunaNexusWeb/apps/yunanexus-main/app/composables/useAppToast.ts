export type AppToastType = "success" | "error" | "info";

export type AppToastItem = {
  id: string;
  type: AppToastType;
  message: string;
  title?: string;
  duration: number;
};

type PushOptions = {
  title?: string;
  duration?: number;
};

const toastTimers = new Map<string, ReturnType<typeof setTimeout>>();

const createToastId = () =>
  `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;

export const useAppToast = () => {
  const toasts = useState<AppToastItem[]>("app-toasts", () => []);

  const remove = (id: string) => {
    const timer = toastTimers.get(id);
    if (timer) {
      clearTimeout(timer);
      toastTimers.delete(id);
    }
    toasts.value = toasts.value.filter((item) => item.id !== id);
  };

  const push = (
    type: AppToastType,
    message: string,
    options: PushOptions = {},
  ) => {
    const id = createToastId();
    const duration = options.duration ?? (type === "error" ? 3600 : 2400);

    toasts.value = [
      ...toasts.value,
      {
        id,
        type,
        message,
        title: options.title,
        duration,
      },
    ];

    if (import.meta.client && duration > 0) {
      const timer = setTimeout(() => {
        remove(id);
      }, duration);
      toastTimers.set(id, timer);
    }

    return id;
  };

  const clear = () => {
    const ids = toasts.value.map((item) => item.id);
    ids.forEach(remove);
  };

  return {
    toasts,
    push,
    remove,
    clear,
    success: (message: string, options?: PushOptions) =>
      push("success", message, options),
    error: (message: string, options?: PushOptions) =>
      push("error", message, options),
    info: (message: string, options?: PushOptions) =>
      push("info", message, options),
  };
};
