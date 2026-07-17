<script setup lang="ts">
import { useToast } from "~/composables/useToast";

definePageMeta({ layout: "default" });

const toast = useToast();

// ==================== 类型定义 ====================
interface RoleVO {
  id: number;
  name: string;
  level: number;
  permissions: string[];
  status: number; // 0=取消 1=启用 2=删除
}

interface UserVO {
  globalId: string;
  username: string;
  email: string;
  phone: string;
  createdAt: string;
  status: number; // 0=注销 1=正常 2=封禁 3=冻结
  nickname: string;
  avatarUuid: string;
  gender: string;
  birthday: string;
  bio: string;
  roles: RoleVO[];
}

interface PageVO<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

// ==================== Mock 数据 ====================
const mockRoles: RoleVO[] = [
  { id: 1, name: "SUPER_ADMIN", level: 99, permissions: ["*:*:*:*"], status: 1 },
  { id: 2, name: "ADMIN", level: 60, permissions: ["admin:*", "user:read", "app:audit", "admin:users:*", "admin:roles:*", "admin:audit:*"], status: 1 },
  { id: 3, name: "VIP", level: 10, permissions: ["storage:premium", "speed:boost", "resource:advanced", "vip:badge"], status: 1 },
  { id: 4, name: "USER", level: 1, permissions: ["resource:basic", "storage:basic", "profile:read"], status: 1 },
];

function makeGlobalId(idx: number): string {
  return `glob-${String(idx).padStart(4, "0")}-${Math.random().toString(16).slice(2, 10)}`;
}

function randomDate(start: string, end: string): string {
  const s = new Date(start).getTime();
  const e = new Date(end).getTime();
  return new Date(s + Math.random() * (e - s)).toISOString().replace("T", " ").slice(0, 19);
}

const mockUsers: UserVO[] = [
  { globalId: makeGlobalId(1), username: "YunaAdmin", email: "admin@yunanexus.com", phone: "13800001001", createdAt: "2024-01-15 10:30:00", status: 1, roles: [mockRoles[0]!, mockRoles[1]!],nickname: "尤娜", avatarUuid: "", gender: "女", birthday: "2001-03-07", bio: "平台超级管理员" },
  { globalId: makeGlobalId(2), username: "dev_admin", email: "dev@yunanexus.com", phone: "13800001002", createdAt: "2024-02-20 14:20:00", status: 1, roles: [mockRoles[1]!],nickname: "开发者001", avatarUuid: "", gender: "男", birthday: "1998-08-15", bio: "后端开发工程师" },
  { globalId: makeGlobalId(3), username: "vip_zhang", email: "zhang@example.com", phone: "13800001003", createdAt: "2024-03-10 09:00:00", status: 1, roles: [mockRoles[2]!],nickname: "张先生", avatarUuid: "", gender: "男", birthday: "1995-12-01", bio: "VIP 用户" },
  { globalId: makeGlobalId(4), username: "vip_wang", email: "wang@example.com", phone: "13800001004", createdAt: "2024-04-05 16:45:00", status: 1, roles: [mockRoles[2]!],nickname: "王小姐", avatarUuid: "", gender: "女", birthday: "1997-06-20", bio: "设计师" },
  { globalId: makeGlobalId(5), username: "user_li", email: "li@example.com", phone: "13800001005", createdAt: "2024-05-18 08:15:00", status: 1, roles: [mockRoles[3]!],nickname: "小李同学", avatarUuid: "", gender: "男", birthday: "2000-01-10", bio: "大学生" },
  { globalId: makeGlobalId(6), username: "user_chen", email: "chen@example.com", phone: "13800001006", createdAt: "2024-06-01 11:30:00", status: 1, roles: [mockRoles[3]!],nickname: "阿晨", avatarUuid: "", gender: "男", birthday: "1999-09-22", bio: "独立开发者" },
  { globalId: makeGlobalId(7), username: "banned_one", email: "banned1@example.com", phone: "13800001007", createdAt: "2024-06-15 10:00:00", status: 2, roles: [mockRoles[3]!],nickname: "违规用户A", avatarUuid: "", gender: "未知", birthday: "2002-04-04", bio: "" },
  { globalId: makeGlobalId(8), username: "banned_two", email: "banned2@example.com", phone: "13800001008", createdAt: "2024-07-01 09:20:00", status: 2, roles: [mockRoles[3]!],nickname: "违规用户B", avatarUuid: "", gender: "未知", birthday: "2003-11-11", bio: "因发布不当内容被封禁" },
  { globalId: makeGlobalId(9), username: "banned_spam", email: "spam@example.com", phone: "13800001009", createdAt: "2024-07-10 15:40:00", status: 2, roles: [mockRoles[3]!],nickname: "垃圾账号001", avatarUuid: "", gender: "未知", birthday: "2000-01-01", bio: "" },
  { globalId: makeGlobalId(10), username: "frozen_liu", email: "liufrozen@example.com", phone: "13800001010", createdAt: "2024-07-22 13:10:00", status: 3, roles: [mockRoles[2]!],nickname: "刘先生", avatarUuid: "", gender: "男", birthday: "1993-05-18", bio: "待核实身份" },
  { globalId: makeGlobalId(11), username: "frozen_zhao", email: "zhao@example.com", phone: "13800001011", createdAt: "2024-08-05 12:00:00", status: 3, roles: [mockRoles[3]!],nickname: "赵女士", avatarUuid: "", gender: "女", birthday: "1996-02-28", bio: "账号存在异常登录" },
  { globalId: makeGlobalId(12), username: "cancelled_one", email: "cancelled@example.com", phone: "13800001012", createdAt: "2024-08-15 08:30:00", status: 0, roles: [mockRoles[3]!],nickname: "已注销用户", avatarUuid: "", gender: "未知", birthday: "", bio: "" },
  { globalId: makeGlobalId(13), username: "user_xu", email: "xu@example.com", phone: "13800001013", createdAt: "2024-09-01 14:00:00", status: 1, roles: [mockRoles[3]!],nickname: "徐徐", avatarUuid: "", gender: "女", birthday: "2001-07-17", bio: "插画师" },
  { globalId: makeGlobalId(14), username: "user_sun", email: "sun@example.com", phone: "13800001014", createdAt: "2024-09-10 16:20:00", status: 1, roles: [mockRoles[3]!],nickname: "阳光", avatarUuid: "", gender: "男", birthday: "1998-12-25", bio: "游戏玩家" },
  { globalId: makeGlobalId(15), username: "mod_huang", email: "huang@yunanexus.com", phone: "13800001015", createdAt: "2024-10-05 10:00:00", status: 1, roles: [mockRoles[1]!, mockRoles[2]!],nickname: "黄管理员", avatarUuid: "", gender: "男", birthday: "1992-03-15", bio: "内容审核" },
  { globalId: makeGlobalId(16), username: "vip_zhou", email: "zhou@example.com", phone: "13800001016", createdAt: "2024-10-20 09:40:00", status: 1, roles: [mockRoles[2]!],nickname: "周先生", avatarUuid: "", gender: "男", birthday: "1994-10-01", bio: "高级会员" },
  { globalId: makeGlobalId(17), username: "user_wu", email: "wu@example.com", phone: "13800001017", createdAt: "2024-11-01 11:15:00", status: 1, roles: [mockRoles[3]!],nickname: "小吴", avatarUuid: "", gender: "女", birthday: "2002-02-14", bio: "学生" },
  { globalId: makeGlobalId(18), username: "frozen_test", email: "testfrozen@example.com", phone: "13800001018", createdAt: "2024-11-12 13:55:00", status: 3, roles: [mockRoles[3]!],nickname: "测试账号", avatarUuid: "", gender: "未知", birthday: "2000-06-06", bio: "风控测试" },
];

// ==================== 状态 ====================
const users = ref<UserVO[]>([]);
const loading = ref(true);
const error = ref("");

// 搜索 & 筛选
const searchKeyword = ref("");
const filterStatus = ref<number | null>(null);
const filterRoleId = ref<number | null>(null);

// 分页
const currentPage = ref(1);
const pageSize = ref(10);
const totalCount = ref(0);

// 选择
const selectedGlobalIds = ref<Set<string>>(new Set());

// 详情面板
const selectedUser = ref<UserVO | null>(null);
const detailTabActive = ref<"profile" | "roles" | "activity">("profile");

// 状态确认对话框
const confirmShow = ref(false);
const confirmTarget = ref<UserVO | null>(null);
const confirmNewStatus = ref(0);
const confirmTitle = computed(() => {
  const labels: Record<number, string> = { 0: "注销", 1: "启用", 2: "封禁", 3: "冻结" };
  return `确认${labels[confirmNewStatus.value] || ""}`;
});
const confirmMessage = computed(() => {
  if (!confirmTarget.value) return "";
  const labels: Record<number, string> = { 0: "注销", 1: "启用", 2: "封禁", 3: "冻结" };
  const name = confirmTarget.value.nickname || confirmTarget.value.username;
  return `确定要将用户「${name}」状态改为「${labels[confirmNewStatus.value]}」吗？`;
});

// 角色分配对话框
const roleDialogShow = ref(false);
const roleDialogTarget = ref<UserVO | null>(null);
const roleDialogSelected = ref<number[]>([]);
const roleDialogSaving = ref(false);

// 批量操作
const batchRoleDialogShow = ref(false);
const batchRoleSelected = ref<number[]>([]);
const batchRoleSaving = ref(false);

// 操作中
const statusUpdating = ref<Set<string>>(new Set());

// ==================== 计算属性 ====================
const statusLabel: Record<number, string> = { 0: "已注销", 1: "正常", 2: "已封禁", 3: "已冻结" };
const statusClass: Record<number, string> = { 0: "status-cancelled", 1: "status-active", 2: "status-banned", 3: "status-frozen" };

const stats = computed(() => ({
  total: users.value.length,
  active: users.value.filter((u) => u.status === 1).length,
  banned: users.value.filter((u) => u.status === 2).length,
  admin: users.value.filter((u) => u.roles.some((r) => r.level >= 60)).length,
}));

const filteredUsers = computed(() => {
  let list = users.value;
  if (filterStatus.value !== null) {
    list = list.filter((u) => u.status === filterStatus.value);
  }
  if (filterRoleId.value !== null) {
    list = list.filter((u) => u.roles.some((r) => r.id === filterRoleId.value));
  }
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase();
    list = list.filter(
      (u) =>
        u.username.toLowerCase().includes(kw) ||
        u.email.toLowerCase().includes(kw) ||
        (u.nickname || "").toLowerCase().includes(kw),
    );
  }
  return list;
});

const paginatedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredUsers.value.slice(start, start + pageSize.value);
});

const totalPages = computed(() => Math.max(1, Math.ceil(filteredUsers.value.length / pageSize.value)));

const allSelected = computed({
  get: () => paginatedUsers.value.length > 0 && paginatedUsers.value.every((u) => selectedGlobalIds.value.has(u.globalId)),
  set: (v: boolean) => {
    for (const u of paginatedUsers.value) {
      if (v) selectedGlobalIds.value.add(u.globalId);
      else selectedGlobalIds.value.delete(u.globalId);
    }
    selectedGlobalIds.value = new Set(selectedGlobalIds.value);
  },
});

const showDetailPanel = computed(() => selectedUser.value !== null);

function roleColorClass(role: RoleVO): string {
  if (role.level >= 99) return "role-super";
  if (role.level >= 60) return "role-admin";
  if (role.level >= 10) return "role-vip";
  return "role-user";
}

function roleLabel(role: RoleVO): string {
  return role.name.replace("_", " ");
}

function getInitials(user: UserVO): string {
  const name = user.nickname || user.username || "";
  if (!name) return "?";
  return name.charAt(0).toUpperCase();
}

function formatDate(str: string): string {
  if (!str) return "--";
  return str.slice(0, 10);
}

function formatDateTime(str: string): string {
  if (!str) return "--";
  return str.slice(0, 16);
}

// ==================== 数据加载 ====================
async function loadUsers() {
  loading.value = true;
  error.value = "";
  if (import.meta.dev) {
    // 模拟延迟
    await new Promise((r) => setTimeout(r, 300));
    users.value = [...mockUsers];
    loading.value = false;
    return;
  }
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const params = new URLSearchParams();
    params.set("page", String(currentPage.value));
    params.set("size", String(pageSize.value));
    if (searchKeyword.value.trim()) params.set("keyword", searchKeyword.value.trim());
    if (filterStatus.value !== null) params.set("status", String(filterStatus.value));
    const res = await fetch<{ code: number; data: PageVO<UserVO>; msg: string }>(
      `/api/admin/users?${params.toString()}`,
    );
    if (res.code === 200) {
      users.value = res.data.records || [];
      totalCount.value = res.data.total || 0;
    } else {
      error.value = res.msg || "获取用户列表失败";
    }
  } catch (e: any) {
    error.value = e?.data?.msg || e?.message || "请求失败";
  } finally {
    loading.value = false;
  }
}

// ==================== 详情面板 ====================
function openDetail(user: UserVO) {
  selectedUser.value = users.value.find((u) => u.globalId === user.globalId) || user;
  detailTabActive.value = "profile";
}

function closeDetail() {
  selectedUser.value = null;
}

// ==================== 状态切换 ====================
function promptStatusChange(user: UserVO, newStatus: number) {
  confirmTarget.value = user;
  confirmNewStatus.value = newStatus;
  confirmShow.value = true;
}

async function doStatusChange() {
  if (!confirmTarget.value) return;
  const user = confirmTarget.value;
  const newStatus = confirmNewStatus.value;
  const uid = user.globalId;
  statusUpdating.value.add(uid);

  if (import.meta.dev) {
    await new Promise((r) => setTimeout(r, 200));
    const target = users.value.find((u) => u.globalId === uid);
    if (target) target.status = newStatus;
    toast.success(`用户状态已更新为「${statusLabel[newStatus]}」`);
    if (selectedUser.value?.globalId === uid) {
      selectedUser.value = users.value.find((u) => u.globalId === uid) || null;
    }
    statusUpdating.value.delete(uid);
    confirmShow.value = false;
    return;
  }
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; msg: string }>(
      `/api/admin/users/${uid}/status`,
      { method: "PUT", body: { status: newStatus } as Record<string, number> },
    );
    if (res.code === 200) {
      toast.success(`用户状态已更新为「${statusLabel[newStatus]}」`);
      await loadUsers();
    } else {
      toast.error(res.msg || "操作失败");
    }
  } catch (e: any) {
    toast.error(e?.data?.msg || e?.message || "操作失败");
  } finally {
    statusUpdating.value.delete(uid);
    confirmShow.value = false;
  }
}

// ==================== 角色分配 ====================
function openRoleDialog(user: UserVO) {
  roleDialogTarget.value = user;
  roleDialogSelected.value = user.roles.map((r) => r.id);
  roleDialogShow.value = true;
}

async function doRoleAssign() {
  if (!roleDialogTarget.value) return;
  roleDialogSaving.value = true;
  const uid = roleDialogTarget.value.globalId;

  if (import.meta.dev) {
    await new Promise((r) => setTimeout(r, 300));
    const target = users.value.find((u) => u.globalId === uid);
    if (target) {
      target.roles = mockRoles.filter((r) => roleDialogSelected.value.includes(r.id));
    }
    toast.success("角色分配成功");
    roleDialogSaving.value = false;
    roleDialogShow.value = false;
    if (selectedUser.value?.globalId === uid) {
      selectedUser.value = users.value.find((u) => u.globalId === uid) || null;
    }
    return;
  }
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; msg: string }>(
      `/api/admin/users/${uid}/roles`,
      { method: "PUT", body: { roleIds: roleDialogSelected.value } as Record<string, number[]> },
    );
    if (res.code === 200) {
      toast.success("角色分配成功");
      roleDialogShow.value = false;
      await loadUsers();
    } else {
      toast.error(res.msg || "操作失败");
    }
  } catch (e: any) {
    toast.error(e?.data?.msg || e?.message || "操作失败");
  } finally {
    roleDialogSaving.value = false;
  }
}

// ==================== 批量操作 ====================
function getSelectedUsers(): UserVO[] {
  return users.value.filter((u) => selectedGlobalIds.value.has(u.globalId));
}

async function batchStatusChange(newStatus: number) {
  const selected = getSelectedUsers();
  if (selected.length === 0) {
    toast.warning("请先选择用户");
    return;
  }
  const labels: Record<number, string> = { 0: "注销", 1: "启用", 2: "封禁", 3: "冻结" };
  if (!confirm(`确定要将 ${selected.length} 个用户的状态改为「${labels[newStatus]}」吗？`)) return;

  if (import.meta.dev) {
    for (const s of selected) {
      const user = users.value.find((u) => u.globalId === s.globalId);
      if (user) user.status = newStatus;
    }
    selectedGlobalIds.value.clear();
    toast.success(`已批量${labels[newStatus]} ${selected.length} 个用户`);
    return;
  }
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    for (const s of selected) {
      await fetch(`/api/admin/users/${s.globalId}/status`, {
        method: "PUT",
        body: { status: newStatus } as Record<string, number>,
      });
    }
    toast.success(`已批量${labels[newStatus]} ${selected.length} 个用户`);
    selectedGlobalIds.value.clear();
    await loadUsers();
  } catch (e: any) {
    toast.error(e?.data?.msg || e?.message || "批量操作失败");
  }
}

function openBatchRoleDialog() {
  const selected = getSelectedUsers();
  if (selected.length === 0) {
    toast.warning("请先选择用户");
    return;
  }
  batchRoleSelected.value = [];
  batchRoleDialogShow.value = true;
}

async function doBatchRoleAssign() {
  if (batchRoleSelected.value.length === 0) {
    toast.warning("请选择角色");
    return;
  }
  batchRoleSaving.value = true;
  const selected = getSelectedUsers();

  if (import.meta.dev) {
    await new Promise((r) => setTimeout(r, 300));
    for (const s of selected) {
      const user = users.value.find((u) => u.globalId === s.globalId);
      if (user) user.roles = mockRoles.filter((r) => batchRoleSelected.value.includes(r.id));
    }
    selectedGlobalIds.value.clear();
    toast.success(`已为 ${selected.length} 个用户批量分配角色`);
    batchRoleSaving.value = false;
    batchRoleDialogShow.value = false;
    return;
  }
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    for (const s of selected) {
      await fetch(`/api/admin/users/${s.globalId}/roles`, {
        method: "PUT",
        body: { roleIds: batchRoleSelected.value } as Record<string, number[]>,
      });
    }
    toast.success(`已为 ${selected.length} 个用户批量分配角色`);
    selectedGlobalIds.value.clear();
    batchRoleDialogShow.value = false;
    await loadUsers();
  } catch (e: any) {
    toast.error(e?.data?.msg || e?.message || "批量分配失败");
  } finally {
    batchRoleSaving.value = false;
  }
}

// ==================== 工具 ====================
function resetFilters() {
  searchKeyword.value = "";
  filterStatus.value = null;
  filterRoleId.value = null;
  currentPage.value = 1;
}

watch([searchKeyword, filterStatus, filterRoleId], () => {
  currentPage.value = 1;
});

watch(currentPage, () => {
  selectedGlobalIds.value.clear();
});

// ==================== 模拟活动数据 ====================
interface ActivityItem {
  action: string;
  detail: string;
  time: string;
  type: "success" | "info" | "warning" | "error";
}

function mockActivityForUser(user: UserVO): ActivityItem[] {
  const base = user.createdAt;
  return [
    { action: "登录", detail: "从 IP 192.168.1.100 登录", time: randomDate(base, "2026-07-09"), type: "info" },
    { action: "更新资料", detail: "修改了个人简介", time: randomDate(base, "2026-07-09"), type: "success" },
    { action: "角色变更", detail: `当前角色: ${user.roles.map((r) => r.name).join(", ")}`, time: randomDate(base, "2026-07-09"), type: "warning" },
    { action: "访问 API", detail: "GET /api/resource/list", time: randomDate(base, "2026-07-09"), type: "info" },
    { action: "文件上传", detail: "上传了 3 个文件", time: randomDate(base, "2026-07-09"), type: "success" },
  ];
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadUsers();
});
</script>

<template>
  <div class="apps-page users-admin-page">
    <!-- 页头 -->
    <section class="page-header fade-up">
      <div class="page-header-left">
        <div class="page-header-overline">Admin Panel / 用户管理</div>
        <h1 class="page-header-title">用户管理</h1>
        <p class="page-header-description">管理平台用户、分配角色、处理异常账号</p>
      </div>
    </section>

    <ClientOnly>
      <!-- 统计卡片 -->
      <div class="stat-row fade-up">
      <div class="stat-card" @click="filterStatus = null; filterRoleId = null">
        <div class="stat-card-number">{{ stats.total }}</div>
        <div class="stat-card-label">总用户数</div>
        <div class="stat-card-sub">全部注册用户</div>
      </div>
      <div class="stat-card stat-card-active" @click="filterStatus = 1; filterRoleId = null">
        <div class="stat-card-number">{{ stats.active }}</div>
        <div class="stat-card-label">正常用户</div>
        <div class="stat-card-sub">状态正常</div>
      </div>
      <div class="stat-card stat-card-warn" @click="filterStatus = 2; filterRoleId = null">
        <div class="stat-card-number">{{ stats.banned }}</div>
        <div class="stat-card-label">已封禁</div>
        <div class="stat-card-sub">违规或被禁</div>
      </div>
      <div class="stat-card stat-card-info" @click="filterRoleId = 2; filterStatus = null">
        <div class="stat-card-number">{{ stats.admin }}</div>
        <div class="stat-card-label">管理员</div>
        <div class="stat-card-sub">含超管和普通管理</div>
      </div>
    </div>

    <!-- 搜索 & 筛选 -->
    <div class="users-toolbar fade-up">
      <div class="users-search-wrap">
        <Icon name="lucide:search" size="15" class="users-search-icon" />
        <input
          v-model="searchKeyword"
          class="users-search-input"
          placeholder="搜索用户名 / 邮箱 / 昵称…"
          type="text"
        />
        <button v-if="searchKeyword" class="users-search-clear" @click="searchKeyword = ''">
          <Icon name="lucide:x" size="14" />
        </button>
      </div>
      <div class="users-filter-group">
        <select v-model="filterStatus" class="users-filter-select">
          <option :value="null">全部状态</option>
          <option :value="1">正常</option>
          <option :value="2">已封禁</option>
          <option :value="3">已冻结</option>
          <option :value="0">已注销</option>
        </select>
        <select v-model="filterRoleId" class="users-filter-select">
          <option :value="null">全部角色</option>
          <option v-for="r in mockRoles" :key="r.id" :value="r.id">{{ r.name }}</option>
        </select>
        <button
          v-if="searchKeyword || filterStatus !== null || filterRoleId !== null"
          class="button button-small"
          @click="resetFilters"
        >
          重置
        </button>
      </div>
    </div>

    <!-- 批量操作栏 -->
    <div v-if="selectedGlobalIds.size > 0" class="users-batch-bar fade-up">
      <span class="users-batch-count">已选择 {{ selectedGlobalIds.size }} 项</span>
      <button class="button button-small" @click="batchStatusChange(1)">批量启用</button>
      <button class="button button-small button-batch-ban" @click="batchStatusChange(2)">批量封禁</button>
      <button class="button button-small" @click="batchStatusChange(3)">批量冻结</button>
      <button class="button button-small" @click="openBatchRoleDialog">批量分配角色</button>
      <button class="button button-small button-ghost" @click="selectedGlobalIds.clear()">取消选择</button>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="users-states fade-up">
      <div class="users-loading-table">
        <div v-for="i in 6" :key="i" class="users-loading-row">
          <div class="skeleton" style="width: 32px; height: 32px; border-radius: 50%" />
          <div class="skeleton" style="width: 120px; height: 14px" />
          <div class="skeleton" style="width: 160px; height: 14px" />
          <div class="skeleton" style="width: 100px; height: 14px" />
          <div class="skeleton" style="width: 60px; height: 22px" />
          <div class="skeleton" style="width: 90px; height: 14px" />
          <div class="skeleton" style="width: 60px; height: 28px" />
        </div>
      </div>
    </div>

    <!-- 错误态 -->
    <div v-else-if="error" class="users-states fade-up">
      <div class="users-empty">
        <Icon name="lucide:alert-circle" size="40" style="opacity: 0.3; color: var(--color-error)" />
        <p class="users-empty-title">加载失败</p>
        <p class="users-empty-desc">{{ error }}</p>
        <button class="button button-primary" @click="loadUsers">重新加载</button>
      </div>
    </div>

    <!-- 空态 -->
    <div v-else-if="filteredUsers.length === 0" class="users-states fade-up">
      <div class="users-empty">
        <Icon name="lucide:users-round" size="40" style="opacity: 0.3" />
        <p class="users-empty-title">暂无用户</p>
        <p class="users-empty-desc">
          {{ searchKeyword || filterStatus !== null || filterRoleId !== null ? "没有匹配的用户，请调整筛选条件" : "用户数据为空" }}
        </p>
        <button v-if="searchKeyword || filterStatus !== null || filterRoleId !== null" class="button" @click="resetFilters">
          重置筛选
        </button>
      </div>
    </div>

    <!-- 用户表格 & 详情双栏 -->
    <div v-else class="users-layout fade-up">
      <!-- 左栏：表格 -->
      <div :class="['users-table-col', { 'has-detail': showDetailPanel }]">
        <div class="users-table-wrap panel-card" style="padding: 0; overflow: hidden">
          <table class="users-table">
            <thead>
              <tr>
                <th class="col-check">
                  <label class="users-checkbox">
                    <input type="checkbox" v-model="allSelected" />
                    <span class="users-checkbox-mark"><Icon v-if="allSelected" name="lucide:check" size="12" /></span>
                  </label>
                </th>
                <th class="col-user">用户</th>
                <th class="col-email">邮箱</th>
                <th class="col-phone">手机号</th>
                <th class="col-roles">角色</th>
                <th class="col-status">状态</th>
                <th class="col-date">注册时间</th>
                <th class="col-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="user in paginatedUsers"
                :key="user.globalId"
                :class="['users-row', { selected: selectedGlobalIds.has(user.globalId), active: selectedUser?.globalId === user.globalId }]"
                @click="openDetail(user)"
              >
                <td class="col-check" @click.stop>
                  <label class="users-checkbox">
                    <input
                      type="checkbox"
                      :checked="selectedGlobalIds.has(user.globalId)"
                      @change="(e: Event) => {
                        const checked = (e.target as HTMLInputElement).checked;
                        if (checked) selectedGlobalIds.add(user.globalId);
                        else selectedGlobalIds.delete(user.globalId);
                        selectedGlobalIds = new Set(selectedGlobalIds);
                      }"
                    />
                    <span class="users-checkbox-mark">
                      <Icon v-if="selectedGlobalIds.has(user.globalId)" name="lucide:check" size="12" />
                    </span>
                  </label>
                </td>
                <td class="col-user">
                  <div class="users-cell-user">
                    <div class="users-avatar" :class="roleColorClass((user.roles[0] || mockRoles[3])!)">
                      {{ getInitials(user) }}
                    </div>
                    <div class="users-cell-user-text">
                      <span class="users-cell-username">{{ user.username }}</span>
                      <span class="users-cell-nickname">{{ user.nickname || "--" }}</span>
                    </div>
                  </div>
                </td>
                <td class="col-email">
                  <span class="users-cell-text">{{ user.email || "--" }}</span>
                </td>
                <td class="col-phone">
                  <span class="users-cell-text">{{ user.phone || "--" }}</span>
                </td>
                <td class="col-roles">
                  <div class="users-role-tags">
                    <span v-if="user.roles.length === 0" class="tag">无角色</span>
                    <span
                      v-for="role in user.roles"
                      :key="role.id"
                      :class="['users-role-tag', roleColorClass(role)]"
                    >
                      {{ roleLabel(role) }}
                    </span>
                  </div>
                </td>
                <td class="col-status">
                  <span :class="['users-status-badge', statusClass[user.status]]">
                    <span class="users-status-dot" />
                    {{ statusLabel[user.status] }}
                  </span>
                </td>
                <td class="col-date">
                  <span class="users-cell-text">{{ formatDate(user.createdAt) }}</span>
                </td>
                <td class="col-actions" @click.stop>
                  <div class="users-actions-group">
                    <button
                      v-if="user.status !== 1"
                      class="users-action-btn users-action-enable"
                      :disabled="statusUpdating.has(user.globalId)"
                      title="启用"
                      @click="promptStatusChange(user, 1)"
                    >
                      <Icon name="lucide:check-circle" size="14" />
                    </button>
                    <button
                      v-if="user.status !== 2"
                      class="users-action-btn users-action-ban"
                      :disabled="statusUpdating.has(user.globalId)"
                      title="封禁"
                      @click="promptStatusChange(user, 2)"
                    >
                      <Icon name="lucide:ban" size="14" />
                    </button>
                    <button
                      v-if="user.status !== 3"
                      class="users-action-btn users-action-freeze"
                      :disabled="statusUpdating.has(user.globalId)"
                      title="冻结"
                      @click="promptStatusChange(user, 3)"
                    >
                      <Icon name="lucide:snowflake" size="14" />
                    </button>
                    <button
                      class="users-action-btn users-action-role"
                      title="分配角色"
                      @click="openRoleDialog(user)"
                    >
                      <Icon name="lucide:shield-check" size="14" />
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 分页 -->
        <div class="users-pagination" v-if="totalPages > 1">
          <button
            class="button button-small"
            :disabled="currentPage <= 1"
            @click="currentPage = Math.max(1, currentPage - 1)"
          >
            <Icon name="lucide:chevron-left" size="14" />
          </button>
          <template v-for="p in totalPages" :key="p">
            <button
              v-if="p === 1 || p === totalPages || Math.abs(p - currentPage) <= 1"
              :class="['button button-small', { 'button-primary': p === currentPage }]"
              @click="currentPage = p"
            >
              {{ p }}
            </button>
            <span v-else-if="p === 2 || p === totalPages - 1" class="users-pagination-ellipsis">…</span>
          </template>
          <button
            class="button button-small"
            :disabled="currentPage >= totalPages"
            @click="currentPage = Math.min(totalPages, currentPage + 1)"
          >
            <Icon name="lucide:chevron-right" size="14" />
          </button>
          <span class="users-pagination-info">
            {{ filteredUsers.length }} 条结果
          </span>
        </div>
      </div>

      <!-- 右栏：详情面板 -->
      <div v-if="showDetailPanel" class="users-detail-col">
        <div class="users-detail-card panel-card" style="padding: 0">
          <div class="users-detail-header">
            <div class="users-detail-user-info">
              <div class="users-avatar users-avatar-lg" :class="roleColorClass((selectedUser!.roles[0] || mockRoles[3])!)">
                {{ getInitials(selectedUser!) }}
              </div>
              <div>
                <h3 class="users-detail-name">{{ selectedUser!.nickname || selectedUser!.username }}</h3>
                <p class="users-detail-username">@{{ selectedUser!.username }}</p>
              </div>
            </div>
            <button class="users-detail-close" @click="closeDetail">
              <Icon name="lucide:x" size="16" />
            </button>
          </div>

          <!-- Tab 切换 -->
          <div class="users-detail-tabs">
            <button
              :class="['users-detail-tab', { active: detailTabActive === 'profile' }]"
              @click="detailTabActive = 'profile'"
            >
              <Icon name="lucide:user" size="13" /> 基本资料
            </button>
            <button
              :class="['users-detail-tab', { active: detailTabActive === 'roles' }]"
              @click="detailTabActive = 'roles'"
            >
              <Icon name="lucide:shield" size="13" /> 角色权限
            </button>
            <button
              :class="['users-detail-tab', { active: detailTabActive === 'activity' }]"
              @click="detailTabActive = 'activity'"
            >
              <Icon name="lucide:activity" size="13" /> 近期活动
            </button>
          </div>

          <!-- 基本资料 -->
          <div v-if="detailTabActive === 'profile'" class="users-detail-body">
            <!-- 身份信息 -->
            <div class="detail-section">
              <h4 class="detail-section-title">身份信息</h4>
              <div class="detail-info-list">
                <div class="detail-info-item">
                  <span class="detail-info-label">全局 ID</span>
                  <code class="detail-code">{{ selectedUser!.globalId }}</code>
                </div>
                <div class="detail-info-item">
                  <span class="detail-info-label">用户名</span>
                  <span class="detail-info-value">@{{ selectedUser!.username }}</span>
                </div>
                <div class="detail-info-item">
                  <span class="detail-info-label">状态</span>
                  <span :class="['detail-status-tag', statusClass[selectedUser!.status]]">
                    {{ statusLabel[selectedUser!.status] }}
                  </span>
                </div>
                <div class="detail-info-item">
                  <span class="detail-info-label">注册时间</span>
                  <span class="detail-info-value">{{ formatDateTime(selectedUser!.createdAt) }}</span>
                </div>
              </div>
            </div>

            <!-- 联系方式 -->
            <div class="detail-section">
              <h4 class="detail-section-title">联系方式</h4>
              <div class="detail-info-list">
                <div class="detail-info-item">
                  <span class="detail-info-label">邮箱</span>
                  <span class="detail-info-value">{{ selectedUser!.email || "未设置" }}</span>
                </div>
                <div class="detail-info-item">
                  <span class="detail-info-label">手机号</span>
                  <span class="detail-info-value">{{ selectedUser!.phone || "未设置" }}</span>
                </div>
              </div>
            </div>

            <!-- 个人资料 -->
            <div class="detail-section">
              <h4 class="detail-section-title">个人资料</h4>
              <div class="detail-info-list">
                <div class="detail-info-item">
                  <span class="detail-info-label">昵称</span>
                  <span class="detail-info-value">{{ selectedUser!.nickname || "未设置" }}</span>
                </div>
                <div class="detail-info-item">
                  <span class="detail-info-label">性别</span>
                  <span class="detail-info-value">{{ selectedUser!.gender || "未知" }}</span>
                </div>
                <div class="detail-info-item">
                  <span class="detail-info-label">生日</span>
                  <span class="detail-info-value">{{ selectedUser!.birthday || "未设置" }}</span>
                </div>
                <div class="detail-info-item">
                  <span class="detail-info-label">个人简介</span>
                  <span class="detail-info-value">{{ selectedUser!.bio || "这个人很懒，什么都没写…" }}</span>
                </div>
              </div>
            </div>

            <!-- 快捷操作 -->
            <div class="users-detail-actions">
              <button
                v-if="selectedUser!.status !== 1"
                class="button button-small"
                @click="promptStatusChange(selectedUser!, 1)"
              >
                <Icon name="lucide:check-circle" size="13" /> 启用
              </button>
              <button
                v-if="selectedUser!.status !== 2"
                class="button button-small"
                @click="promptStatusChange(selectedUser!, 2)"
              >
                <Icon name="lucide:ban" size="13" /> 封禁
              </button>
              <button
                v-if="selectedUser!.status !== 3"
                class="button button-small"
                @click="promptStatusChange(selectedUser!, 3)"
              >
                <Icon name="lucide:snowflake" size="13" /> 冻结
              </button>
              <button class="button button-small" @click="openRoleDialog(selectedUser!)">
                <Icon name="lucide:shield-check" size="13" /> 分配角色
              </button>
            </div>
          </div>

          <!-- 角色权限 -->
          <div v-if="detailTabActive === 'roles'" class="users-detail-body" style="padding: 14px 20px">
            <div v-if="selectedUser!.roles.length === 0" class="users-detail-empty-text">
              该用户暂无角色
            </div>
            <div v-else class="users-roles-list">
              <div v-for="role in selectedUser!.roles" :key="role.id" :class="['users-role-card', roleColorClass(role)]">
                <div class="users-role-card-header">
                  <span class="users-role-card-name">{{ roleLabel(role) }}</span>
                  <span class="users-role-card-level">Level {{ role.level }}</span>
                </div>
                <div class="users-role-card-perms">
                  <code v-for="perm in role.permissions" :key="perm" class="detail-code">{{ perm }}</code>
                </div>
              </div>
            </div>
          </div>

          <!-- 近期活动 -->
          <div v-if="detailTabActive === 'activity'" class="users-detail-body" style="padding: 14px 20px">
            <div class="activity-list">
              <div v-for="(act, idx) in mockActivityForUser(selectedUser!)" :key="idx" class="activity-item">
                <div :class="['activity-dot', { green: act.type === 'success', blue: act.type === 'info', amber: act.type === 'warning' }]" />
                <div class="activity-title">
                  <strong>{{ act.action }}</strong> · {{ act.detail }}
                </div>
                <span class="activity-time">{{ formatDateTime(act.time) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 无选中详情占位 -->
      <div v-else class="users-detail-col users-detail-placeholder">
        <div class="admin-detail-empty">
          <Icon name="lucide:pointer" size="24" style="opacity: 0.2" />
          <span>点击左侧用户行查看详情</span>
        </div>
      </div>
    </div>

    <!-- 状态确认对话框 -->
    <Modal :show="confirmShow" :title="confirmTitle" width="400px" @close="confirmShow = false">
      <p>{{ confirmMessage }}</p>
      <template #footer>
        <button class="button" @click="confirmShow = false">取消</button>
        <button class="button button-primary" @click="doStatusChange">确认</button>
      </template>
    </Modal>

    <!-- 角色分配对话框 -->
    <Modal :show="roleDialogShow" title="分配角色" width="480px" @close="roleDialogShow = false">
      <p style="margin-bottom: 12px">
        为用户
        <strong>{{ roleDialogTarget?.nickname || roleDialogTarget?.username }}</strong>
        分配角色：
      </p>
      <div class="users-role-checkboxes">
        <label
          v-for="role in mockRoles"
          :key="role.id"
          :class="['users-role-checkbox-item', { checked: roleDialogSelected.includes(role.id) }]"
        >
          <input
            type="checkbox"
            :value="role.id"
            :checked="roleDialogSelected.includes(role.id)"
            @change="(e: Event) => {
              const checked = (e.target as HTMLInputElement).checked;
              if (checked) roleDialogSelected.push(role.id);
              else roleDialogSelected = roleDialogSelected.filter((id) => id !== role.id);
            }"
          />
          <span class="users-role-checkbox-label">
            <span class="users-role-checkbox-name">{{ roleLabel(role) }}</span>
            <span class="users-role-checkbox-level">Level {{ role.level }}</span>
          </span>
        </label>
      </div>
      <template #footer>
        <button class="button" @click="roleDialogShow = false">取消</button>
        <button class="button button-primary" :disabled="roleDialogSaving" @click="doRoleAssign">
          {{ roleDialogSaving ? "保存中…" : "保存" }}
        </button>
      </template>
    </Modal>

    <!-- 批量角色分配对话框 -->
    <Modal :show="batchRoleDialogShow" title="批量分配角色" width="480px" @close="batchRoleDialogShow = false">
      <p style="margin-bottom: 12px">
        为已选择的 <strong>{{ selectedGlobalIds.size }}</strong> 个用户批量分配角色：
      </p>
      <div class="users-role-checkboxes">
        <label
          v-for="role in mockRoles"
          :key="role.id"
          :class="['users-role-checkbox-item', { checked: batchRoleSelected.includes(role.id) }]"
        >
          <input
            type="checkbox"
            :value="role.id"
            :checked="batchRoleSelected.includes(role.id)"
            @change="(e: Event) => {
              const checked = (e.target as HTMLInputElement).checked;
              if (checked) batchRoleSelected.push(role.id);
              else batchRoleSelected = batchRoleSelected.filter((id) => id !== role.id);
            }"
          />
          <span class="users-role-checkbox-label">
            <span class="users-role-checkbox-name">{{ roleLabel(role) }}</span>
            <span class="users-role-checkbox-level">Level {{ role.level }}</span>
          </span>
        </label>
      </div>
      <template #footer>
        <button class="button" @click="batchRoleDialogShow = false">取消</button>
        <button class="button button-primary" :disabled="batchRoleSaving" @click="doBatchRoleAssign">
          {{ batchRoleSaving ? "保存中…" : "批量保存" }}
        </button>
      </template>
    </Modal>
    </ClientOnly>
  </div>
</template>

<style scoped>
/* ==================== 页面容器 ==================== */
.users-admin-page {
  max-width: 100%;
  margin: 0 auto;
  width: 100%;
  display: grid;
  gap: 20px;
  padding-bottom: 32px;
}

/* ==================== 统计卡片变体 ==================== */
.stat-card-active .stat-card-number { color: var(--color-success); }
.stat-card-warn .stat-card-number { color: var(--color-error); }
.stat-card-info .stat-card-number { color: var(--color-info); }

.stat-card {
  cursor: pointer;
  user-select: none;
}

.stat-card:active {
  transform: scale(0.98);
}

/* ==================== 搜索工具栏 ==================== */
.users-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.users-search-wrap {
  position: relative;
  flex: 1;
  min-width: 220px;
  max-width: 380px;
}

.users-search-icon {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-font-assist);
  pointer-events: none;
}

.users-search-input {
  width: 100%;
  height: 36px;
  padding: 0 32px 0 30px;
  font-size: 13px;
  font-family: inherit;
  color: var(--color-font);
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  outline: none;
  transition: border-color 0.15s;
  box-sizing: border-box;
}

.users-search-input:focus {
  border-color: var(--color-emphasis);
  box-shadow: 0 0 0 2px var(--color-emphasis-soft);
}

.users-search-input::placeholder {
  color: var(--color-font-assist);
  opacity: 0.6;
}

.users-search-clear {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--color-font-assist);
  cursor: pointer;
  border-radius: var(--radius-sm);
}

.users-search-clear:hover {
  background: var(--color-primary-background);
  color: var(--color-font);
}

.users-filter-group {
  display: flex;
  gap: 8px;
  align-items: center;
}

.users-filter-select {
  height: 36px;
  padding: 0 28px 0 10px;
  font-size: 13px;
  font-family: inherit;
  color: var(--color-font);
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  outline: none;
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%23a0a0a0' stroke-width='2'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 8px center;
  transition: border-color 0.15s;
}

.users-filter-select:focus {
  border-color: var(--color-emphasis);
}

/* ==================== 批量操作栏 ==================== */
.users-batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: var(--color-card);
  border: 1px solid var(--color-emphasis);
  border-radius: var(--radius-md);
  flex-wrap: wrap;
  box-shadow: 0 0 0 2px var(--color-emphasis-soft);
}

.users-batch-count {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-emphasis);
  margin-right: 4px;
}

.button-batch-ban {
  color: var(--color-error);
}

/* ==================== 加载/空/错误态 ==================== */
.users-states {
  min-height: 300px;
}

.users-loading-table {
  background: var(--color-card);
  border-radius: var(--radius-lg);
  padding: 8px 14px;
  box-shadow: var(--shadow-card);
  display: grid;
  gap: 4px;
}

.users-loading-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border);
}

.users-loading-row:last-child {
  border-bottom: none;
}

.users-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  text-align: center;
  background: var(--color-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.users-empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-font);
  margin-top: 12px;
  margin-bottom: 4px;
}

.users-empty-desc {
  font-size: 13px;
  color: var(--color-font-assist);
  margin-bottom: 16px;
  max-width: 320px;
}

/* ==================== 双栏布局 ==================== */
.users-layout {
  display: grid;
  grid-template-columns: 1fr 420px;
  gap: 24px;
  align-items: start;
}

.users-table-col {
  min-width: 0;
}

.users-table-col.has-detail {
  /* 当有详情面板时保持自身宽度 */
}

/* ==================== 表格 ==================== */
.users-table-wrap {
  overflow-x: auto;
}

.users-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.users-table thead {
  background: var(--color-primary-background);
  border-bottom: 1px solid var(--color-border);
}

.users-table th {
  padding: 12px 12px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-font-assist);
  text-align: left;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  white-space: nowrap;
}

.users-table th.col-check {
  width: 40px;
  text-align: center;
  padding-left: 14px;
  padding-right: 4px;
}

.users-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-border);
  vertical-align: middle;
}

.users-table td.col-check {
  text-align: center;
  padding-left: 14px;
  padding-right: 4px;
}

.users-row {
  cursor: pointer;
  transition: background 0.1s;
}

.users-row:hover {
  background: var(--color-primary-background);
}

.users-row.selected {
  background: var(--color-emphasis-soft);
}

.users-row.active {
  background: var(--color-emphasis-soft);
  border-left: 3px solid var(--color-emphasis);
}

/* ==================== 复选框 ==================== */
.users-checkbox {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  position: relative;
}

.users-checkbox input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.users-checkbox-mark {
  width: 16px;
  height: 16px;
  border-radius: 3px;
  border: 1.5px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  transition: all 0.15s;
  background: var(--color-card);
}

.users-checkbox input:checked + .users-checkbox-mark {
  background: var(--color-emphasis);
  border-color: var(--color-emphasis);
}

/* ==================== 表格单元格 ==================== */
.users-cell-user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.users-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  flex-shrink: 0;
}

.users-avatar-lg {
  width: 44px;
  height: 44px;
  font-size: 16px;
}

.role-super {
  background: linear-gradient(135deg, #7c3aed, #a855f7);
}

.role-admin {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
}

.role-vip {
  background: linear-gradient(135deg, #d97706, #f59e0b);
}

.role-user {
  background: linear-gradient(135deg, #6b7280, #9ca3af);
}

.users-cell-user-text {
  display: grid;
  gap: 1px;
  min-width: 0;
}

.users-cell-username {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-font);
}

.users-cell-nickname {
  font-size: 11px;
  color: var(--color-font-assist);
}

.users-cell-text {
  font-size: 12px;
  color: var(--color-font-secondary);
  font-family: var(--font-mono);
}

/* ==================== 角色标签 ==================== */
.users-role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.users-role-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

.users-role-tag.role-super {
  background: rgba(124, 58, 237, 0.1);
  color: #7c3aed;
  border: 1px solid rgba(124, 58, 237, 0.2);
}

.users-role-tag.role-admin {
  background: rgba(37, 99, 235, 0.1);
  color: #2563eb;
  border: 1px solid rgba(37, 99, 235, 0.2);
}

.users-role-tag.role-vip {
  background: rgba(217, 119, 6, 0.1);
  color: #b45309;
  border: 1px solid rgba(217, 119, 6, 0.2);
}

.users-role-tag.role-user {
  background: var(--color-primary-background);
  color: var(--color-font-secondary);
  border: 1px solid var(--color-border);
}

/* ==================== 状态徽章 ==================== */
.users-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.users-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-active {
  color: var(--color-success);
  background: var(--color-success-soft);
}
.status-active .users-status-dot {
  background: var(--color-success);
}

.status-banned {
  color: var(--color-error);
  background: var(--color-error-soft);
}
.status-banned .users-status-dot {
  background: var(--color-error);
}

.status-frozen {
  color: var(--color-info);
  background: var(--color-info-soft);
}
.status-frozen .users-status-dot {
  background: var(--color-info);
}

.status-cancelled {
  color: var(--color-font-assist);
  background: var(--color-primary-background);
}
.status-cancelled .users-status-dot {
  background: var(--color-font-assist);
}

/* ==================== 操作按钮 ==================== */
.users-actions-group {
  display: flex;
  gap: 2px;
}

.users-action-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-font-assist);
  cursor: pointer;
  transition: all 0.12s;
}

.users-action-btn:hover {
  background: var(--color-primary-background);
  color: var(--color-font);
}

.users-action-enable:hover {
  color: var(--color-success);
  background: var(--color-success-soft);
}

.users-action-ban:hover {
  color: var(--color-error);
  background: var(--color-error-soft);
}

.users-action-freeze:hover {
  color: var(--color-info);
  background: var(--color-info-soft);
}

.users-action-role:hover {
  color: #7c3aed;
  background: rgba(124, 58, 237, 0.08);
}

.users-action-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
  pointer-events: none;
}

/* ==================== 分页 ==================== */
.users-pagination {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 16px;
  justify-content: center;
}

.users-pagination-ellipsis {
  font-size: 13px;
  color: var(--color-font-assist);
  padding: 0 4px;
}

.users-pagination-info {
  font-size: 12px;
  color: var(--color-font-assist);
  margin-left: 12px;
}

/* ==================== 详情面板 ==================== */
.users-detail-col {
  position: sticky;
  top: 20px;
}

.users-detail-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}

.users-detail-card {
  overflow: hidden;
}

.users-detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--color-border);
}

.users-detail-user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.users-detail-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-font);
}

.users-detail-username {
  font-size: 12px;
  color: var(--color-font-assist);
  font-family: var(--font-mono);
  margin-top: 2px;
}

.users-detail-close {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-font-assist);
  cursor: pointer;
  transition: all 0.1s;
}

.users-detail-close:hover {
  background: var(--color-primary-background);
  color: var(--color-font);
}

/* ==================== 详情 Tab ==================== */
.users-detail-tabs {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--color-border);
  padding: 0 16px;
}

.users-detail-tab {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 10px 14px;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-font-assist);
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  cursor: pointer;
  transition: all 0.12s;
  font-family: inherit;
  margin-bottom: -1px;
}

.users-detail-tab:hover {
  color: var(--color-font-secondary);
}

.users-detail-tab.active {
  color: var(--color-emphasis);
  border-bottom-color: var(--color-emphasis);
}

/* ==================== 详情内容 ==================== */
.users-detail-body {
  padding: 0;
  display: grid;
  gap: 0;
}

.detail-section {
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-separator);
}
.detail-section:last-of-type { border-bottom: none; }

.detail-section-title {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.6px;
  color: var(--color-font-assist);
  margin: 0 0 8px;
}

.detail-info-list {
  display: grid;
  gap: 0;
}
.detail-info-item {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-separator);
  align-items: center;
}
.detail-info-item:last-child { border-bottom: none; }
.detail-info-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-font-secondary);
  flex-shrink: 0;
}
.detail-info-value {
  font-size: 13px;
  color: var(--color-font);
  word-break: break-all;
}

.detail-code {
  font-family: var(--font-mono);
  font-size: 11px;
  padding: 2px 8px;
  background: var(--color-primary-background);
  border-radius: var(--radius-sm);
  color: var(--color-font-secondary);
  word-break: break-all;
  justify-self: start;
}

.detail-status-tag {
  display: inline-flex;
  align-items: center;
  justify-self: start;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 500;
}
.detail-status-tag.status-active { background: var(--color-success-soft); color: var(--color-success); }
.detail-status-tag.status-banned { background: var(--color-error-soft); color: var(--color-error); }
.detail-status-tag.status-frozen { background: var(--color-warning-soft); color: var(--color-warning); }
.detail-status-tag.status-cancelled { background: var(--color-primary-background); color: var(--color-font-assist); }

.users-detail-empty-text {
  text-align: center;
  padding: 24px;
  color: var(--color-font-assist);
  font-size: 13px;
}

.users-detail-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 4px 20px 14px;
}

/* ==================== 角色卡片 ==================== */
.users-roles-list {
  display: grid;
  gap: 10px;
}

.users-role-card {
  padding: 12px 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
}

.users-role-card.role-super {
  border-color: rgba(124, 58, 237, 0.25);
  background: rgba(124, 58, 237, 0.04);
}

.users-role-card.role-admin {
  border-color: rgba(37, 99, 235, 0.25);
  background: rgba(37, 99, 235, 0.04);
}

.users-role-card.role-vip {
  border-color: rgba(217, 119, 6, 0.25);
  background: rgba(217, 119, 6, 0.04);
}

.users-role-card.role-user {
  border-color: var(--color-border);
  background: var(--color-primary-background);
}

.users-role-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.users-role-card-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-font);
}

.users-role-card-level {
  font-size: 11px;
  font-family: var(--font-mono);
  color: var(--color-font-assist);
}

.users-role-card-perms {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

/* ==================== 角色复选框列表 ==================== */
.users-role-checkboxes {
  display: grid;
  gap: 6px;
}

.users-role-checkbox-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.12s;
}

.users-role-checkbox-item:hover {
  border-color: var(--color-emphasis);
  background: var(--color-emphasis-soft);
}

.users-role-checkbox-item.checked {
  border-color: var(--color-emphasis);
  background: var(--color-emphasis-soft);
}

.users-role-checkbox-item input {
  accent-color: var(--color-emphasis);
  width: 15px;
  height: 15px;
  cursor: pointer;
}

.users-role-checkbox-label {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.users-role-checkbox-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-font);
}

.users-role-checkbox-level {
  font-size: 11px;
  color: var(--color-font-assist);
  font-family: var(--font-mono);
}

/* ==================== 响应式 ==================== */
@media (max-width: 1100px) {
  .users-layout {
    grid-template-columns: 1fr;
  }

  .users-detail-col {
    position: static;
    order: -1;
  }
}

@media (max-width: 768px) {
  .users-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .users-search-wrap {
    max-width: 100%;
  }

  .users-filter-group {
    flex-wrap: wrap;
  }

  .users-table .col-phone {
    display: none;
  }

  .stat-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .users-batch-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 480px) {
  .users-table .col-email,
  .users-table .col-roles {
    display: none;
  }

  .stat-row {
    grid-template-columns: 1fr;
  }
}
</style>