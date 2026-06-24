export interface ThemeSettings {
  dark: boolean;
  [key: string]: unknown;
}

const STORAGE_KEY = "user-theme-info";

// TODO: 主题配置默认值在这里修改
function getDefaultSettings(): Record<string, unknown> {
  return {
    dark: false,
  };
}

function loadOrInit(): Record<string, unknown> {
  if (import.meta.server) return getDefaultSettings();
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    /* ignore */
  }
  const defaults = getDefaultSettings();
  localStorage.setItem(STORAGE_KEY, JSON.stringify(defaults));
  return defaults;
}

function saveSettings(partial: Partial<Record<string, unknown>>) {
  if (import.meta.server) return;
  const existing = loadOrInit();
  const merged = { ...existing, ...partial };
  localStorage.setItem(STORAGE_KEY, JSON.stringify(merged));
}

export function useTheme() {
  const theme = useState<ThemeSettings>(
    "theme-settings",
    () => loadOrInit() as ThemeSettings,
  );

  function toggle(pos?: { x: number; y: number }) {
    if (pos) {
      document.documentElement.style.setProperty("--theme-x", pos.x + "px");
      document.documentElement.style.setProperty("--theme-y", pos.y + "px");
    }

    document.startViewTransition(() => {
      theme.value.dark = !theme.value.dark;
      saveSettings({ dark: theme.value.dark });
      if (theme.value.dark) {
        document.body.setAttribute("data-theme", "dark");
      } else {
        document.body.removeAttribute("data-theme");
      }
    });
  }

  return { theme, toggle };
}
