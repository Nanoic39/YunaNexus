export interface AppContextMenuItem {
  key: string;
  label?: string;
  type?: "item" | "separator";
  icon?: string;
  checked?: boolean;
  disabled?: boolean;
  danger?: boolean;
  children?: AppContextMenuItem[];
  action?: () => void | Promise<void>;
}

interface AppContextMenuState {
  visible: boolean;
  x: number;
  y: number;
  target: HTMLElement | null;
  items: AppContextMenuItem[];
}

export const useAppContextMenu = () => {
  const state = useState<AppContextMenuState>("app-context-menu-state", () => ({
    visible: false,
    x: 0,
    y: 0,
    target: null,
    items: [],
  }));

  const close = () => {
    state.value.visible = false;
  };

  const open = (
    event: MouseEvent,
    items: AppContextMenuItem[],
    target?: HTMLElement | null,
  ) => {
    event.preventDefault();
    state.value = {
      visible: true,
      x: event.clientX,
      y: event.clientY,
      target: target ?? (event.target as HTMLElement | null),
      items,
    };
  };

  return {
    state,
    open,
    close,
  };
};
