<script setup lang="ts">
import { useToast } from "~/composables/useToast";

definePageMeta({ layout: "default" });

interface Resource {
  id: number;
  parentId: number;
  name: string;
  code: string;
  type: number;
  icon: string;
  path: string;
  redirect: string;
  component: string;
  sortNo: number;
  visible: number;
  createdAt: string;
  updatedAt: string;
}

interface FlatRow {
  resource: Resource;
  depth: number;
  hasChildren: boolean;
  isExpanded: boolean;
  index: number;
}

const toast = useToast();

// ---- 数据状态 ----
const resources = ref<Resource[]>([]);
const loading = ref(true);
const error = ref("");

// ---- 筛选 ----
const filterType = ref<number | null>(null);

// ---- 弹窗 ----
const dialogShow = ref(false);
const dialogTitle = ref("新建资源");
const dialogLoading = ref(false);
const dialogError = ref("");
const isEditing = ref(false);
const editingId = ref<number | null>(null);

const form = reactive({
  parentId: 0,
  name: "",
  code: "",
  type: 0,
  icon: "",
  path: "",
  redirect: "",
  component: "",
  sortNo: 0,
  visible: 1,
});

function resetForm() {
  form.parentId = 0;
  form.name = "";
  form.code = "";
  form.type = 0;
  form.icon = "";
  form.path = "";
  form.redirect = "";
  form.component = "";
  form.sortNo = 0;
  form.visible = 1;
  dialogError.value = "";
}

function openCreate(parentId?: number) {
  resetForm();
  if (parentId !== undefined) form.parentId = parentId;
  isEditing.value = false;
  editingId.value = null;
  dialogTitle.value = "新建资源";
  dialogShow.value = true;
}

function openEdit(res: Resource) {
  form.parentId = res.parentId;
  form.name = res.name;
  form.code = res.code;
  form.type = res.type;
  form.icon = res.icon;
  form.path = res.path;
  form.redirect = res.redirect;
  form.component = res.component;
  form.sortNo = res.sortNo;
  form.visible = res.visible;
  isEditing.value = true;
  editingId.value = res.id;
  dialogTitle.value = "编辑资源";
  dialogError.value = "";
  dialogShow.value = true;
}

function closeDialog() {
  dialogShow.value = false;
  resetForm();
}

// ---- 删除确认 ----
const deleteTarget = ref<Resource | null>(null);
const deleteShow = ref(false);
const deleteLoading = ref(false);
const deleteChildCount = ref(0);

function openDelete(res: Resource) {
  deleteTarget.value = res;
  deleteChildCount.value = countChildren(res.id);
  deleteShow.value = true;
}

function closeDelete() {
  deleteTarget.value = null;
  deleteChildCount.value = 0;
  deleteShow.value = false;
}

function countChildren(parentId: number): number {
  const direct = resources.value.filter((r) => r.parentId === parentId);
  let count = direct.length;
  for (const child of direct) {
    count += countChildren(child.id);
  }
  return count;
}

// ---- 展开/折叠状态 ----
const expandedIds = ref(new Set<number>());

function toggleExpand(id: number) {
  if (expandedIds.value.has(id)) {
    expandedIds.value.delete(id);
  } else {
    expandedIds.value.add(id);
  }
  // 触发响应式
  expandedIds.value = new Set(expandedIds.value);
}

function expandAll(list: Resource[]) {
  for (const r of list) {
    if (hasChildren(r.id)) {
      expandedIds.value.add(r.id);
    }
  }
  expandedIds.value = new Set(expandedIds.value);
}

function collapseAll() {
  expandedIds.value = new Set();
}

// ---- 模拟数据 ----
function createMockResources(): Resource[] {
  const now = "2026-06-";
  return [
    // 顶层：仪表盘
    { id: 1, parentId: 0, name: "仪表盘", code: null as any, type: 1, icon: "dashboard", path: "/", redirect: "", component: "index.vue", sortNo: 1, visible: 1, createdAt: `${now}01 08:00:00`, updatedAt: `${now}01 08:00:00` },

    // 顶层：文件管理（目录）
    { id: 2, parentId: 0, name: "文件管理", code: null as any, type: 0, icon: "folder", path: "/files", redirect: "/files/recent", component: "", sortNo: 2, visible: 1, createdAt: `${now}01 08:00:00`, updatedAt: `${now}01 08:00:00` },
    { id: 3, parentId: 2, name: "最近文件", code: "file:list", type: 1, icon: "clock", path: "/files/recent", redirect: "", component: "files/recent.vue", sortNo: 1, visible: 1, createdAt: `${now}01 08:00:00`, updatedAt: `${now}01 08:00:00` },
    { id: 4, parentId: 2, name: "我的文件", code: "file:list", type: 1, icon: "folder-open", path: "/files/mine", redirect: "", component: "files/mine.vue", sortNo: 2, visible: 1, createdAt: `${now}01 08:00:00`, updatedAt: `${now}01 08:00:00` },
    { id: 30, parentId: 2, name: "上传文件", code: "file:upload", type: 2, icon: "", path: "", redirect: "", component: "", sortNo: 1, visible: 1, createdAt: `${now}05 10:00:00`, updatedAt: `${now}05 10:00:00` },
    { id: 31, parentId: 2, name: "删除文件", code: "file:delete", type: 2, icon: "", path: "", redirect: "", component: "", sortNo: 2, visible: 1, createdAt: `${now}05 10:00:00`, updatedAt: `${now}05 10:00:00` },

    // 顶层：应用管理（目录）
    { id: 5, parentId: 0, name: "应用管理", code: null as any, type: 0, icon: "box", path: "/apps", redirect: "/apps/list", component: "", sortNo: 3, visible: 1, createdAt: `${now}01 08:00:00`, updatedAt: `${now}01 08:00:00` },
    { id: 6, parentId: 5, name: "应用列表", code: "oauth:client:list", type: 1, icon: "list", path: "/apps", redirect: "", component: "apps/index.vue", sortNo: 1, visible: 1, createdAt: `${now}01 08:00:00`, updatedAt: `${now}01 08:00:00` },
    { id: 7, parentId: 5, name: "申请接入", code: "oauth:client:apply", type: 1, icon: "plus-square", path: "/apps/apply", redirect: "", component: "apps/apply.vue", sortNo: 2, visible: 1, createdAt: `${now}01 08:00:00`, updatedAt: `${now}01 08:00:00` },
    { id: 8, parentId: 5, name: "应用详情", code: "oauth:client:detail", type: 1, icon: "info", path: "/apps/:uuid", redirect: "", component: "apps/[uuid].vue", sortNo: 3, visible: 0, createdAt: `${now}01 08:00:00`, updatedAt: `${now}01 08:00:00` },
    { id: 32, parentId: 5, name: "审核通过", code: "oauth:audit:approve", type: 2, icon: "", path: "", redirect: "", component: "", sortNo: 1, visible: 1, createdAt: `${now}05 10:00:00`, updatedAt: `${now}05 10:00:00` },
    { id: 33, parentId: 5, name: "审核拒绝", code: "oauth:audit:reject", type: 2, icon: "", path: "", redirect: "", component: "", sortNo: 2, visible: 1, createdAt: `${now}05 10:00:00`, updatedAt: `${now}05 10:00:00` },

    // 顶层：个人中心（目录）
    { id: 9, parentId: 0, name: "个人中心", code: null as any, type: 0, icon: "user", path: "/profile", redirect: "", component: "", sortNo: 4, visible: 1, createdAt: `${now}01 08:00:00`, updatedAt: `${now}01 08:00:00` },
    { id: 10, parentId: 9, name: "个人信息", code: "user:profile:view", type: 1, icon: "id-card", path: "/profile", redirect: "", component: "profile.vue", sortNo: 1, visible: 1, createdAt: `${now}01 08:00:00`, updatedAt: `${now}01 08:00:00` },
    { id: 34, parentId: 9, name: "编辑资料", code: "user:profile:edit", type: 2, icon: "", path: "", redirect: "", component: "", sortNo: 1, visible: 1, createdAt: `${now}05 10:00:00`, updatedAt: `${now}05 10:00:00` },
    { id: 35, parentId: 9, name: "修改密码", code: "user:password:change", type: 2, icon: "", path: "", redirect: "", component: "", sortNo: 2, visible: 1, createdAt: `${now}05 10:00:00`, updatedAt: `${now}05 10:00:00` },

    // 顶层：系统设置
    { id: 11, parentId: 0, name: "系统设置", code: null as any, type: 1, icon: "settings", path: "/settings", redirect: "", component: "settings.vue", sortNo: 5, visible: 1, createdAt: `${now}01 08:00:00`, updatedAt: `${now}01 08:00:00` },

    // 顶层：Admin（目录）
    { id: 12, parentId: 0, name: "Admin 后台", code: null as any, type: 0, icon: "shield", path: "/admin", redirect: "/admin/apps", component: "", sortNo: 6, visible: 1, createdAt: `${now}02 10:00:00`, updatedAt: `${now}02 10:00:00` },

    // Admin > 应用管理
    { id: 13, parentId: 12, name: "应用审核", code: "core:oauth:audit", type: 1, icon: "clipboard-check", path: "/admin/apps", redirect: "", component: "admin/apps.vue", sortNo: 1, visible: 1, createdAt: `${now}02 10:00:00`, updatedAt: `${now}02 10:00:00` },
    { id: 36, parentId: 12, name: "应用列表管理", code: "core:oauth:list:manage", type: 2, icon: "", path: "", redirect: "", component: "", sortNo: 1, visible: 1, createdAt: `${now}06 08:00:00`, updatedAt: `${now}06 08:00:00` },

    // Admin > 用户管理
    { id: 14, parentId: 12, name: "用户列表", code: "core:user:list", type: 1, icon: "list", path: "/admin/users", redirect: "", component: "admin/users.vue", sortNo: 2, visible: 1, createdAt: `${now}02 10:00:00`, updatedAt: `${now}06 08:00:00` },
    { id: 15, parentId: 12, name: "角色管理", code: "core:user:role", type: 1, icon: "shield-check", path: "/admin/roles", redirect: "", component: "admin/roles.vue", sortNo: 3, visible: 0, createdAt: `${now}02 10:00:00`, updatedAt: `${now}02 10:00:00` },
    { id: 37, parentId: 12, name: "创建用户", code: "user:create", type: 2, icon: "", path: "", redirect: "", component: "", sortNo: 1, visible: 1, createdAt: `${now}06 09:00:00`, updatedAt: `${now}06 09:00:00` },
    { id: 38, parentId: 12, name: "编辑用户", code: "user:edit", type: 2, icon: "", path: "", redirect: "", component: "", sortNo: 2, visible: 1, createdAt: `${now}06 09:00:00`, updatedAt: `${now}06 09:00:00` },
    { id: 39, parentId: 12, name: "删除用户", code: "user:delete", type: 2, icon: "", path: "", redirect: "", component: "", sortNo: 3, visible: 1, createdAt: `${now}06 09:00:00`, updatedAt: `${now}06 09:00:00` },

    // Admin > 资源管理
    { id: 16, parentId: 12, name: "接口端点", code: "core:endpoint:list", type: 1, icon: "plug", path: "/admin/endpoints", redirect: "", component: "admin/endpoints.vue", sortNo: 4, visible: 1, createdAt: `${now}03 14:00:00`, updatedAt: `${now}03 14:00:00` },
    { id: 17, parentId: 12, name: "前端资源", code: "core:resource:list", type: 1, icon: "layout", path: "/admin/resources", redirect: "", component: "admin/resources.vue", sortNo: 5, visible: 1, createdAt: `${now}03 14:00:00`, updatedAt: `${now}03 14:00:00` },
    { id: 40, parentId: 12, name: "新建端点", code: "endpoint:create", type: 2, icon: "", path: "", redirect: "", component: "", sortNo: 1, visible: 1, createdAt: `${now}06 10:00:00`, updatedAt: `${now}06 10:00:00` },
    { id: 41, parentId: 12, name: "删除端点", code: "endpoint:delete", type: 2, icon: "", path: "", redirect: "", component: "", sortNo: 2, visible: 1, createdAt: `${now}06 10:00:00`, updatedAt: `${now}06 10:00:00` },
    { id: 42, parentId: 12, name: "管理资源", code: "resource:manage", type: 2, icon: "", path: "", redirect: "", component: "", sortNo: 3, visible: 1, createdAt: `${now}06 10:00:00`, updatedAt: `${now}06 10:00:00` },
  ];
}

// ---- 数据加载 ----
async function loadResources() {
  loading.value = true;
  error.value = "";
  if (import.meta.dev) {
    await new Promise((r) => setTimeout(r, 300));
    resources.value = [...createMockResources()];
    expandAll(resources.value);
    loading.value = false;
    return;
  }
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const qs = filterType.value !== null ? `?type=${filterType.value}` : "";
    const res = await fetch<{ code: number; data: Resource[]; msg: string }>(`/api/admin/resources${qs}`);
    if (res.code === 200) {
      resources.value = res.data || [];
      expandAll(resources.value);
    } else {
      error.value = res.msg || "获取资源列表失败";
    }
  } catch (e: any) {
    error.value = e?.data?.msg || e?.message || "请求失败";
  } finally {
    loading.value = false;
  }
}

// ---- 扁平化树 ----
const isNode = (r: Resource) => r.type === 0;
const isMenu = (r: Resource) => r.type === 1;
const isButton = (r: Resource) => r.type === 2;

function hasChildren(id: number): boolean {
  const filtered = filterType.value !== null
    ? resources.value.filter((r) => r.type === filterType.value)
    : resources.value;
  return filtered.some((r) => r.parentId === id);
}

function getChildren(parentId: number): Resource[] {
  let list = resources.value.filter((r) => r.parentId === parentId);
  if (filterType.value !== null) {
    list = list.filter((r) => r.type === filterType.value);
  }
  list.sort((a, b) => a.sortNo - b.sortNo);
  return list;
}

const flatRows = computed<FlatRow[]>(() => {
  const result: FlatRow[] = [];
  const topLevel = filterType.value !== null
    ? resources.value.filter((r) => r.parentId === 0 && r.type === filterType.value)
    : resources.value.filter((r) => r.parentId === 0);
  topLevel.sort((a, b) => a.sortNo - b.sortNo);

  function walk(list: Resource[], depth: number) {
    for (const r of list) {
      const children = getChildren(r.id);
      result.push({
        resource: r,
        depth,
        hasChildren: children.length > 0,
        isExpanded: expandedIds.value.has(r.id),
        index: result.length,
      });
      if (children.length > 0 && expandedIds.value.has(r.id)) {
        walk(children, depth + 1);
      }
    }
  }

  walk(topLevel, 0);
  return result;
});

const filteredCount = computed(() => {
  if (filterType.value === null) return resources.value.length;
  return resources.value.filter((r) => r.type === filterType.value).length;
});

// ---- 获取父级名称 ----
function parentName(parentId: number): string {
  if (parentId === 0) return "顶层";
  const p = resources.value.find((r) => r.id === parentId);
  return p ? p.name : "未知";
}

// 获取用于下拉的父级选项（只显示目录和菜单）
const parentOptions = computed(() => {
  return resources.value
    .filter((r) => r.type === 0 || r.type === 1)
    .sort((a, b) => a.sortNo - b.sortNo)
    .map((r) => ({ id: r.id, name: r.name, type: r.type }));
});

// ---- 创建/编辑 ----
async function submitForm() {
  dialogError.value = "";
  if (!form.name.trim()) { dialogError.value = "请输入资源名称"; return; }
  if (form.type === 2 && !form.code.trim()) { dialogError.value = "按钮资源必须填写权限码"; return; }
  if (form.type === 1 && !form.path.trim()) { dialogError.value = "菜单资源必须填写路由路径"; return; }
  dialogLoading.value = true;

  if (import.meta.dev) {
    await new Promise((r) => setTimeout(r, 400));
    if (isEditing.value && editingId.value !== null) {
      const idx = resources.value.findIndex((r) => r.id === editingId.value);
      if (idx > -1) {
        resources.value[idx] = {
          ...resources.value[idx],
          parentId: form.parentId,
          name: form.name.trim(),
          code: form.code.trim(),
          type: form.type,
          icon: form.icon.trim(),
          path: form.path.trim(),
          redirect: form.redirect.trim(),
          component: form.component.trim(),
          sortNo: form.sortNo,
          visible: form.visible,
          updatedAt: new Date().toISOString().replace("T", " ").substring(0, 19),
        } as Resource;
      }
    } else {
      const newRes: Resource = {
        id: Math.max(...resources.value.map((r) => r.id), 0) + 1,
        parentId: form.parentId,
        name: form.name.trim(),
        code: form.code.trim(),
        type: form.type,
        icon: form.icon.trim(),
        path: form.path.trim(),
        redirect: form.redirect.trim(),
        component: form.component.trim(),
        sortNo: form.sortNo,
        visible: form.visible,
        createdAt: new Date().toISOString().replace("T", " ").substring(0, 19),
        updatedAt: new Date().toISOString().replace("T", " ").substring(0, 19),
      };
      resources.value.push(newRes);
    }
    toast.success(isEditing.value ? "更新成功" : "创建成功");
    dialogLoading.value = false;
    closeDialog();
    return;
  }

  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const body: Record<string, string | number> = {
      parentId: form.parentId,
      name: form.name.trim(),
      code: form.code.trim(),
      type: form.type,
      icon: form.icon.trim(),
      path: form.path.trim(),
      redirect: form.redirect.trim(),
      component: form.component.trim(),
      sortNo: form.sortNo,
      visible: form.visible,
    };
    let res: { code: number; msg: string };
    if (isEditing.value && editingId.value !== null) {
      res = await fetch(`/api/admin/resources/${editingId.value}`, { method: "PUT", body });
    } else {
      res = await fetch("/api/admin/resources", { method: "POST", body });
    }
    if (res.code === 200) {
      toast.success(isEditing.value ? "更新成功" : "创建成功");
      closeDialog();
      await loadResources();
    } else {
      dialogError.value = res.msg || "操作失败";
    }
  } catch (e: any) {
    dialogError.value = e?.data?.msg || e?.message || "请求失败";
  } finally {
    dialogLoading.value = false;
  }
}

// ---- 排序 ----
async function moveUp(row: FlatRow) {
  const siblings = getChildren(row.resource.parentId);
  const idx = siblings.findIndex((r) => r.id === row.resource.id);
  if (idx <= 0) return;

  if (import.meta.dev) {
    const prev = siblings[idx - 1]!;
    const tempSort = prev.sortNo;
    const prevIdx = resources.value.findIndex((r) => r.id === prev.id);
    const curIdx = resources.value.findIndex((r) => r.id === row.resource.id);
    if (prevIdx > -1 && curIdx > -1) {
      resources.value[prevIdx] = { ...resources.value[prevIdx], sortNo: row.resource.sortNo } as Resource;
      resources.value[curIdx] = { ...resources.value[curIdx], sortNo: tempSort } as Resource;
    }
    return;
  }

  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; msg: string }>(
      `/api/admin/resources/${row.resource.id}/sort?sortNo=${row.resource.sortNo - 1}`,
      { method: "PUT" },
    );
    if (res.code === 200) await loadResources();
    else toast.error(res.msg || "操作失败");
  } catch {
    toast.error("请求失败");
  }
}

async function moveDown(row: FlatRow) {
  const siblings = getChildren(row.resource.parentId);
  const idx = siblings.findIndex((r) => r.id === row.resource.id);
  if (idx < 0 || idx >= siblings.length - 1) return;

  if (import.meta.dev) {
    const next = siblings[idx + 1]!;
    const tempSort = next.sortNo;
    const nextIdx = resources.value.findIndex((r) => r.id === next.id);
    const curIdx = resources.value.findIndex((r) => r.id === row.resource.id);
    if (nextIdx > -1 && curIdx > -1) {
      resources.value[nextIdx] = { ...resources.value[nextIdx], sortNo: row.resource.sortNo } as Resource;
      resources.value[curIdx] = { ...resources.value[curIdx], sortNo: tempSort } as Resource;
    }
    return;
  }

  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; msg: string }>(
      `/api/admin/resources/${row.resource.id}/sort?sortNo=${row.resource.sortNo + 1}`,
      { method: "PUT" },
    );
    if (res.code === 200) await loadResources();
    else toast.error(res.msg || "操作失败");
  } catch {
    toast.error("请求失败");
  }
}

// ---- 删除 ----
async function confirmDelete() {
  if (!deleteTarget.value) return;
  deleteLoading.value = true;
  if (import.meta.dev) {
    await new Promise((r) => setTimeout(r, 300));
    const removeIds = new Set<number>();
    function collectIds(parentId: number) {
      resources.value.filter((r) => r.parentId === parentId).forEach((c) => {
        removeIds.add(c.id);
        collectIds(c.id);
      });
    }
    removeIds.add(deleteTarget.value.id);
    collectIds(deleteTarget.value.id);
    resources.value = resources.value.filter((r) => !removeIds.has(r.id));
    toast.success("已删除");
    deleteLoading.value = false;
    closeDelete();
    return;
  }
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; msg: string }>(`/api/admin/resources/${deleteTarget.value.id}`, { method: "DELETE" });
    if (res.code === 200) {
      toast.success("已删除");
      closeDelete();
      await loadResources();
    } else {
      toast.error(res.msg || "删除失败");
    }
  } catch {
    toast.error("请求失败");
  } finally {
    deleteLoading.value = false;
  }
}

// ---- 工具 ----
const typeLabel: Record<number, string> = { 0: "目录", 1: "菜单", 2: "按钮" };
const typeIcons: Record<number, string> = { 0: "lucide:folder-tree", 1: "lucide:file-text", 2: "lucide:mouse-pointer-click" };
const typeColors: Record<number, string> = { 0: "gray", 1: "blue", 2: "green" };
const visibleLabel: Record<number, string> = { 0: "隐藏", 1: "显示" };

const filterChips = [
  { label: "全部", value: null as number | null },
  { label: "目录", value: 0 },
  { label: "菜单", value: 1 },
  { label: "按钮", value: 2 },
];

function filterCount(typeVal: number | null): number {
  if (typeVal === null) return resources.value.length;
  return resources.value.filter((r) => r.type === typeVal).length;
}

function setFilterType(val: number | null) {
  filterType.value = val;
  if (val !== null) {
    expandAll(resources.value.filter((r) => r.type === val));
  } else {
    expandAll(resources.value);
  }
}

onMounted(loadResources);
</script>

<template>
  <div class="apps-page">
    <section class="page-header fade-up">
      <div class="page-header-left">
        <div class="page-header-overline">Admin / Resources</div>
        <h1 class="page-header-title">前端资源管理</h1>
        <p class="page-header-description">管理前端菜单、路由与按钮级权限资源，支持树形层级结构</p>
      </div>
      <div class="page-header-actions">
        <button class="button" @click="expandAll(resources)">
          <Icon name="lucide:folders-open" size="14" />
          全部展开
        </button>
        <button class="button" style="margin-left: 6px" @click="collapseAll">
          <Icon name="lucide:folders" size="14" />
          全部收起
        </button>
        <button class="button button-primary" style="margin-left: 8px" @click="openCreate()">
          <Icon name="lucide:plus" size="15" />
          新建资源
        </button>
      </div>
    </section>

    <!-- 筛选栏 -->
    <div class="admin-filters fade-up">
      <div
        v-for="chip in filterChips"
        :key="chip.label"
        :class="['filter-chip', { active: filterType === chip.value }]"
        @click="setFilterType(chip.value)"
      >
        {{ chip.label }} ({{ filterCount(chip.value) }})
      </div>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="panel-card fade-up">
      <div class="panel-card-body" style="text-align: center; padding: 48px 24px; color: var(--color-font-assist)">
        <Icon name="lucide:loader-circle" size="32" style="margin-bottom: 12px; opacity: 0.4; animation: spin 1s linear infinite" />
        <p>加载中…</p>
      </div>
    </div>

    <!-- 错误态 -->
    <div v-else-if="error" class="panel-card fade-up">
      <div class="panel-card-body" style="text-align: center; padding: 48px 24px">
        <Icon name="lucide:alert-triangle" size="40" style="margin-bottom: 12px; color: var(--color-error); opacity: 0.6" />
        <p style="color: var(--color-error); margin-bottom: 16px">{{ error }}</p>
        <button class="button button-primary" @click="loadResources">重新加载</button>
      </div>
    </div>

    <!-- 空态 -->
    <div v-else-if="flatRows.length === 0" class="panel-card fade-up">
      <div class="panel-card-body" style="text-align: center; padding: 48px 24px; color: var(--color-font-assist)">
        <Icon name="lucide:layout" size="48" style="margin-bottom: 16px; opacity: 0.3" />
        <p style="font-size: 16px; margin-bottom: 8px">暂无资源数据</p>
        <p style="font-size: 13px; margin-bottom: 20px">点击「新建资源」开始构建前端菜单结构</p>
        <button class="button button-primary" @click="openCreate()">
          <Icon name="lucide:plus" size="14" />
          新建资源
        </button>
      </div>
    </div>

    <!-- 树形表格 -->
    <div v-else class="panel-card fade-up" style="padding: 0; overflow: hidden">
      <div class="resource-table-wrapper">
        <table class="resource-table">
          <thead>
            <tr>
              <th class="res-col-name">名称</th>
              <th>类型</th>
              <th>权限码</th>
              <th>路由</th>
              <th>图标</th>
              <th>排序</th>
              <th>可见</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in flatRows"
              :key="row.resource.id"
              :class="['res-row', `res-depth-${Math.min(row.depth, 5)}`]"
            >
              <!-- 名称（带缩进和展开按钮） -->
              <td class="res-col-name">
                <div class="res-name-cell" :style="{ paddingLeft: row.depth * 24 + 'px' }">
                  <!-- 展开/折叠按钮 -->
                  <button
                    v-if="row.hasChildren"
                    class="res-expand-btn"
                    @click="toggleExpand(row.resource.id)"
                  >
                    <Icon
                      :name="row.isExpanded ? 'lucide:chevron-down' : 'lucide:chevron-right'"
                      size="14"
                    />
                  </button>
                  <span v-else class="res-expand-placeholder" />

                  <!-- 图标 -->
                  <Icon
                    v-if="row.resource.icon"
                    :name="`lucide:${row.resource.icon}`"
                    size="15"
                    class="res-icon"
                  />

                  <!-- 名称，目录可点击展开 -->
                  <span class="res-name-text">{{ row.resource.name }}</span>

                  <!-- 添加子资源按钮（仅目录显示） -->
                  <button
                    v-if="row.resource.type === 0"
                    class="res-add-child"
                    title="添加子资源"
                    @click="openCreate(row.resource.id)"
                  >
                    <Icon name="lucide:plus" size="12" />
                  </button>
                </div>
              </td>

              <!-- 类型 -->
              <td>
                <span :class="['res-type-tag', `res-type-${typeColors[row.resource.type]}`]">
                  <Icon :name="typeIcons[row.resource.type]!" size="11" />
                  {{ typeLabel[row.resource.type]! }}
                </span>
              </td>

              <!-- 权限码 -->
              <td>
                <code v-if="row.resource.code" class="res-code-tag">{{ row.resource.code }}</code>
                <span v-else class="res-none">—</span>
              </td>

              <!-- 路由 -->
              <td>
                <code v-if="row.resource.path" class="res-path">{{ row.resource.path }}</code>
                <span v-else class="res-none">—</span>
              </td>

              <!-- 图标 -->
              <td>
                <span v-if="row.resource.icon" class="res-icon-tag">
                  <Icon :name="`lucide:${row.resource.icon}`" size="13" />
                  {{ row.resource.icon }}
                </span>
                <span v-else class="res-none">—</span>
              </td>

              <!-- 排序号 -->
              <td>
                <span class="res-sort-no">{{ row.resource.sortNo }}</span>
              </td>

              <!-- 可见 -->
              <td>
                <span :class="['res-visible-tag', row.resource.visible === 1 ? 'res-visible-on' : 'res-visible-off']">
                  {{ visibleLabel[row.resource.visible] }}
                </span>
              </td>

              <!-- 操作 -->
              <td>
                <div class="res-actions">
                  <button class="button button-small" @click="openEdit(row.resource)" title="编辑">
                    <Icon name="lucide:pencil" size="13" />
                  </button>
                  <button
                    class="button button-small"
                    :disabled="row.index === 0 || getChildren(row.resource.parentId)[0]?.id === row.resource.id"
                    @click="moveUp(row)"
                    title="上移"
                  >
                    <Icon name="lucide:chevron-up" size="13" />
                  </button>
                  <button
                    class="button button-small"
                    :disabled="row.index === flatRows.length - 1 || getChildren(row.resource.parentId).at(-1)?.id === row.resource.id"
                    @click="moveDown(row)"
                    title="下移"
                  >
                    <Icon name="lucide:chevron-down" size="13" />
                  </button>
                  <button class="button button-small button-danger" @click="openDelete(row.resource)" title="删除">
                    <Icon name="lucide:trash-2" size="13" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 创建/编辑弹窗 -->
    <Modal :show="dialogShow" :title="dialogTitle" width="520px" @close="closeDialog">
      <div class="dialog-form">
        <div v-if="dialogError" class="profile-edit-error" style="margin-bottom: 6px">{{ dialogError }}</div>

        <div class="form-field">
          <label>父级资源</label>
          <select v-model="form.parentId" class="form-input" :disabled="dialogLoading">
            <option :value="0">顶层（无父级）</option>
            <option v-for="p in parentOptions" :key="p.id" :value="p.id">
              {{ p.name }} ({{ typeLabel[p.type] }})
            </option>
          </select>
        </div>

        <div class="form-field">
          <label>资源类型</label>
          <select v-model="form.type" class="form-input" :disabled="dialogLoading">
            <option :value="0">目录</option>
            <option :value="1">菜单</option>
            <option :value="2">页面资源 / 按钮</option>
          </select>
        </div>

        <div class="form-field">
          <label>名称 <span style="color: var(--color-error)">*</span></label>
          <input v-model="form.name" class="form-input" placeholder="例如：用户管理" :disabled="dialogLoading" />
        </div>

        <!-- 权限码：目录可为空，按钮必填 -->
        <div class="form-field">
          <label>
            权限码
            <span v-if="form.type === 2" style="color: var(--color-error)">*</span>
          </label>
          <input v-model="form.code" class="form-input" placeholder="例如 user:list" :disabled="dialogLoading" />
          <span v-if="form.type === 0" class="apply-hint">目录无需权限码</span>
        </div>

        <!-- 路由路径：仅菜单 -->
        <div class="form-field" v-if="form.type === 1">
          <label>路由路径 <span style="color: var(--color-error)">*</span></label>
          <input v-model="form.path" class="form-input" placeholder="例如 /admin/users" :disabled="dialogLoading" />
        </div>

        <!-- 重定向：仅目录 -->
        <div class="form-field" v-if="form.type === 0">
          <label>重定向</label>
          <input v-model="form.redirect" class="form-input" placeholder="例如 /admin/users" :disabled="dialogLoading" />
          <span class="apply-hint">目录默认跳转到的子路由</span>
        </div>

        <!-- 组件路径：仅菜单 -->
        <div class="form-field" v-if="form.type === 1">
          <label>组件路径</label>
          <input v-model="form.component" class="form-input" placeholder="例如 admin/users.vue" :disabled="dialogLoading" />
        </div>

        <!-- 图标：仅目录和菜单 -->
        <div class="form-field" v-if="form.type !== 2">
          <label>图标</label>
          <input v-model="form.icon" class="form-input" placeholder="Lucide 图标名，例如 folder" :disabled="dialogLoading" />
        </div>

        <!-- 排序 -->
        <div class="form-field">
          <label>排序号</label>
          <input v-model.number="form.sortNo" class="form-input" type="number" placeholder="0" :disabled="dialogLoading" />
        </div>

        <!-- 可见性 -->
        <div class="form-field">
          <label>可见性</label>
          <select v-model.number="form.visible" class="form-input" :disabled="dialogLoading">
            <option :value="1">显示</option>
            <option :value="0">隐藏</option>
          </select>
        </div>
      </div>
      <template #footer>
        <button class="button" @click="closeDialog" :disabled="dialogLoading">取消</button>
        <button class="button button-primary" :disabled="dialogLoading" @click="submitForm">
          {{ dialogLoading ? "提交中…" : isEditing ? "保存修改" : "创建" }}
        </button>
      </template>
    </Modal>

    <!-- 删除确认弹窗 -->
    <Modal :show="deleteShow" title="确认删除" width="400px" @close="closeDelete">
      <div style="display: grid; gap: 8px">
        <p style="margin: 0; color: var(--color-font-secondary)">
          确定要删除资源「{{ deleteTarget?.name }}」吗？此操作不可撤销。
        </p>
        <div v-if="deleteTarget" class="delete-preview">
          <span :class="['res-type-tag', `res-type-${typeColors[deleteTarget.type]!}`]">
            <Icon :name="typeIcons[deleteTarget.type]!" size="11" />
            {{ typeLabel[deleteTarget.type]! }}
          </span>
          <span style="font-size: 13px; color: var(--color-font)">{{ deleteTarget.name }}</span>
        </div>
        <div v-if="deleteChildCount > 0" class="delete-warning">
          <Icon name="lucide:alert-triangle" size="14" />
          <span>将同时删除其下 {{ deleteChildCount }} 个子资源</span>
        </div>
      </div>
      <template #footer>
        <button class="button" @click="closeDelete" :disabled="deleteLoading">取消</button>
        <button class="button button-primary" style="background: var(--color-error)" :disabled="deleteLoading" @click="confirmDelete">
          {{ deleteLoading ? "删除中…" : "确认删除" }}
        </button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.apps-page {
  max-width: 100%;
  margin: 0 auto;
  width: 100%;
}

.page-header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

/* ---- 表格 ---- */
.resource-table-wrapper {
  overflow-x: auto;
}

.resource-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.resource-table thead {
  background: var(--color-primary-background);
}

.resource-table th {
  padding: 10px 12px;
  text-align: left;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-font-assist);
  text-transform: uppercase;
  letter-spacing: 0.03em;
  white-space: nowrap;
  border-bottom: 1px solid var(--color-border);
}

.resource-table td {
  padding: 9px 12px;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-font-secondary);
  vertical-align: middle;
}

.resource-table tbody tr {
  transition: background 0.1s;
}

.resource-table tbody tr:hover {
  background: var(--color-primary-background);
}

.resource-table tbody tr:last-child td {
  border-bottom: none;
}

.res-col-name {
  min-width: 240px;
}

/* ---- 名称列 ---- */
.res-name-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.res-expand-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  background: transparent;
  color: var(--color-font-assist);
  cursor: pointer;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
  padding: 0;
  transition: all 0.1s;
}

.res-expand-btn:hover {
  background: var(--color-border);
  color: var(--color-font);
}

.res-expand-placeholder {
  width: 22px;
  flex-shrink: 0;
}

.res-icon {
  color: var(--color-font-assist);
  flex-shrink: 0;
}

.res-name-text {
  font-weight: 500;
  color: var(--color-font);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.res-add-child {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: 1px dashed var(--color-border);
  background: transparent;
  color: var(--color-font-assist);
  cursor: pointer;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
  padding: 0;
  opacity: 0;
  transition: all 0.12s;
  margin-left: 4px;
}

.res-row:hover .res-add-child {
  opacity: 1;
}

.res-add-child:hover {
  border-color: var(--color-emphasis);
  color: var(--color-emphasis);
  background: var(--color-emphasis-soft);
}

/* 深度颜色微调 */
.res-depth-0 .res-name-text { color: var(--color-font); font-weight: 600; font-size: 13px; }
.res-depth-1 .res-name-text { color: var(--color-font); font-weight: 500; }
.res-depth-2 .res-name-text { font-weight: 400; font-size: 12px; }
.res-depth-3 .res-name-text,
.res-depth-4 .res-name-text,
.res-depth-5 .res-name-text { color: var(--color-font-secondary); font-weight: 400; font-size: 12px; }

/* ---- 类型标签 ---- */
.res-type-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

.res-type-gray {
  background: var(--color-primary-background);
  color: var(--color-font-assist);
  border: 1px solid var(--color-border);
}

.res-type-blue {
  background: var(--color-info-soft);
  color: var(--color-info);
  border: 1px solid rgba(2, 132, 199, 0.15);
}

.res-type-green {
  background: var(--color-emphasis-soft);
  color: var(--color-emphasis);
  border: 1px solid rgba(22, 163, 74, 0.15);
}

/* ---- 权限码 ---- */
.res-code-tag {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-info-text);
  background: var(--color-info-soft);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  border: 1px solid rgba(2, 132, 199, 0.12);
  white-space: nowrap;
}

/* ---- 路由 ---- */
.res-path {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-font);
  background: var(--color-primary-background);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  white-space: nowrap;
}

/* ---- 图标标签 ---- */
.res-icon-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-font-secondary);
  background: var(--color-primary-background);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  white-space: nowrap;
}

/* ---- 排序号 ---- */
.res-sort-no {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-font-assist);
}

/* ---- 可见性 ---- */
.res-visible-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

.res-visible-on {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.res-visible-off {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}

/* ---- 空值 ---- */
.res-none {
  color: var(--color-font-assist);
  font-size: 12px;
}

/* ---- 操作 ---- */
.res-actions {
  display: flex;
  align-items: center;
  gap: 3px;
}

/* ---- 弹窗表单 ---- */
.dialog-form {
  display: grid;
  gap: 14px;
}

.dialog-form .form-field label {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-font-secondary);
}

.dialog-form .form-input {
  width: 100%;
  box-sizing: border-box;
}

/* ---- 删除预览/警告 ---- */
.delete-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--color-primary-background);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
}

.delete-warning {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: var(--color-warning-soft);
  border-radius: var(--radius-md);
  color: var(--color-warning);
  font-size: 12px;
  font-weight: 500;
}

/* ---- 加载旋转 ---- */
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 响应式 */
@media (max-width: 900px) {
  .page-header-actions {
    margin-top: 8px;
    width: 100%;
    justify-content: flex-start;
  }

  .resource-table th:nth-child(4),
  .resource-table td:nth-child(4),
  .resource-table th:nth-child(5),
  .resource-table td:nth-child(5) {
    display: none;
  }
}

@media (max-width: 640px) {
  .resource-table th:nth-child(6),
  .resource-table td:nth-child(6),
  .resource-table th:nth-child(7),
  .resource-table td:nth-child(7) {
    display: none;
  }

  .res-col-name {
    min-width: 160px;
  }
}
</style>
