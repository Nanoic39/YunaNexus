<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { AppContextMenuItem } from "../composables/useAppContextMenu";
import { useAppToast } from "../composables/useAppToast";
import {
  useFileApi,
  type FileUploadProgress,
  type UserFileDetail,
  type UserFileShareItem,
  type UserFolderItem,
  type UserManagedFileItem,
  type UserStorageSummary,
} from "../composables/useFileApi";
import AppButton from "../components/form/AppButton.vue";

useHead({
  title: "文件管理",
});

type UploadTaskItem = {
  id: string;
  fileName: string;
  fileSize: number;
  status: "preparing" | "uploading" | "merging" | "done" | "error";
  percent: number;
  text: string;
  errorMessage?: string;
};

type FolderTreeNode = UserFolderItem & {
  id?: number | null;
  children: FolderTreeNode[];
};

type BrowserFolderEntry = {
  key: string;
  kind: "folder";
  folder: FolderTreeNode;
  name: string;
  createTime?: string | null;
};

type BrowserFileEntry = {
  key: string;
  kind: "file";
  file: UserManagedFileItem;
  name: string;
  createTime?: string | null;
  size: number;
  ext?: string | null;
  mime?: string | null;
};

type BrowserEntry = BrowserFolderEntry | BrowserFileEntry;
type NamePanelMode = "create-folder" | "rename-folder" | "rename-file";
type DragEntry =
  | { kind: "folder"; folderUuid: string; parentId: number | null }
  | { kind: "file"; fileUuid: string; folderId: number | null };

const authApi = useAuthApi();
const fileApi = useFileApi();
const toast = useAppToast();
const route = useRoute();
const contextMenu = useAppContextMenu();

const pageReady = ref(false);
const activeTab = ref<"files" | "recycle">("files");
const viewModeCookie = useCookie<"detail" | "grid">("yn-file-view-mode", {
  default: () => "detail",
  sameSite: "lax",
});
const viewMode = ref<"detail" | "grid">(
  viewModeCookie.value === "grid" ? "grid" : "detail",
);
const uploadInputRef = ref<HTMLInputElement | null>(null);
const uploadFabRef = ref<HTMLButtonElement | null>(null);
const uploadPanelRef = ref<HTMLElement | null>(null);

const loadingCurrent = ref(false);
const loadingRecycle = ref(false);
const uploading = ref(false);
const operatingUuid = ref("");
const uploadPanelOpen = ref(false);
const externalDragActive = ref(false);
const uploadDragDepth = ref(0);

const currentFiles = ref<UserManagedFileItem[]>([]);
const recycleFiles = ref<UserManagedFileItem[]>([]);
const uploadTasks = ref<UploadTaskItem[]>([]);
const storageSummary = ref<UserStorageSummary | null>(null);
const folderTree = ref<FolderTreeNode[]>([]);
const activeFolderId = ref<number | null>(null);

const selectedEntryKeys = ref<string[]>([]);
const draggingEntry = ref<DragEntry | null>(null);
const dragHoverFolderUuid = ref("");

const detailLoading = ref(false);
const detailPanelOpen = ref(false);
const selectedFileDetail = ref<UserFileDetail | null>(null);
const previewLoading = ref(false);
const previewUrl = ref("");
const previewText = ref("");
const previewError = ref("");

const sharePanelOpen = ref(false);
const shareLoading = ref(false);
const shareSubmitting = ref(false);
const shareTargetFile = ref<UserManagedFileItem | null>(null);
const shareItems = ref<UserFileShareItem[]>([]);
const shareExtractCode = ref("");
const shareExpireAt = ref("");
const shareMaxDownloadCount = ref("");
const shareViewAuthMode = ref<0 | 1>(0);
const shareDownloadAuthMode = ref<0 | 1>(0);

const namePanelOpen = ref(false);
const namePanelMode = ref<NamePanelMode | null>(null);
const namePanelValue = ref("");
const namePanelTargetFolder = ref<FolderTreeNode | null>(null);
const namePanelTargetFile = ref<UserManagedFileItem | null>(null);
const namePanelParentFolder = ref<FolderTreeNode | null>(null);

const deletePanelOpen = ref(false);
const deleteEntries = ref<BrowserEntry[]>([]);

const movePanelOpen = ref(false);
const moveEntries = ref<BrowserEntry[]>([]);
const moveTargetFolderUuid = ref<string | null>(null);

const pageResolved = computed(
  () => pageReady.value && authApi.sessionReady.value,
);
const isAuthenticated = computed(() => !!authApi.accessToken.value);

const activeUploadCount = computed(
  () =>
    uploadTasks.value.filter(
      (item) =>
        item.status === "preparing" ||
        item.status === "uploading" ||
        item.status === "merging",
    ).length,
);
const hasSelection = computed(() => selectedEntryKeys.value.length > 0);

const findFolderNodeById = (
  nodes: FolderTreeNode[],
  id: number | null,
): FolderTreeNode | null => {
  for (const node of nodes) {
    if (node.id === id) {
      return node;
    }
    const child = findFolderNodeById(node.children || [], id);
    if (child) {
      return child;
    }
  }
  return null;
};

const findFolderPath = (
  nodes: FolderTreeNode[],
  id: number | null,
  trail: FolderTreeNode[] = [],
): FolderTreeNode[] => {
  for (const node of nodes) {
    const nextTrail = [...trail, node];
    if (node.id === id) {
      return nextTrail;
    }
    const childTrail = findFolderPath(node.children || [], id, nextTrail);
    if (childTrail.length) {
      return childTrail;
    }
  }
  return [];
};

const flattenFolderTree = (
  nodes: FolderTreeNode[],
  acc: FolderTreeNode[] = [],
): FolderTreeNode[] => {
  for (const node of nodes) {
    acc.push(node);
    flattenFolderTree(node.children || [], acc);
  }
  return acc;
};

const formatFolderDisplayPath = (
  folderPath?: string | null,
  folderName?: string,
) => {
  const rawPath = (folderPath || "").trim();
  if (!rawPath) {
    return folderName ? `/ > ${folderName}` : "/";
  }
  const segments = rawPath
    .split("/")
    .map((segment) => segment.trim())
    .filter(Boolean);
  if (!segments.length) {
    return "/";
  }
  return `/ > ${segments.join(" > ")}`;
};

const activeFolderNode = computed(() =>
  activeFolderId.value == null
    ? null
    : findFolderNodeById(folderTree.value, activeFolderId.value),
);

const activeFolderName = computed(
  () => activeFolderNode.value?.folderName || "全部文件",
);
const activeFolderDisplayPath = computed(() =>
  activeFolderNode.value
    ? formatFolderDisplayPath(
        activeFolderNode.value.folderPath,
        activeFolderNode.value.folderName,
      )
    : "/",
);

const currentFolderItems = computed(() =>
  activeFolderId.value == null
    ? folderTree.value
    : activeFolderNode.value?.children || [],
);

const currentEntries = computed<BrowserEntry[]>(() => {
  const folders: BrowserFolderEntry[] = currentFolderItems.value.map(
    (folder) => ({
      key: `folder:${folder.folderUuid}`,
      kind: "folder",
      folder,
      name: folder.folderName,
      createTime: folder.createTime,
    }),
  );
  const files: BrowserFileEntry[] = currentFiles.value.map((file) => ({
    key: `file:${file.fileUuid}`,
    kind: "file",
    file,
    name: file.originName || file.fileName,
    createTime: file.createTime,
    size: file.fileSize,
    ext: file.fileExt,
    mime: file.fileMime,
  }));
  return [...folders, ...files];
});

const selectedEntries = computed(() => {
  const keySet = new Set(selectedEntryKeys.value);
  return currentEntries.value.filter((entry) => keySet.has(entry.key));
});

const previewKind = computed(() => {
  const mime = selectedFileDetail.value?.fileMime || "";
  if (mime.startsWith("image/")) {
    return "image";
  }
  if (mime.startsWith("video/")) {
    return "video";
  }
  if (mime.startsWith("audio/")) {
    return "audio";
  }
  if (
    mime.startsWith("text/") ||
    mime.includes("json") ||
    mime.includes("xml") ||
    mime.includes("javascript")
  ) {
    return "text";
  }
  return "unknown";
});

const breadcrumbItems = computed(() => {
  const base = [
    {
      id: null as number | null,
      folderUuid: "root",
      folderName: "全部文件",
      displayLabel: "/",
    },
  ];
  if (!activeFolderNode.value) {
    return base;
  }
  const path = findFolderPath(
    folderTree.value,
    activeFolderNode.value.id ?? null,
  );
  return base.concat(
    path.map((folder) => ({
      id: folder.id ?? null,
      folderUuid: folder.folderUuid,
      folderName: folder.folderName,
      displayLabel: folder.folderName,
    })),
  );
});

const movePanelFolders = computed(() =>
  flattenFolderTree(folderTree.value, []),
);

const setViewMode = (mode: "detail" | "grid") => {
  viewMode.value = mode;
  viewModeCookie.value = mode;
};

watch(
  [() => authApi.sessionReady.value, () => authApi.accessToken.value],
  async ([ready, token]) => {
    if (import.meta.client && ready && !token && route.path === "/files") {
      await navigateTo("/login");
    }
  },
  { immediate: true },
);

watch(
  pageResolved,
  async (ready) => {
    if (ready && isAuthenticated.value) {
      await loadAll();
    }
  },
  { immediate: true },
);

watch([activeFolderId, activeTab], () => {
  selectedEntryKeys.value = [];
});

watch(detailPanelOpen, (open) => {
  if (!import.meta.client) {
    return;
  }
  document.documentElement.style.overflow = open ? "hidden" : "";
  document.body.style.overflow = open ? "hidden" : "";
});

watch(viewMode, (mode) => {
  viewModeCookie.value = mode;
});

const formatBytes = (value?: number | null) => {
  const size = Number(value || 0);
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  if (size < 1024 * 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
  }
  return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`;
};

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return "暂无";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString("zh-CN", {
    hour12: false,
  });
};

const buildShareAbsoluteUrl = (sharePath: string) => {
  if (!import.meta.client) {
    return sharePath;
  }
  return new URL(sharePath, window.location.origin).toString();
};

const getAuthorizationHeader = () =>
  `${authApi.tokenType.value || "Bearer"} ${authApi.accessToken.value || ""}`;

const revokePreviewUrl = () => {
  if (
    previewUrl.value &&
    import.meta.client &&
    !previewUrl.value.startsWith("/api/")
  ) {
    URL.revokeObjectURL(previewUrl.value);
  }
  previewUrl.value = "";
};

const resetPreview = () => {
  revokePreviewUrl();
  previewText.value = "";
  previewError.value = "";
  previewLoading.value = false;
};

const loadPreview = async (detail: UserFileDetail) => {
  resetPreview();

  if (!import.meta.client || !authApi.accessToken.value) {
    return;
  }

  const kind = previewKind.value;
  if (kind === "unknown") {
    previewError.value = "当前文件类型暂不支持在线预览";
    return;
  }

  if (kind === "video" || kind === "audio" || kind === "image") {
    previewUrl.value = `/api/file/download/${encodeURIComponent(detail.fileUuid)}?preview=1`;
    return;
  }

  previewLoading.value = true;
  try {
    const response = await fetch(
      `/api/file/download/${encodeURIComponent(detail.fileUuid)}`,
      {
        method: "GET",
        headers: {
          Authorization: getAuthorizationHeader(),
        },
      },
    );

    if (!response.ok) {
      const rawText = await response.text();
      throw new Error(rawText || "文件预览加载失败");
    }

    if (kind === "text") {
      previewText.value = await response.text();
    }
  } catch (error) {
    previewError.value =
      error instanceof Error ? error.message : "文件预览加载失败";
  } finally {
    previewLoading.value = false;
  }
};

const syncActiveFolder = () => {
  if (activeFolderId.value == null) {
    return;
  }
  if (!findFolderNodeById(folderTree.value, activeFolderId.value)) {
    activeFolderId.value = null;
  }
};

const loadCurrentFiles = async () => {
  loadingCurrent.value = true;
  try {
    const result = await fileApi.listFiles(activeFolderId.value);
    currentFiles.value = result.data || [];
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "文件列表加载失败");
  } finally {
    loadingCurrent.value = false;
  }
};

const loadRecycleFiles = async () => {
  loadingRecycle.value = true;
  try {
    const result = await fileApi.listRecycleFiles();
    recycleFiles.value = result.data || [];
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "回收站加载失败");
  } finally {
    loadingRecycle.value = false;
  }
};

const loadStorageSummary = async () => {
  try {
    const result = await fileApi.getStorageSummary();
    storageSummary.value = result.data;
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "空间信息加载失败");
  }
};

const loadFolderTree = async (
  parentId?: number | null,
): Promise<FolderTreeNode[]> => {
  const result = await fileApi.listFolders(parentId);
  const items = (result.data || []) as FolderTreeNode[];
  return await Promise.all(
    items.map(async (item) => ({
      ...item,
      children:
        typeof item.id === "number" ? await loadFolderTree(item.id) : [],
    })),
  );
};

const reloadFolders = async () => {
  try {
    folderTree.value = await loadFolderTree(null);
    syncActiveFolder();
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "目录树加载失败");
  }
};

const loadAll = async () => {
  await Promise.all([
    loadCurrentFiles(),
    loadRecycleFiles(),
    loadStorageSummary(),
    reloadFolders(),
  ]);
};

const navigateToFolderId = async (folderId: number | null) => {
  activeFolderId.value = folderId;
  selectedEntryKeys.value = [];
  await loadCurrentFiles();
};

const openFolder = async (folder: FolderTreeNode) => {
  if (typeof folder.id !== "number") {
    toast.info("当前目录暂不可进入");
    return;
  }
  await navigateToFolderId(folder.id);
};

const dropEntryToFolderById = async (targetId: number | null) => {
  const targetFolder =
    targetId == null ? null : findFolderNodeById(folderTree.value, targetId);
  await dropEntryToFolder(targetFolder);
};

const openCurrentFolderContextMenu = (event: MouseEvent) => {
  if (activeTab.value !== "files") {
    return;
  }
  openFolderContextMenu(event, activeFolderNode.value);
};

const navigateToParent = async () => {
  if (!activeFolderNode.value) {
    return;
  }
  await navigateToFolderId(activeFolderNode.value.parentId ?? null);
};

const toggleEntrySelection = (entryKey: string, checked: boolean) => {
  const next = new Set(selectedEntryKeys.value);
  if (checked) {
    next.add(entryKey);
  } else {
    next.delete(entryKey);
  }
  selectedEntryKeys.value = Array.from(next);
};

const clearSelection = () => {
  selectedEntryKeys.value = [];
};

const selectAllCurrentEntries = () => {
  if (selectedEntryKeys.value.length === currentEntries.value.length) {
    clearSelection();
    return;
  }
  selectedEntryKeys.value = currentEntries.value.map((entry) => entry.key);
};

const closeDetail = () => {
  detailPanelOpen.value = false;
  selectedFileDetail.value = null;
  detailLoading.value = false;
  resetPreview();
};

const openFileDetail = async (item: UserManagedFileItem) => {
  detailPanelOpen.value = true;
  detailLoading.value = true;
  try {
    const result = await fileApi.getFileDetail(item.fileUuid);
    selectedFileDetail.value = result.data;
    await loadPreview(result.data);
  } catch (error) {
    closeDetail();
    toast.error(error instanceof Error ? error.message : "文件详情加载失败");
  } finally {
    detailLoading.value = false;
  }
};

const resetShareForm = () => {
  shareExtractCode.value = "";
  shareExpireAt.value = "";
  shareMaxDownloadCount.value = "";
  shareViewAuthMode.value = 0;
  shareDownloadAuthMode.value = 0;
};

const closeSharePanel = () => {
  sharePanelOpen.value = false;
  shareTargetFile.value = null;
  shareItems.value = [];
  shareLoading.value = false;
  shareSubmitting.value = false;
  resetShareForm();
};

const loadShareItems = async (fileUuid: string) => {
  shareLoading.value = true;
  try {
    const result = await fileApi.listFileShares(fileUuid);
    shareItems.value = result.data || [];
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "分享列表加载失败");
  } finally {
    shareLoading.value = false;
  }
};

const openSharePanel = async (item: UserManagedFileItem) => {
  shareTargetFile.value = item;
  sharePanelOpen.value = true;
  resetShareForm();
  await loadShareItems(item.fileUuid);
};

const openSharePanelFromDetail = async () => {
  if (!selectedFileDetail.value) {
    return;
  }
  await openSharePanel({
    fileUuid: selectedFileDetail.value.fileUuid,
    fileName: selectedFileDetail.value.fileName,
    originName: selectedFileDetail.value.originName,
    fileSize: selectedFileDetail.value.fileSize,
    fileExt: selectedFileDetail.value.fileExt,
    fileMime: selectedFileDetail.value.fileMime,
    folderId: selectedFileDetail.value.folderId,
  } as UserManagedFileItem);
};

const submitSharePanel = async () => {
  if (!shareTargetFile.value) {
    return;
  }
  shareSubmitting.value = true;
  try {
    const result = await fileApi.createFileShare({
      fileUuid: shareTargetFile.value.fileUuid,
      extractCode: shareExtractCode.value.trim() || null,
      expireAt: shareExpireAt.value || null,
      maxDownloadCount: shareMaxDownloadCount.value
        ? Number(shareMaxDownloadCount.value)
        : null,
      viewAuthMode: shareViewAuthMode.value,
      downloadAuthMode: shareDownloadAuthMode.value,
    });
    toast.success(result.msg || "分享创建成功");
    resetShareForm();
    await loadShareItems(shareTargetFile.value.fileUuid);
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "创建分享失败");
  } finally {
    shareSubmitting.value = false;
  }
};

const revokeShareItem = async (shareUuid: string) => {
  if (!shareTargetFile.value) {
    return;
  }
  try {
    const result = await fileApi.revokeFileShare(shareUuid);
    toast.success(result.msg || "分享已取消");
    await loadShareItems(shareTargetFile.value.fileUuid);
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "取消分享失败");
  }
};

const copyShareLink = async (sharePath: string) => {
  const target = buildShareAbsoluteUrl(sharePath);
  if (!import.meta.client || !navigator.clipboard) {
    toast.info(target);
    return;
  }
  try {
    await navigator.clipboard.writeText(target);
    toast.success("分享链接已复制");
  } catch {
    toast.info(target);
  }
};

const openUploadPicker = () => {
  uploadInputRef.value?.click();
};

const createUploadTask = (file: File) => {
  const task: UploadTaskItem = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    fileName: file.name,
    fileSize: file.size,
    status: "preparing",
    percent: 0,
    text: "准备上传…",
  };
  uploadTasks.value = [task, ...uploadTasks.value].slice(0, 30);
  uploadPanelOpen.value = true;
  return task.id;
};

const updateUploadTask = (taskId: string, progress: FileUploadProgress) => {
  uploadTasks.value = uploadTasks.value.map((item) =>
    item.id === taskId
      ? {
          ...item,
          status: progress.phase,
          percent: progress.percent,
          text: progress.text,
        }
      : item,
  );
};

const finishUploadTask = (taskId: string) => {
  uploadTasks.value = uploadTasks.value.map((item) =>
    item.id === taskId
      ? {
          ...item,
          status: "done",
          percent: 100,
          text: "上传完成",
        }
      : item,
  );
};

const failUploadTask = (taskId: string, message: string) => {
  uploadTasks.value = uploadTasks.value.map((item) =>
    item.id === taskId
      ? {
          ...item,
          status: "error",
          text: "上传失败",
          errorMessage: message,
        }
      : item,
  );
};

const clearFinishedUploadTasks = () => {
  uploadTasks.value = uploadTasks.value.filter(
    (item) => item.status !== "done",
  );
};

const uploadFiles = async (files: File[]) => {
  if (!files.length) {
    return;
  }
  uploading.value = true;
  externalDragActive.value = false;
  try {
    for (const file of files) {
      const taskId = createUploadTask(file);
      try {
        const result = await fileApi.uploadFile(
          file,
          activeFolderId.value,
          (progress) => updateUploadTask(taskId, progress),
        );
        finishUploadTask(taskId);
        toast.success(result.msg || `${file.name} 上传成功`);
      } catch (error) {
        const message = error instanceof Error ? error.message : "文件上传失败";
        failUploadTask(taskId, message);
        toast.error(message);
      }
    }
    activeTab.value = "files";
    await Promise.all([loadCurrentFiles(), loadStorageSummary()]);
  } finally {
    uploading.value = false;
  }
};

const handleUploadChange = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = "";
  await uploadFiles(files);
};

const handleDownloadFile = async (item: UserManagedFileItem) => {
  operatingUuid.value = item.fileUuid;
  try {
    await fileApi.downloadFile(item);
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "文件下载失败");
  } finally {
    operatingUuid.value = "";
  }
};

const handleDownloadFolder = async (folder: FolderTreeNode) => {
  fileApi.downloadFolder(folder);
};

const handleDeleteFile = async (item: UserManagedFileItem) => {
  operatingUuid.value = item.fileUuid;
  try {
    const result = await fileApi.deleteFile(item.fileUuid);
    toast.success(result.msg || "文件已移入回收站");
    if (selectedFileDetail.value?.fileUuid === item.fileUuid) {
      closeDetail();
    }
    await Promise.all([
      loadCurrentFiles(),
      loadRecycleFiles(),
      loadStorageSummary(),
    ]);
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "文件删除失败");
  } finally {
    operatingUuid.value = "";
  }
};

const handleRestore = async (item: UserManagedFileItem) => {
  operatingUuid.value = item.fileUuid;
  try {
    const result = await fileApi.restoreFile(item.fileUuid);
    toast.success(result.msg || "文件已恢复");
    await Promise.all([
      loadCurrentFiles(),
      loadRecycleFiles(),
      loadStorageSummary(),
    ]);
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "文件恢复失败");
  } finally {
    operatingUuid.value = "";
  }
};

const openNamePanelForCreate = (parentFolder?: FolderTreeNode | null) => {
  namePanelMode.value = "create-folder";
  namePanelTargetFolder.value = null;
  namePanelTargetFile.value = null;
  namePanelParentFolder.value = parentFolder || activeFolderNode.value || null;
  namePanelValue.value = "";
  namePanelOpen.value = true;
};

const openNamePanelForRenameEntry = (entry: BrowserEntry) => {
  if (entry.kind === "folder") {
    namePanelMode.value = "rename-folder";
    namePanelTargetFolder.value = entry.folder;
    namePanelTargetFile.value = null;
    namePanelValue.value = entry.folder.folderName;
  } else {
    namePanelMode.value = "rename-file";
    namePanelTargetFile.value = entry.file;
    namePanelTargetFolder.value = null;
    namePanelValue.value = entry.file.originName || entry.file.fileName;
  }
  namePanelParentFolder.value = null;
  namePanelOpen.value = true;
};

const closeNamePanel = () => {
  namePanelOpen.value = false;
  namePanelMode.value = null;
  namePanelValue.value = "";
  namePanelTargetFolder.value = null;
  namePanelTargetFile.value = null;
  namePanelParentFolder.value = null;
};

const submitNamePanel = async () => {
  const safeName = namePanelValue.value.trim();
  if (!safeName) {
    toast.info("请输入名称");
    return;
  }
  try {
    if (namePanelMode.value === "create-folder") {
      await fileApi.createFolder(
        safeName,
        namePanelParentFolder.value?.id ?? activeFolderId.value ?? null,
      );
      toast.success("目录创建成功");
    } else if (
      namePanelMode.value === "rename-folder" &&
      namePanelTargetFolder.value
    ) {
      await fileApi.renameFolder(
        namePanelTargetFolder.value.folderUuid,
        safeName,
      );
      toast.success("目录已重命名");
    } else if (
      namePanelMode.value === "rename-file" &&
      namePanelTargetFile.value
    ) {
      await fileApi.renameFile(namePanelTargetFile.value.fileUuid, safeName);
      toast.success("文件已重命名");
    }
    closeNamePanel();
    await Promise.all([reloadFolders(), loadCurrentFiles()]);
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "名称操作失败");
  }
};

const openDeletePanel = (entries: BrowserEntry[]) => {
  deleteEntries.value = entries;
  deletePanelOpen.value = true;
};

const closeDeletePanel = () => {
  deletePanelOpen.value = false;
  deleteEntries.value = [];
};

const submitDeletePanel = async () => {
  try {
    for (const entry of deleteEntries.value) {
      if (entry.kind === "folder") {
        await fileApi.deleteFolder(entry.folder.folderUuid);
      } else {
        await fileApi.deleteFile(entry.file.fileUuid);
      }
    }
    toast.success(
      deleteEntries.value.length > 1 ? "已批量移入回收站" : "已移入回收站",
    );
    closeDeletePanel();
    clearSelection();
    await Promise.all([
      reloadFolders(),
      loadCurrentFiles(),
      loadRecycleFiles(),
      loadStorageSummary(),
    ]);
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "删除失败");
  }
};

const openMovePanel = (entries: BrowserEntry[]) => {
  moveEntries.value = entries;
  moveTargetFolderUuid.value = activeFolderNode.value?.folderUuid || null;
  movePanelOpen.value = true;
};

const closeMovePanel = () => {
  movePanelOpen.value = false;
  moveEntries.value = [];
  moveTargetFolderUuid.value = null;
};

const submitMovePanel = async () => {
  try {
    for (const entry of moveEntries.value) {
      if (entry.kind === "folder") {
        await fileApi.moveFolder(
          entry.folder.folderUuid,
          moveTargetFolderUuid.value,
        );
      } else {
        await fileApi.moveFile(entry.file.fileUuid, moveTargetFolderUuid.value);
      }
    }
    toast.success(moveEntries.value.length > 1 ? "已批量移动" : "移动成功");
    closeMovePanel();
    clearSelection();
    await Promise.all([reloadFolders(), loadCurrentFiles()]);
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "移动失败");
  }
};

const beginEntryDrag = (event: DragEvent, entry: BrowserEntry) => {
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData(
      "text/plain",
      entry.kind === "folder" ? entry.folder.folderUuid : entry.file.fileUuid,
    );
  }
  draggingEntry.value =
    entry.kind === "folder"
      ? {
          kind: "folder",
          folderUuid: entry.folder.folderUuid,
          parentId: entry.folder.parentId ?? null,
        }
      : {
          kind: "file",
          fileUuid: entry.file.fileUuid,
          folderId: entry.file.folderId ?? null,
        };
};

const endEntryDrag = () => {
  draggingEntry.value = null;
  dragHoverFolderUuid.value = "";
};

const dropEntryToFolder = async (targetFolder: FolderTreeNode | null) => {
  const dragging = draggingEntry.value;
  if (!dragging) {
    return;
  }
  const targetId = targetFolder?.id ?? null;
  const targetFolderUuid = targetFolder?.folderUuid ?? null;
  const isSameTarget =
    dragging.kind === "folder"
      ? (dragging.parentId ?? null) === targetId
      : (dragging.folderId ?? null) === targetId;
  if (
    isSameTarget ||
    (dragging.kind === "folder" && dragging.folderUuid === targetFolderUuid)
  ) {
    endEntryDrag();
    return;
  }
  try {
    if (dragging.kind === "folder") {
      await fileApi.moveFolder(dragging.folderUuid, targetFolderUuid);
    } else {
      await fileApi.moveFile(dragging.fileUuid, targetFolderUuid);
    }
    await Promise.all([reloadFolders(), loadCurrentFiles()]);
    toast.success("移动成功");
  } catch (error) {
    toast.error(error instanceof Error ? error.message : "拖拽移动失败");
  } finally {
    endEntryDrag();
  }
};

const handleBatchDownload = async () => {
  for (const entry of selectedEntries.value) {
    if (entry.kind === "folder") {
      fileApi.downloadFolder(entry.folder);
    } else {
      await fileApi.downloadFile(entry.file);
    }
  }
};

const handleBatchDelete = () => {
  if (!selectedEntries.value.length) {
    return;
  }
  openDeletePanel(selectedEntries.value);
};

const isExternalFileDrag = (event: DragEvent) =>
  Array.from(event.dataTransfer?.types || []).includes("Files");

const handleDropzoneDragEnter = (event: DragEvent) => {
  if (!isExternalFileDrag(event)) {
    return;
  }
  event.preventDefault();
  uploadDragDepth.value += 1;
  externalDragActive.value = true;
};

const handleDropzoneDragOver = (event: DragEvent) => {
  if (!isExternalFileDrag(event)) {
    return;
  }
  event.preventDefault();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = "copy";
  }
  externalDragActive.value = true;
};

const handleDropzoneDragLeave = (event: DragEvent) => {
  if (!isExternalFileDrag(event)) {
    return;
  }
  event.preventDefault();
  uploadDragDepth.value = Math.max(0, uploadDragDepth.value - 1);
  if (uploadDragDepth.value === 0) {
    externalDragActive.value = false;
  }
};

const handleDropzoneDrop = async (event: DragEvent) => {
  if (!isExternalFileDrag(event)) {
    return;
  }
  event.preventDefault();
  uploadDragDepth.value = 0;
  externalDragActive.value = false;
  const files = Array.from(event.dataTransfer?.files || []);
  await uploadFiles(files);
};

const openFileContextMenu = (event: MouseEvent, item: UserManagedFileItem) => {
  const entry = currentEntries.value.find(
    (candidate) =>
      candidate.kind === "file" && candidate.file.fileUuid === item.fileUuid,
  );
  if (!entry || entry.kind !== "file") {
    return;
  }
  const items: AppContextMenuItem[] = [
    {
      key: `detail-${item.fileUuid}`,
      label: "查看详情",
      icon: "lucide:panel-right-open",
      action: () => openFileDetail(item),
    },
    {
      key: `rename-${item.fileUuid}`,
      label: "重命名",
      icon: "lucide:pencil-line",
      action: () => openNamePanelForRenameEntry(entry),
    },
    {
      key: `move-${item.fileUuid}`,
      label: "移动",
      icon: "lucide:move-right",
      action: () => openMovePanel([entry]),
    },
    {
      key: `download-${item.fileUuid}`,
      label: "下载文件",
      icon: "lucide:download",
      action: () => handleDownloadFile(item),
    },
    {
      key: `share-${item.fileUuid}`,
      label: "分享文件",
      icon: "lucide:share-2",
      action: () => openSharePanel(item),
    },
    {
      key: `delete-${item.fileUuid}`,
      label: "移入回收站",
      icon: "lucide:trash-2",
      danger: true,
      action: () => openDeletePanel([entry]),
    },
  ];
  contextMenu.open(event, items);
};

const openFolderContextMenu = (
  event: MouseEvent,
  folder: FolderTreeNode | null,
) => {
  const entry =
    folder == null
      ? null
      : currentEntries.value.find(
          (candidate) =>
            candidate.kind === "folder" &&
            candidate.folder.folderUuid === folder.folderUuid,
        );
  const items: AppContextMenuItem[] = [
    {
      key: folder ? `open-${folder.folderUuid}` : "open-root",
      label: folder ? "打开目录" : "回到根目录",
      icon: "lucide:folder-open",
      action: () => (folder ? openFolder(folder) : navigateToFolderId(null)),
    },
    {
      key: folder ? `create-${folder.folderUuid}` : "create-root",
      label: folder ? "新建子目录" : "新建目录",
      icon: "lucide:folder-plus",
      action: () => openNamePanelForCreate(folder),
    },
    ...(entry && entry.kind === "folder"
      ? [
          {
            key: `rename-${folder?.folderUuid}`,
            label: "重命名目录",
            icon: "lucide:pencil-line",
            action: () => openNamePanelForRenameEntry(entry),
          } satisfies AppContextMenuItem,
          {
            key: `move-${folder?.folderUuid}`,
            label: "移动目录",
            icon: "lucide:move-right",
            action: () => openMovePanel([entry]),
          } satisfies AppContextMenuItem,
          {
            key: `download-${folder?.folderUuid}`,
            label: "下载为压缩包",
            icon: "lucide:download",
            action: () =>
              entry.kind === "folder"
                ? handleDownloadFolder(entry.folder)
                : undefined,
          } satisfies AppContextMenuItem,
          {
            key: `delete-${folder?.folderUuid}`,
            label: "删除目录",
            icon: "lucide:trash-2",
            danger: true,
            action: () => openDeletePanel([entry]),
          } satisfies AppContextMenuItem,
        ]
      : []),
    {
      key: folder ? `refresh-${folder.folderUuid}` : "refresh-root",
      label: "刷新目录",
      icon: "lucide:refresh-cw",
      action: () => loadAll(),
    },
  ];
  contextMenu.open(event, items);
};

const handleClickOutsideUploadPanel = (event: MouseEvent) => {
  if (!uploadPanelOpen.value) {
    return;
  }
  const target = event.target as Node | null;
  if (
    uploadPanelRef.value?.contains(target) ||
    uploadFabRef.value?.contains(target)
  ) {
    return;
  }
  uploadPanelOpen.value = false;
};

onMounted(async () => {
  pageReady.value = true;
  document.addEventListener("mousedown", handleClickOutsideUploadPanel);
  await reloadFolders();
});

onBeforeUnmount(() => {
  document.removeEventListener("mousedown", handleClickOutsideUploadPanel);
  if (import.meta.client) {
    document.documentElement.style.overflow = "";
    document.body.style.overflow = "";
  }
  revokePreviewUrl();
});
</script>

<template>
  <section class="files-page">
    <div v-if="pageResolved" class="files-browser files-surface-panel">
      <div class="files-browser-toolbar">
        <div class="files-toolbar-left">
          <div class="files-storage-chip">
            <span>已用 {{ formatBytes(storageSummary?.usedBytes) }}</span>
            <strong
              >剩余 {{ formatBytes(storageSummary?.remainingBytes) }}</strong
            >
          </div>

          <div class="files-tab-switcher">
            <button
              class="files-tab-button"
              :class="{ 'files-tab-button-active': activeTab === 'files' }"
              type="button"
              @click="activeTab = 'files'"
            >
              文件
            </button>
            <button
              class="files-tab-button"
              :class="{ 'files-tab-button-active': activeTab === 'recycle' }"
              type="button"
              @click="activeTab = 'recycle'"
            >
              回收站
            </button>
          </div>
        </div>

        <div class="files-toolbar-actions">
          <input
            ref="uploadInputRef"
            class="files-hidden-input"
            type="file"
            multiple
            @change="handleUploadChange"
          />

          <AppButton variant="secondary" @click="openNamePanelForCreate()">
            新建文件夹
          </AppButton>

          <AppButton
            variant="secondary"
            :loading="loadingCurrent || loadingRecycle"
            @click="loadAll"
          >
            刷新
          </AppButton>

          <div class="files-view-switcher">
            <button
              class="files-view-button"
              :class="{ 'files-view-button-active': viewMode === 'detail' }"
              type="button"
              @click="setViewMode('detail')"
            >
              <Icon name="lucide:list" />
            </button>
            <button
              class="files-view-button"
              :class="{ 'files-view-button-active': viewMode === 'grid' }"
              type="button"
              @click="setViewMode('grid')"
            >
              <Icon name="lucide:grid-2x2" />
            </button>
          </div>

          <AppButton :loading="uploading" @click="openUploadPicker">
            上传文件
          </AppButton>
        </div>
      </div>

      <div class="files-browser-pathbar">
        <div class="files-browser-pathbar-main">
          <button
            class="files-nav-button"
            type="button"
            :disabled="activeFolderId === null || activeTab !== 'files'"
            @click="navigateToParent"
          >
            <Icon name="lucide:arrow-left" />
            返回上一级
          </button>

          <div v-if="activeTab === 'files'" class="files-breadcrumbs">
            <button
              v-for="crumb in breadcrumbItems"
              :key="crumb.folderUuid"
              class="files-breadcrumb-item"
              :class="{
                'files-breadcrumb-item-active': activeFolderId === crumb.id,
                'files-breadcrumb-item-drop':
                  dragHoverFolderUuid ===
                  (crumb.id === null ? 'root' : crumb.folderUuid),
              }"
              type="button"
              @click="navigateToFolderId(crumb.id)"
              @dragover.prevent="
                dragHoverFolderUuid =
                  crumb.id === null ? 'root' : crumb.folderUuid
              "
              @dragleave="dragHoverFolderUuid = ''"
              @drop.stop.prevent="dropEntryToFolderById(crumb.id)"
            >
              {{ crumb.displayLabel }}
            </button>
          </div>

          <div v-else class="files-breadcrumbs">
            <span class="files-breadcrumb-static">回收站</span>
          </div>
        </div>
      </div>

      <div
        v-if="activeTab === 'files' && hasSelection"
        class="files-batch-actions"
      >
        <span class="files-batch-actions-count">
          已选 {{ selectedEntryKeys.length }} 项
        </span>
        <button type="button" @click="handleBatchDownload">批量下载</button>
        <button type="button" @click="openMovePanel(selectedEntries)">
          批量移动
        </button>
        <button type="button" @click="handleBatchDelete">批量删除</button>
        <button type="button" @click="clearSelection">取消选择</button>
      </div>

      <div
        class="files-browser-content"
        :class="{ 'files-browser-content-drag': externalDragActive }"
        @dragenter="handleDropzoneDragEnter"
        @dragover="handleDropzoneDragOver"
        @dragleave="handleDropzoneDragLeave"
        @drop="handleDropzoneDrop"
        @contextmenu.self.prevent="openCurrentFolderContextMenu"
      >
        <div
          v-if="activeTab === 'files'"
          class="files-list-shell"
          @contextmenu.self.prevent="openCurrentFolderContextMenu"
        >
          <div class="files-list-topbar">
            <label class="files-check-all">
              <input
                type="checkbox"
                :checked="
                  currentEntries.length > 0 &&
                  selectedEntryKeys.length === currentEntries.length
                "
                @change="selectAllCurrentEntries"
              />
              <span class="files-check-all-indicator" />
              <span>全选当前目录</span>
            </label>

            <span class="files-list-summary">
              当前目录：{{ activeFolderDisplayPath }} ·
              {{ currentEntries.length }} 项
            </span>
          </div>

          <div
            class="files-entry-list"
            :class="{
              'files-entry-list-grid': viewMode === 'grid',
              'files-entry-list-detail': viewMode === 'detail',
            }"
          >
            <article
              v-for="entry in currentEntries"
              :key="entry.key"
              class="files-entry-card"
              :class="{
                'files-entry-card-selected': selectedEntryKeys.includes(
                  entry.key,
                ),
                'files-entry-card-folder': entry.kind === 'folder',
                'files-entry-card-drop':
                  entry.kind === 'folder' &&
                  dragHoverFolderUuid === entry.folder.folderUuid,
              }"
              draggable="true"
              @dragstart="beginEntryDrag($event, entry)"
              @dragend="endEntryDrag"
              @dragover.prevent="
                entry.kind === 'folder'
                  ? (dragHoverFolderUuid = entry.folder.folderUuid)
                  : undefined
              "
              @dragleave="
                entry.kind === 'folder' ? (dragHoverFolderUuid = '') : undefined
              "
              @drop.prevent="
                entry.kind === 'folder'
                  ? dropEntryToFolder(entry.folder)
                  : undefined
              "
              @dblclick="
                entry.kind === 'folder'
                  ? openFolder(entry.folder)
                  : openFileDetail(entry.file)
              "
              @contextmenu="
                entry.kind === 'folder'
                  ? openFolderContextMenu($event, entry.folder)
                  : openFileContextMenu($event, entry.file)
              "
            >
              <label
                class="files-entry-check"
                :aria-label="`选择${entry.name}`"
              >
                <input
                  type="checkbox"
                  :checked="selectedEntryKeys.includes(entry.key)"
                  @change="
                    toggleEntrySelection(
                      entry.key,
                      ($event.target as HTMLInputElement).checked,
                    )
                  "
                />
                <span class="files-entry-check-indicator" />
              </label>

              <button
                class="files-entry-main"
                type="button"
                @click="
                  entry.kind === 'folder'
                    ? openFolder(entry.folder)
                    : openFileDetail(entry.file)
                "
              >
                <span
                  class="files-entry-icon-wrap"
                  :class="{
                    'files-entry-icon-wrap-folder': entry.kind === 'folder',
                  }"
                >
                  <Icon
                    :name="
                      entry.kind === 'folder' ? 'lucide:folder' : 'lucide:file'
                    "
                    class="files-entry-icon"
                  />
                </span>

                <span class="files-entry-body">
                  <strong class="files-entry-name">{{ entry.name }}</strong>
                  <span class="files-entry-meta">
                    <template v-if="entry.kind === 'folder'">
                      <span>目录</span>
                      <span>{{ formatDateTime(entry.createTime) }}</span>
                    </template>
                    <template v-else>
                      <span>{{ formatBytes(entry.size) }}</span>
                      <span>{{ entry.ext || "未知类型" }}</span>
                      <span>{{ formatDateTime(entry.createTime) }}</span>
                    </template>
                  </span>
                </span>
              </button>

              <div class="files-entry-actions">
                <button
                  v-if="entry.kind === 'folder'"
                  type="button"
                  @click="openFolder(entry.folder)"
                >
                  打开
                </button>
                <button
                  v-else
                  type="button"
                  @click="openFileDetail(entry.file)"
                >
                  详情
                </button>
                <button
                  type="button"
                  @click="openNamePanelForRenameEntry(entry)"
                >
                  重命名
                </button>
                <button type="button" @click="openMovePanel([entry])">
                  移动
                </button>
                <button
                  type="button"
                  @click="
                    entry.kind === 'folder'
                      ? handleDownloadFolder(entry.folder)
                      : handleDownloadFile(entry.file)
                  "
                >
                  下载
                </button>
                <button
                  v-if="entry.kind === 'file'"
                  type="button"
                  @click="openSharePanel(entry.file)"
                >
                  分享
                </button>
                <button type="button" @click="openDeletePanel([entry])">
                  删除
                </button>
              </div>
            </article>
          </div>

          <div
            v-if="!loadingCurrent && !currentEntries.length"
            class="files-empty-state"
          >
            <p>当前目录没有文件或文件夹</p>
            <span>拖拽文件到这里即可上传</span>
          </div>
        </div>

        <div v-else class="files-recycle-list">
          <article
            v-for="item in recycleFiles"
            :key="item.fileUuid"
            class="files-entry-card files-entry-card-recycle"
          >
            <button
              class="files-entry-main"
              type="button"
              @click="openFileDetail(item)"
            >
              <span class="files-entry-icon-wrap">
                <Icon name="lucide:trash-2" class="files-entry-icon" />
              </span>

              <span class="files-entry-body">
                <strong class="files-entry-name">
                  {{ item.originName || item.fileName }}
                </strong>
                <span class="files-entry-meta">
                  <span>{{ formatBytes(item.fileSize) }}</span>
                  <span>删除于 {{ formatDateTime(item.deletedAt) }}</span>
                  <span>到期 {{ formatDateTime(item.recycleExpireAt) }}</span>
                </span>
              </span>
            </button>

            <div class="files-entry-actions">
              <button type="button" @click="handleRestore(item)">恢复</button>
            </div>
          </article>

          <div
            v-if="!loadingRecycle && !recycleFiles.length"
            class="files-empty-state"
          >
            <p>回收站当前为空</p>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="files-skeleton-grid" aria-hidden="true">
      <article class="files-skeleton-card" />
      <article class="files-skeleton-card" />
      <article class="files-skeleton-card" />
    </div>

    <button
      ref="uploadFabRef"
      class="files-upload-fab"
      type="button"
      @click="uploadPanelOpen = !uploadPanelOpen"
    >
      上传进度
      <span class="files-upload-fab-badge">{{ activeUploadCount }}</span>
    </button>

    <Teleport to="body">
      <section v-if="namePanelOpen" class="files-modal-mask">
        <div class="files-modal-panel files-modal-panel-compact">
          <strong>{{
            namePanelMode === "create-folder"
              ? "新建文件夹"
              : namePanelMode === "rename-folder"
                ? "重命名目录"
                : "重命名文件"
          }}</strong>

          <input
            v-model="namePanelValue"
            class="files-modal-input"
            type="text"
            maxlength="180"
            placeholder="请输入名称"
          />

          <div class="files-modal-actions">
            <AppButton variant="secondary" @click="closeNamePanel"
              >取消</AppButton
            >
            <AppButton @click="submitNamePanel">确认</AppButton>
          </div>
        </div>
      </section>
    </Teleport>

    <Teleport to="body">
      <section v-if="deletePanelOpen" class="files-modal-mask">
        <div class="files-modal-panel files-modal-panel-compact">
          <strong>确认删除</strong>
          <p class="files-modal-tip">
            选中的
            {{ deleteEntries.length }}
            项会进入回收站，目录下内容也会一并进入回收站。
          </p>
          <div class="files-modal-actions">
            <AppButton variant="secondary" @click="closeDeletePanel"
              >取消</AppButton
            >
            <AppButton @click="submitDeletePanel">确认删除</AppButton>
          </div>
        </div>
      </section>
    </Teleport>

    <Teleport to="body">
      <section v-if="movePanelOpen" class="files-modal-mask">
        <div class="files-modal-panel">
          <div class="files-modal-header">
            <strong>选择移动目标</strong>
            <button
              class="files-modal-close"
              type="button"
              @click="closeMovePanel"
            >
              <Icon name="lucide:x" />
            </button>
          </div>

          <div class="files-move-layout">
            <div class="files-move-summary">
              <span>待移动 {{ moveEntries.length }} 项</span>
              <strong>{{
                moveEntries.map((entry) => entry.name).join("、")
              }}</strong>
            </div>

            <div class="files-move-tree">
              <button
                class="files-move-tree-item"
                :class="{
                  'files-move-tree-item-active': moveTargetFolderUuid === null,
                }"
                type="button"
                @click="moveTargetFolderUuid = null"
              >
                全部文件
              </button>

              <button
                v-for="folder in movePanelFolders"
                :key="folder.folderUuid"
                class="files-move-tree-item"
                :class="{
                  'files-move-tree-item-active':
                    moveTargetFolderUuid === folder.folderUuid,
                }"
                type="button"
                @click="moveTargetFolderUuid = folder.folderUuid"
              >
                {{
                  formatFolderDisplayPath(folder.folderPath, folder.folderName)
                }}
              </button>
            </div>
          </div>

          <div class="files-modal-actions">
            <AppButton variant="secondary" @click="closeMovePanel"
              >取消</AppButton
            >
            <AppButton @click="submitMovePanel">确认移动</AppButton>
          </div>
        </div>
      </section>
    </Teleport>

    <Teleport to="body">
      <section v-if="sharePanelOpen" class="files-modal-mask">
        <div class="files-modal-panel files-share-panel">
          <div class="files-modal-header">
            <div class="files-share-panel-title">
              <strong>文件分享</strong>
              <span>{{
                shareTargetFile?.originName || shareTargetFile?.fileName || "未选择文件"
              }}</span>
            </div>
            <button
              class="files-modal-close"
              type="button"
              @click="closeSharePanel"
            >
              <Icon name="lucide:x" />
            </button>
          </div>

          <div class="files-share-form">
            <label class="files-share-field">
              <span>提取码</span>
              <input
                v-model="shareExtractCode"
                class="files-modal-input"
                type="text"
                maxlength="8"
                placeholder="留空表示无需提取码"
              />
            </label>

            <label class="files-share-field">
              <span>过期时间</span>
              <input
                v-model="shareExpireAt"
                class="files-modal-input"
                type="datetime-local"
              />
            </label>

            <label class="files-share-field">
              <span>下载次数上限</span>
              <input
                v-model="shareMaxDownloadCount"
                class="files-modal-input"
                type="number"
                min="1"
                placeholder="留空表示不限"
              />
            </label>

            <label class="files-share-field">
              <span>查看权限</span>
              <select
                v-model.number="shareViewAuthMode"
                class="files-share-select"
              >
                <option :value="0">免登录查看</option>
                <option :value="1">登录后查看</option>
              </select>
            </label>

            <label class="files-share-field">
              <span>下载权限</span>
              <select
                v-model.number="shareDownloadAuthMode"
                class="files-share-select"
              >
                <option :value="0">免登录下载</option>
                <option :value="1">登录后下载</option>
              </select>
            </label>
          </div>

          <div class="files-modal-actions">
            <AppButton variant="secondary" @click="closeSharePanel"
              >关闭</AppButton
            >
            <AppButton :loading="shareSubmitting" @click="submitSharePanel">
              创建分享
            </AppButton>
          </div>

          <div class="files-share-list">
            <div v-if="shareLoading" class="files-preview-empty">
              正在加载分享列表…
            </div>
            <article
              v-for="share in shareItems"
              :key="share.shareUuid"
              class="files-share-item"
            >
              <div class="files-share-item-main">
                <strong>{{ buildShareAbsoluteUrl(share.sharePath) }}</strong>
                <span>
                  {{
                    share.status === 1
                      ? share.hasExtractCode
                        ? "启用提取码"
                        : "无需提取码"
                      : "已取消"
                  }}
                  ·
                  {{
                    share.viewAuthMode === 1 ? "登录查看" : "免登录查看"
                  }}
                  ·
                  {{
                    share.downloadAuthMode === 1 ? "登录下载" : "免登录下载"
                  }}
                </span>
                <span>
                  查看 {{ share.viewCount }} 次 · 下载 {{ share.downloadCount }}
                  次
                  <template v-if="share.maxDownloadCount">
                    / {{ share.maxDownloadCount }}
                  </template>
                  · 过期 {{ formatDateTime(share.expireAt) }}
                </span>
              </div>
              <div class="files-share-item-actions">
                <button type="button" @click="copyShareLink(share.sharePath)">
                  复制链接
                </button>
                <button
                  type="button"
                  :disabled="share.status !== 1"
                  @click="revokeShareItem(share.shareUuid)"
                >
                  取消分享
                </button>
              </div>
            </article>

            <div v-if="!shareLoading && !shareItems.length" class="files-preview-empty">
              当前文件还没有分享记录
            </div>
          </div>
        </div>
      </section>
    </Teleport>

    <Teleport to="body">
      <div
        v-if="detailPanelOpen"
        class="files-detail-drawer-mask"
        @click.self="closeDetail"
      >
        <section class="files-detail-modal" role="dialog" aria-modal="true">
          <div class="files-modal-header">
            <strong>文件详情</strong>
            <div class="files-detail-header-actions">
              <AppButton
                v-if="selectedFileDetail"
                variant="secondary"
                @click="openSharePanelFromDetail"
              >
                分享
              </AppButton>
              <button
                class="files-modal-close"
                type="button"
                @click="closeDetail"
              >
                <Icon name="lucide:x" />
              </button>
            </div>
          </div>

          <div
            v-if="detailLoading"
            class="files-detail-body files-detail-body-single"
          >
            <div class="files-preview-empty">正在加载详情…</div>
          </div>

          <template v-else-if="selectedFileDetail">
            <div class="files-detail-body">
              <div
                class="files-preview-shell"
                :class="{
                  'files-preview-shell-media':
                    previewKind === 'video' || previewKind === 'audio',
                  'files-preview-shell-video': previewKind === 'video',
                }"
              >
                <div v-if="previewLoading" class="files-preview-empty">
                  正在加载预览…
                </div>
                <div v-else-if="previewError" class="files-preview-empty">
                  {{ previewError }}
                </div>
                <img
                  v-else-if="previewKind === 'image' && previewUrl"
                  :src="previewUrl"
                  class="files-preview-image"
                  alt="文件预览"
                />
                <video
                  v-else-if="previewKind === 'video' && previewUrl"
                  :src="previewUrl"
                  class="files-preview-media"
                  controls
                  preload="metadata"
                  playsinline
                />
                <audio
                  v-else-if="previewKind === 'audio' && previewUrl"
                  :src="previewUrl"
                  class="files-preview-audio"
                  controls
                  preload="metadata"
                />
                <pre
                  v-else-if="previewKind === 'text' && previewText"
                  class="files-preview-text"
                  >{{ previewText }}</pre
                >
                <div v-else class="files-preview-empty">
                  当前文件类型暂不支持在线预览
                </div>
              </div>

              <div class="files-detail-list">
                <div class="files-detail-row">
                  <span>名称</span>
                  <strong>{{
                    selectedFileDetail.originName || selectedFileDetail.fileName
                  }}</strong>
                </div>
                <div class="files-detail-row">
                  <span>大小</span>
                  <strong>{{
                    formatBytes(selectedFileDetail.fileSize)
                  }}</strong>
                </div>
                <div class="files-detail-row">
                  <span>类型</span>
                  <strong>{{
                    selectedFileDetail.fileMime ||
                    selectedFileDetail.fileExt ||
                    "未知"
                  }}</strong>
                </div>
                <div class="files-detail-row">
                  <span>哈希</span>
                  <strong>{{ selectedFileDetail.fileHash || "暂无" }}</strong>
                </div>
                <div class="files-detail-row">
                  <span>对象 UUID</span>
                  <strong>{{ selectedFileDetail.objectUuid || "暂无" }}</strong>
                </div>
                <div class="files-detail-row">
                  <span>存储节点</span>
                  <strong>{{
                    selectedFileDetail.primaryNodeName ||
                    selectedFileDetail.primaryNodeCode ||
                    "暂无"
                  }}</strong>
                </div>
                <div class="files-detail-row">
                  <span>创建时间</span>
                  <strong>{{
                    formatDateTime(selectedFileDetail.createTime)
                  }}</strong>
                </div>
              </div>
            </div>
          </template>
        </section>
      </div>
    </Teleport>

    <section
      v-if="uploadPanelOpen"
      ref="uploadPanelRef"
      class="files-upload-panel"
    >
      <header class="files-upload-panel-header">
        <strong>上传列表</strong>
        <div class="files-upload-panel-actions">
          <button type="button" @click="clearFinishedUploadTasks">
            清除已完成
          </button>
          <button type="button" @click="uploadPanelOpen = false">收起</button>
        </div>
      </header>

      <div v-if="uploadTasks.length" class="files-upload-panel-list">
        <article
          v-for="task in uploadTasks"
          :key="task.id"
          class="files-upload-item"
        >
          <div class="files-upload-item-header">
            <strong class="files-upload-item-name">{{ task.fileName }}</strong>
            <span class="files-upload-item-percent">{{ task.percent }}%</span>
          </div>
          <div class="files-upload-progress-track">
            <span
              class="files-upload-progress-fill"
              :style="{ width: `${task.percent}%` }"
            />
          </div>
          <div class="files-upload-item-meta">
            <span>{{ formatBytes(task.fileSize) }}</span>
            <span>{{ task.text }}</span>
          </div>
          <p v-if="task.errorMessage" class="files-upload-item-error">
            {{ task.errorMessage }}
          </p>
        </article>
      </div>

      <div v-else class="files-upload-empty-state">当前没有上传任务</div>
    </section>
  </section>
</template>

<style scoped lang="scss">
.files-page {
  display: grid;
}

.files-surface-panel,
.files-entry-card,
.files-skeleton-card {
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: calc(var(--yn-radius-large) + 2px);
  background: color-mix(in srgb, var(--yn-color-surface) 95%, transparent);
  box-shadow: var(--yn-shadow-card);
  backdrop-filter: blur(12px);
}

.files-browser {
  display: grid;
  gap: 14px;
  padding: 18px;
}

.files-hidden-input {
  display: none;
}

.files-browser-toolbar,
.files-browser-pathbar,
.files-list-topbar,
.files-toolbar-left,
.files-toolbar-actions,
.files-tab-switcher,
.files-view-switcher,
.files-batch-actions,
.files-upload-panel-header,
.files-upload-item-header,
.files-upload-item-meta,
.files-upload-panel-actions,
.files-modal-header,
.files-modal-actions,
.files-detail-header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.files-browser-toolbar,
.files-browser-pathbar,
.files-list-topbar,
.files-upload-panel-header,
.files-upload-item-header,
.files-upload-item-meta,
.files-modal-header,
.files-modal-actions {
  justify-content: space-between;
}

.files-storage-chip {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface-raised);
  color: var(--yn-color-text-secondary);
}

.files-storage-chip strong {
  color: var(--yn-color-text-primary);
}

.files-tab-switcher,
.files-view-switcher {
  padding: 4px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface-raised);
}

.files-tab-button,
.files-view-button,
.files-nav-button,
.files-breadcrumb-item,
.files-entry-main,
.files-entry-actions button,
.files-upload-panel-actions button,
.files-modal-close,
.files-move-tree-item {
  border: 0;
  background: transparent;
  color: var(--yn-color-text-secondary);
  cursor: pointer;
}

.files-tab-button,
.files-view-button {
  min-height: 34px;
  padding: 0 12px;
  border-radius: var(--yn-radius-small);
}

.files-tab-button-active,
.files-view-button-active {
  background: color-mix(
    in srgb,
    var(--yn-color-primary) 12%,
    var(--yn-color-surface)
  );
  color: var(--yn-color-text-primary);
}

.files-browser-pathbar {
  display: grid;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: var(--yn-color-surface-raised);
}

.files-browser-pathbar-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.files-nav-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 0 12px;
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface);
}

.files-nav-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.files-breadcrumbs {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.files-breadcrumb-item,
.files-breadcrumb-static {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  min-height: 36px;
  padding: 0 12px;
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface);
  color: var(--yn-color-text-secondary);
  word-break: break-word;
}

.files-breadcrumb-item:not(:first-child)::before {
  content: ">";
  margin-right: 10px;
  color: var(--yn-color-text-tertiary);
}

.files-breadcrumb-item {
  border: 0;
  cursor: pointer;
}

.files-breadcrumb-item:hover {
  color: var(--yn-color-text-primary);
  background: color-mix(
    in srgb,
    var(--yn-color-primary) 8%,
    var(--yn-color-surface)
  );
}

.files-breadcrumb-item-drop {
  color: var(--yn-color-text-primary);
  background: color-mix(
    in srgb,
    var(--yn-color-primary) 14%,
    var(--yn-color-surface)
  );
}

.files-breadcrumb-item-active {
  color: var(--yn-color-text-primary);
  background: color-mix(
    in srgb,
    var(--yn-color-primary) 10%,
    var(--yn-color-surface)
  );
}

.files-batch-actions {
  position: fixed;
  right: 24px;
  bottom: 88px;
  z-index: 42;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: color-mix(in srgb, var(--yn-color-surface) 92%, transparent);
  box-shadow: var(--yn-shadow-overlay);
  backdrop-filter: blur(12px);
}

.files-batch-actions-count {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 10px;
  border-radius: 999px;
  background: color-mix(
    in srgb,
    var(--yn-color-primary) 10%,
    var(--yn-color-surface)
  );
  color: var(--yn-color-text-primary);
  font-size: 13px;
  font-weight: 600;
}

.files-batch-actions button,
.files-entry-actions button,
.files-upload-panel-actions button {
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface);
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    color 0.2s ease;
}

.files-batch-actions button:hover,
.files-entry-actions button:hover,
.files-upload-panel-actions button:hover {
  border-color: color-mix(in srgb, var(--yn-color-primary) 24%, transparent);
  background: color-mix(
    in srgb,
    var(--yn-color-primary) 8%,
    var(--yn-color-surface)
  );
  color: var(--yn-color-text-primary);
}

.files-browser-content {
  position: relative;
  min-height: 520px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: color-mix(
    in srgb,
    var(--yn-color-surface-raised) 72%,
    var(--yn-color-surface)
  );
  padding: 14px;
}

.files-browser-content-drag::after {
  content: "拖拽文件到此处上传";
  position: absolute;
  inset: 10px;
  display: grid;
  place-items: center;
  border: 2px dashed
    color-mix(in srgb, var(--yn-color-primary) 60%, transparent);
  border-radius: calc(var(--yn-radius-large) - 4px);
  background: color-mix(in srgb, var(--yn-color-primary) 8%, transparent);
  color: var(--yn-color-primary);
  font-size: 16px;
  font-weight: 700;
  pointer-events: none;
}

.files-list-shell,
.files-recycle-list {
  display: grid;
  gap: 12px;
}

.files-list-topbar {
  flex-wrap: wrap;
}

.files-check-all {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--yn-color-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.files-check-all input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.files-check-all-indicator {
  position: relative;
  display: inline-flex;
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border: 1px solid var(--yn-color-border-medium);
  border-radius: 6px;
  background: var(--yn-color-surface);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.35);
}

.files-check-all-indicator::after {
  content: "";
  position: absolute;
  inset: 3px;
  border-radius: 4px;
  background: var(--yn-color-primary);
  opacity: 0;
  transform: scale(0.8);
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.files-check-all input:checked + .files-check-all-indicator {
  border-color: color-mix(in srgb, var(--yn-color-primary) 36%, transparent);
  background: color-mix(
    in srgb,
    var(--yn-color-primary) 10%,
    var(--yn-color-surface)
  );
}

.files-check-all input:checked + .files-check-all-indicator::after {
  opacity: 1;
  transform: scale(1);
}

.files-list-summary {
  color: var(--yn-color-text-tertiary);
  font-size: 13px;
}

.files-entry-list {
  display: grid;
  gap: 12px;
}

.files-entry-list-grid {
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
}

.files-entry-list-grid .files-entry-card {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: flex-start;
  align-content: start;
  min-height: 210px;
}

.files-entry-list-grid .files-entry-main {
  align-items: flex-start;
}

.files-entry-list-grid .files-entry-body {
  align-content: start;
}

.files-entry-list-grid .files-entry-name {
  display: -webkit-box;
  overflow: hidden;
  word-break: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.files-entry-list-grid .files-entry-meta {
  gap: 6px;
}

.files-entry-list-grid .files-entry-actions {
  grid-column: 1 / -1;
  justify-content: flex-start;
  align-self: end;
  padding-top: 4px;
}

.files-entry-list-grid .files-entry-actions button {
  flex: 0 0 auto;
}

.files-entry-list-detail {
  grid-template-columns: 1fr;
}

.files-entry-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 14px;
  transition:
    border-color 0.2s ease,
    background 0.2s ease;
}

.files-entry-card:hover {
  border-color: color-mix(in srgb, var(--yn-color-primary) 20%, transparent);
  background: color-mix(
    in srgb,
    var(--yn-color-primary) 5%,
    var(--yn-color-surface)
  );
}

.files-entry-card-selected {
  border-color: color-mix(in srgb, var(--yn-color-primary) 35%, transparent);
  background: color-mix(
    in srgb,
    var(--yn-color-primary) 8%,
    var(--yn-color-surface)
  );
}

.files-entry-card-drop {
  border-color: color-mix(in srgb, var(--yn-color-primary) 48%, transparent);
  background: color-mix(
    in srgb,
    var(--yn-color-primary) 14%,
    var(--yn-color-surface)
  );
}

.files-entry-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.files-entry-check input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.files-entry-check-indicator {
  position: relative;
  display: inline-flex;
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border: 1px solid var(--yn-color-border-medium);
  border-radius: 6px;
  background: var(--yn-color-surface);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.35);
}

.files-entry-check-indicator::after {
  content: "";
  position: absolute;
  inset: 3px;
  border-radius: 4px;
  background: var(--yn-color-primary);
  opacity: 0;
  transform: scale(0.8);
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.files-entry-check input:checked + .files-entry-check-indicator {
  border-color: color-mix(in srgb, var(--yn-color-primary) 36%, transparent);
  background: color-mix(
    in srgb,
    var(--yn-color-primary) 10%,
    var(--yn-color-surface)
  );
}

.files-entry-check input:checked + .files-entry-check-indicator::after {
  opacity: 1;
  transform: scale(1);
}

.files-entry-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 0;
  text-align: left;
}

.files-entry-icon-wrap {
  display: inline-flex;
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: var(--yn-radius-medium);
  background: color-mix(
    in srgb,
    var(--yn-color-primary) 10%,
    var(--yn-color-surface)
  );
  color: var(--yn-color-primary);
}

.files-entry-icon-wrap-folder {
  background: color-mix(in srgb, #f59e0b 16%, var(--yn-color-surface));
  color: #d97706;
}

.files-entry-icon {
  font-size: 18px;
}

.files-entry-body {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.files-entry-name {
  color: var(--yn-color-text-primary);
  font-size: 15px;
  font-weight: 700;
  word-break: break-all;
}

.files-entry-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  color: var(--yn-color-text-tertiary);
  font-size: 12px;
}

.files-entry-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.files-empty-state,
.files-upload-empty-state,
.files-preview-empty {
  display: grid;
  place-items: center;
  min-height: 180px;
  border: 1px dashed var(--yn-color-border-medium);
  border-radius: var(--yn-radius-large);
  color: var(--yn-color-text-tertiary);
  text-align: center;
  padding: 20px;
}

.files-empty-state p,
.files-empty-state span,
.files-modal-tip {
  margin: 0;
  line-height: 1.6;
}

.files-modal-mask,
.files-detail-drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 1400;
  background: rgba(15, 23, 42, 0.4);
  backdrop-filter: blur(6px);
}

.files-modal-mask {
  display: grid;
  place-items: center;
  padding: 20px;
}

.files-modal-panel {
  width: min(720px, calc(100vw - 24px));
  display: grid;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: var(--yn-color-surface);
  box-shadow: var(--yn-shadow-overlay);
}

.files-modal-panel-compact {
  width: min(420px, calc(100vw - 24px));
}

.files-modal-input {
  min-height: 44px;
  border: 1px solid var(--yn-color-border-medium);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface-raised);
  color: var(--yn-color-text-primary);
  padding: 0 14px;
}

.files-share-panel {
  width: min(880px, calc(100vw - 24px));
}

.files-share-panel-title {
  display: grid;
  gap: 4px;
}

.files-share-panel-title span {
  color: var(--yn-color-text-tertiary);
  font-size: 12px;
  word-break: break-all;
}

.files-share-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.files-share-field {
  display: grid;
  gap: 8px;
}

.files-share-field span {
  color: var(--yn-color-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.files-share-select {
  min-height: 44px;
  border: 1px solid var(--yn-color-border-medium);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface-raised);
  color: var(--yn-color-text-primary);
  padding: 0 14px;
}

.files-share-list {
  display: grid;
  gap: 12px;
  max-height: 360px;
  overflow: auto;
}

.files-share-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface-raised);
}

.files-share-item-main {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.files-share-item-main strong {
  color: var(--yn-color-text-primary);
  word-break: break-all;
}

.files-share-item-main span {
  color: var(--yn-color-text-tertiary);
  font-size: 12px;
  line-height: 1.6;
}

.files-share-item-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.files-share-item-actions button {
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface);
  color: var(--yn-color-text-secondary);
}

.files-modal-close {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border-radius: var(--yn-radius-small);
}

.files-move-layout {
  display: grid;
  gap: 14px;
}

.files-move-summary {
  display: grid;
  gap: 6px;
  color: var(--yn-color-text-secondary);
}

.files-move-summary strong {
  color: var(--yn-color-text-primary);
  word-break: break-all;
}

.files-move-tree {
  display: grid;
  gap: 8px;
  max-height: 320px;
  overflow: auto;
}

.files-move-tree-item {
  min-height: 40px;
  border-radius: var(--yn-radius-medium);
  padding: 0 12px;
  text-align: left;
  background: var(--yn-color-surface-raised);
}

.files-move-tree-item-active {
  color: var(--yn-color-text-primary);
  background: color-mix(
    in srgb,
    var(--yn-color-primary) 12%,
    var(--yn-color-surface)
  );
}

.files-detail-drawer-mask {
  display: grid;
  place-items: center;
  padding: 20px;
}

.files-detail-modal {
  width: min(1280px, calc(100vw - 40px));
  height: min(90vh, 980px);
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 16px;
  padding: 20px;
  overflow: hidden;
  border-radius: calc(var(--yn-radius-large) + 4px);
  background: var(--yn-color-surface);
  box-shadow: var(--yn-shadow-overlay);
}

.files-detail-body {
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(340px, 420px);
  gap: 20px;
  overflow: hidden;
}

.files-detail-body-single {
  grid-template-columns: 1fr;
}

.files-preview-shell {
  display: grid;
  min-height: 320px;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background:
    linear-gradient(
      180deg,
      color-mix(in srgb, var(--yn-color-surface-raised) 82%, transparent),
      transparent
    ),
    var(--yn-color-surface-raised);
}

.files-preview-shell-media {
  align-items: center;
  justify-items: center;
  min-height: 0;
  padding: 12px;
  background: #05070b;
}

.files-preview-shell-video {
  align-self: start;
  height: auto;
  aspect-ratio: 16 / 9;
  min-height: clamp(300px, 52vh, 620px);
}

.files-preview-image,
.files-preview-media {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.files-preview-audio {
  width: 100%;
  align-self: center;
}

.files-preview-media {
  display: block;
  height: auto;
  max-height: 100%;
  background: #000;
}

.files-preview-text {
  margin: 0;
  padding: 16px;
  color: var(--yn-color-text-secondary);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  overflow: auto;
}

.files-detail-list {
  display: grid;
  gap: 10px;
  min-height: 0;
  overflow: auto;
  align-content: start;
  padding-right: 4px;
}

.files-detail-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface-raised);
}

.files-detail-row span {
  color: var(--yn-color-text-tertiary);
  font-size: 12px;
}

.files-detail-row strong {
  min-width: 0;
  color: var(--yn-color-text-primary);
  font-size: 13px;
  text-align: right;
  word-break: break-all;
}

.files-skeleton-grid {
  display: grid;
  gap: 12px;
}

.files-skeleton-card {
  min-height: 84px;
  background: color-mix(
    in srgb,
    var(--yn-color-surface-raised) 72%,
    var(--yn-color-surface)
  );
}

.files-upload-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 40;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 16px;
  border: 1px solid color-mix(in srgb, var(--yn-color-primary) 24%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--yn-color-surface) 92%, transparent);
  box-shadow: var(--yn-shadow-overlay);
  color: var(--yn-color-text-primary);
  cursor: pointer;
  backdrop-filter: blur(12px);
}

.files-upload-fab-badge {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--yn-color-primary);
  color: #fff;
  font-size: 12px;
  line-height: 20px;
  text-align: center;
}

.files-upload-panel {
  position: fixed;
  right: 24px;
  bottom: 80px;
  z-index: 40;
  width: min(360px, calc(100vw - 24px));
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: color-mix(in srgb, var(--yn-color-surface) 96%, transparent);
  box-shadow: var(--yn-shadow-overlay);
  backdrop-filter: blur(12px);
}

.files-upload-panel-list {
  display: grid;
  gap: 10px;
  max-height: 320px;
  overflow: auto;
}

.files-upload-item {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-medium);
  background: var(--yn-color-surface);
}

.files-upload-item-name {
  min-width: 0;
  color: var(--yn-color-text-primary);
  font-size: 13px;
  font-weight: 700;
  word-break: break-all;
}

.files-upload-item-percent,
.files-upload-item-meta {
  color: var(--yn-color-text-tertiary);
  font-size: 12px;
}

.files-upload-progress-track {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--yn-color-surface-raised);
}

.files-upload-progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(
    90deg,
    var(--yn-color-primary),
    color-mix(in srgb, var(--yn-color-primary) 60%, #ffffff)
  );
}

.files-upload-item-error {
  margin: 0;
  color: #dc2626;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 960px) {
  .files-browser-toolbar,
  .files-browser-pathbar,
  .files-toolbar-left,
  .files-toolbar-actions,
  .files-batch-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .files-entry-card {
    grid-template-columns: 1fr;
  }

  .files-entry-actions {
    justify-content: flex-start;
  }

  .files-detail-modal {
    width: min(820px, calc(100vw - 24px));
    height: min(92vh, 920px);
  }

  .files-share-form {
    grid-template-columns: 1fr;
  }

  .files-share-item {
    grid-template-columns: 1fr;
  }

  .files-share-item-actions {
    justify-content: flex-start;
  }

  .files-detail-body {
    grid-template-columns: 1fr;
    grid-template-rows: auto minmax(0, 1fr);
  }

  .files-preview-shell {
    min-height: 220px;
  }

  .files-preview-shell-video {
    min-height: clamp(200px, 32vh, 320px);
  }
}

@media (max-width: 640px) {
  .files-browser {
    padding: 14px;
  }

  .files-upload-fab {
    right: 12px;
    bottom: 12px;
  }

  .files-upload-panel {
    right: 12px;
    bottom: 68px;
    left: 12px;
    width: auto;
  }

  .files-detail-drawer-mask {
    padding: 12px;
  }

  .files-detail-modal {
    width: 100%;
    height: calc(100dvh - 24px);
    padding: 16px;
    gap: 12px;
  }

  .files-share-panel {
    width: 100%;
  }

  .files-preview-shell {
    min-height: 180px;
  }

  .files-preview-shell-video {
    min-height: clamp(180px, 28vh, 240px);
  }

  .files-detail-row {
    flex-direction: column;
    gap: 8px;
  }

  .files-detail-row strong {
    text-align: left;
  }
}
</style>
