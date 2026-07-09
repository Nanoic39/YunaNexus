<script setup lang="ts">
import { useToast } from "~/composables/useToast";

definePageMeta({ layout: "default" });

const toast = useToast();

// ===== Types =====
interface RoleItem {
  id: number;
  name: string;
  level: number;
  permissions: string[];
  status: number;    // 0=取消, 1=启用, 2=删除
  userCount: number;
  createdAt: string;
}

interface RoleForm {
  name: string;
  level: number;
  permissions: string[];
  status: number;
}

interface PermLeaf {
  code: string;
  label: string;
}

interface PermTreeNode {
  key: string;
  label: string;
  children?: (PermTreeNode | PermLeaf)[];
}

// ===== Permission Tree =====
const permTree: PermTreeNode[] = [
  {
    key: "core", label: "core（核心功能）", children: [
      {
        key: "core:identity", label: "用户身份", children: [
          {
            key: "core:identity:basic", label: "基础信息", children: [
              { code: "core:identity:basic:read", label: "读取 UUID / 用户名" },
            ],
          },
          {
            key: "core:identity:profile", label: "个人资料", children: [
              { code: "core:identity:profile:read", label: "读取资料" },
              { code: "core:identity:profile:write", label: "编辑资料" },
            ],
          },
          {
            key: "core:identity:avatar", label: "头像", children: [
              { code: "core:identity:avatar:read", label: "读取头像" },
              { code: "core:identity:avatar:write", label: "上传头像" },
            ],
          },
          {
            key: "core:identity:contact", label: "联系方式", children: [
              { code: "core:identity:email:read", label: "读取邮箱" },
              { code: "core:identity:phone:read", label: "读取手机" },
            ],
          },
        ],
      },
      {
        key: "core:file", label: "文件服务", children: [
          {
            key: "core:file:list", label: "文件列表", children: [
              { code: "core:file:list:read", label: "列举文件" },
            ],
          },
          {
            key: "core:file:content", label: "文件内容", children: [
              { code: "core:file:content:read", label: "下载文件" },
              { code: "core:file:content:write", label: "上传文件" },
              { code: "core:file:content:delete", label: "删除文件" },
            ],
          },
        ],
      },
      {
        key: "core:apps", label: "应用管理", children: [
          {
            key: "core:apps:client", label: "OAuth 客户端", children: [
              { code: "core:apps:client:list", label: "查看列表" },
              { code: "core:apps:client:read", label: "查看详情" },
              { code: "core:apps:client:write", label: "创建/编辑" },
              { code: "core:apps:client:delete", label: "删除应用" },
            ],
          },
          {
            key: "core:apps:secret", label: "密钥管理", children: [
              { code: "core:apps:secret:read", label: "查看密钥" },
            ],
          },
        ],
      },
    ],
  },
  {
    key: "admin", label: "admin（管理功能）", children: [
      {
        key: "admin:users", label: "用户管理", children: [
          {
            key: "admin:users:list", label: "用户列表", children: [
              { code: "admin:users:list", label: "查看列表" },
              { code: "admin:users:read", label: "查看详情" },
            ],
          },
          {
            key: "admin:users:write", label: "用户操作", children: [
              { code: "admin:users:write", label: "编辑用户" },
              { code: "admin:users:delete", label: "删除用户" },
              { code: "admin:users:status", label: "启用/禁用用户" },
              { code: "admin:users:roles", label: "管理角色" },
            ],
          },
        ],
      },
      {
        key: "admin:oauth", label: "OAuth 审核", children: [
          {
            key: "admin:oauth:audit", label: "应用审核", children: [
              { code: "admin:oauth:audit:list", label: "查看列表" },
              { code: "admin:oauth:audit:read", label: "查看详情" },
              { code: "admin:oauth:audit:approve", label: "通过审核" },
              { code: "admin:oauth:audit:reject", label: "拒绝申请" },
            ],
          },
          {
            key: "admin:oauth:toggle", label: "应用管理", children: [
              { code: "admin:oauth:toggle", label: "启用/禁用应用" },
              { code: "admin:oauth:client:delete", label: "删除应用" },
            ],
          },
        ],
      },
      {
        key: "admin:system", label: "系统管理", children: [
          {
            key: "admin:system:resources", label: "资源权限", children: [
              { code: "admin:system:resources:read", label: "查看资源" },
              { code: "admin:system:resources:write", label: "编辑资源" },
            ],
          },
          {
            key: "admin:system:roles", label: "角色管理", children: [
              { code: "admin:system:roles:read", label: "查看角色" },
              { code: "admin:system:roles:write", label: "编辑角色" },
            ],
          },
          {
            key: "admin:system:endpoints", label: "端点管理", children: [
              { code: "admin:system:endpoints:read", label: "查看端点" },
              { code: "admin:system:endpoints:write", label: "编辑端点" },
            ],
          },
        ],
      },
    ],
  },
  {
    key: "special", label: "special（特殊权限）", children: [
      {
        key: "special:offline", label: "离线访问", children: [
          { code: "special:offline_access", label: "Refresh Token 离线访问" },
        ],
      },
    ],
  },
];

// Flatten tree to get all leaf perm codes
function collectLeafCodes(node: PermTreeNode | PermLeaf): string[] {
  if ("code" in node && !("children" in node)) return [node.code];
  if ("children" in node && node.children) {
    return (node.children as (PermTreeNode | PermLeaf)[]).flatMap((c) => collectLeafCodes(c));
  }
  return [];
}
const allPermCodes = permTree.flatMap((ns) => collectLeafCodes(ns));

// ===== Mock Data =====
const mockRoles: RoleItem[] = [
  { id: 1, name: "SUPER_ADMIN", level: 99, permissions: ["*:*:*:*"], status: 1, userCount: 2, createdAt: "2024-01-01" },
  { id: 2, name: "ADMIN", level: 60, permissions: ["core:*:*:manage", "admin:users:list", "admin:users:read", "admin:users:write", "admin:oauth:audit:*", "admin:system:resources:read"], status: 1, userCount: 5, createdAt: "2024-01-01" },
  { id: 3, name: "VIP", level: 10, permissions: ["core:identity:profile:read", "core:identity:profile:write", "core:file:list:read", "core:file:content:*"], status: 1, userCount: 23, createdAt: "2024-01-01" },
  { id: 4, name: "USER", level: 1, permissions: ["core:identity:profile:read"], status: 1, userCount: 156, createdAt: "2024-01-01" },
  { id: 5, name: "MODERATOR", level: 30, permissions: ["admin:users:list", "admin:users:read", "core:file:list:read", "core:file:content:read", "admin:oauth:audit:read"], status: 1, userCount: 8, createdAt: "2024-03-15" },
];

let mockIdCounter = 100;

// ===== State =====
const roles = ref<RoleItem[]>([]);
const loading = ref(true);
const error = ref("");
const selectedRoleId = ref<number | null>(null);
const saving = ref(false);
const deleting = ref(false);
const isCreating = ref(false);

// Form state
const form = ref<RoleForm>({
  name: "",
  level: 1,
  permissions: [],
  status: 1,
});
const formErrors = ref<Record<string, string>>({});

// Delete confirmation
const showDeleteConfirm = ref(false);
const roleToDelete = ref<RoleItem | null>(null);

// ===== Computed =====
const selectedRole = computed(() => {
  if (selectedRoleId.value === null) return null;
  return roles.value.find((r) => r.id === selectedRoleId.value) ?? null;
});

const isEditing = computed(() => selectedRoleId.value !== null && !isCreating.value);

const savingLabel = computed(() => {
  if (isCreating.value) return saving.value ? "创建中…" : "创建角色";
  return saving.value ? "保存中…" : "保存";
});

// ===== Permission helpers =====
function hasPerm(code: string): boolean {
  return form.value.permissions.includes(code);
}

function hasWildcard(): boolean {
  return form.value.permissions.includes("*:*:*:*");
}

function togglePerm(code: string) {
  const idx = form.value.permissions.indexOf(code);
  if (idx >= 0) {
    form.value.permissions.splice(idx, 1);
  } else {
    form.value.permissions.push(code);
  }
}

// Get all leaf codes under a tree node
function getNodePermCodes(node: PermTreeNode): string[] {
  if (!node.children) return [];
  return node.children.flatMap((c) => {
    if ("code" in c && !("children" in c)) return [c.code];
    return getNodePermCodes(c as PermTreeNode);
  });
}

function isNodeFullyChecked(node: PermTreeNode): boolean {
  const codes = getNodePermCodes(node);
  if (codes.length === 0) return false;
  return codes.every((c) => form.value.permissions.includes(c));
}

function isNodePartiallyChecked(node: PermTreeNode): boolean {
  const codes = getNodePermCodes(node);
  if (codes.length === 0) return false;
  const checked = codes.filter((c) => form.value.permissions.includes(c));
  return checked.length > 0 && checked.length < codes.length;
}

function toggleNode(node: PermTreeNode) {
  const codes = getNodePermCodes(node);
  if (codes.length === 0) return;
  const allChecked = codes.every((c) => form.value.permissions.includes(c));
  if (allChecked) {
    form.value.permissions = form.value.permissions.filter((p) => !codes.includes(p));
  } else {
    for (const code of codes) {
      if (!form.value.permissions.includes(code)) {
        form.value.permissions.push(code);
      }
    }
  }
}

// ===== Tree expansion =====
const expandedNodes = ref<Set<string>>(new Set(["core", "admin", "special"]));

function toggleExpand(key: string) {
  if (expandedNodes.value.has(key)) {
    expandedNodes.value.delete(key);
  } else {
    expandedNodes.value.add(key);
  }
}

// ===== Form logic =====
function clearFieldError(field: string) {
  if (formErrors.value[field]) {
    const copy = { ...formErrors.value };
    delete copy[field];
    formErrors.value = copy;
  }
}

function validate(): boolean {
  const errs: Record<string, string> = {};
  if (!form.value.name.trim()) errs.name = "请输入角色名称";
  else if (!/^[A-Z][A-Z0-9_]*$/.test(form.value.name.trim())) errs.name = "角色名称须为大写字母、数字和下划线，首字符必须为字母";
  if (form.value.level < 1 || form.value.level > 99) errs.level = "等级范围 1-99";
  else if (!Number.isInteger(form.value.level)) errs.level = "等级必须为整数";
  formErrors.value = errs;
  return Object.keys(errs).length === 0;
}

function resetForm() {
  form.value = { name: "", level: 1, permissions: [], status: 1 };
  formErrors.value = {};
  isCreating.value = false;
}

function openCreate() {
  resetForm();
  isCreating.value = true;
  form.value.name = "";
  form.value.level = 1;
  form.value.permissions = [];
  form.value.status = 1;
}

function selectRole(role: RoleItem) {
  if (isCreating.value && isFormDirty()) {
    if (!confirm("当前编辑内容尚未保存，确定放弃并切换角色？")) return;
  }
  selectedRoleId.value = role.id;
  isCreating.value = false;
  form.value = {
    name: role.name,
    level: role.level,
    permissions: [...role.permissions],
    status: role.status,
  };
  formErrors.value = {};
}

function isFormDirty(): boolean {
  if (isCreating.value) {
    return form.value.name.trim() !== "" || form.value.permissions.length > 0 || form.value.level !== 1;
  }
  if (!selectedRole.value) return false;
  const r = selectedRole.value;
  return (
    form.value.name !== r.name ||
    form.value.level !== r.level ||
    form.value.status !== r.status ||
    JSON.stringify([...form.value.permissions].sort()) !== JSON.stringify([...r.permissions].sort())
  );
}

// ===== API calls =====
async function loadRoles() {
  loading.value = true;
  error.value = "";
  if (import.meta.dev) {
    roles.value = [...mockRoles];
    loading.value = false;
    return;
  }
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; data: RoleItem[]; msg: string }>("/api/admin/roles");
    if (res.code === 200) roles.value = res.data || [];
    else error.value = res.msg || "获取角色列表失败";
  } catch (e: any) {
    error.value = e?.data?.msg || e?.message || "请求失败";
  } finally {
    loading.value = false;
  }
}

async function saveRole() {
  if (!validate()) return;

  saving.value = true;
  try {
    const body: Record<string, string | number | string[]> = {
      name: form.value.name.trim(),
      level: form.value.level,
      permissions: form.value.permissions,
      status: form.value.status,
    };

    if (import.meta.dev) {
      if (isCreating.value) {
        const newRole: RoleItem = {
          id: ++mockIdCounter,
          name: body.name as string,
          level: body.level as number,
          permissions: body.permissions as string[],
          status: body.status as number,
          userCount: 0,
          createdAt: new Date().toISOString().slice(0, 10),
        };
        mockRoles.push(newRole);
        roles.value = [...mockRoles];
        selectedRoleId.value = newRole.id;
        isCreating.value = false;
        toast.success("角色创建成功");
      } else if (selectedRole.value) {
        const idx = mockRoles.findIndex((r) => r.id === selectedRole.value!.id);
        if (idx >= 0) {
          mockRoles[idx] = { ...mockRoles[idx], name: body.name as string, level: body.level as number, permissions: body.permissions as string[], status: body.status as number } as RoleItem;
          roles.value = [...mockRoles];
        }
        toast.success("角色已保存");
      }
      saving.value = false;
      return;
    }

    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;

    if (isCreating.value) {
      const res = await fetch<{ code: number; data: RoleItem; msg: string }>("/api/admin/roles", {
        method: "POST",
        body,
      });
      if (res.code === 200) {
        toast.success("角色创建成功");
        await loadRoles();
        selectedRoleId.value = res.data?.id ?? null;
        isCreating.value = false;
        if (selectedRoleId.value) {
          const created = roles.value.find((r) => r.id === selectedRoleId.value);
          if (created) selectRoleSilent(created);
        }
      } else {
        toast.error(res.msg || "创建失败");
      }
    } else if (selectedRole.value) {
      const res = await fetch<{ code: number; msg: string }>(`/api/admin/roles/${selectedRole.value.id}`, {
        method: "PUT",
        body,
      });
      if (res.code === 200) {
        toast.success("角色已保存");
        await loadRoles();
      } else {
        toast.error(res.msg || "保存失败");
      }
    }
  } catch (e: any) {
    toast.error(e?.data?.msg || e?.message || "请求失败");
  } finally {
    saving.value = false;
  }
}

function selectRoleSilent(role: RoleItem) {
  selectedRoleId.value = role.id;
  form.value = {
    name: role.name,
    level: role.level,
    permissions: [...role.permissions],
    status: role.status,
  };
}

function promptDelete(role: RoleItem) {
  roleToDelete.value = role;
  showDeleteConfirm.value = true;
}

async function confirmDelete() {
  if (!roleToDelete.value) return;
  deleting.value = true;
  try {
    if (import.meta.dev) {
      const idx = mockRoles.findIndex((r) => r.id === roleToDelete.value!.id);
      if (idx >= 0) {
        mockRoles.splice(idx, 1);
        roles.value = [...mockRoles];
      }
      if (selectedRoleId.value === roleToDelete.value.id) {
        selectedRoleId.value = null;
        resetForm();
      }
      toast.success("角色已删除");
      deleting.value = false;
      showDeleteConfirm.value = false;
      roleToDelete.value = null;
      return;
    }

    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; msg: string }>(`/api/admin/roles/${roleToDelete.value.id}`, {
      method: "DELETE",
    });
    if (res.code === 200) {
      toast.success("角色已删除");
      if (selectedRoleId.value === roleToDelete.value.id) {
        selectedRoleId.value = null;
        resetForm();
      }
      await loadRoles();
    } else {
      toast.error(res.msg || "删除失败");
    }
  } catch (e: any) {
    toast.error(e?.data?.msg || e?.message || "请求失败");
  } finally {
    deleting.value = false;
    showDeleteConfirm.value = false;
    roleToDelete.value = null;
  }
}

function cancelDelete() {
  showDeleteConfirm.value = false;
  roleToDelete.value = null;
}

// ===== Status helpers =====
const statusLabel: Record<number, string> = { 0: "已取消", 1: "启用", 2: "已删除" };
const statusClass: Record<number, string> = { 0: "tag-amber", 1: "tag-green", 2: "tag-red" };

function toggleStatus() {
  form.value.status = form.value.status === 1 ? 0 : 1;
}

// ===== Custom permission input =====
const customPermInput = ref("");

const customPerms = computed(() =>
  form.value.permissions.filter((p) => !allPermCodes.includes(p) && p !== "*:*:*:*"),
);

function addCustomPerm() {
  const val = customPermInput.value.trim();
  if (!val) return;
  if (hasWildcard()) {
    toast.warning("已启用超级通配符，无需添加其他权限");
    return;
  }
  if (!form.value.permissions.includes(val)) {
    form.value.permissions.push(val);
  }
  customPermInput.value = "";
}

function removeCustomPerm(code: string) {
  const idx = form.value.permissions.indexOf(code);
  if (idx >= 0) form.value.permissions.splice(idx, 1);
}

// ===== Lifecycle =====
onMounted(() => {
  loadRoles();
});
</script>

<template>
  <div class="roles-page">
    <!-- 页面头部 -->
    <section class="page-header fade-up">
      <div class="page-header-left">
        <div class="page-header-overline">Admin / System</div>
        <h1 class="page-header-title">角色与权限管理</h1>
        <p class="page-header-description">
          管理平台角色定义和权限分配，控制各角色可访问的功能范围
        </p>
      </div>
    </section>

    <!-- 加载 / 错误状态 -->
    <div v-if="loading" class="roles-empty fade-up">
      <div class="roles-empty-icon">
        <Icon name="lucide:loader-circle" size="36" class="spin-icon" />
      </div>
      <p class="roles-empty-title">正在加载角色列表...</p>
    </div>

    <div v-else-if="error" class="roles-empty fade-up">
      <div class="roles-empty-icon" style="color: var(--color-error)">
        <Icon name="lucide:alert-triangle" size="36" />
      </div>
      <p class="roles-empty-title">加载失败</p>
      <p class="roles-empty-desc">{{ error }}</p>
      <button class="button button-primary" @click="loadRoles">重新加载</button>
    </div>

    <!-- 双栏布局 -->
    <div v-else class="roles-layout fade-up">
      <!-- 左栏：角色列表 -->
      <div class="roles-list-col">
        <div class="roles-list-header">
          <span class="roles-list-count">{{ roles.length }} 个角色</span>
          <button class="button button-primary button-small" @click="openCreate">
            <Icon name="lucide:plus" size="14" />
            新建角色
          </button>
        </div>

        <div class="roles-list-body">
          <div
            v-for="role in roles"
            :key="role.id"
            :class="['roles-list-card', {
              selected: selectedRoleId === role.id,
              creating: isCreating && selectedRoleId === null,
            }]"
            @click="selectRole(role)"
          >
            <div class="roles-card-icon">
              <Icon name="lucide:shield" size="18" />
            </div>
            <div class="roles-card-info">
              <div class="roles-card-name">{{ role.name }}</div>
              <div class="roles-card-meta">
                <span :class="['roles-level-badge', `roles-level-${role.level >= 60 ? 'high' : role.level >= 30 ? 'mid' : 'low'}`]">
                  Lv.{{ role.level }}
                </span>
                <span :class="['tag', statusClass[role.status]]">{{ statusLabel[role.status] }}</span>
                <span class="roles-card-count">
                  <Icon name="lucide:users" size="11" />
                  {{ role.userCount }}
                </span>
                <span class="roles-card-count">
                  <Icon name="lucide:key" size="11" />
                  {{ role.permissions.length }}
                </span>
              </div>
            </div>
          </div>

          <!-- 空列表 -->
          <div v-if="roles.length === 0" class="roles-empty" style="padding: 32px 0">
            <div class="roles-empty-icon">
              <Icon name="lucide:shapes" size="36" />
            </div>
            <p class="roles-empty-desc">暂无角色，点击右上角"新建角色"创建</p>
          </div>
        </div>
      </div>

      <!-- 右栏：角色编辑器 -->
      <div class="roles-editor-col">
        <!-- 空选状态 -->
        <div v-if="selectedRoleId === null && !isCreating" class="roles-editor-placeholder">
          <div class="roles-editor-empty">
            <div class="roles-editor-empty-icon">
              <Icon name="lucide:shield-question" size="40" />
            </div>
            <span class="roles-editor-empty-title">选择或创建角色</span>
            <span class="roles-editor-empty-desc">从左侧列表选择一个角色进行编辑，或点击"新建角色"创建新角色</span>
          </div>
        </div>

        <!-- 编辑器面板 -->
        <div v-else class="roles-editor-panel panel-card">
          <div class="panel-card-header">
            <Icon
              :name="isCreating ? 'lucide:plus-circle' : 'lucide:edit'"
              size="18"
              class="panel-card-header-icon"
            />
            <div class="panel-card-header-text">
              <h3>{{ isCreating ? "创建新角色" : "编辑角色" }}</h3>
              <p class="panel-card-header-sub">
                {{ isCreating ? "定义角色名称、等级和权限" : selectedRole?.name || "" }}
              </p>
            </div>
          </div>

          <div class="panel-card-body" style="gap: 18px">
            <!-- 基本信息 -->
            <fieldset class="detail-fieldset">
              <legend><Icon name="lucide:info" size="13" /> 基本信息</legend>

              <div class="form-field">
                <label>角色名称 <span class="apply-required">*</span></label>
                <input
                  v-model="form.name"
                  :class="['form-input', { 'input-error': formErrors.name }]"
                  placeholder="例如：MODERATOR"
                  maxlength="32"
                  @input="clearFieldError('name')"
                />
                <span v-if="formErrors.name" class="field-error-text">{{ formErrors.name }}</span>
              </div>

              <div class="roles-form-row">
                <div class="form-field" style="flex: 1; max-width: 160px">
                  <label>等级 (1-99) <span class="apply-required">*</span></label>
                  <input
                    v-model.number="form.level"
                    :class="['form-input', { 'input-error': formErrors.level }]"
                    type="number"
                    min="1"
                    max="99"
                    @input="clearFieldError('level')"
                  />
                  <span v-if="formErrors.level" class="field-error-text">{{ formErrors.level }}</span>
                </div>

                <div class="form-field">
                  <label>状态</label>
                  <button
                    :class="['roles-status-toggle', { active: form.status === 1 }]"
                    @click="toggleStatus"
                  >
                    <span class="roles-status-dot" />
                    {{ form.status === 1 ? "启用" : "已取消" }}
                  </button>
                </div>
              </div>

              <!-- 通配符权限快捷开关 -->
              <div class="roles-wildcard-row">
                <label :class="['roles-wildcard-chip', { active: hasWildcard() }]">
                  <input
                    type="checkbox"
                    :checked="hasWildcard()"
                    class="apply-check-input"
                    @change="hasWildcard() ? (form.permissions = form.permissions.filter(p => p !== '*:*:*:*')) : (form.permissions = ['*:*:*:*'])"
                  />
                  <span class="roles-wildcard-icon">
                    <Icon v-if="hasWildcard()" name="lucide:check" size="13" />
                  </span>
                  <span class="roles-wildcard-label">超级通配符 <code>*:*:*:*</code></span>
                  <span class="roles-wildcard-hint">授予全部权限，谨慎使用</span>
                </label>
              </div>
            </fieldset>

            <!-- 权限树 -->
            <fieldset class="detail-fieldset">
              <legend>
                <Icon name="lucide:key-round" size="13" />
                权限分配
                <span class="roles-perm-count">{{ form.permissions.length }}/{{ allPermCodes.length }} 项</span>
              </legend>

              <div class="roles-perm-tree">
                <template v-for="ns in permTree" :key="ns.key">
                  <!-- 顶级命名空间 -->
                  <div class="perm-tree-node perm-tree-ns">
                    <button class="perm-tree-toggle" @click="toggleExpand(ns.key)">
                      <Icon
                        :name="expandedNodes.has(ns.key) ? 'lucide:chevron-down' : 'lucide:chevron-right'"
                        size="13"
                      />
                    </button>
                    <label class="perm-tree-check-wrap" @click.stop>
                      <input
                        type="checkbox"
                        class="perm-tree-check"
                        :checked="isNodeFullyChecked(ns)"
                        :indeterminate.prop="isNodePartiallyChecked(ns)"
                        @change="toggleNode(ns)"
                      />
                    </label>
                    <span class="perm-tree-label perm-tree-label-ns">{{ ns.label }}</span>
                  </div>

                  <div v-if="expandedNodes.has(ns.key)" class="perm-tree-children">
                    <template v-for="page in (ns.children as PermTreeNode[])" :key="page.key">
                      <!-- 二级页面 -->
                      <div class="perm-tree-node perm-tree-page">
                        <span class="perm-tree-indent" />
                        <button class="perm-tree-toggle" @click="toggleExpand(page.key)">
                          <Icon
                            :name="expandedNodes.has(page.key) ? 'lucide:chevron-down' : 'lucide:chevron-right'"
                            size="13"
                          />
                        </button>
                        <label class="perm-tree-check-wrap" @click.stop>
                          <input
                            type="checkbox"
                            class="perm-tree-check"
                            :checked="isNodeFullyChecked(page)"
                            :indeterminate.prop="isNodePartiallyChecked(page)"
                            @change="toggleNode(page)"
                          />
                        </label>
                        <span class="perm-tree-label perm-tree-page-label">{{ page.label }}</span>
                      </div>

                      <div v-if="expandedNodes.has(page.key)" class="perm-tree-children">
                        <template v-for="group in (page.children as PermTreeNode[])" :key="group.key">
                          <!-- 三级分组 -->
                          <div class="perm-tree-node perm-tree-group">
                            <span class="perm-tree-indent" />
                            <span class="perm-tree-indent" />
                            <button class="perm-tree-toggle" @click="toggleExpand(group.key)">
                              <Icon
                                :name="expandedNodes.has(group.key) ? 'lucide:chevron-down' : 'lucide:chevron-right'"
                                size="12"
                              />
                            </button>
                            <label class="perm-tree-check-wrap" @click.stop>
                              <input
                                type="checkbox"
                                class="perm-tree-check"
                                :checked="isNodeFullyChecked(group)"
                                :indeterminate.prop="isNodePartiallyChecked(group)"
                                @change="toggleNode(group)"
                              />
                            </label>
                            <span class="perm-tree-label perm-tree-group-label">{{ group.label }}</span>
                          </div>

                          <!-- 四级叶子权限 -->
                          <div v-if="expandedNodes.has(group.key)" class="perm-tree-children">
                            <label
                              v-for="leaf in (group.children as PermLeaf[])"
                              :key="leaf.code"
                              class="perm-tree-node perm-tree-leaf"
                              :class="{ disabled: hasWildcard() }"
                            >
                              <span class="perm-tree-indent" />
                              <span class="perm-tree-indent" />
                              <span class="perm-tree-indent" />
                              <input
                                type="checkbox"
                                class="perm-tree-check"
                                :checked="hasWildcard() || hasPerm(leaf.code)"
                                :disabled="hasWildcard()"
                                @change="togglePerm(leaf.code)"
                              />
                              <span class="perm-tree-label perm-tree-leaf-label">{{ leaf.label }}</span>
                              <code class="perm-tree-code">{{ leaf.code }}</code>
                            </label>
                          </div>
                        </template>
                      </div>
                    </template>
                  </div>
                </template>
              </div>

              <!-- 自定义权限输入 -->
              <div class="roles-custom-perm">
                <div class="roles-custom-perm-header">
                  <Icon name="lucide:plus-circle" size="13" />
                  <span>自定义权限</span>
                </div>
                <div class="roles-custom-perm-input">
                  <input
                    v-model="customPermInput"
                    class="form-input"
                    placeholder="输入权限代码，如 core:custom:feature:read"
                    @keydown.enter.prevent="addCustomPerm"
                  />
                  <button class="button button-small" @click="addCustomPerm">添加</button>
                </div>
                <div v-if="customPerms.length" class="roles-custom-perm-tags">
                  <span v-for="cp in customPerms" :key="cp" class="roles-custom-perm-tag">
                    <code>{{ cp }}</code>
                    <button class="roles-custom-perm-tag-remove" @click="removeCustomPerm(cp)">
                      <Icon name="lucide:x" size="11" />
                    </button>
                  </span>
                </div>
              </div>
            </fieldset>

            <!-- 操作按钮 -->
            <div class="roles-editor-actions">
              <div class="roles-editor-actions-left">
                <button
                  v-if="!isCreating && selectedRole"
                  class="button button-danger"
                  :disabled="saving || deleting"
                  @click="promptDelete(selectedRole)"
                >
                  <Icon name="lucide:trash-2" size="14" />
                  删除角色
                </button>
                <button
                  v-if="isCreating"
                  class="button"
                  @click="isCreating = false; resetForm()"
                >
                  取消创建
                </button>
              </div>
              <div class="roles-editor-actions-right">
                <button
                  v-if="!isCreating"
                  class="button"
                  @click="openCreate"
                >
                  <Icon name="lucide:plus" size="14" />
                  新建
                </button>
                <button
                  class="button button-primary"
                  :disabled="saving || hasWildcard() && form.permissions.length > 1"
                  @click="saveRole"
                >
                  <Icon v-if="saving" name="lucide:loader-circle" size="14" class="spin-icon" />
                  <Icon v-else name="lucide:save" size="14" />
                  {{ savingLabel }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 删除确认对话框 -->
    <Modal :show="showDeleteConfirm" title="确认删除" width="420px" @close="cancelDelete">
      <div class="roles-delete-body">
        <div class="roles-delete-warn">
          <Icon name="lucide:alert-triangle" size="20" />
        </div>
        <p class="roles-delete-text">
          确定要删除角色 <strong>{{ roleToDelete?.name }}</strong> 吗？
        </p>
        <p class="roles-delete-sub">
          此操作不可撤销。拥有该角色的 {{ roleToDelete?.userCount ?? 0 }} 位用户将失去相应权限。
        </p>
      </div>
      <template #footer>
        <button class="button" @click="cancelDelete" :disabled="deleting">取消</button>
        <button
          class="button button-danger"
          :disabled="deleting"
          @click="confirmDelete"
        >
          <Icon v-if="deleting" name="lucide:loader-circle" size="14" class="spin-icon" />
          {{ deleting ? "删除中…" : "确认删除" }}
        </button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.roles-page {
  max-width: 100%;
  margin: 0 auto;
  width: 100%;
  display: grid;
  gap: 20px;
}

/* ===== 双栏布局 ===== */
.roles-layout {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 24px;
  align-items: start;
}

.roles-list-col {
  display: grid;
  gap: 0;
  position: sticky;
  top: 20px;
  align-self: start;
}

.roles-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  padding: 0 2px;
}

.roles-list-count {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-font-assist);
}

.roles-list-body {
  display: grid;
  gap: 6px;
}

/* ===== 角色列表卡片 ===== */
.roles-list-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.12s;
}

.roles-list-card:hover {
  border-color: var(--color-border-heavy);
}

.roles-list-card.selected {
  border-color: var(--color-emphasis);
  background: var(--color-emphasis-soft);
}

.roles-card-icon {
  width: 38px;
  height: 38px;
  border-radius: var(--radius-md);
  background: var(--color-primary-background);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--color-font-assist);
}

.roles-list-card.selected .roles-card-icon {
  background: var(--color-emphasis-ghost);
  color: var(--color-emphasis);
}

.roles-card-info {
  min-width: 0;
  flex: 1;
}

.roles-card-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-font);
  font-family: var(--font-mono);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.roles-card-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  flex-wrap: wrap;
}

.roles-level-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
}

.roles-level-high {
  color: var(--color-error);
  background: var(--color-error-soft);
}

.roles-level-mid {
  color: var(--color-warning);
  background: var(--color-warning-soft);
}

.roles-level-low {
  color: var(--color-font-assist);
  background: var(--color-primary-background);
}

.roles-card-count {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--color-font-assist);
}

/* ===== 编辑器面板 ===== */
.roles-editor-col {
  position: sticky;
  top: 20px;
}

.roles-editor-panel {
  padding: 0;
}

.roles-editor-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  background: var(--color-card);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
}

.roles-editor-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 48px 32px;
  text-align: center;
}

.roles-editor-empty-icon {
  color: var(--color-font-assist);
  opacity: 0.35;
  margin-bottom: 4px;
}

.roles-editor-empty-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--color-font-secondary);
}

.roles-editor-empty-desc {
  font-size: 13px;
  color: var(--color-font-assist);
  max-width: 280px;
}

/* ===== 空态 / 加载 / 错误 ===== */
.roles-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  text-align: center;
  gap: 10px;
}

.roles-empty-icon {
  color: var(--color-font-assist);
  opacity: 0.4;
}

.roles-empty-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--color-font-secondary);
}

.roles-empty-desc {
  font-size: 13px;
  color: var(--color-font-assist);
}

/* ===== 表单字段 ===== */
.roles-form-row {
  display: flex;
  align-items: flex-end;
  gap: 16px;
}

.roles-status-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card);
  color: var(--color-font-assist);
  font-size: 12px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
}

.roles-status-toggle:hover {
  border-color: var(--color-border-heavy);
}

.roles-status-toggle.active {
  border-color: var(--color-emphasis);
  color: var(--color-emphasis);
  background: var(--color-emphasis-soft);
}

.roles-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

/* ===== 通配符开关 ===== */
.roles-wildcard-row {
  margin-top: 2px;
}

.roles-wildcard-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.15s;
  user-select: none;
}

.roles-wildcard-chip:hover {
  border-color: var(--color-border-heavy);
}

.roles-wildcard-chip.active {
  border-color: var(--color-error);
  background: var(--color-error-soft);
}

.roles-wildcard-icon {
  width: 20px;
  height: 20px;
  border-radius: var(--radius-sm);
  border: 2px solid var(--color-border-heavy);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.15s;
  color: #fff;
}

.roles-wildcard-chip.active .roles-wildcard-icon {
  border-color: var(--color-error);
  background: var(--color-error);
}

.roles-wildcard-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-font);
}

.roles-wildcard-label code {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-font-assist);
  margin-left: 4px;
}

.roles-wildcard-chip.active .roles-wildcard-label {
  color: var(--color-error);
}

.roles-wildcard-chip.active .roles-wildcard-label code {
  color: var(--color-error);
}

.roles-wildcard-hint {
  font-size: 11px;
  color: var(--color-font-assist);
  margin-left: auto;
}

/* ===== 权限树 ===== */
.roles-perm-count {
  font-size: 11px;
  font-weight: 400;
  color: var(--color-font-assist);
  margin-left: auto;
}

.roles-perm-tree {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 6px 0;
  background: var(--color-card);
  max-height: 480px;
  overflow-y: auto;
}

.perm-tree-node {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  height: 28px;
}

.perm-tree-children {
  /* nesting container */
}

.perm-tree-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: var(--color-font-assist);
  border-radius: 3px;
  cursor: pointer;
  flex-shrink: 0;
  padding: 0;
}

.perm-tree-toggle:hover {
  background: var(--color-primary-background);
}

.perm-tree-check-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}

.perm-tree-check {
  width: 13px;
  height: 13px;
  accent-color: var(--color-emphasis);
  cursor: pointer;
  flex-shrink: 0;
}

.perm-tree-label {
  font-size: 12px;
  color: var(--color-font);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.perm-tree-label-ns {
  font-weight: 600;
  color: var(--color-emphasis);
  font-family: var(--font-mono);
}

.perm-tree-page-label {
  font-size: 13px;
  font-weight: 500;
}

.perm-tree-group-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-font-secondary);
}

.perm-tree-leaf-label {
  font-size: 12px;
  color: var(--color-font-secondary);
}

.perm-tree-code {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-font-assist);
  margin-left: auto;
  flex-shrink: 0;
}

.perm-tree-indent {
  display: inline-block;
  width: 20px;
  flex-shrink: 0;
}

.perm-tree-leaf.disabled {
  opacity: 0.45;
  pointer-events: none;
}

/* ===== 自定义权限输入 ===== */
.roles-custom-perm {
  margin-top: 4px;
}

.roles-custom-perm-header {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 500;
  color: var(--color-font-assist);
  margin-bottom: 6px;
}

.roles-custom-perm-input {
  display: flex;
  gap: 8px;
}

.roles-custom-perm-input .form-input {
  flex: 1;
}

.roles-custom-perm-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
}

.roles-custom-perm-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 3px 1px 7px;
  background: var(--color-warning-soft);
  border: 1px solid rgba(217, 119, 6, 0.15);
  border-radius: var(--radius-sm);
  font-size: 11px;
}

.roles-custom-perm-tag code {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-warning);
}

.roles-custom-perm-tag-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border: none;
  background: transparent;
  color: var(--color-font-assist);
  border-radius: 2px;
  cursor: pointer;
  padding: 0;
}

.roles-custom-perm-tag-remove:hover {
  background: rgba(217, 119, 6, 0.15);
  color: var(--color-warning);
}

/* ===== 操作按钮栏 ===== */
.roles-editor-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 4px;
  border-top: 1px solid var(--color-border);
  gap: 8px;
}

.roles-editor-actions-left,
.roles-editor-actions-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ===== 删除确认对话框 ===== */
.roles-delete-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
}

.roles-delete-warn {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--color-error-soft);
  color: var(--color-error);
  display: flex;
  align-items: center;
  justify-content: center;
}

.roles-delete-text {
  font-size: 13px;
  color: var(--color-font);
}

.roles-delete-text strong {
  font-weight: 600;
}

.roles-delete-sub {
  font-size: 12px;
  color: var(--color-font-assist);
  line-height: 1.5;
}

/* ===== 复用样式 ===== */
.roles-editor-panel .detail-fieldset {
  border: none;
  padding: 0;
  margin: 0;
}

.roles-editor-panel .detail-fieldset legend {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-font-secondary);
  padding-bottom: 10px;
  width: 100%;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 10px;
}

.roles-editor-panel .form-field {
  display: grid;
  gap: 4px;
}

.roles-editor-panel .form-field label {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-font-secondary);
}

.apply-required {
  color: var(--color-error);
  margin-left: 2px;
}

.spin-icon {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .roles-layout {
    grid-template-columns: 1fr;
  }

  .roles-list-col {
    position: static;
  }

  .roles-editor-col {
    position: static;
  }

  .roles-form-row {
    flex-direction: column;
    align-items: stretch;
  }

  .roles-form-row .form-field {
    max-width: none;
  }
}
</style>
