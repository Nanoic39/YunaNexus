export function useToast() {
  const toasts = useState<{ id: string; type: string; message: string }[]>(
    "global-toasts",
    () => [],
  );

  function addToast(type: string, message: string) {
    const id = Math.random().toString(36).slice(2);
    toasts.value.push({ id, type, message });
    setTimeout(() => {
      toasts.value = toasts.value.filter((t) => t.id !== id);
    }, 3500);
  }

  return {
    toasts,
    addToast,
    success: (m: string) => addToast("success", m),
    error: (m: string) => addToast("error", m),
    warning: (m: string) => addToast("warning", m),
    info: (m: string) => addToast("info", m),
  };
}
