<script setup lang="ts">
import { useMockApps, findMockApp, type MockApp } from "~/composables/useMockApps";
import { useToast } from "~/composables/useToast";

definePageMeta({ layout: "default" });

const route = useRoute();
const toast = useToast();
const uuid = route.params.uuid as string;

type AppDetail = MockApp;

const app = ref<AppDetail | null>(null);
const loading = ref(true);
const error = ref("");
const copiedField = ref("");

async function load() {
  loading.value = true;
  error.value = "";

  if (import.meta.dev) {
    const found = findMockApp(uuid);
    if (found) { app.value = found; loading.value = false; return; }
  }

  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; data: AppDetail; msg: string }>(`/api/oauth/client/${uuid}`);
    if (res.code === 200) app.value = res.data;
    else error.value = res.msg || "获取详情失败";
  } catch (e: any) {
    error.value = e?.data?.msg || e?.message || "请求失败";
  } finally {
    loading.value = false;
  }
}

// ---- 状态 & 编辑 ----
const editing = ref(false);
const editForm = ref({ clientName: "", redirectUri: "", description: "", grantTypes: [] as string[], scope: [] as string[] });
const editErrors = ref<Record<string, string>>({});
const saving = ref(false);
const toggling = ref(false);

const grantTypeOptions = [
  { label: "Authorization Code", value: "authorization_code", desc: "标准 OAuth2 授权码模式" },
  { label: "Refresh Token", value: "refresh_token", desc: "允许刷新 Access Token" },
  { label: "Password", value: "password", desc: "账号密码模式（高信任度）" },
];

function toggleCheckbox(arr: string[], val: string) {
  const idx = arr.indexOf(val); if (idx >= 0) arr.splice(idx, 1); else arr.push(val);
}

function startEdit() {
  if (!app.value) return;
  editForm.value = {
    clientName: app.value.clientName,
    redirectUri: app.value.redirectUri,
    description: app.value.description,
    grantTypes: app.value.grantTypes.split(",").filter(Boolean).map(s => s.trim()),
    scope: app.value.scope.split(",").filter(Boolean).map(s => s.trim()),
  };
  editErrors.value = {};
  editing.value = true;
}

function cancelEdit() {
  editing.value = false;
}

function clearEditError(f: string) {
  if (editErrors.value[f]) { const c = { ...editErrors.value }; delete c[f]; editErrors.value = c; }
}

function validateEdit(): boolean {
  const e: Record<string, string> = {};
  if (!editForm.value.clientName.trim()) e.clientName = "请输入应用名称";
  if (!editForm.value.redirectUri.trim()) e.redirectUri = "请输入回调地址";
  else if (!/^https?:\/\/.+/.test(editForm.value.redirectUri)) e.redirectUri = "请输入有效 URL";
  editErrors.value = e;
  return Object.keys(e).length === 0;
}

async function submitEdit() {
  if (!validateEdit()) return;
  saving.value = true;
  try {
    if (import.meta.dev) {
      const apps = useMockApps();
      const target = apps.value.find(a => a.uuid === uuid);
      if (target) {
        target.clientName = editForm.value.clientName;
        target.redirectUri = editForm.value.redirectUri;
        target.description = editForm.value.description;
        target.grantTypes = editForm.value.grantTypes.join(",");
        target.scope = editForm.value.scope.join(",");
        target.auditStatus = 0; // re-submit for audit
        target.auditOpinion = null;
        target.updatedAt = new Date().toISOString().replace("T", " ").substring(0, 19);
      }
      app.value = apps.value.find(a => a.uuid === uuid) || app.value;
      toast.success("已更新并重新提交审核");
      editing.value = false;
      saving.value = false;
      return;
    }
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; msg: string }>(`/api/oauth/client/${uuid}`, {
      method: "PUT",
      body: {
        clientName: editForm.value.clientName,
        redirectUri: editForm.value.redirectUri,
        description: editForm.value.description,
        grantTypes: editForm.value.grantTypes.join(","),
        scope: editForm.value.scope.join(","),
      },
    });
    if (res.code === 200) { toast.success("更新成功"); editing.value = false; await load(); }
    else toast.error(res.msg || "更新失败");
  } catch (e: any) {
    toast.error(e?.data?.msg || e?.message || "请求失败");
  } finally { saving.value = false; }
}

// ---- 启停 ----
async function toggleApp() {
  if (!app.value) return;
  toggling.value = true;
  try {
    if (import.meta.dev) {
      const apps = useMockApps();
      const target = apps.value.find(a => a.uuid === uuid);
      if (target) target.status = target.status === 0 ? 1 : 0;
      app.value = apps.value.find(a => a.uuid === uuid) || app.value;
      toast.success(app.value.status === 1 ? "应用已启用" : "应用已禁用");
      toggling.value = false;
      return;
    }
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; msg: string }>(`/api/oauth/client/${uuid}/toggle`, { method: "POST" });
    if (res.code === 200) { await load(); toast.success("操作成功"); }
    else toast.error(res.msg || "操作失败");
  } catch (e: any) { toast.error(e?.data?.msg || e?.message || "请求失败"); }
  finally { toggling.value = false; }
}

const statusConfig = computed(() => {
  const s = app.value?.auditStatus;
  if (s === 1) return { label: "审核通过", class: "approved", icon: "lucide:check-circle" };
  if (s === 2) return { label: "审核拒绝", class: "rejected", icon: "lucide:x-circle" };
  return { label: "待审核", class: "pending", icon: "lucide:clock" };
});

const enabledLabel = computed(() => (app.value?.status === 1 ? "已启用" : "已禁用"));
const typeLabel: Record<number, string> = { 1: "官方应用", 2: "第三方应用" };

async function copyToClipboard(text: string, field: string) {
  try {
    await navigator.clipboard.writeText(text);
    copiedField.value = field;
    setTimeout(() => { copiedField.value = ""; }, 1500);
  } catch {
    const ta = document.createElement("textarea"); ta.value = text; document.body.appendChild(ta);
    ta.select(); document.execCommand("copy"); document.body.removeChild(ta);
    copiedField.value = field;
    setTimeout(() => { copiedField.value = ""; }, 1500);
  }
}

onMounted(() => {
  load().then(() => {
    if (route.query.edit === "1") nextTick(() => startEdit());
  });
});
</script>

<template>
  <div class="detail-page">
    <!-- 面包屑 -->
    <nav class="detail-breadcrumb fade-up">
      <NuxtLink to="/apps" class="detail-breadcrumb-link">
        <Icon name="lucide:arrow-left" size="14" /> 返回应用列表
      </NuxtLink>
    </nav>

    <!-- 加载态 -->
    <div v-if="loading" class="detail-loading fade-up">
      <div class="skeleton skeleton-card" style="height: 120px" />
      <div class="skeleton skeleton-card" style="height: 180px" />
      <div class="skeleton skeleton-card" style="height: 160px" />
    </div>

    <!-- 错误态 -->
    <div v-else-if="error" class="panel-card fade-up">
      <div class="panel-card-body" style="text-align: center; padding: 48px 24px;">
        <Icon name="lucide:alert-circle" size="40" style="color: var(--color-error); margin-bottom: 12px;" />
        <p style="color: var(--color-error); margin-bottom: 16px;">{{ error }}</p>
        <button class="button button-primary" @click="load">重新加载</button>
      </div>
    </div>

    <!-- 正常态 -->
    <template v-else-if="app">
      <!-- 页面标题 + 状态 -->
      <section class="page-header fade-up">
        <div class="page-header-left">
          <div class="page-header-overline">OAuth Application</div>
          <h1 class="page-header-title">{{ app.clientName }}</h1>
          <p class="page-header-description">{{ app.description || "暂无说明" }}</p>
        </div>
        <div class="detail-header-badges">
          <span :class="['detail-status-badge', statusConfig.class]">
            <Icon :name="statusConfig.icon" size="14" /> {{ statusConfig.label }}
          </span>
          <span class="detail-status-badge" :class="{ disabled: app.status === 0 }">
            <Icon :name="app.status === 1 ? 'lucide:toggle-right' : 'lucide:toggle-left'" size="14" />
            {{ enabledLabel }}
          </span>
        </div>
      </section>

      <!-- 编辑表单（替代卡片区） -->
      <div v-if="editing" class="panel-card fade-up">
        <div class="panel-card-header">
          <div class="panel-card-header-icon"><Icon name="lucide:pencil-line" size="15" /></div>
          <div class="panel-card-header-text">
            <h3>编辑应用</h3>
            <span class="panel-card-header-sub">{{ app.auditStatus === 2 ? '修改后将自动重新提交审核' : '修改应用信息' }}</span>
          </div>
        </div>
        <div class="panel-card-body" style="gap: 16px">
          <div class="edit-field">
            <label class="edit-label">应用名称</label>
            <input v-model="editForm.clientName" :class="['form-input', { 'input-error': editErrors.clientName }]" maxlength="32" @input="clearEditError('clientName')" />
            <span v-if="editErrors.clientName" class="field-error-text">{{ editErrors.clientName }}</span>
          </div>
          <div class="edit-field">
            <label class="edit-label">回调地址</label>
            <input v-model="editForm.redirectUri" :class="['form-input', { 'input-error': editErrors.redirectUri }]" @input="clearEditError('redirectUri')" />
            <span v-if="editErrors.redirectUri" class="field-error-text">{{ editErrors.redirectUri }}</span>
          </div>
          <div class="edit-field">
            <label class="edit-label">应用说明</label>
            <textarea v-model="editForm.description" class="apply-textarea" rows="3" maxlength="500" />
          </div>
          <div class="edit-field">
            <label class="edit-label">授权模式</label>
            <div class="edit-check-group">
              <label v-for="opt in grantTypeOptions" :key="opt.value" :class="['edit-check-chip', { checked: editForm.grantTypes.includes(opt.value) }]">
                <input type="checkbox" :checked="editForm.grantTypes.includes(opt.value)" class="edit-check-input" @change="toggleCheckbox(editForm.grantTypes, opt.value)" />
                <span class="edit-check-mark"><Icon v-if="editForm.grantTypes.includes(opt.value)" name="lucide:check" size="12" /></span>
                <span>{{ opt.label }}</span>
              </label>
            </div>
          </div>
          <div class="edit-actions">
            <button class="button" @click="cancelEdit">取消</button>
            <button class="button button-primary" :disabled="saving" @click="submitEdit">
              {{ saving ? "保存中…" : (app.auditStatus === 2 ? "保存并重新申请" : "保存修改") }}
            </button>
          </div>
        </div>
      </div>

      <!-- 查看模式：内容卡片区 -->
      <template v-else>
        <div class="detail-grid fade-up">
          <!-- 基本信息 -->
          <div class="panel-card detail-card">
            <div class="panel-card-header">
              <div class="panel-card-header-icon"><Icon name="lucide:info" size="15" /></div>
              <div class="panel-card-header-text"><h3>基本信息</h3><span class="panel-card-header-sub">应用注册信息</span></div>
            </div>
            <div class="panel-card-body detail-info-list">
              <div class="detail-info-item">
                <span class="detail-info-label">应用名称</span><span class="detail-info-value">{{ app.clientName }}</span>
              </div>
              <div class="detail-info-item">
                <span class="detail-info-label">应用类型</span><span class="detail-info-value">{{ typeLabel[app.clientType] || "第三方应用" }}</span>
              </div>
              <div class="detail-info-item">
                <span class="detail-info-label">创建时间</span><span class="detail-info-value">{{ app.createdAt }}</span>
              </div>
              <div class="detail-info-item">
                <span class="detail-info-label">最后更新</span><span class="detail-info-value">{{ app.updatedAt || app.createdAt }}</span>
              </div>
            </div>
          </div>

          <!-- OAuth 配置 -->
          <div class="panel-card detail-card">
            <div class="panel-card-header">
              <div class="panel-card-header-icon"><Icon name="lucide:shield" size="15" /></div>
              <div class="panel-card-header-text"><h3>OAuth 配置</h3><span class="panel-card-header-sub">授权回调与权限设置</span></div>
            </div>
            <div class="panel-card-body detail-info-list">
              <div class="detail-info-item">
                <span class="detail-info-label">回调地址</span>
                <div class="detail-info-value-row">
                  <code class="detail-code">{{ app.redirectUri }}</code>
                  <button class="detail-copy-btn" @click="copyToClipboard(app.redirectUri, 'redirect')">
                    <Icon :name="copiedField === 'redirect' ? 'lucide:check' : 'lucide:copy'" size="13" />
                  </button>
                </div>
              </div>
              <div class="detail-info-item">
                <span class="detail-info-label">授权模式</span>
                <span class="detail-info-value"><code class="detail-code-tag">{{ app.grantTypes }}</code></span>
              </div>
              <div class="detail-info-item">
                <span class="detail-info-label">授权范围</span>
                <span class="detail-info-value"><code class="detail-code-tag">{{ app.scope }}</code></span>
              </div>
              <div class="detail-info-item">
                <span class="detail-info-label">应用标识</span>
                <div class="detail-info-value-row">
                  <code class="detail-code detail-code-dim">{{ app.uuid }}</code>
                  <button class="detail-copy-btn" @click="copyToClipboard(app.uuid, 'uuid')">
                    <Icon :name="copiedField === 'uuid' ? 'lucide:check' : 'lucide:copy'" size="13" />
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- 审核信息 -->
          <div class="panel-card detail-card">
            <div class="panel-card-header">
              <div class="panel-card-header-icon"><Icon name="lucide:clipboard-check" size="15" /></div>
              <div class="panel-card-header-text"><h3>审核信息</h3><span class="panel-card-header-sub">审核状态与意见</span></div>
            </div>
            <div class="panel-card-body detail-info-list">
              <div class="detail-info-item">
                <span class="detail-info-label">审核状态</span>
                <span :class="['detail-status-tag', statusConfig.class]">
                  <Icon :name="statusConfig.icon" size="12" /> {{ statusConfig.label }}
                </span>
              </div>
              <div class="detail-info-item">
                <span class="detail-info-label">审核意见</span>
                <span class="detail-info-value" :class="{ 'detail-opinion-rejected': app.auditStatus === 2 }">{{ app.auditOpinion || "暂无" }}</span>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- 底部操作 -->
      <div class="detail-actions fade-up">
        <NuxtLink to="/apps" class="button">
          <Icon name="lucide:arrow-left" size="14" /> 返回列表
        </NuxtLink>
        <!-- 已通过：启停 -->
        <button v-if="app.auditStatus === 1" class="button" :disabled="toggling" @click="toggleApp">
          <Icon :name="app.status === 1 ? 'lucide:toggle-left' : 'lucide:toggle-right'" size="14" />
          {{ app.status === 1 ? "禁用" : "启用" }}
        </button>
        <!-- 待审核 / 已拒绝：编辑 -->
        <button v-if="app.auditStatus === 0 || app.auditStatus === 2" class="button button-primary" @click="startEdit">
          <Icon name="lucide:pencil-line" size="14" />
          {{ app.auditStatus === 2 ? "修改并重新申请" : "编辑" }}
        </button>
        <NuxtLink v-if="app.auditStatus === 1" to="/" class="button button-primary">
          <Icon name="lucide:external-link" size="14" /> 查看授权页面
        </NuxtLink>
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail-page {
  max-width: 100%; margin: 0 auto; width: 100%;
  display: grid; gap: 20px;
}

.detail-breadcrumb { display: flex; align-items: center; }
.detail-breadcrumb-link {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 13px; color: var(--color-font-secondary);
  text-decoration: none; padding: 4px 0; transition: color 0.15s;
}
.detail-breadcrumb-link:hover { color: var(--color-emphasis); }

.detail-loading { display: grid; gap: 16px; }

.detail-header-badges { display: flex; gap: 8px; flex-shrink: 0; }

.detail-status-badge {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 5px 12px; border-radius: var(--radius-full);
  font-size: 12px; font-weight: 500;
  background: var(--color-success-soft); color: var(--color-success);
}
.detail-status-badge.pending { background: var(--color-warning-soft); color: var(--color-warning); }
.detail-status-badge.rejected { background: var(--color-error-soft); color: var(--color-error); }
.detail-status-badge.disabled { background: var(--color-primary-background); color: var(--color-font-assist); }

.detail-grid { display: grid; gap: 16px; }
.detail-card .panel-card-header { padding-bottom: 0; }

.detail-info-list { display: grid; gap: 0; }
.detail-info-item {
  display: grid; grid-template-columns: 100px 1fr; gap: 12px;
  padding: 11px 0; border-bottom: 1px solid var(--color-separator); align-items: center;
}
.detail-info-item:last-child { border-bottom: none; }
.detail-info-label { font-size: 12px; font-weight: 500; color: var(--color-font-secondary); flex-shrink: 0; }
.detail-info-value { font-size: 13px; color: var(--color-font); word-break: break-all; }
.detail-info-value-row { display: flex; align-items: center; gap: 8px; }

.detail-code {
  font-family: var(--font-mono); font-size: 12px; padding: 3px 8px;
  background: var(--color-primary-background); border-radius: var(--radius-sm);
  color: var(--color-font-secondary); word-break: break-all;
}
.detail-code-dim { color: var(--color-font-assist); font-size: 11px; }
.detail-code-tag {
  font-family: var(--font-mono); font-size: 12px; padding: 2px 8px;
  background: var(--color-emphasis-soft); border-radius: var(--radius-sm); color: var(--color-emphasis);
}

.detail-copy-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 26px; height: 26px; border: none; background: transparent;
  color: var(--color-font-assist); border-radius: var(--radius-sm);
  cursor: pointer; flex-shrink: 0; transition: all 0.15s;
}
.detail-copy-btn:hover { background: var(--color-emphasis-soft); color: var(--color-emphasis); }

.detail-status-tag {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 8px; border-radius: var(--radius-sm); font-size: 12px; font-weight: 500;
}
.detail-status-tag.approved { background: var(--color-success-soft); color: var(--color-success); }
.detail-status-tag.pending { background: var(--color-warning-soft); color: var(--color-warning); }
.detail-status-tag.rejected { background: var(--color-error-soft); color: var(--color-error); }
.detail-opinion-rejected { color: var(--color-error) !important; font-weight: 500; }

.detail-actions { display: flex; gap: 8px; padding-top: 4px; }

/* 编辑表单 */
.edit-field { display: grid; gap: 4px; }
.edit-label { font-size: 13px; font-weight: 500; color: var(--color-font); }
.edit-field .apply-textarea {
  width: 100%; min-height: 72px; padding: 8px 12px; font-size: 13px; font-family: inherit;
  color: var(--color-font); background: var(--color-card); border: 1px solid var(--color-border);
  border-radius: var(--radius-md); outline: none; resize: vertical; box-sizing: border-box;
}
.edit-field .apply-textarea:focus { border-color: var(--color-emphasis); box-shadow: 0 0 0 2px var(--color-emphasis-soft); }

.edit-check-group { display: flex; flex-wrap: wrap; gap: 8px; }
.edit-check-chip {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 12px; border: 1px solid var(--color-border);
  border-radius: var(--radius-md); cursor: pointer; font-size: 12px;
  transition: all 0.12s; user-select: none;
}
.edit-check-chip:hover { border-color: var(--color-border-heavy); }
.edit-check-chip.checked { border-color: var(--color-emphasis); background: var(--color-emphasis-soft); color: var(--color-emphasis); }
.edit-check-input { display: none; }
.edit-check-mark {
  width: 16px; height: 16px; border-radius: 3px; border: 2px solid var(--color-border-heavy);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0; transition: all 0.12s;
}
.edit-check-chip.checked .edit-check-mark { border-color: var(--color-emphasis); background: var(--color-emphasis); color: #fff; }

.edit-actions { display: flex; gap: 8px; justify-content: flex-end; padding-top: 4px; }

@media (max-width: 640px) {
  .detail-info-item { grid-template-columns: 1fr; gap: 2px; padding: 10px 0; }
  .detail-header-badges { margin-top: 8px; }
}
</style>
