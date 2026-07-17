<script setup lang="ts">
import { useToast } from "~/composables/useToast";

definePageMeta({ layout: "default" });

const toast = useToast();
const { isLoggedIn } = useAuth();

// ==================== 类型定义 ====================
interface FileItemData {
  fileUuid: string;
  name: string;
  size: number;
  fileType: string;
  fileExt: string;
  folderId: string;
  createdAt: string;
  updatedAt: string;
  isFolder: boolean;
  childCount?: number;
}

interface StorageSummary {
  usedStorage: number;
  maxTotalStorage: number;
  totalStorageUnlimited: boolean;
}

interface ShareItem {
  shareUuid: string;
  targetUuid: string;
  targetName: string;
  shareCode: string;
  needLogin: boolean;
  extractCode: string;
  allowPreview: boolean;
  maxViewCount: number;
  maxDownloadCount: number;
  viewCount: number;
  downloadCount: number;
  expireAt: string | null;
  createdAt: string;
}

interface Breadcrumb {
  id: string;
  name: string;
}

// ==================== 工具函数 ====================
function formatSize(bytes: number): string {
  if (bytes === 0) return "0 B";
  if (bytes < 0) return "--";
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + " KB";
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + " MB";
  return (bytes / 1073741824).toFixed(1) + " GB";
}

function formatDate(str: string): string {
  if (!str) return "--";
  const d = new Date(str);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  const h = String(d.getHours()).padStart(2, "0");
  const min = String(d.getMinutes()).padStart(2, "0");
  return `${y}-${m}-${day} ${h}:${min}`;
}

function getFileIcon(item: FileItemData): string {
  if (item.isFolder) return "lucide:folder";
  const ext = (item.fileExt || "").toLowerCase();
  const typeMap: Record<string, string> = {
    jpg: "lucide:image", jpeg: "lucide:image", png: "lucide:image", gif: "lucide:image", svg: "lucide:image", webp: "lucide:image", bmp: "lucide:image", ico: "lucide:image",
    mp4: "lucide:video", avi: "lucide:video", mkv: "lucide:video", mov: "lucide:video", webm: "lucide:video", flv: "lucide:video",
    mp3: "lucide:music", wav: "lucide:music", flac: "lucide:music", aac: "lucide:music", ogg: "lucide:music",
    pdf: "lucide:file-text", doc: "lucide:file-text", docx: "lucide:file-text", xls: "lucide:file-spreadsheet", xlsx: "lucide:file-spreadsheet", ppt: "lucide:presentation", pptx: "lucide:presentation",
    zip: "lucide:file-archive", rar: "lucide:file-archive", "7z": "lucide:file-archive", tar: "lucide:file-archive", gz: "lucide:file-archive",
    js: "lucide:file-code", ts: "lucide:file-code", vue: "lucide:file-code", py: "lucide:file-code", java: "lucide:file-code", go: "lucide:file-code", rs: "lucide:file-code", html: "lucide:file-code", css: "lucide:file-code", json: "lucide:file-code", xml: "lucide:file-code",
    txt: "lucide:file-text", md: "lucide:file-text",
  };
  return typeMap[ext] || "lucide:file";
}

function getFileIconColor(item: FileItemData): string {
  if (item.isFolder) return "#eab308";
  const ext = (item.fileExt || "").toLowerCase();
  if (["jpg", "jpeg", "png", "gif", "svg", "webp", "bmp", "ico"].includes(ext)) return "#ec4899";
  if (["mp4", "avi", "mkv", "mov", "webm", "flv"].includes(ext)) return "#8b5cf6";
  if (["mp3", "wav", "flac", "aac", "ogg"].includes(ext)) return "#f97316";
  if (["pdf"].includes(ext)) return "#ef4444";
  if (["zip", "rar", "7z", "tar", "gz"].includes(ext)) return "#6b7280";
  if (["js", "ts", "vue", "py", "java", "go", "rs", "html", "css", "json", "xml"].includes(ext)) return "#3b82f6";
  return "#6b7280";
}

// ==================== Mock 数据 ====================
function generateMockFiles(folderId: string): FileItemData[] {
  const baseTime = Date.now();
  const folders: FileItemData[] = [];
  const files: FileItemData[] = [];

  if (folderId === undefined || folderId === "" || folderId === null) {
    folders.push(
      { fileUuid: "mock-folder-1", name: "工作文档", size: 0, fileType: "folder", fileExt: "", folderId: "", createdAt: new Date(baseTime - 86400000 * 15).toISOString(), updatedAt: new Date(baseTime - 86400000 * 3).toISOString(), isFolder: true, childCount: 5 },
      { fileUuid: "mock-folder-2", name: "个人照片", size: 0, fileType: "folder", fileExt: "", folderId: "", createdAt: new Date(baseTime - 86400000 * 30).toISOString(), updatedAt: new Date(baseTime - 86400000 * 7).toISOString(), isFolder: true, childCount: 12 },
      { fileUuid: "mock-folder-3", name: "项目资料", size: 0, fileType: "folder", fileExt: "", folderId: "", createdAt: new Date(baseTime - 86400000 * 60).toISOString(), updatedAt: new Date(baseTime - 86400000 * 1).toISOString(), isFolder: true, childCount: 8 },
      { fileUuid: "mock-folder-4", name: "音乐收藏", size: 0, fileType: "folder", fileExt: "", folderId: "", createdAt: new Date(baseTime - 86400000 * 45).toISOString(), updatedAt: new Date(baseTime - 86400000 * 14).toISOString(), isFolder: true, childCount: 3 },
      { fileUuid: "mock-folder-5", name: "代码备份", size: 0, fileType: "folder", fileExt: "", folderId: "", createdAt: new Date(baseTime - 86400000 * 90).toISOString(), updatedAt: new Date(baseTime - 86400000 * 2).toISOString(), isFolder: true, childCount: 20 },
    );
    files.push(
      { fileUuid: "mock-file-1", name: "年度报告.pdf", size: 2458624, fileType: "application/pdf", fileExt: "pdf", folderId: "", createdAt: new Date(baseTime - 86400000 * 5).toISOString(), updatedAt: new Date(baseTime - 86400000 * 5).toISOString(), isFolder: false },
      { fileUuid: "mock-file-2", name: "封面设计.png", size: 1520640, fileType: "image/png", fileExt: "png", folderId: "", createdAt: new Date(baseTime - 86400000 * 10).toISOString(), updatedAt: new Date(baseTime - 86400000 * 8).toISOString(), isFolder: false },
      { fileUuid: "mock-file-3", name: "演示视频.mp4", size: 52428800, fileType: "video/mp4", fileExt: "mp4", folderId: "", createdAt: new Date(baseTime - 86400000 * 3).toISOString(), updatedAt: new Date(baseTime - 86400000 * 3).toISOString(), isFolder: false },
      { fileUuid: "mock-file-4", name: "app.ts", size: 4096, fileType: "text/typescript", fileExt: "ts", folderId: "", createdAt: new Date(baseTime - 86400000 * 1).toISOString(), updatedAt: new Date(baseTime - 3600000 * 2).toISOString(), isFolder: false },
      { fileUuid: "mock-file-5", name: "备份文件.zip", size: 104857600, fileType: "application/zip", fileExt: "zip", folderId: "", createdAt: new Date(baseTime - 86400000 * 20).toISOString(), updatedAt: new Date(baseTime - 86400000 * 20).toISOString(), isFolder: false },
      { fileUuid: "mock-file-6", name: "readme.md", size: 2048, fileType: "text/markdown", fileExt: "md", folderId: "", createdAt: new Date(baseTime - 86400000 * 7).toISOString(), updatedAt: new Date(baseTime - 86400000 * 6).toISOString(), isFolder: false },
      { fileUuid: "mock-file-7", name: "配置文件.json", size: 512, fileType: "application/json", fileExt: "json", folderId: "", createdAt: new Date(baseTime - 86400000 * 12).toISOString(), updatedAt: new Date(baseTime - 86400000 * 12).toISOString(), isFolder: false },
      { fileUuid: "mock-file-8", name: "心湖.mp3", size: 8388608, fileType: "audio/mpeg", fileExt: "mp3", folderId: "", createdAt: new Date(baseTime - 86400000 * 30).toISOString(), updatedAt: new Date(baseTime - 86400000 * 30).toISOString(), isFolder: false },
    );
  } else if (folderId === "mock-folder-1") {
    files.push(
      { fileUuid: "mock-file-11", name: "Q3季度总结.docx", size: 1048576, fileType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document", fileExt: "docx", folderId: "mock-folder-1", createdAt: new Date(baseTime - 86400000 * 20).toISOString(), updatedAt: new Date(baseTime - 86400000 * 18).toISOString(), isFolder: false },
      { fileUuid: "mock-file-12", name: "预算表.xlsx", size: 524288, fileType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileExt: "xlsx", folderId: "mock-folder-1", createdAt: new Date(baseTime - 86400000 * 25).toISOString(), updatedAt: new Date(baseTime - 86400000 * 25).toISOString(), isFolder: false },
      { fileUuid: "mock-file-13", name: "会议记录.txt", size: 8192, fileType: "text/plain", fileExt: "txt", folderId: "mock-folder-1", createdAt: new Date(baseTime - 86400000 * 10).toISOString(), updatedAt: new Date(baseTime - 86400000 * 10).toISOString(), isFolder: false },
      { fileUuid: "mock-file-14", name: "项目计划.pdf", size: 3670016, fileType: "application/pdf", fileExt: "pdf", folderId: "mock-folder-1", createdAt: new Date(baseTime - 86400000 * 5).toISOString(), updatedAt: new Date(baseTime - 86400000 * 2).toISOString(), isFolder: false },
      { fileUuid: "mock-file-15", name: "架构图.png", size: 3145728, fileType: "image/png", fileExt: "png", folderId: "mock-folder-1", createdAt: new Date(baseTime - 86400000 * 8).toISOString(), updatedAt: new Date(baseTime - 86400000 * 8).toISOString(), isFolder: false },
    );
  } else if (folderId === "mock-folder-2") {
    files.push(
      { fileUuid: "mock-file-21", name: "IMG_20240115.jpg", size: 4194304, fileType: "image/jpeg", fileExt: "jpg", folderId: "mock-folder-2", createdAt: new Date(baseTime - 86400000 * 40).toISOString(), updatedAt: new Date(baseTime - 86400000 * 40).toISOString(), isFolder: false },
      { fileUuid: "mock-file-22", name: "IMG_20240220.jpg", size: 5242880, fileType: "image/jpeg", fileExt: "jpg", folderId: "mock-folder-2", createdAt: new Date(baseTime - 86400000 * 35).toISOString(), updatedAt: new Date(baseTime - 86400000 * 35).toISOString(), isFolder: false },
      { fileUuid: "mock-file-23", name: "旅行照片.webp", size: 2097152, fileType: "image/webp", fileExt: "webp", folderId: "mock-folder-2", createdAt: new Date(baseTime - 86400000 * 28).toISOString(), updatedAt: new Date(baseTime - 86400000 * 28).toISOString(), isFolder: false },
    );
  } else if (folderId === "mock-folder-5") {
    files.push(
      { fileUuid: "mock-file-51", name: "index.ts", size: 2048, fileType: "text/typescript", fileExt: "ts", folderId: "mock-folder-5", createdAt: new Date(baseTime - 86400000 * 60).toISOString(), updatedAt: new Date(baseTime - 86400000 * 3).toISOString(), isFolder: false },
      { fileUuid: "mock-file-52", name: "utils.py", size: 4096, fileType: "text/x-python", fileExt: "py", folderId: "mock-folder-5", createdAt: new Date(baseTime - 86400000 * 55).toISOString(), updatedAt: new Date(baseTime - 86400000 * 1).toISOString(), isFolder: false },
      { fileUuid: "mock-file-53", name: "App.vue", size: 8192, fileType: "text/x-vue", fileExt: "vue", folderId: "mock-folder-5", createdAt: new Date(baseTime - 86400000 * 45).toISOString(), updatedAt: new Date(baseTime - 86400000 * 2).toISOString(), isFolder: false },
    );
  }
  return [...folders, ...files];
}

function generateMockStorage(): StorageSummary {
  return { usedStorage: 128 * 1024 * 1024, maxTotalStorage: 1024 * 1024 * 1024, totalStorageUnlimited: false };
}

function generateMockShares(): ShareItem[] {
  const baseTime = Date.now();
  return [
    { shareUuid: "mock-share-1", targetUuid: "mock-file-1", targetName: "年度报告.pdf", shareCode: "abc123", needLogin: false, extractCode: "", allowPreview: true, maxViewCount: 0, maxDownloadCount: 10, viewCount: 5, downloadCount: 2, expireAt: new Date(baseTime + 86400000 * 7).toISOString(), createdAt: new Date(baseTime - 86400000 * 1).toISOString() },
    { shareUuid: "mock-share-2", targetUuid: "mock-folder-1", targetName: "工作文档", shareCode: "xyz789", needLogin: true, extractCode: "1234", allowPreview: true, maxViewCount: 50, maxDownloadCount: 5, viewCount: 23, downloadCount: 3, expireAt: null, createdAt: new Date(baseTime - 86400000 * 3).toISOString() },
    { shareUuid: "mock-share-3", targetUuid: "mock-file-3", targetName: "演示视频.mp4", shareCode: "vid456", needLogin: false, extractCode: "", allowPreview: false, maxViewCount: 100, maxDownloadCount: 0, viewCount: 78, downloadCount: 0, expireAt: new Date(baseTime + 86400000 * 30).toISOString(), createdAt: new Date(baseTime - 86400000 * 5).toISOString() },
  ];
}

// ==================== 状态 ====================
const files = ref<FileItemData[]>([]);
const loading = ref(true);
const error = ref("");

const storage = ref<StorageSummary>({ usedStorage: 0, maxTotalStorage: 0, totalStorageUnlimited: false });
const currentFolderId = ref<string>("");
const breadcrumbs = ref<Breadcrumb[]>([{ id: "", name: "全部文件" }]);
const viewMode = ref<"list" | "grid">("list");
const searchQuery = ref("");
const currentPage = ref(1);
const pageSize = 20;
const totalItems = ref(0);

const selectedUuids = ref<Set<string>>(new Set());

// ==================== 弹窗状态 ====================
const showUploadDialog = ref(false);
const showNewFolderDialog = ref(false);
const showRenameDialog = ref(false);
const showShareDialog = ref(false);
const showDeleteDialog = ref(false);
const showMoveDialog = ref(false);
const showSharesPanel = ref(false);
const newFolderName = ref("");
const renameTarget = ref<FileItemData | null>(null);
const renameName = ref("");
const shareTarget = ref<FileItemData | null>(null);
const moveTargetUuids = ref<string[]>([]);
const actionTarget = ref<FileItemData | null>(null);
const deleteTargetUuids = ref<string[]>([]);

// Share form
const shareForm = ref({
  needLogin: false,
  extractCode: "",
  allowPreview: true,
  maxViewCount: 0,
  maxDownloadCount: 0,
  expireAt: "",
});

// Shares panel
const shares = ref<ShareItem[]>([]);
const sharesLoading = ref(false);

// Context menu
const contextMenu = ref({ show: false, x: 0, y: 0, item: null as FileItemData | null });
const uploadDragOver = ref(false);
const uploadFiles = ref<File[]>([]);

// Preview
const showPreview = ref(false);
const previewItem = ref<FileItemData | null>(null);
const previewLoading = ref(false);
const previewError = ref("");
const previewTextContent = ref("");
const previewUrl = ref("");

const folderTree = ref<FileItemData[]>([]);

// ==================== API 调用 ====================
async function apiGet<T>(url: string): Promise<{ code: number; data: T; msg: string }> {
  const { $fetch: _f } = useNuxtApp();
  return (_f as typeof $fetch)(url);
}

async function apiPost<T>(url: string, body?: BodyInit | Record<string, any>): Promise<{ code: number; data: T; msg: string }> {
  const { $fetch: _f } = useNuxtApp();
  return (_f as typeof $fetch)(url, { method: "POST", ...(body !== undefined ? { body } : {}) } as any);
}

async function loadFiles() {
  loading.value = true;
  error.value = "";

  if (import.meta.dev) {
    await new Promise((r) => setTimeout(r, 300));
    const all = generateMockFiles(currentFolderId.value);
    if (searchQuery.value) {
      const q = searchQuery.value.toLowerCase();
      const filtered = all.filter((f) => f.name.toLowerCase().includes(q));
      files.value = filtered;
      totalItems.value = filtered.length;
    } else {
      totalItems.value = all.length;
      const start = (currentPage.value - 1) * pageSize;
      files.value = all.slice(start, start + pageSize);
    }
    loading.value = false;
    return;
  }

  try {
    const params = new URLSearchParams();
    if (currentFolderId.value) params.set("folderId", currentFolderId.value);
    params.set("page", String(currentPage.value));
    params.set("size", String(pageSize));
    if (searchQuery.value) params.set("keyword", searchQuery.value);
    const res = await apiGet<{ records: FileItemData[]; total: number }>(`/api/file/list?${params.toString()}`);
    if (res.code === 200) {
      const records = res.data.records || [];
      files.value = records;
      totalItems.value = res.data.total || 0;
    } else {
      error.value = res.msg || "加载文件列表失败";
    }
  } catch (e: any) {
    error.value = e?.data?.msg || e?.message || "请求失败";
  } finally {
    loading.value = false;
  }
}

async function loadStorage() {
  if (import.meta.dev) {
    storage.value = generateMockStorage();
    return;
  }
  try {
    const res = await apiGet<StorageSummary>("/api/file/storage/summary");
    if (res.code === 200 && res.data) storage.value = res.data;
  } catch { /* ignore */ }
}

async function loadShares() {
  sharesLoading.value = true;
  if (import.meta.dev) {
    await new Promise((r) => setTimeout(r, 200));
    shares.value = generateMockShares();
    sharesLoading.value = false;
    return;
  }
  try {
    const res = await apiGet<{ records: ShareItem[] }>("/api/file/share/my?page=1&size=50");
    if (res.code === 200) shares.value = res.data.records || [];
  } catch { /* ignore */ }
  sharesLoading.value = false;
}

async function loadFolderTree() {
  if (import.meta.dev) {
    folderTree.value = [
      { fileUuid: "mock-folder-1", name: "工作文档", size: 0, fileType: "folder", fileExt: "", folderId: "", createdAt: "", updatedAt: "", isFolder: true },
      { fileUuid: "mock-folder-2", name: "个人照片", size: 0, fileType: "folder", fileExt: "", folderId: "", createdAt: "", updatedAt: "", isFolder: true },
      { fileUuid: "mock-folder-3", name: "项目资料", size: 0, fileType: "folder", fileExt: "", folderId: "", createdAt: "", updatedAt: "", isFolder: true },
      { fileUuid: "mock-folder-4", name: "音乐收藏", size: 0, fileType: "folder", fileExt: "", folderId: "", createdAt: "", updatedAt: "", isFolder: true },
      { fileUuid: "mock-folder-5", name: "代码备份", size: 0, fileType: "folder", fileExt: "", folderId: "", createdAt: "", updatedAt: "", isFolder: true },
    ];
    return;
  }
  try {
    const res = await apiGet<{ records: FileItemData[] }>("/api/file/list?page=1&size=1000");
    if (res.code === 200) {
      folderTree.value = (res.data.records || []).filter((f) => f.isFolder);
    }
  } catch { /* ignore */ }
}

// ==================== 导航 ====================
function navigateToFolder(id: string, name: string) {
  // 构建面包屑
  const idx = breadcrumbs.value.findIndex((b) => b.id === id);
  if (idx >= 0) {
    breadcrumbs.value = breadcrumbs.value.slice(0, idx + 1);
  } else {
    breadcrumbs.value.push({ id, name });
  }
  currentFolderId.value = id;
  currentPage.value = 1;
  selectedUuids.value.clear();
  loadFiles();
}

function navigateBreadcrumb(index: number) {
  const target = breadcrumbs.value[index];
  if (target) {
    breadcrumbs.value = breadcrumbs.value.slice(0, index + 1);
    currentFolderId.value = target.id;
    currentPage.value = 1;
    selectedUuids.value.clear();
    loadFiles();
  }
}

function handleItemClick(item: FileItemData) {
  if (item.isFolder) {
    navigateToFolder(item.fileUuid, item.name);
  }
}

// ==================== 选择 ====================
function toggleSelect(uuid: string) {
  const s = new Set(selectedUuids.value);
  if (s.has(uuid)) s.delete(uuid);
  else s.add(uuid);
  selectedUuids.value = s;
}

function toggleSelectAll() {
  if (selectedUuids.value.size === files.value.length) {
    selectedUuids.value = new Set();
  } else {
    selectedUuids.value = new Set(files.value.map((f) => f.fileUuid));
  }
}

function isSelected(uuid: string): boolean {
  return selectedUuids.value.has(uuid);
}

// ==================== 操作 ====================
async function doUpload() {
  if (uploadFiles.value.length === 0) return;
  for (const file of uploadFiles.value) {
    if (import.meta.dev) {
      await new Promise((r) => setTimeout(r, 500));
      toast.success(`${file.name} 上传成功`);
      continue;
    }
    try {
      const formData = new FormData();
      formData.append("file", file);
      if (currentFolderId.value) formData.append("folderId", currentFolderId.value);
      const res = await apiPost<{ fileUuid: string }>("/api/file/upload", formData);
      if (res.code === 200) toast.success(`${file.name} 上传成功`);
      else toast.error(`${file.name}: ${res.msg || "上传失败"}`);
    } catch (e: any) {
      toast.error(`${file.name}: ${e?.data?.msg || e?.message || "上传失败"}`);
    }
  }
  uploadFiles.value = [];
  showUploadDialog.value = false;
  loadFiles();
  loadStorage();
}

async function doCreateFolder() {
  const name = newFolderName.value.trim();
  if (!name) { toast.error("请输入文件夹名称"); return; }
  if (import.meta.dev) {
    await new Promise((r) => setTimeout(r, 200));
    toast.success(`文件夹 "${name}" 创建成功`);
    showNewFolderDialog.value = false;
    newFolderName.value = "";
    loadFiles();
    return;
  }
  try {
    // 通过上传一个空文件片段实现创建文件夹，使用单独的接口约定
    const res = await apiPost<any>("/api/file/upload", { folderId: currentFolderId.value || undefined });
    if (res.code === 200) toast.success(`文件夹 "${name}" 创建成功`);
    else toast.error(res.msg || "创建失败");
  } catch (e: any) {
    toast.error(e?.data?.msg || e?.message || "创建失败");
  }
  showNewFolderDialog.value = false;
  newFolderName.value = "";
  loadFiles();
}

async function doRename() {
  if (!renameTarget.value) return;
  const name = renameName.value.trim();
  if (!name) { toast.error("名称不能为空"); return; }
  if (name === renameTarget.value.name) { showRenameDialog.value = false; return; }
  if (import.meta.dev) {
    await new Promise((r) => setTimeout(r, 200));
    toast.success("重命名成功");
    showRenameDialog.value = false;
    loadFiles();
    return;
  }
  try {
    const res = await apiPost<any>("/api/file/rename", { fileUuid: renameTarget.value.fileUuid, newName: name });
    if (res.code === 200) toast.success("重命名成功");
    else toast.error(res.msg || "重命名失败");
  } catch (e: any) {
    toast.error(e?.data?.msg || e?.message || "重命名失败");
  }
  showRenameDialog.value = false;
  loadFiles();
}

function openRename(item: FileItemData) {
  renameTarget.value = item;
  renameName.value = item.name;
  showRenameDialog.value = true;
}

async function doDelete() {
  const uuids = deleteTargetUuids.value;
  if (uuids.length === 0) return;
  if (import.meta.dev) {
    await new Promise((r) => setTimeout(r, 300));
    toast.success(`已删除 ${uuids.length} 个项目`);
    showDeleteDialog.value = false;
    deleteTargetUuids.value = [];
    selectedUuids.value.clear();
    loadFiles();
    loadStorage();
    return;
  }
  try {
    const res = await apiPost<any>("/api/file/delete", { fileUuids: uuids });
    if (res.code === 200) toast.success(`已删除 ${uuids.length} 个项目`);
    else toast.error(res.msg || "删除失败");
  } catch (e: any) {
    toast.error(e?.data?.msg || e?.message || "删除失败");
  }
  showDeleteDialog.value = false;
  deleteTargetUuids.value = [];
  selectedUuids.value.clear();
  loadFiles();
  loadStorage();
}

function confirmDeleteSingle(item: FileItemData) {
  deleteTargetUuids.value = [item.fileUuid];
  showDeleteDialog.value = true;
}

function confirmDeleteSelected() {
  if (selectedUuids.value.size === 0) { toast.info("请先选择文件"); return; }
  deleteTargetUuids.value = Array.from(selectedUuids.value);
  showDeleteDialog.value = true;
}

async function doMove() {
  if (moveTargetUuids.value.length === 0 || !moveFolderId.value) return;
  if (import.meta.dev) {
    await new Promise((r) => setTimeout(r, 300));
    toast.success("移动成功");
    showMoveDialog.value = false;
    moveTargetUuids.value = [];
    moveFolderId.value = "";
    selectedUuids.value.clear();
    loadFiles();
    return;
  }
  try {
    const res = await apiPost<any>("/api/file/move", { fileUuids: moveTargetUuids.value, targetFolderId: moveFolderId.value });
    if (res.code === 200) toast.success("移动成功");
    else toast.error(res.msg || "移动失败");
  } catch (e: any) {
    toast.error(e?.data?.msg || e?.message || "移动失败");
  }
  showMoveDialog.value = false;
  moveTargetUuids.value = [];
  moveFolderId.value = "";
  selectedUuids.value.clear();
  loadFiles();
}

const moveFolderId = ref("");
function openMove(item: FileItemData) {
  moveTargetUuids.value = [item.fileUuid];
  moveFolderId.value = "";
  loadFolderTree();
  showMoveDialog.value = true;
}

function openMoveSelected() {
  if (selectedUuids.value.size === 0) { toast.info("请先选择文件"); return; }
  moveTargetUuids.value = Array.from(selectedUuids.value);
  moveFolderId.value = "";
  loadFolderTree();
  showMoveDialog.value = true;
}

async function doShare() {
  if (!shareTarget.value) return;
  const f = shareForm.value;
  const body: Record<string, any> = {
    targetUuids: [shareTarget.value.fileUuid],
    needLogin: f.needLogin,
    allowPreview: f.allowPreview,
  };
  if (f.extractCode.trim()) body.extractCode = f.extractCode.trim();
  if (f.maxViewCount > 0) body.maxViewCount = f.maxViewCount;
  if (f.maxDownloadCount > 0) body.maxDownloadCount = f.maxDownloadCount;
  if (f.expireAt) body.expireAt = new Date(f.expireAt).toISOString();

  if (import.meta.dev) {
    await new Promise((r) => setTimeout(r, 300));
    toast.success("分享链接创建成功");
    showShareDialog.value = false;
    return;
  }
  try {
    const res = await apiPost<any>("/api/file/share/create", body);
    if (res.code === 200) toast.success("分享链接创建成功");
    else toast.error(res.msg || "创建分享失败");
  } catch (e: any) {
    toast.error(e?.data?.msg || e?.message || "创建分享失败");
  }
  showShareDialog.value = false;
}

function openShare(item: FileItemData) {
  shareTarget.value = item;
  shareForm.value = { needLogin: false, extractCode: "", allowPreview: true, maxViewCount: 0, maxDownloadCount: 0, expireAt: "" };
  showShareDialog.value = true;
}

async function cancelShare(shareUuid: string) {
  if (import.meta.dev) {
    shares.value = shares.value.filter((s) => s.shareUuid !== shareUuid);
    toast.success("已取消分享");
    return;
  }
  try {
    const res = await apiPost<any>("/api/file/share/cancel", { shareUuid });
    if (res.code === 200) { toast.success("已取消分享"); loadShares(); }
    else toast.error(res.msg || "取消失败");
  } catch (e: any) {
    toast.error(e?.data?.msg || e?.message || "取消失败");
  }
}

async function downloadFile(item: FileItemData) {
  const url = `/api/file/download/${item.fileUuid}`;
  if (import.meta.dev) {
    toast.info(`开发模式：模拟下载 ${item.name}`);
    return;
  }
  await downloadWithAuth(url, item.name);
}

function downloadSelected() {
  if (selectedUuids.value.size === 0) { toast.info("请先选择文件"); return; }
  const items = Array.from(selectedUuids.value);
  if (import.meta.dev) {
    toast.info(`开发模式：模拟批量下载 ${items.length} 个文件`);
    return;
  }
  items.forEach((uuid) => downloadWithAuth(`/api/file/download/${uuid}`));
}

async function downloadWithAuth(url: string, filename?: string) {
  try {
    let token = "";
    try {
      const raw = localStorage.getItem("user-auth-info");
      if (raw) token = JSON.parse(raw).accessToken || "";
    } catch {}
    const res = await fetch(url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) {
      toast.error("下载失败");
      return;
    }
    const blob = await res.blob();
    const blobUrl = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = blobUrl;
    a.download = filename || "";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(blobUrl);
  } catch (e: any) {
    toast.error(e?.message || "下载失败");
  }
}

// ==================== 预览 ====================
const imageExts = ["jpg", "jpeg", "png", "gif", "svg", "webp", "bmp", "ico"];
const videoExts = ["mp4", "webm", "mov", "avi", "mkv"];
const audioExts = ["mp3", "wav", "flac", "aac", "ogg"];
const textExts = ["txt", "md", "json", "xml", "yaml", "yml", "csv", "log", "sql", "sh", "bat", "ini", "cfg", "env"];
const codeExts = ["js", "ts", "vue", "py", "java", "go", "rs", "c", "cpp", "h", "html", "css", "scss", "less", "php", "rb", "swift", "kt"];

function isFilePreviewable(item: FileItemData): boolean {
  if (item.isFolder) return false;
  const ext = (item.fileExt || "").toLowerCase();
  return [...imageExts, ...videoExts, ...audioExts, "pdf", ...textExts, ...codeExts].includes(ext);
}

function getPreviewType(item: FileItemData): "image" | "video" | "audio" | "pdf" | "text" | "other" {
  const ext = (item.fileExt || "").toLowerCase();
  if (imageExts.includes(ext)) return "image";
  if (videoExts.includes(ext)) return "video";
  if (audioExts.includes(ext)) return "audio";
  if (ext === "pdf") return "pdf";
  if (textExts.includes(ext) || codeExts.includes(ext)) return "text";
  return "other";
}

async function openPreview(item: FileItemData) {
  if (item.isFolder || !isFilePreviewable(item)) return;
  previewItem.value = item;
  previewLoading.value = true;
  previewError.value = "";
  previewTextContent.value = "";
  previewUrl.value = "";
  showPreview.value = true;

  const type = getPreviewType(item);
  const ext = (item.fileExt || "").toLowerCase();

  if (import.meta.dev) {
    // Dev mode 模拟不同文件类型预览
    await new Promise((r) => setTimeout(r, 400));
    if (type === "image") {
      // 使用占位图生成图片预览
      previewUrl.value = `https://coresg-normal.trae.ai/api/ide/v1/text_to_image?prompt=${encodeURIComponent("A beautiful landscape photo")}&image_size=landscape_16_9`;
    } else if (type === "video") {
      previewUrl.value = "https://www.w3schools.com/html/mov_bbb.mp4";
    } else if (type === "audio") {
      previewUrl.value = "https://www.w3schools.com/html/horse.mp3";
    } else if (type === "text") {
      previewTextContent.value = generateMockTextContent(item);
    } else if (type === "pdf") {
      previewUrl.value = "about:blank";
      previewTextContent.value = "PDF 文件";
    }
    previewLoading.value = false;
    return;
  }

  // 生产环境：通过后端预览/下载接口获取（携带认证 Token）
  try {
    // 读取 Token
    let token = "";
    try {
      const raw = localStorage.getItem("user-auth-info");
      if (raw) token = JSON.parse(raw).accessToken || "";
    } catch {}
    const authHeaders: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {};

    if (["image", "video", "audio", "pdf"].includes(type)) {
      const res = await fetch(`/api/file/download/${item.fileUuid}`, { headers: authHeaders });
      if (!res.ok) { previewError.value = "加载预览失败"; previewLoading.value = false; return; }
      const blob = await res.blob();
      previewUrl.value = URL.createObjectURL(blob);
    } else if (type === "text") {
      const res = await fetch(`/api/file/download/${item.fileUuid}`, { headers: authHeaders });
      if (!res.ok) { previewError.value = "加载预览失败"; previewLoading.value = false; return; }
      previewTextContent.value = await res.text();
    } else {
      previewError.value = "该文件类型暂不支持预览";
    }
  } catch (e: any) {
    previewError.value = e?.message || "加载预览失败";
  }
  previewLoading.value = false;
}

function closePreview() {
  showPreview.value = false;
  previewItem.value = null;
  previewUrl.value = "";
  previewTextContent.value = "";
  previewError.value = "";
}

function generateMockTextContent(item: FileItemData): string {
  const ext = (item.fileExt || "").toLowerCase();
  const lines = [
    `// ${item.name}`,
    `// 文件大小: ${formatSize(item.size)}`,
    `// 最后修改: ${formatDate(item.updatedAt)}`,
    ``,
  ];
  if (codeExts.includes(ext)) {
    lines.push(`// 这是 ${item.name} 的代码文件预览内容`,
      ``,
      `import { defineComponent } from 'vue';`,
      ``,
      `export default defineComponent({`,
      `  name: 'App',`,
      `  setup() {`,
      `    const message = ref('Hello YunaNexus!');`,
      `    return { message };`,
      `  },`,
      `});`,
      ``,
      `// ... 更多内容请在线上查看`,
    );
  } else {
    lines.push(`这是 ${item.name} 的文本文件预览内容。`,
      ``,
      `YunaNexus 是一个企业级 B2B OAuth2 平台，`,
      `提供安全、高效的身份认证与授权服务。`,
    );
  }
  return lines.join("\n");
}

// ==================== Search ====================
let searchDebounce: ReturnType<typeof setTimeout> | null = null;
function onSearchInput() {
  if (searchDebounce) clearTimeout(searchDebounce);
  searchDebounce = setTimeout(() => {
    currentPage.value = 1;
    loadFiles();
  }, 400);
}

// ==================== Upload handlers ====================
function onUploadDialogOpen() {
  uploadFiles.value = [];
  showUploadDialog.value = true;
}

function onFileInputChange(e: Event) {
  const target = e.target as HTMLInputElement;
  if (target.files) {
    for (let i = 0; i < target.files.length; i++) {
      const f = target.files[i];
      if (f) uploadFiles.value.push(f);
    }
  }
  target.value = "";
}

function removeUploadFile(index: number) {
  uploadFiles.value.splice(index, 1);
}

function onDragOver(e: DragEvent) {
  e.preventDefault();
  uploadDragOver.value = true;
}

function onDragLeave() {
  uploadDragOver.value = false;
}

function onDrop(e: DragEvent) {
  e.preventDefault();
  uploadDragOver.value = false;
  if (e.dataTransfer?.files) {
    for (let i = 0; i < e.dataTransfer.files.length; i++) {
      const f = e.dataTransfer.files[i];
      if (f) uploadFiles.value.push(f);
    }
  }
}

// ==================== Context Menu ====================
function onContextMenu(e: MouseEvent, item: FileItemData) {
  e.preventDefault();
  contextMenu.value = { show: true, x: e.clientX, y: e.clientY, item };
  selectedUuids.value.clear();
  selectedUuids.value.add(item.fileUuid);
}

function closeContextMenu() {
  contextMenu.value.show = false;
}

function contextAction(action: string) {
  const item = contextMenu.value.item;
  closeContextMenu();
  if (!item) return;
  switch (action) {
    case "preview": if (item && isFilePreviewable(item)) openPreview(item); break;
    case "download": downloadFile(item); break;
    case "share": openShare(item); break;
    case "rename": openRename(item); break;
    case "move": openMove(item); break;
    case "delete": confirmDeleteSingle(item); break;
  }
}

// ==================== 分页 ====================
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize)));
function goToPage(page: number) {
  if (page < 1 || page > totalPages.value) return;
  currentPage.value = page;
  loadFiles();
}

// ==================== 工具栏操作 ====================
function batchDownload() { downloadSelected(); }
function batchMove() { openMoveSelected(); }
function batchDelete() { confirmDeleteSelected(); }

// ==================== 初始化 ====================
onMounted(() => {
  if (isLoggedIn.value) {
    loadFiles();
    loadStorage();
  }
});

// 监听点击关闭右键菜单
if (import.meta.client) {
  onMounted(() => {
    document.addEventListener("click", closeContextMenu);
  });
  onUnmounted(() => {
    document.removeEventListener("click", closeContextMenu);
  });
}

// ==================== 响应式计算 ====================
const storagePercent = computed(() => {
  if (storage.value.totalStorageUnlimited || storage.value.maxTotalStorage <= 0) return 0;
  return Math.min(100, (storage.value.usedStorage / storage.value.maxTotalStorage) * 100);
});

const storageColor = computed(() => {
  const p = storagePercent.value;
  if (p > 90) return "var(--color-error)";
  if (p > 70) return "var(--color-warning)";
  return "var(--color-emphasis)";
});

const folders = computed(() => files.value.filter((f) => f.isFolder));
const fileItems = computed(() => files.value.filter((f) => !f.isFolder));
</script>

<template>
  <div class="apps-page files-page" @click="closeContextMenu">
    <!-- ========== 页面标题 ========== -->
    <section class="page-header fade-up">
      <div class="page-header-left">
        <div class="page-header-overline">Files</div>
        <h1 class="page-header-title">文件管理</h1>
        <p class="page-header-description">管理您上传的文件，支持文件夹、分享和批量操作</p>
      </div>
      <div class="files-header-actions">
        <button class="button" @click="showSharesPanel = !showSharesPanel">
          <Icon name="lucide:link" size="15" />
          <span class="hide-mobile">我的分享</span>
        </button>
      </div>
    </section>

    <!-- ========== 存储空间栏 ========== -->
    <section class="panel-card fade-up files-storage-card">
      <div class="files-storage-header">
        <div class="files-storage-title">
          <Icon name="lucide:hard-drive" size="16" />
          <span>存储空间</span>
        </div>
        <div class="files-storage-used">
          {{ formatSize(storage.usedStorage) }}
          <span class="files-storage-sep">/</span>
          {{ storage.totalStorageUnlimited ? "无限制" : formatSize(storage.maxTotalStorage) }}
        </div>
      </div>
      <div v-if="!storage.totalStorageUnlimited" class="files-storage-bar">
        <div
          class="files-storage-bar-fill"
          :style="{ width: storagePercent + '%', background: storageColor }"
        />
      </div>
      <div v-if="!storage.totalStorageUnlimited" class="files-storage-percent">
        {{ storagePercent.toFixed(1) }}% 已使用
      </div>
    </section>

    <!-- ========== 面包屑导航 ========== -->
    <nav class="files-breadcrumb fade-up">
      <button
        v-for="(crumb, idx) in breadcrumbs"
        :key="crumb.id"
        class="files-breadcrumb-item"
        :class="{ active: idx === breadcrumbs.length - 1 }"
        @click="navigateBreadcrumb(idx)"
      >
        <Icon v-if="idx === 0" name="lucide:home" size="13" />
        <span>{{ crumb.name }}</span>
        <Icon v-if="idx < breadcrumbs.length - 1" name="lucide:chevron-right" size="12" class="files-breadcrumb-arrow" />
      </button>
    </nav>

    <!-- ========== 工具栏 ========== -->
    <div class="files-toolbar fade-up">
      <div class="files-toolbar-left">
        <button class="button button-primary button-small" @click="onUploadDialogOpen">
          <Icon name="lucide:upload" size="14" />
          <span class="hide-mobile">上传文件</span>
        </button>
        <button class="button button-small" @click="showNewFolderDialog = true">
          <Icon name="lucide:folder-plus" size="14" />
          <span class="hide-mobile">新建文件夹</span>
        </button>
        <div v-if="selectedUuids.size > 0" class="files-batch-actions">
          <span class="files-batch-count">已选 {{ selectedUuids.size }} 项</span>
          <button class="button button-small" @click="batchDownload">
            <Icon name="lucide:download" size="14" />
          </button>
          <button class="button button-small" @click="batchMove">
            <Icon name="lucide:folder-input" size="14" />
          </button>
          <button class="button button-small button-danger" @click="batchDelete">
            <Icon name="lucide:trash-2" size="14" />
          </button>
        </div>
      </div>
      <div class="files-toolbar-right">
        <div class="files-search">
          <Icon name="lucide:search" size="14" class="files-search-icon" />
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索文件..."
            class="files-search-input"
            @input="onSearchInput"
          />
          <button
            v-if="searchQuery"
            class="files-search-clear"
            @click="searchQuery = ''; currentPage = 1; loadFiles()"
          >
            <Icon name="lucide:x" size="13" />
          </button>
        </div>
        <div class="files-view-toggle">
          <button
            class="files-view-btn"
            :class="{ active: viewMode === 'list' }"
            @click="viewMode = 'list'"
            title="列表视图"
          >
            <Icon name="lucide:list" size="15" />
          </button>
          <button
            class="files-view-btn"
            :class="{ active: viewMode === 'grid' }"
            @click="viewMode = 'grid'"
            title="网格视图"
          >
            <Icon name="lucide:layout-grid" size="15" />
          </button>
        </div>
      </div>
    </div>

    <!-- ========== 我的分享面板 ========== -->
    <div v-if="showSharesPanel" class="panel-card fade-up">
      <div class="panel-card-header" style="display: flex; align-items: center; justify-content: space-between; padding: 18px 24px 0;">
        <span>我的分享</span>
        <button class="button button-ghost button-small" @click="showSharesPanel = false">
          <Icon name="lucide:x" size="14" />
        </button>
      </div>
      <div class="panel-card-body" style="padding-top: 12px;">
        <div v-if="sharesLoading" class="files-empty">加载中…</div>
        <div v-else-if="shares.length === 0" class="files-empty">暂无分享链接</div>
        <div v-else class="files-shares-list">
          <div v-for="share in shares" :key="share.shareUuid" class="files-share-item">
            <div class="files-share-item-icon">
              <Icon name="lucide:link" size="14" />
            </div>
            <div class="files-share-item-info">
              <div class="files-share-item-name">{{ share.targetName }}</div>
              <div class="files-share-item-meta">
                <span>分享码: {{ share.shareCode }}</span>
                <span v-if="share.extractCode">提取码: {{ share.extractCode }}</span>
                <span v-if="share.expireAt">到期: {{ formatDate(share.expireAt) }}</span>
                <span v-else>永久有效</span>
                <span>{{ share.viewCount }}/{{ share.maxViewCount || '∞' }} 浏览</span>
                <span>{{ share.downloadCount }}/{{ share.maxDownloadCount || '∞' }} 下载</span>
              </div>
            </div>
            <button class="button button-small button-danger" @click="cancelShare(share.shareUuid)">取消</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== 加载状态 ========== -->
    <div v-if="loading" class="panel-card fade-up">
      <div class="panel-card-body" style="text-align: center; padding: 64px 24px; color: var(--color-font-assist)">
        <Icon name="lucide:loader-2" size="32" style="animation: spin 1s linear infinite; margin-bottom: 12px;" />
        <p>加载中…</p>
      </div>
    </div>

    <!-- ========== 错误状态 ========== -->
    <div v-else-if="error" class="panel-card fade-up">
      <div class="panel-card-body" style="text-align: center; padding: 48px 24px; color: var(--color-font-assist)">
        <Icon name="lucide:alert-circle" size="48" style="margin-bottom: 16px; opacity: 0.3" />
        <p style="font-size: 16px; margin-bottom: 8px; color: var(--color-error)">加载失败</p>
        <p style="font-size: 13px; margin-bottom: 16px;">{{ error }}</p>
        <button class="button button-primary" @click="loadFiles">重试</button>
      </div>
    </div>

    <!-- ========== 空状态 ========== -->
    <div v-else-if="files.length === 0" class="panel-card fade-up">
      <div class="panel-card-body" style="text-align: center; padding: 64px 24px; color: var(--color-font-assist)">
        <Icon name="lucide:folder-open" size="48" style="margin-bottom: 16px; opacity: 0.3" />
        <p style="font-size: 16px; margin-bottom: 8px;">
          {{ searchQuery ? `没有找到 "${searchQuery}" 相关文件` : "此文件夹为空" }}
        </p>
        <p style="font-size: 13px; margin-bottom: 16px;">拖拽文件到此处或点击上传按钮开始</p>
        <button class="button button-primary" @click="onUploadDialogOpen">
          <Icon name="lucide:upload" size="15" />
          上传文件
        </button>
      </div>
    </div>

    <!-- ========== 列表视图 ========== -->
    <div v-else-if="viewMode === 'list'" class="panel-card fade-up files-table-card">
      <div class="files-table-wrapper">
        <table class="files-table">
          <thead>
            <tr>
              <th class="files-th-check">
                <input
                  type="checkbox"
                  :checked="selectedUuids.size === files.length && files.length > 0"
                  @change="toggleSelectAll"
                />
              </th>
              <th class="files-th-name">名称</th>
              <th class="files-th-size">大小</th>
              <th class="files-th-date">修改时间</th>
              <th class="files-th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in files"
              :key="item.fileUuid"
              class="files-row"
              :class="{ selected: isSelected(item.fileUuid) }"
              @contextmenu="onContextMenu($event, item)"
            >
              <td class="files-td-check" @click.stop="toggleSelect(item.fileUuid)">
                <input
                  type="checkbox"
                  :checked="isSelected(item.fileUuid)"
                  @click.stop="toggleSelect(item.fileUuid)"
                />
              </td>
              <td class="files-td-name">
                <div
                  class="files-name-cell"
                  :class="{ clickable: item.isFolder || isFilePreviewable(item) }"
                  @click="item.isFolder ? handleItemClick(item) : isFilePreviewable(item) ? openPreview(item) : undefined"
                >
                  <Icon
                    :name="getFileIcon(item)"
                    :size="item.isFolder ? 18 : 16"
                    :style="{ color: getFileIconColor(item), flexShrink: 0 }"
                  />
                  <span class="files-name-text">{{ item.name }}</span>
                </div>
              </td>
              <td class="files-td-size">
                <span v-if="item.isFolder" class="files-folder-size">{{ item.childCount ?? 0 }} 项</span>
                <span v-else class="files-file-size">{{ formatSize(item.size) }}</span>
              </td>
              <td class="files-td-date">{{ formatDate(item.updatedAt) }}</td>
              <td class="files-td-actions">
                <div class="files-row-actions">
                  <button v-if="!item.isFolder && isFilePreviewable(item)" class="files-action-btn" title="预览" @click.stop="openPreview(item)">
                    <Icon name="lucide:eye" size="14" />
                  </button>
                  <button v-if="!item.isFolder" class="files-action-btn" title="下载" @click.stop="downloadFile(item)">
                    <Icon name="lucide:download" size="14" />
                  </button>
                  <button class="files-action-btn" title="分享" @click.stop="openShare(item)">
                    <Icon name="lucide:share-2" size="14" />
                  </button>
                  <button class="files-action-btn" title="重命名" @click.stop="openRename(item)">
                    <Icon name="lucide:pencil" size="14" />
                  </button>
                  <button class="files-action-btn" title="移动" @click.stop="openMove(item)">
                    <Icon name="lucide:folder-input" size="14" />
                  </button>
                  <button class="files-action-btn files-action-btn-danger" title="删除" @click.stop="confirmDeleteSingle(item)">
                    <Icon name="lucide:trash-2" size="14" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ========== 网格视图 ========== -->
    <div v-else-if="viewMode === 'grid'" class="files-grid fade-up">
      <div
        v-for="item in files"
        :key="item.fileUuid"
        class="files-grid-item"
        :class="{ selected: isSelected(item.fileUuid) }"
        @click="item.isFolder ? handleItemClick(item) : isFilePreviewable(item) ? openPreview(item) : undefined"
        @contextmenu="onContextMenu($event, item)"
      >
        <div class="files-grid-item-check" @click.stop="toggleSelect(item.fileUuid)">
          <input type="checkbox" :checked="isSelected(item.fileUuid)" />
        </div>
        <div class="files-grid-item-icon">
          <Icon
            :name="getFileIcon(item)"
            :size="item.isFolder ? 36 : 32"
            :style="{ color: getFileIconColor(item) }"
          />
        </div>
        <div class="files-grid-item-name" :title="item.name">{{ item.name }}</div>
        <div class="files-grid-item-meta">
          <template v-if="item.isFolder">{{ item.childCount ?? 0 }} 项</template>
          <template v-else>{{ formatSize(item.size) }}</template>
        </div>
        <div class="files-grid-item-actions">
          <button v-if="!item.isFolder" class="files-action-btn" title="下载" @click.stop="downloadFile(item)">
            <Icon name="lucide:download" size="13" />
          </button>
          <button class="files-action-btn" title="分享" @click.stop="openShare(item)">
            <Icon name="lucide:share-2" size="13" />
          </button>
          <button class="files-action-btn" title="更多" @click.stop="onContextMenu($event, item)">
            <Icon name="lucide:ellipsis-vertical" size="13" />
          </button>
        </div>
      </div>
    </div>

    <!-- ========== 分页 ========== -->
    <div v-if="!loading && totalPages > 1" class="files-pagination fade-up">
      <button class="button button-small" :disabled="currentPage <= 1" @click="goToPage(currentPage - 1)">
        <Icon name="lucide:chevron-left" size="14" />
      </button>
      <template v-for="p in totalPages" :key="p">
        <button
          v-if="p === 1 || p === totalPages || (p >= currentPage - 2 && p <= currentPage + 2)"
          class="files-page-btn"
          :class="{ active: p === currentPage }"
          @click="goToPage(p)"
        >
          {{ p }}
        </button>
        <span v-else-if="p === currentPage - 3 || p === currentPage + 3" class="files-page-ellipsis">…</span>
      </template>
      <button class="button button-small" :disabled="currentPage >= totalPages" @click="goToPage(currentPage + 1)">
        <Icon name="lucide:chevron-right" size="14" />
      </button>
    </div>

    <!-- ========== 上传弹窗 ========== -->
    <Teleport to="body">
      <div v-if="showUploadDialog" class="files-overlay" @click.self="showUploadDialog = false">
        <div class="files-dialog files-dialog-upload">
          <div class="files-dialog-header">
            <h3>上传文件</h3>
            <button class="files-dialog-close" @click="showUploadDialog = false">
              <Icon name="lucide:x" size="18" />
            </button>
          </div>
          <div
            class="files-upload-dropzone"
            :class="{ dragover: uploadDragOver }"
            @dragover="onDragOver"
            @dragleave="onDragLeave"
            @drop="onDrop"
          >
            <Icon name="lucide:upload-cloud" size="40" style="opacity: 0.3; margin-bottom: 12px;" />
            <p>拖拽文件到此处</p>
            <p style="font-size: 12px; color: var(--color-font-assist); margin-top: 4px;">或</p>
            <label class="button button-primary" style="margin-top: 12px; cursor: pointer;">
              选择文件
              <input type="file" multiple style="display: none;" @change="onFileInputChange" />
            </label>
          </div>
          <div v-if="uploadFiles.length > 0" class="files-upload-list">
            <div v-for="(file, idx) in uploadFiles" :key="idx" class="files-upload-item">
              <Icon name="lucide:file" size="14" style="flex-shrink: 0; color: var(--color-font-assist);" />
              <span class="files-upload-name">{{ file.name }}</span>
              <span class="files-upload-size">{{ formatSize(file.size) }}</span>
              <button class="files-upload-remove" @click="removeUploadFile(idx)">
                <Icon name="lucide:x" size="12" />
              </button>
            </div>
          </div>
          <div class="files-dialog-footer">
            <button class="button" @click="showUploadDialog = false; uploadFiles = []">取消</button>
            <button class="button button-primary" :disabled="uploadFiles.length === 0" @click="doUpload">
              上传 ({{ uploadFiles.length }})
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ========== 新建文件夹弹窗 ========== -->
    <Teleport to="body">
      <div v-if="showNewFolderDialog" class="files-overlay" @click.self="showNewFolderDialog = false; newFolderName = ''">
        <div class="files-dialog">
          <div class="files-dialog-header">
            <h3>新建文件夹</h3>
            <button class="files-dialog-close" @click="showNewFolderDialog = false; newFolderName = ''">
              <Icon name="lucide:x" size="18" />
            </button>
          </div>
          <div class="files-dialog-body">
            <label class="auth-field">
              <span>文件夹名称</span>
              <input v-model="newFolderName" placeholder="输入文件夹名称" @keyup.enter="doCreateFolder" />
            </label>
          </div>
          <div class="files-dialog-footer">
            <button class="button" @click="showNewFolderDialog = false; newFolderName = ''">取消</button>
            <button class="button button-primary" @click="doCreateFolder">创建</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ========== 重命名弹窗 ========== -->
    <Teleport to="body">
      <div v-if="showRenameDialog && renameTarget" class="files-overlay" @click.self="showRenameDialog = false">
        <div class="files-dialog">
          <div class="files-dialog-header">
            <h3>重命名</h3>
            <button class="files-dialog-close" @click="showRenameDialog = false">
              <Icon name="lucide:x" size="18" />
            </button>
          </div>
          <div class="files-dialog-body">
            <label class="auth-field">
              <span>新名称</span>
              <input v-model="renameName" placeholder="输入新名称" @keyup.enter="doRename" />
            </label>
          </div>
          <div class="files-dialog-footer">
            <button class="button" @click="showRenameDialog = false">取消</button>
            <button class="button button-primary" :disabled="!renameName.trim()" @click="doRename">确认</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ========== 分享弹窗 ========== -->
    <Teleport to="body">
      <div v-if="showShareDialog && shareTarget" class="files-overlay" @click.self="showShareDialog = false">
        <div class="files-dialog files-dialog-wide">
          <div class="files-dialog-header">
            <h3>创建分享 - {{ shareTarget.name }}</h3>
            <button class="files-dialog-close" @click="showShareDialog = false">
              <Icon name="lucide:x" size="18" />
            </button>
          </div>
          <div class="files-dialog-body" style="display: grid; gap: 14px;">
            <label class="files-share-option">
              <div class="files-share-option-left">
                <Icon name="lucide:lock" size="14" />
                <span>需要登录</span>
              </div>
              <input type="checkbox" v-model="shareForm.needLogin" />
            </label>
            <div class="auth-field">
              <span>提取码（可选）</span>
              <input v-model="shareForm.extractCode" placeholder="留空则不设置提取码" maxlength="10" />
            </div>
            <label class="files-share-option">
              <div class="files-share-option-left">
                <Icon name="lucide:eye" size="14" />
                <span>允许预览</span>
              </div>
              <input type="checkbox" v-model="shareForm.allowPreview" />
            </label>
            <div class="auth-field">
              <span>最大浏览次数（0 = 不限制）</span>
              <input v-model.number="shareForm.maxViewCount" type="number" min="0" placeholder="0" />
            </div>
            <div class="auth-field">
              <span>最大下载次数（0 = 不限制）</span>
              <input v-model.number="shareForm.maxDownloadCount" type="number" min="0" placeholder="0" />
            </div>
            <div class="auth-field">
              <span>过期时间（可选）</span>
              <input v-model="shareForm.expireAt" type="datetime-local" />
            </div>
          </div>
          <div class="files-dialog-footer">
            <button class="button" @click="showShareDialog = false">取消</button>
            <button class="button button-primary" @click="doShare">创建分享</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ========== 删除确认弹窗 ========== -->
    <Teleport to="body">
      <div v-if="showDeleteDialog" class="files-overlay" @click.self="showDeleteDialog = false">
        <div class="files-dialog">
          <div class="files-dialog-header">
            <h3>确认删除</h3>
            <button class="files-dialog-close" @click="showDeleteDialog = false">
              <Icon name="lucide:x" size="18" />
            </button>
          </div>
          <div class="files-dialog-body" style="text-align: center;">
            <Icon name="lucide:alert-triangle" size="36" style="color: var(--color-warning); margin-bottom: 12px;" />
            <p>确定要删除选中的 {{ deleteTargetUuids.length }} 个项目吗？</p>
            <p style="font-size: 12px; color: var(--color-font-assist); margin-top: 6px;">此操作将移入回收站，可在回收站中恢复</p>
          </div>
          <div class="files-dialog-footer">
            <button class="button" @click="showDeleteDialog = false">取消</button>
            <button class="button button-primary" style="background: var(--color-error);" @click="doDelete">确认删除</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ========== 移动到弹窗 ========== -->
    <Teleport to="body">
      <div v-if="showMoveDialog" class="files-overlay" @click.self="showMoveDialog = false">
        <div class="files-dialog">
          <div class="files-dialog-header">
            <h3>移动到文件夹</h3>
            <button class="files-dialog-close" @click="showMoveDialog = false">
              <Icon name="lucide:x" size="18" />
            </button>
          </div>
          <div class="files-dialog-body">
            <p style="font-size: 13px; color: var(--color-font-secondary); margin-bottom: 12px;">
              移动 {{ moveTargetUuids.length }} 个项目到：
            </p>
            <div class="files-move-list">
              <button
                class="files-move-item"
                :class="{ active: moveFolderId === '' }"
                @click="moveFolderId = ''"
              >
                <Icon name="lucide:home" size="15" />
                <span>根目录</span>
              </button>
              <button
                v-for="folder in folderTree"
                :key="folder.fileUuid"
                class="files-move-item"
                :class="{ active: moveFolderId === folder.fileUuid }"
                @click="moveFolderId = folder.fileUuid"
              >
                <Icon name="lucide:folder" size="15" style="color: #eab308;" />
                <span>{{ folder.name }}</span>
              </button>
            </div>
          </div>
          <div class="files-dialog-footer">
            <button class="button" @click="showMoveDialog = false">取消</button>
            <button class="button button-primary" @click="doMove">移动到此处</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ========== 文件预览弹窗 ========== -->
    <Teleport to="body">
      <div v-if="showPreview" class="files-overlay" @click.self="closePreview">
        <div class="files-dialog files-preview-dialog">
          <div class="files-dialog-header">
            <h3>
              <Icon :name="previewItem ? getFileIcon(previewItem) : 'lucide:file'" :size="16" :style="{ color: previewItem ? getFileIconColor(previewItem) : undefined }" />
              {{ previewItem?.name || "预览" }}
            </h3>
            <div class="files-preview-header-actions">
              <button v-if="previewItem && !previewItem.isFolder" class="button button-small" title="下载" @click="downloadFile(previewItem!)">
                <Icon name="lucide:download" size="14" />
              </button>
              <button class="files-dialog-close" @click="closePreview">
                <Icon name="lucide:x" size="18" />
              </button>
            </div>
          </div>
          <div class="files-preview-body">
            <!-- 加载中 -->
            <div v-if="previewLoading" class="files-preview-placeholder">
              <Icon name="lucide:loader-2" size="32" style="animation: spin 1s linear infinite; opacity: 0.5;" />
              <span>加载预览中…</span>
            </div>
            <!-- 错误 -->
            <div v-else-if="previewError" class="files-preview-placeholder">
              <Icon name="lucide:alert-circle" size="32" style="opacity: 0.3;" />
              <span>{{ previewError }}</span>
            </div>
            <!-- 不支持的格式 -->
            <div v-else-if="previewItem && !isFilePreviewable(previewItem!)" class="files-preview-placeholder">
              <Icon :name="previewItem ? getFileIcon(previewItem) : 'lucide:file'" size="40" :style="{ color: previewItem ? getFileIconColor(previewItem) : undefined, opacity: 0.5 }" />
              <span>该文件类型暂不支持预览</span>
            </div>
            <!-- 图片预览 -->
            <div v-else-if="previewItem && getPreviewType(previewItem) === 'image'" class="files-preview-media">
              <img :src="previewUrl || `/api/file/download/${previewItem.fileUuid}`" :alt="previewItem.name" />
            </div>
            <!-- 视频预览 -->
            <div v-else-if="previewItem && getPreviewType(previewItem) === 'video'" class="files-preview-media">
              <video controls autoplay :src="previewUrl || `/api/file/download/${previewItem.fileUuid}`" />
            </div>
            <!-- 音频预览 -->
            <div v-else-if="previewItem && getPreviewType(previewItem) === 'audio'" class="files-preview-media files-preview-audio">
              <Icon :name="getFileIcon(previewItem)" :size="48" :style="{ color: getFileIconColor(previewItem), marginBottom: '16px', opacity: 0.5 }" />
              <span style="margin-bottom: 12px;">{{ previewItem.name }}</span>
              <audio controls autoplay :src="previewUrl || `/api/file/download/${previewItem.fileUuid}`" />
            </div>
            <!-- PDF 预览 -->
            <div v-else-if="previewItem && getPreviewType(previewItem) === 'pdf'" class="files-preview-media">
              <iframe :src="previewUrl || `/api/file/download/${previewItem.fileUuid}`" frameborder="0" />
            </div>
            <!-- 文本/代码预览 -->
            <div v-else-if="previewItem && getPreviewType(previewItem) === 'text'" class="files-preview-text">
              <pre><code>{{ previewTextContent }}</code></pre>
            </div>
          </div>
          <div class="files-dialog-footer">
            <div class="files-preview-meta" v-if="previewItem">
              <span>{{ formatSize(previewItem.size) }}</span>
              <span>{{ previewItem.fileExt.toUpperCase() }}</span>
              <span>{{ formatDate(previewItem.updatedAt) }}</span>
            </div>
            <div class="files-preview-footer-actions">
              <button class="button" @click="closePreview">关闭</button>
              <button v-if="previewItem && !previewItem.isFolder" class="button button-primary" @click="downloadFile(previewItem!)">
                <Icon name="lucide:download" size="14" />
                下载
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ========== 右键菜单 ========== -->
    <Teleport to="body">
      <div
        v-if="contextMenu.show"
        class="files-context-menu"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
        @click.stop
      >
        <button class="files-context-item" @click="contextAction('preview')">
          <Icon name="lucide:eye" size="14" />
          <span>预览</span>
        </button>
        <button class="files-context-item" @click="contextAction('download')">
          <Icon name="lucide:download" size="14" />
          <span>下载</span>
        </button>
        <button class="files-context-item" @click="contextAction('share')">
          <Icon name="lucide:share-2" size="14" />
          <span>分享</span>
        </button>
        <button class="files-context-item" @click="contextAction('rename')">
          <Icon name="lucide:pencil" size="14" />
          <span>重命名</span>
        </button>
        <button class="files-context-item" @click="contextAction('move')">
          <Icon name="lucide:folder-input" size="14" />
          <span>移动到</span>
        </button>
        <div class="files-context-divider" />
        <button class="files-context-item files-context-item-danger" @click="contextAction('delete')">
          <Icon name="lucide:trash-2" size="14" />
          <span>删除</span>
        </button>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* ========== 页面布局 ========== */
.files-page {
  gap: 16px;
}

.files-header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

/* ========== 存储空间卡片 ========== */
.files-storage-card {
  padding: 18px 22px;
}

.files-storage-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.files-storage-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-font-secondary);
}

.files-storage-used {
  font-size: 13px;
  font-family: var(--font-mono);
  color: var(--color-font);
}

.files-storage-sep {
  color: var(--color-font-assist);
  margin: 0 4px;
}

.files-storage-bar {
  height: 6px;
  border-radius: 999px;
  background: var(--color-primary-background);
  overflow: hidden;
}

.files-storage-bar-fill {
  height: 100%;
  border-radius: inherit;
  transition: width 0.4s ease;
}

.files-storage-percent {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-font-assist);
  text-align: right;
}

/* ========== 面包屑 ========== */
.files-breadcrumb {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-wrap: wrap;
}

.files-breadcrumb-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--color-font-secondary);
  background: none;
  border: none;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.1s;
}

.files-breadcrumb-item:hover {
  color: var(--color-emphasis);
  background: var(--color-emphasis-soft);
}

.files-breadcrumb-item.active {
  color: var(--color-font);
  font-weight: 500;
}

.files-breadcrumb-arrow {
  color: var(--color-font-assist);
}

/* ========== 工具栏 ========== */
.files-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.files-toolbar-left,
.files-toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.files-batch-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-left: 12px;
  border-left: 1px solid var(--color-border);
}

.files-batch-count {
  font-size: 12px;
  color: var(--color-font-assist);
  font-family: var(--font-mono);
}

/* ========== 搜索 ========== */
.files-search {
  position: relative;
  display: flex;
  align-items: center;
}

.files-search-icon {
  position: absolute;
  left: 10px;
  color: var(--color-font-assist);
  pointer-events: none;
}

.files-search-input {
  height: 32px;
  width: 200px;
  padding: 0 32px 0 32px;
  font-size: 13px;
  font-family: inherit;
  color: var(--color-font);
  background: var(--color-primary-background);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.15s, width 0.2s;
}

.files-search-input:focus {
  border-color: var(--color-emphasis);
  width: 260px;
}

.files-search-clear {
  position: absolute;
  right: 4px;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  color: var(--color-font-assist);
  cursor: pointer;
}

.files-search-clear:hover {
  background: var(--color-primary-background);
  color: var(--color-font);
}

/* ========== 视图切换 ========== */
.files-view-toggle {
  display: flex;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.files-view-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: var(--color-card);
  color: var(--color-font-assist);
  cursor: pointer;
  transition: all 0.1s;
  font-family: inherit;
}

.files-view-btn:hover {
  background: var(--color-primary-background);
  color: var(--color-font);
}

.files-view-btn.active {
  background: var(--color-emphasis-soft);
  color: var(--color-emphasis);
}

.files-view-btn + .files-view-btn {
  border-left: 1px solid var(--color-border);
}

/* ========== 表格 ========== */
.files-table-card {
  padding: 0;
  overflow: hidden;
}

.files-table-wrapper {
  overflow-x: auto;
}

.files-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.files-table thead th {
  padding: 12px 16px;
  text-align: left;
  font-weight: 500;
  font-size: 12px;
  color: var(--color-font-assist);
  text-transform: uppercase;
  letter-spacing: 0.02em;
  background: var(--color-primary-background);
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}

.files-th-check {
  width: 44px;
  text-align: center;
}

.files-th-name { min-width: 200px; }
.files-th-size { width: 100px; }
.files-th-date { width: 160px; }
.files-th-actions { width: 180px; }

.files-table tbody td {
  padding: 10px 16px;
  border-bottom: 1px solid var(--color-separator);
  vertical-align: middle;
}

.files-row {
  transition: background 0.1s;
}

.files-row:hover {
  background: var(--color-emphasis-soft);
}

.files-row.selected {
  background: var(--color-emphasis-soft);
}

.files-td-check {
  text-align: center;
}

.files-td-check input[type="checkbox"],
.files-th-check input[type="checkbox"] {
  width: 15px;
  height: 15px;
  accent-color: var(--color-emphasis);
  cursor: pointer;
}

.files-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.files-name-cell.clickable {
  cursor: pointer;
}

.files-name-cell.clickable:hover .files-name-text {
  color: var(--color-emphasis);
}

.files-name-text {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.1s;
}

.files-td-size {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-font-secondary);
}

.files-folder-size {
  color: var(--color-font-assist);
  font-family: inherit;
}

.files-file-size {
  white-space: nowrap;
}

.files-td-date {
  font-size: 12px;
  color: var(--color-font-secondary);
  white-space: nowrap;
}

.files-row-actions {
  display: flex;
  gap: 2px;
}

.files-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  color: var(--color-font-assist);
  cursor: pointer;
  transition: all 0.1s;
  font-family: inherit;
}

.files-action-btn:hover {
  background: var(--color-primary-background);
  color: var(--color-emphasis);
}

.files-action-btn-danger:hover {
  background: var(--color-error-soft);
  color: var(--color-error);
}

/* ========== 网格视图 ========== */
.files-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.files-grid-item {
  position: relative;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 16px 12px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.15s;
  box-shadow: var(--shadow-card);
}

.files-grid-item:hover {
  border-color: var(--color-emphasis);
  box-shadow: var(--shadow-card-hover);
}

.files-grid-item.selected {
  border-color: var(--color-emphasis);
  background: var(--color-emphasis-soft);
}

.files-grid-item-check {
  position: absolute;
  top: 6px;
  left: 6px;
  opacity: 0;
  transition: opacity 0.15s;
}

.files-grid-item:hover .files-grid-item-check,
.files-grid-item.selected .files-grid-item-check {
  opacity: 1;
}

.files-grid-item-check input[type="checkbox"] {
  width: 15px;
  height: 15px;
  accent-color: var(--color-emphasis);
  cursor: pointer;
}

.files-grid-item-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  background: var(--color-primary-background);
}

.files-grid-item-name {
  font-size: 13px;
  font-weight: 500;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: 100%;
  line-height: 1.3;
}

.files-grid-item-meta {
  font-size: 11px;
  color: var(--color-font-assist);
}

.files-grid-item-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}

.files-grid-item:hover .files-grid-item-actions {
  opacity: 1;
}

/* ========== 分页 ========== */
.files-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.files-page-btn {
  min-width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-font-secondary);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.1s;
}

.files-page-btn:hover {
  border-color: var(--color-emphasis);
  color: var(--color-emphasis);
}

.files-page-btn.active {
  border-color: var(--color-emphasis);
  background: var(--color-emphasis-soft);
  color: var(--color-emphasis);
  font-weight: 600;
}

.files-page-ellipsis {
  width: 32px;
  text-align: center;
  font-size: 14px;
  color: var(--color-font-assist);
}

/* ========== 弹窗通用 ========== */
.files-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  animation: fadeIn 0.15s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.files-dialog {
  background: var(--color-card);
  border-radius: var(--radius-xl);
  width: 400px;
  max-width: 90vw;
  max-height: 85vh;
  overflow-y: auto;
  box-shadow: var(--shadow-overlay);
  animation: dialogIn 0.2s ease;
}

@keyframes dialogIn {
  from { opacity: 0; transform: scale(0.95) translateY(8px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.files-dialog-wide {
  width: 480px;
}

.files-dialog-upload {
  width: 520px;
}

.files-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px 14px;
  border-bottom: 1px solid var(--color-separator);
}

.files-dialog-header h3 {
  font-size: 16px;
  font-weight: 600;
}

.files-dialog-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  color: var(--color-font-assist);
  cursor: pointer;
  transition: all 0.1s;
  font-family: inherit;
}

.files-dialog-close:hover {
  background: var(--color-primary-background);
  color: var(--color-font);
}

.files-dialog-body {
  padding: 18px 22px;
}

.files-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 22px 18px;
  border-top: 1px solid var(--color-separator);
}

/* ========== 上传弹窗 ========== */
.files-upload-dropzone {
  padding: 36px 24px;
  margin: 18px 22px 0;
  border: 2px dashed var(--color-border);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  align-items: center;
  transition: all 0.15s;
}

.files-upload-dropzone.dragover {
  border-color: var(--color-emphasis);
  background: var(--color-emphasis-soft);
}

.files-upload-list {
  padding: 0 22px;
  margin-top: 12px;
  max-height: 200px;
  overflow-y: auto;
  display: grid;
  gap: 4px;
}

.files-upload-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-background);
  font-size: 13px;
}

.files-upload-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.files-upload-size {
  font-size: 11px;
  color: var(--color-font-assist);
  font-family: var(--font-mono);
  flex-shrink: 0;
}

.files-upload-remove {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  color: var(--color-font-assist);
  cursor: pointer;
  flex-shrink: 0;
}

.files-upload-remove:hover {
  background: var(--color-error-soft);
  color: var(--color-error);
}

/* ========== 分享选项 ========== */
.files-share-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  background: var(--color-primary-background);
  cursor: pointer;
}

.files-share-option-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-font-secondary);
}

.files-share-option input[type="checkbox"] {
  width: 16px;
  height: 16px;
  accent-color: var(--color-emphasis);
  cursor: pointer;
}

/* ========== 移动列表 ========== */
.files-move-list {
  display: grid;
  gap: 4px;
  max-height: 240px;
  overflow-y: auto;
}

.files-move-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-font);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.1s;
}

.files-move-item:hover {
  background: var(--color-emphasis-soft);
  border-color: var(--color-emphasis);
}

.files-move-item.active {
  background: var(--color-emphasis-soft);
  border-color: var(--color-emphasis);
  color: var(--color-emphasis);
  font-weight: 500;
}

/* ========== 右键菜单 ========== */
.files-context-menu {
  position: fixed;
  z-index: 200;
  min-width: 160px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-overlay);
  padding: 4px;
  animation: contextIn 0.12s ease;
}

@keyframes contextIn {
  from { opacity: 0; transform: scale(0.92); }
  to { opacity: 1; transform: scale(1); }
}

.files-context-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  color: var(--color-font);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.08s;
  text-align: left;
}

.files-context-item:hover {
  background: var(--color-primary-background);
  color: var(--color-emphasis);
}

.files-context-item-danger:hover {
  background: var(--color-error-soft);
  color: var(--color-error);
}

.files-context-divider {
  height: 1px;
  background: var(--color-separator);
  margin: 4px 8px;
}

/* ========== 分享面板 ========== */
.files-shares-list {
  display: grid;
  gap: 8px;
}

.files-share-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--color-primary-background);
  border: 1px solid var(--color-border);
}

.files-share-item-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  background: var(--color-emphasis-soft);
  color: var(--color-emphasis);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.files-share-item-info {
  flex: 1;
  min-width: 0;
}

.files-share-item-name {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 2px;
}

.files-share-item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 11px;
  color: var(--color-font-assist);
}

/* ========== 空/加载状态 ========== */
.files-empty {
  text-align: center;
  padding: 24px;
  color: var(--color-font-assist);
  font-size: 13px;
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .hide-mobile {
    display: none;
  }

  .files-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .files-toolbar-left,
  .files-toolbar-right {
    justify-content: space-between;
  }

  .files-search-input {
    width: 140px;
  }

  .files-search-input:focus {
    width: 180px;
  }

  .files-th-size,
  .files-td-size,
  .files-th-date,
  .files-td-date {
    display: none;
  }

  .files-th-actions {
    width: 140px;
  }

  .files-grid {
    grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
    gap: 8px;
  }

  .files-dialog,
  .files-dialog-wide,
  .files-dialog-upload {
    width: 95vw;
    max-width: 95vw;
    margin: 16px;
  }
}

@media (max-width: 480px) {
  .files-toolbar-right {
    flex-direction: column;
    gap: 6px;
  }

  .files-search-input {
    width: 100%;
  }

  .files-search-input:focus {
    width: 100%;
  }

  .files-batch-actions {
    padding-left: 8px;
    flex-wrap: wrap;
  }

  .files-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* ========== 预览弹窗 ========== */
.files-preview-dialog {
  width: 85vw;
  max-width: 1100px;
  height: 85vh;
  display: flex;
  flex-direction: column;
}

.files-preview-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.files-preview-body {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.02);
  border-top: 1px solid var(--color-separator);
  border-bottom: 1px solid var(--color-separator);
  overflow: auto;
  padding: 0;
}

[data-theme="dark"] .files-preview-body {
  background: rgba(255, 255, 255, 0.02);
}

.files-preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--color-font-assist);
  font-size: 14px;
  padding: 32px;
}

.files-preview-media {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.files-preview-media img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.files-preview-media video {
  max-width: 100%;
  max-height: 100%;
}

.files-preview-media iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.files-preview-audio {
  flex-direction: column;
}

.files-preview-audio audio {
  width: 320px;
}

.files-preview-text {
  width: 100%;
  height: 100%;
  display: flex;
}

.files-preview-text pre {
  flex: 1;
  margin: 0;
  padding: 20px 24px;
  overflow: auto;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-font);
  white-space: pre-wrap;
  word-break: break-all;
  background: transparent;
  border: none;
  border-radius: 0;
}

.files-preview-text code {
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.7;
}

.files-preview-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--color-font-assist);
}

.files-preview-meta span {
  padding: 3px 8px;
  background: var(--color-primary-background);
  border-radius: var(--radius-sm);
}

.files-preview-footer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

@media (max-width: 768px) {
  .files-preview-dialog {
    width: 95vw;
    height: 90vh;
    max-width: 95vw;
    margin: 8px;
  }
}
</style>