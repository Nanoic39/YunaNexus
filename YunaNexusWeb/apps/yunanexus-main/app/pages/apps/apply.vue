<script setup lang="ts">
import { useToast } from "~/composables/useToast";

definePageMeta({ layout: "default" });

const toast = useToast();
const { buttons, maxRoleLevel } = useAuth();

const form = ref({
  clientName: "",
  redirectUri: "",
  description: "",
  grantTypes: ["authorization_code"] as string[],
  scope: [] as string[],
  selectedRole: "" as string, // role binding
});

const fieldErrors = ref<Record<string, string>>({});
const saving = ref(false);
const secretDialog = ref({ show: false, secret: "", uuid: "" });

function showSecretDialog(secret: string, uuid: string) {
  secretDialog.value = { show: true, secret, uuid };
}

function copySecret() {
  navigator.clipboard.writeText(secretDialog.value.secret);
  toast.success("密钥已复制到剪贴板");
}

function closeSecretDialog() {
  secretDialog.value.show = false;
}

// ---- 授权模式（仅后端实际支持的） ----
const grantTypeOptions = [
  { label: "Authorization Code", value: "authorization_code", desc: "标准 OAuth2 授权码模式，适用于 Web 应用" },
  { label: "Refresh Token", value: "refresh_token", desc: "允许客户端刷新 Access Token，需配合其他模式使用" },
  { label: "Password", value: "password", desc: "账号密码模式，适用于官方客户端等高信任度场景（第三方应用禁止使用）" },
];

// ===== 授权范围：角色绑定 / 权限绑定 =====
interface ScopeNode {
  key: string;
  label: string;
  desc?: string;
  children?: ScopeNode[];
}

// 角色定义
interface RoleOption { label: string; value: string; level: number; desc: string }

const allRoles: RoleOption[] = [
  { label: "USER", value: "USER", level: 1, desc: "普通用户，仅能访问自身数据" },
  { label: "VIP", value: "VIP", level: 10, desc: "高级用户，更高的存储配额" },
  { label: "ADMIN", value: "ADMIN", level: 60, desc: "管理员，可管理平台大部分资源" },
  { label: "SUPER_ADMIN", value: "SUPER_ADMIN", level: 99, desc: "超级管理员，拥有最高权限" },
];

// 当前用户可选的角色（<= 自身等级的）
const availableRoles = computed(() =>
  allRoles.filter(r => r.level <= maxRoleLevel.value),
);

// 当前用户的权限集
const userPermissions = computed<Set<string>>(() => {
  if (import.meta.dev) return new Set(["*:*:*:*"]);
  return new Set(buttons.value ?? []);
});

function hasPerm(code: string): boolean {
  if (userPermissions.value.has("*:*:*:*")) return true;
  return userPermissions.value.has(code);
}

// 完整权限树
const fullScopeTree: ScopeNode[] = [
  { key: "core", label: "core", children: [
    {
      key: "identity", label: "用户身份", children: [
        { key: "identity:basic", label: "基础信息", children: [
          { key: "perm:core:identity:basic:read", label: "读取 UUID / 用户名", desc: "获取用户的唯一标识和登录名" },
        ]},
        { key: "identity:profile", label: "个人资料", children: [
          { key: "perm:core:identity:profile:read", label: "读取资料", desc: "读取昵称、头像、生日、简介" },
          { key: "perm:core:identity:profile:write", label: "编辑资料", desc: "修改昵称、生日、简介" },
        ]},
        { key: "identity:avatar", label: "头像", children: [
          { key: "perm:core:identity:avatar:read", label: "读取头像", desc: "获取头像文件地址" },
          { key: "perm:core:identity:avatar:write", label: "上传头像", desc: "上传和更换头像" },
        ]},
        { key: "identity:contact", label: "联系方式", children: [
          { key: "perm:core:identity:email:read", label: "读取邮箱", desc: "获取绑定邮箱地址" },
          { key: "perm:core:identity:phone:read", label: "读取手机", desc: "获取绑定手机号" },
        ]},
      ]},
    { key: "file", label: "文件服务", children: [
      { key: "file:list", label: "文件列表", children: [
        { key: "perm:core:file:list:read", label: "列举文件", desc: "查看文件列表和元数据" },
      ]},
      { key: "file:content", label: "文件内容", children: [
        { key: "perm:core:file:content:read", label: "下载文件", desc: "读取和下载文件内容" },
        { key: "perm:core:file:content:write", label: "上传文件", desc: "上传新文件到用户空间" },
        { key: "perm:core:file:content:delete", label: "删除文件", desc: "删除已上传的文件" },
      ]},
    ]},
    { key: "apps", label: "应用管理", children: [
      { key: "apps:client", label: "OAuth 客户端", children: [
        { key: "perm:core:apps:client:list", label: "查看列表", desc: "列举已创建的应用" },
        { key: "perm:core:apps:client:read", label: "查看详情", desc: "读取应用配置和审核状态" },
        { key: "perm:core:apps:client:write", label: "创建/编辑", desc: "创建新应用或编辑已有应用" },
        { key: "perm:core:apps:client:delete", label: "删除应用", desc: "删除已创建的应用" },
      ]},
      { key: "apps:secret", label: "密钥管理", children: [
        { key: "perm:core:apps:secret:read", label: "查看密钥", desc: "查看 Client ID 和 Secret" },
      ]},
    ]},
  ]},
  { key: "admin", label: "admin（管理）", children: [
    { key: "users", label: "用户管理", children: [
      { key: "users:list", label: "用户列表", children: [
        { key: "perm:admin:users:list", label: "查看列表", desc: "列举所有平台用户" },
        { key: "perm:admin:users:read", label: "查看详情", desc: "读取任意用户完整信息" },
      ]},
      { key: "users:write", label: "用户操作", children: [
        { key: "perm:admin:users:write", label: "编辑用户", desc: "创建或编辑用户信息" },
        { key: "perm:admin:users:delete", label: "删除用户", desc: "删除用户账户" },
        { key: "perm:admin:users:status", label: "启用/禁用用户", desc: "启用或禁用用户账户" },
        { key: "perm:admin:users:roles", label: "管理角色", desc: "分配或移除角色" },
      ]},
    ]},
    { key: "oauth", label: "OAuth 审核", children: [
      { key: "oauth:audit", label: "应用审核", children: [
        { key: "perm:admin:oauth:audit:list", label: "查看列表", desc: "查看所有待审核应用" },
        { key: "perm:admin:oauth:audit:read", label: "查看详情", desc: "读取申请完整信息" },
        { key: "perm:admin:oauth:audit:approve", label: "通过审核", desc: "批准应用申请" },
        { key: "perm:admin:oauth:audit:reject", label: "拒绝申请", desc: "拒绝应用申请" },
      ]},
      { key: "oauth:toggle", label: "应用管理", children: [
        { key: "perm:admin:oauth:toggle", label: "启用/禁用应用", desc: "启用或禁用已通过应用" },
        { key: "perm:admin:oauth:client:delete", label: "删除应用", desc: "删除 OAuth 应用" },
      ]},
    ]},
    { key: "system", label: "系统管理", children: [
      { key: "system:resources", label: "资源权限", children: [
        { key: "perm:admin:system:resources:read", label: "查看资源", desc: "查看菜单/按钮/API 资源" },
        { key: "perm:admin:system:resources:write", label: "编辑资源", desc: "创建或修改资源定义" },
      ]},
      { key: "system:roles", label: "角色管理", children: [
        { key: "perm:admin:system:roles:read", label: "查看角色", desc: "查看所有角色和权限" },
        { key: "perm:admin:system:roles:write", label: "编辑角色", desc: "创建或修改角色定义" },
      ]},
      { key: "system:endpoints", label: "端点管理", children: [
        { key: "perm:admin:system:endpoints:read", label: "查看端点", desc: "查看 API 端点配置" },
        { key: "perm:admin:system:endpoints:write", label: "编辑端点", desc: "修改端点权限映射" },
      ]},
    ]},
  ]},
  { key: "special", label: "特殊权限", children: [
    { key: "offline", label: "离线访问", children: [
      { key: "perm:special:offline_access", label: "Refresh Token", desc: "长期离线访问，无需用户重新授权" },
    ]},
  ]},
];

// 根据当前用户权限过滤树
function filterTree(nodes: ScopeNode[]): ScopeNode[] {
  return nodes.reduce<ScopeNode[]>((acc, node) => {
    if (!node.children) {
      // 叶子节点：检查用户是否拥有该权限
      return hasPerm(node.key.replace(/^perm:/, "")) ? [...acc, node] : acc;
    }
    const filtered = filterTree(node.children);
    if (filtered.length > 0) acc.push({ ...node, children: filtered });
    return acc;
  }, []);
}

const scopeTree = computed(() => filterTree(fullScopeTree));

const expandedNodes = ref<Set<string>>(new Set(["core"]));
const customScope = ref("");

function toggleExpand(key: string) {
  if (expandedNodes.value.has(key)) expandedNodes.value.delete(key);
  else expandedNodes.value.add(key);
}

function toggleScope(key: string) {
  toggleCheckbox(form.value.scope, key);
}

function selectRole(role: string) {
  form.value.selectedRole = form.value.selectedRole === role ? "" : role;
}

function addCustomScope() {
  const val = customScope.value.trim();
  if (!val) return;
  if (!form.value.scope.includes(val)) form.value.scope.push(val);
  customScope.value = "";
}

function removeScope(key: string) {
  const idx = form.value.scope.indexOf(key);
  if (idx >= 0) form.value.scope.splice(idx, 1);
}

// ---- 表单逻辑 ----
function toggleCheckbox(arr: string[], val: string) {
  const idx = arr.indexOf(val);
  if (idx >= 0) arr.splice(idx, 1);
  else arr.push(val);
}

function validate(): boolean {
  const errors: Record<string, string> = {};
  if (!form.value.clientName.trim()) errors.clientName = "请输入应用名称";
  else if (form.value.clientName.length < 2) errors.clientName = "名称至少 2 个字符";
  if (!form.value.redirectUri.trim()) errors.redirectUri = "请输入回调地址";
  else if (!/^https?:\/\/.+/.test(form.value.redirectUri)) errors.redirectUri = "请输入有效的 URL (http/https)";
  fieldErrors.value = errors;
  return Object.keys(errors).length === 0;
}

function clearFieldError(field: string) {
  if (fieldErrors.value[field]) {
    const copy = { ...fieldErrors.value };
    delete copy[field];
    fieldErrors.value = copy;
  }
}

function buildSubmitBody() {
  const scopeParts: string[] = [];
  if (form.value.selectedRole) scopeParts.push(`role:${form.value.selectedRole}`);
  scopeParts.push(...form.value.scope);
  return {
    ...form.value,
    grantTypes: form.value.grantTypes.join(","),
    scope: scopeParts.join(","),
  };
}

async function submit() {
  if (!validate()) {
    toast.error("请检查表单中的错误");
    return;
  }
  saving.value = true;
  try {
    const body = buildSubmitBody();
    if (import.meta.dev) {
      const { addMockApp } = await import("~/composables/useMockApps");
      addMockApp(body);
      toast.success("应用提交成功！");
      form.value = { clientName: "", redirectUri: "", description: "", grantTypes: ["authorization_code"], scope: [], selectedRole: "" };
      fieldErrors.value = {};
      customScope.value = "";
      saving.value = false;
      return;
    }
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; data: any; msg: string }>("/api/oauth/client/register", {
      method: "POST",
      body,
    });
    if (res.code === 200) {
      const secret = res.data?.clientSecret;
      if (secret) {
        toast.success("应用创建成功！请保存密钥");
        // 显示密钥对话框
        showSecretDialog(secret, res.data?.uuid);
      } else {
        toast.success("应用提交成功！");
      }
      form.value = { clientName: "", redirectUri: "", description: "", grantTypes: ["authorization_code"], scope: [], selectedRole: "" };
      fieldErrors.value = {};
      customScope.value = "";
    } else {
      toast.error(res.msg || "提交失败");
    }
  } catch (e: any) {
    toast.error(e?.data?.msg || e?.message || "请求失败");
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <!-- 密钥展示对话框 -->
  <div v-if="secretDialog.show" class="secret-overlay" @click.self="closeSecretDialog">
    <div class="secret-dialog panel-card">
      <div class="secret-dialog-header">
        <Icon name="lucide:key" size="20" style="color: var(--color-warning)" />
        <h3>应用密钥</h3>
        <button class="secret-dialog-close" @click="closeSecretDialog">
          <Icon name="lucide:x" size="16" />
        </button>
      </div>
      <div class="secret-dialog-body">
        <p class="secret-warning">
          <Icon name="lucide:alert-triangle" size="14" />
          密钥仅在创建时显示一次，请立即复制并妥善保存！
        </p>
        <div class="secret-field">
          <label class="apply-label">Client ID</label>
          <code class="secret-value">{{ secretDialog.uuid }}</code>
        </div>
        <div class="secret-field">
          <label class="apply-label">Client Secret</label>
          <code class="secret-value secret-value-blur">{{ secretDialog.secret }}</code>
        </div>
        <button class="button button-primary secret-copy-btn" @click="copySecret">
          <Icon name="lucide:copy" size="14" /> 复制密钥
        </button>
      </div>
    </div>
  </div>

  <div class="apply-page">
    <!-- 面包屑导航 -->
    <nav class="apply-breadcrumb fade-up">
      <NuxtLink to="/apps" class="apply-breadcrumb-link">
        <Icon name="lucide:arrow-left" size="14" />
        返回应用列表
      </NuxtLink>
    </nav>

    <!-- 页面标题 -->
    <section class="page-header fade-up">
      <div class="page-header-left">
        <div class="page-header-overline">OAuth Applications</div>
        <h1 class="page-header-title">创建应用</h1>
        <p class="page-header-description">
          申请接入 OAuth 认证服务，审核通过后将获得 Client ID 和 Client Secret
        </p>
      </div>
    </section>

    <!-- 主内容区：双栏 -->
    <div class="apply-layout fade-up">
      <!-- 左栏：表单 -->
      <div class="apply-form-card panel-card">
        <div class="panel-card-body" style="gap: 20px">
          <!-- 应用信息 -->
          <fieldset class="apply-fieldset">
            <legend class="apply-fieldset-legend">
              <Icon name="lucide:info" size="14" />
              基本信息
            </legend>

            <div class="apply-field">
              <label class="apply-label">应用名称 <span class="apply-required">*</span></label>
              <input
                v-model="form.clientName"
                :class="['form-input', { 'input-error': fieldErrors.clientName }]"
                placeholder="例如：我的网页应用"
                maxlength="32"
                @input="clearFieldError('clientName')"
              />
              <span v-if="fieldErrors.clientName" class="field-error-text">{{ fieldErrors.clientName }}</span>
              <span v-else class="apply-hint">2-32 个字符，用于在授权页面向用户展示</span>
            </div>

            <div class="apply-field">
              <label class="apply-label">回调地址 <span class="apply-required">*</span></label>
              <input
                v-model="form.redirectUri"
                :class="['form-input', { 'input-error': fieldErrors.redirectUri }]"
                placeholder="https://example.com/oauth/callback"
                @input="clearFieldError('redirectUri')"
              />
              <span v-if="fieldErrors.redirectUri" class="field-error-text">{{ fieldErrors.redirectUri }}</span>
              <span v-else class="apply-hint">用户授权后跳转的地址，生产环境必须使用 HTTPS</span>
            </div>

            <div class="apply-field">
              <label class="apply-label">应用说明</label>
              <textarea
                v-model="form.description"
                class="apply-textarea"
                placeholder="简要说明应用的功能和用途..."
                rows="3"
                maxlength="500"
              />
              <span class="apply-hint">选填，清晰的说明有助于加快审核</span>
            </div>
          </fieldset>

          <!-- OAuth 配置 -->
          <fieldset class="apply-fieldset">
            <legend class="apply-fieldset-legend">
              <Icon name="lucide:shield" size="14" />
              OAuth 配置
            </legend>

            <div class="apply-field">
              <label class="apply-label">授权模式</label>
              <div class="apply-check-group">
                <label
                  v-for="opt in grantTypeOptions"
                  :key="opt.value"
                  :class="['apply-check-chip', { checked: form.grantTypes.includes(opt.value) }]"
                >
                  <input
                    type="checkbox"
                    :checked="form.grantTypes.includes(opt.value)"
                    class="apply-check-input"
                    @change="toggleCheckbox(form.grantTypes, opt.value)"
                  />
                  <span class="apply-check-icon">
                    <Icon v-if="form.grantTypes.includes(opt.value)" name="lucide:check" size="13" />
                  </span>
                  <div class="apply-check-text">
                    <span class="apply-check-label">{{ opt.label }}</span>
                    <span class="apply-check-desc">{{ opt.desc }}</span>
                  </div>
                </label>
              </div>
              <span class="apply-hint">可选择多个，Authorization Code 适用于大多数场景</span>
            </div>

            <div class="apply-field">
              <label class="apply-label">授权范围</label>
              <!-- 已选标签 -->
              <div v-if="form.selectedRole || form.scope.length" class="apply-scope-tags">
                <span v-if="form.selectedRole" class="apply-scope-tag apply-scope-role-tag">
                  <code>role:{{ form.selectedRole }}</code>
                  <button class="apply-scope-tag-remove" @click="form.selectedRole = ''">
                    <Icon name="lucide:x" size="11" />
                  </button>
                </span>
                <span v-for="s in form.scope" :key="s" class="apply-scope-tag">
                  <code>{{ s }}</code>
                  <button class="apply-scope-tag-remove" @click="removeScope(s)">
                    <Icon name="lucide:x" size="11" />
                  </button>
                </span>
              </div>

              <!-- 角色绑定 -->
              <div class="apply-scope-section">
                <span class="apply-scope-section-title">角色绑定</span>
                <span class="apply-scope-section-hint">绑定角色后将自动获得该角色的全部权限</span>
              </div>
              <div class="apply-role-group">
                <label
                  v-for="role in availableRoles"
                  :key="role.value"
                  :class="['apply-role-chip', { checked: form.selectedRole === role.value }]"
                >
                  <input
                    type="radio"
                    :checked="form.selectedRole === role.value"
                    class="apply-check-input"
                    name="role-binding"
                    @change="selectRole(role.value)"
                  />
                  <div class="apply-role-info">
                    <span class="apply-role-label">{{ role.label }}</span>
                    <span class="apply-role-desc">{{ role.desc }}</span>
                  </div>
                  <span class="apply-role-level">Lv.{{ role.level }}</span>
                </label>
              </div>

              <!-- 权限绑定 -->
              <div class="apply-scope-section">
                <span class="apply-scope-section-title">权限绑定</span>
                <span class="apply-scope-section-hint">仅可选择你自身拥有的权限</span>
              </div>
              <!-- 权限树 -->
              <div class="apply-scope-tree">
                <template v-for="ns in scopeTree" :key="ns.key">
                  <div class="scope-tree-node scope-tree-ns">
                    <button class="scope-tree-toggle" @click="toggleExpand(ns.key)">
                      <Icon
                        :name="expandedNodes.has(ns.key) ? 'lucide:chevron-down' : 'lucide:chevron-right'"
                        size="13"
                      />
                    </button>
                    <span class="scope-tree-label scope-tree-label-ns">{{ ns.key }}</span>
                  </div>
                  <div v-if="expandedNodes.has(ns.key)" class="scope-tree-children">
                    <template v-for="page in ns.children" :key="page.key">
                      <div class="scope-tree-node scope-tree-page">
                        <span class="scope-tree-indent" />
                        <button class="scope-tree-toggle" @click="toggleExpand(ns.key + ':' + page.key)">
                          <Icon
                            :name="expandedNodes.has(ns.key + ':' + page.key) ? 'lucide:chevron-down' : 'lucide:chevron-right'"
                            size="13"
                          />
                        </button>
                        <span class="scope-tree-label">{{ page.label }}</span>
                      </div>
                      <div v-if="expandedNodes.has(ns.key + ':' + page.key)" class="scope-tree-children">
                        <template v-for="group in page.children" :key="group.key">
                          <div class="scope-tree-node scope-tree-group">
                            <span class="scope-tree-indent" />
                            <span class="scope-tree-indent" />
                            <button class="scope-tree-toggle" @click="toggleExpand(ns.key + ':' + page.key + ':' + group.key)">
                              <Icon
                                :name="expandedNodes.has(ns.key + ':' + page.key + ':' + group.key) ? 'lucide:chevron-down' : 'lucide:chevron-right'"
                                size="13"
                              />
                            </button>
                            <span class="scope-tree-label scope-tree-group-label">{{ group.label }}</span>
                          </div>
                          <div v-if="expandedNodes.has(ns.key + ':' + page.key + ':' + group.key)" class="scope-tree-children">
                            <label
                              v-for="action in group.children"
                              :key="action.key"
                              class="scope-tree-node scope-tree-action"
                            >
                              <span class="scope-tree-indent" />
                              <span class="scope-tree-indent" />
                              <span class="scope-tree-indent" />
                              <input
                                type="checkbox"
                                class="scope-tree-check"
                                :checked="form.scope.includes(action.key)"
                                @change="toggleScope(action.key)"
                              />
                              <div class="scope-tree-action-info">
                                <span class="scope-tree-label scope-tree-action-label">{{ action.label }}</span>
                                <span v-if="action.desc" class="scope-tree-action-desc">{{ action.desc }}</span>
                              </div>
                              <code class="scope-tree-code">{{ action.key }}</code>
                            </label>
                          </div>
                        </template>
                      </div>
                    </template>
                  </div>
                </template>
              </div>
              <!-- 自定义输入 -->
              <div class="apply-scope-custom">
                <input
                  v-model="customScope"
                  class="form-input"
                  placeholder="输入自定义 scope，如 core:*:*:*"
                  @keydown.enter.prevent="addCustomScope"
                />
                <button class="button button-small" @click="addCustomScope">添加</button>
              </div>
              <span class="apply-hint">从权限树中选择，或手动输入自定义 scope；多个 scope 将以英文逗号分隔提交</span>
            </div>
          </fieldset>

          <!-- 提交按钮 -->
          <div class="apply-submit-row">
            <NuxtLink to="/apps" class="button">取消</NuxtLink>
            <button
              class="button button-primary"
              :disabled="saving"
              @click="submit"
            >
              <Icon v-if="saving" name="lucide:loader-circle" size="14" class="spin-icon" />
              {{ saving ? "提交中…" : "提交申请" }}
            </button>
          </div>
        </div>
      </div>

      <!-- 右栏：引导卡片 -->
      <aside class="apply-guide">
        <div class="apply-guide-card">
          <div class="apply-guide-icon">
            <Icon name="lucide:file-text" size="20" />
          </div>
          <h4 class="apply-guide-title">填写提示</h4>
          <div class="apply-guide-section">
            <h5 class="apply-guide-subtitle">应用信息</h5>
            <ul class="apply-guide-list">
              <li>应用名称将展示在授权页面，建议使用品牌全称</li>
              <li>清晰的用途说明有助于审核人员快速理解你的业务场景</li>
              <li>回调地址是用户完成授权后的跳转目标，生产环境需使用 HTTPS</li>
            </ul>
          </div>
          <div class="apply-guide-section">
            <h5 class="apply-guide-subtitle">权限策略</h5>
            <ul class="apply-guide-list">
              <li>按最小权限原则选择 Scope，仅申请业务实际需要的权限</li>
              <li>审核时会重点评估权限范围是否合理</li>
            </ul>
          </div>
        </div>

        <div class="apply-guide-card">
          <div class="apply-guide-icon" style="background: var(--color-warning-soft); color: var(--color-warning)">
            <Icon name="lucide:shield-check" size="20" />
          </div>
          <h4 class="apply-guide-title">审核说明</h4>
          <ul class="apply-guide-list">
            <li><strong>时效</strong>：提交后 1~3 个工作日内处理</li>
            <li><strong>重点</strong>：应用名称真实性、回调地址合法性、权限范围合理性</li>
            <li><strong>拒绝</strong>：会附原因说明，修正后可重新提交</li>
            <li><strong>通过</strong>：获得 Client ID 和 Secret，应用即刻可用</li>
          </ul>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.apply-page {
  max-width: 100%;
  margin: 0 auto;
  width: 100%;
  display: grid;
  gap: 20px;
}

.apply-breadcrumb {
  display: flex;
  align-items: center;
}

.apply-breadcrumb-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-font-secondary);
  text-decoration: none;
  padding: 4px 0;
  transition: color 0.15s;
}

.apply-breadcrumb-link:hover {
  color: var(--color-emphasis);
}

.apply-layout {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 24px;
  align-items: start;
}

.apply-form-card {
  padding: 24px;
}

.apply-fieldset {
  border: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 14px;
}

.apply-fieldset-legend {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-font);
  padding: 0 0 10px 0;
  border-bottom: 1px solid var(--color-border);
  width: 100%;
}

.apply-field {
  display: grid;
  gap: 4px;
}

.apply-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-font);
}

.apply-required {
  color: var(--color-error);
  margin-left: 2px;
}

.apply-hint {
  font-size: 12px;
  color: var(--color-font-assist);
}

.apply-textarea {
  width: 100%;
  min-height: 80px;
  padding: 8px 12px;
  font-size: 13px;
  font-family: inherit;
  color: var(--color-font);
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  outline: none;
  resize: vertical;
  box-sizing: border-box;
}

.apply-textarea:focus {
  border-color: var(--color-emphasis);
  box-shadow: 0 0 0 2px var(--color-emphasis-soft);
}

.apply-textarea::placeholder {
  color: var(--color-font-assist);
  opacity: 0.6;
}

.apply-submit-row {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding-top: 4px;
}

/* ====== 多选组件 ====== */
.apply-check-group {
  display: grid;
  gap: 10px;
}

.apply-check-chip {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.15s;
  user-select: none;
}

.apply-check-chip:hover {
  border-color: var(--color-border-heavy);
  background: var(--color-primary-background);
}

.apply-check-chip.checked {
  border-color: var(--color-emphasis);
  background: var(--color-emphasis-soft);
}

.apply-check-input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
  pointer-events: none;
}

.apply-check-icon {
  width: 22px;
  height: 22px;
  border-radius: var(--radius-sm);
  border: 2px solid var(--color-border-heavy);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.15s;
  color: #fff;
}

.apply-check-chip.checked .apply-check-icon {
  border-color: var(--color-emphasis);
  background: var(--color-emphasis);
}

.apply-check-text {
  display: grid;
  gap: 2px;
}

.apply-check-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-font);
}

.apply-check-desc {
  font-size: 12px;
  color: var(--color-font-assist);
}

/* ====== 授权范围：标签 + 树 + 自定义 ====== */
.apply-scope-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.apply-scope-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 4px 2px 8px;
  background: var(--color-emphasis-soft);
  border: 1px solid rgba(22, 163, 74, 0.15);
  border-radius: var(--radius-sm);
  font-size: 12px;
}

.apply-scope-tag code {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-emphasis);
}

.apply-scope-tag-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: none;
  background: transparent;
  color: var(--color-font-assist);
  border-radius: 3px;
  cursor: pointer;
  padding: 0;
}

.apply-scope-tag-remove:hover {
  background: rgba(22, 163, 74, 0.12);
  color: var(--color-emphasis);
}

.apply-scope-role-tag {
  background: var(--color-info-soft);
  border-color: rgba(59, 130, 246, 0.15);
}

.apply-scope-role-tag code {
  color: var(--color-info);
}

/* 角色/权限分组标题 */
.apply-scope-section {
  display: flex; align-items: baseline; gap: 8px; margin-top: 6px; margin-bottom: 8px;
}
.apply-scope-section-title {
  font-size: 12px; font-weight: 600; color: var(--color-font-secondary);
}
.apply-scope-section-hint {
  font-size: 11px; color: var(--color-font-assist);
}

/* 角色芯片 */
.apply-role-group {
  display: grid; gap: 6px;
}
.apply-role-chip {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 12px; border: 1px solid var(--color-border);
  border-radius: var(--radius-md); cursor: pointer;
  transition: all 0.12s;
}
.apply-role-chip:hover { border-color: var(--color-border-heavy); }
.apply-role-chip.checked { border-color: var(--color-info); background: var(--color-info-soft); }
.apply-role-info { flex: 1; display: grid; gap: 1px; }
.apply-role-label { font-size: 13px; font-weight: 500; color: var(--color-font); }
.apply-role-desc { font-size: 11px; color: var(--color-font-assist); }
.apply-role-level {
  font-size: 10px; font-weight: 700; color: var(--color-font-assist);
  background: var(--color-primary-background); padding: 2px 6px; border-radius: var(--radius-sm);
  font-family: var(--font-mono);
}
.apply-role-chip.checked .apply-role-level { color: var(--color-info); background: rgba(59,130,246,0.1); }

.apply-scope-tree {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 8px 0;
  background: var(--color-card);
}

.scope-tree-node {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  height: 30px;
}

.scope-tree-children {
  /* nested */
}

.scope-tree-toggle {
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

.scope-tree-toggle:hover {
  background: var(--color-primary-background);
}

.scope-tree-label {
  font-size: 13px;
  color: var(--color-font);
}

.scope-tree-label-ns {
  font-weight: 600;
  color: var(--color-emphasis);
  font-family: var(--font-mono);
  font-size: 12px;
}

.scope-tree-action-label {
  font-size: 12px;
  color: var(--color-font-secondary);
}

.scope-tree-group-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-font);
}

.scope-tree-action-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 100px;
}

.scope-tree-action-desc {
  font-size: 11px;
  color: var(--color-font-assist);
}

.scope-tree-code {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-font-assist);
  margin-left: 8px;
}

.scope-tree-check {
  width: 14px;
  height: 14px;
  accent-color: var(--color-emphasis);
  cursor: pointer;
  flex-shrink: 0;
}

.scope-tree-indent {
  display: inline-block;
  width: 20px;
  flex-shrink: 0;
}

.apply-scope-custom {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.apply-scope-custom .form-input {
  flex: 1;
}

.spin-icon {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ====== 右侧引导卡片 ====== */
.apply-guide {
  display: grid;
  gap: 14px;
  position: sticky;
  top: 20px;
}

.apply-guide-card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 18px;
  box-shadow: var(--shadow-card);
}

.apply-guide-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: var(--color-emphasis-soft);
  color: var(--color-emphasis);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
}

.apply-guide-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 10px 0;
}

.apply-guide-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}

.apply-guide-list li {
  font-size: 12px;
  color: var(--color-font-secondary);
  line-height: 1.6;
  padding-left: 14px;
  position: relative;
}

.apply-guide-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--color-border-heavy);
}

/* 指引卡片 */
.apply-guide-section {
  margin-top: 14px;
}

.apply-guide-section + .apply-guide-section {
  border-top: 1px solid var(--color-separator);
  padding-top: 14px;
}

.apply-guide-subtitle {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-font-secondary);
  margin: 0 0 8px 0;
}

/* 响应式 */
@media (max-width: 768px) {
  .apply-layout {
    grid-template-columns: 1fr;
  }

  .apply-guide {
    position: static;
  }

  .apply-form-card {
    padding: 18px;
  }
}

/* 密钥对话框 */
.secret-overlay {
  position: fixed; inset: 0; z-index: 100;
  background: rgba(0, 0, 0, 0.4);
  display: flex; align-items: center; justify-content: center;
}
.secret-dialog {
  width: 420px; max-width: 90vw; padding: 24px;
}
.secret-dialog-header {
  display: flex; align-items: center; gap: 10px;
  margin-bottom: 16px;
}
.secret-dialog-header h3 {
  margin: 0; font-size: 16px; flex: 1;
}
.secret-dialog-close {
  display: inline-flex; align-items: center; justify-content: center;
  width: 28px; height: 28px; border: none; background: transparent;
  color: var(--color-font-assist); border-radius: 6px; cursor: pointer;
}
.secret-dialog-close:hover { background: var(--color-primary-background); }
.secret-warning {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; color: var(--color-warning);
  background: var(--color-warning-soft); padding: 8px 12px;
  border-radius: var(--radius-md); margin-bottom: 16px;
}
.secret-field {
  margin-bottom: 12px;
}
.secret-value {
  display: block; padding: 10px 12px; margin-top: 4px;
  background: var(--color-primary-background);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-family: var(--font-mono); font-size: 12px;
  word-break: break-all; color: var(--color-font-secondary);
}
.secret-value-blur {
  color: transparent; text-shadow: 0 0 10px rgba(0,0,0,0.6);
  user-select: none;
}
.secret-value-blur:focus, .secret-value-blur:active {
  color: var(--color-font-secondary); text-shadow: none; user-select: text;
}
.secret-copy-btn {
  width: 100%; margin-top: 8px;
}
</style>
