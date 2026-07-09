<script setup lang="ts">
import { useToast } from "~/composables/useToast";

definePageMeta({ layout: "default" });

interface Endpoint {
  id: number;
  serviceName: string;
  httpMethod: string;
  pathPattern: string;
  requiredCode: string;
  description: string;
  source: number;
  status: number;
  reportedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

const toast = useToast();

// ---- 数据状态 ----
const endpoints = ref<Endpoint[]>([]);
const loading = ref(true);
const error = ref("");

// ---- 筛选 ----
const filterService = ref("");
const filterMethod = ref("");
const filterPath = ref("");

// ---- 分页 ----
const page = ref(1);
const pageSize = 12;

// ---- 弹窗 ----
const dialogShow = ref(false);
const dialogTitle = ref("新建接口端点");
const dialogLoading = ref(false);
const dialogError = ref("");
const isEditing = ref(false);
const editingId = ref<number | null>(null);

const form = reactive({
  serviceName: "",
  httpMethod: "GET",
  pathPattern: "",
  requiredCode: "",
  description: "",
});

function resetForm() {
  form.serviceName = "";
  form.httpMethod = "GET";
  form.pathPattern = "";
  form.requiredCode = "";
  form.description = "";
  dialogError.value = "";
}

function openCreate() {
  resetForm();
  isEditing.value = false;
  editingId.value = null;
  dialogTitle.value = "新建接口端点";
  dialogShow.value = true;
}

function openEdit(ep: Endpoint) {
  form.serviceName = ep.serviceName;
  form.httpMethod = ep.httpMethod;
  form.pathPattern = ep.pathPattern;
  form.requiredCode = ep.requiredCode;
  form.description = ep.description;
  isEditing.value = true;
  editingId.value = ep.id;
  dialogTitle.value = "编辑接口端点";
  dialogError.value = "";
  dialogShow.value = true;
}

function closeDialog() {
  dialogShow.value = false;
  resetForm();
}

// ---- 删除确认 ----
const deleteTarget = ref<Endpoint | null>(null);
const deleteShow = ref(false);
const deleteLoading = ref(false);

function openDelete(ep: Endpoint) {
  deleteTarget.value = ep;
  deleteShow.value = true;
}

function closeDelete() {
  deleteTarget.value = null;
  deleteShow.value = false;
}

// ---- 模拟数据 ----
function createMockEndpoints(): Endpoint[] {
  const now = "2026-07-";
  const mocks: Endpoint[] = [
    // yunanexus-auth
    { id: 1, serviceName: "yunanexus-auth", httpMethod: "POST", pathPattern: "/api/login", requiredCode: "", description: "用户登录接口", source: 0, status: 1, reportedAt: `${now}01 08:12:00`, createdAt: `${now}01 08:12:00`, updatedAt: `${now}01 08:12:00` },
    { id: 2, serviceName: "yunanexus-auth", httpMethod: "POST", pathPattern: "/api/login/refresh", requiredCode: "", description: "刷新令牌", source: 0, status: 1, reportedAt: `${now}01 08:12:00`, createdAt: `${now}01 08:12:00`, updatedAt: `${now}01 08:12:00` },
    { id: 3, serviceName: "yunanexus-auth", httpMethod: "POST", pathPattern: "/api/login/logout", requiredCode: "", description: "用户登出", source: 0, status: 1, reportedAt: `${now}01 08:12:00`, createdAt: `${now}01 08:12:00`, updatedAt: `${now}01 08:12:00` },
    { id: 4, serviceName: "yunanexus-auth", httpMethod: "GET", pathPattern: "/api/key/public", requiredCode: "", description: "获取公钥", source: 0, status: 1, reportedAt: `${now}01 08:12:00`, createdAt: `${now}01 08:12:00`, updatedAt: `${now}01 08:12:00` },
    { id: 5, serviceName: "yunanexus-auth", httpMethod: "POST", pathPattern: "/api/user/register", requiredCode: "", description: "用户注册", source: 0, status: 1, reportedAt: `${now}01 08:12:00`, createdAt: `${now}01 08:12:00`, updatedAt: `${now}01 08:12:00` },
    { id: 6, serviceName: "yunanexus-auth", httpMethod: "GET", pathPattern: "/api/admin/users", requiredCode: "user:list", description: "获取用户列表", source: 1, status: 1, reportedAt: null, createdAt: `${now}02 14:30:00`, updatedAt: `${now}05 09:15:00` },
    { id: 7, serviceName: "yunanexus-auth", httpMethod: "PUT", pathPattern: "/api/admin/users/{id}/role", requiredCode: "user:role:assign", description: "分配用户角色", source: 1, status: 1, reportedAt: null, createdAt: `${now}02 14:30:00`, updatedAt: `${now}02 14:30:00` },
    // yunanexus-user
    { id: 8, serviceName: "yunanexus-user", httpMethod: "GET", pathPattern: "/api/user/profile", requiredCode: "", description: "获取当前用户资料", source: 0, status: 1, reportedAt: `${now}01 08:15:00`, createdAt: `${now}01 08:15:00`, updatedAt: `${now}01 08:15:00` },
    { id: 9, serviceName: "yunanexus-user", httpMethod: "PUT", pathPattern: "/api/user/profile", requiredCode: "", description: "更新用户资料", source: 0, status: 1, reportedAt: `${now}01 08:15:00`, createdAt: `${now}01 08:15:00`, updatedAt: `${now}01 08:15:00` },
    { id: 10, serviceName: "yunanexus-user", httpMethod: "POST", pathPattern: "/api/user/avatar", requiredCode: "", description: "上传用户头像", source: 0, status: 1, reportedAt: `${now}01 08:15:00`, createdAt: `${now}01 08:15:00`, updatedAt: `${now}01 08:15:00` },
    { id: 11, serviceName: "yunanexus-user", httpMethod: "GET", pathPattern: "/api/user/menus", requiredCode: "", description: "获取用户菜单权限", source: 0, status: 1, reportedAt: `${now}01 08:15:00`, createdAt: `${now}01 08:15:00`, updatedAt: `${now}01 08:15:00` },
    { id: 12, serviceName: "yunanexus-user", httpMethod: "GET", pathPattern: "/api/user/buttons", requiredCode: "", description: "获取用户按钮权限", source: 0, status: 1, reportedAt: `${now}01 08:15:00`, createdAt: `${now}01 08:15:00`, updatedAt: `${now}01 08:15:00` },
    { id: 13, serviceName: "yunanexus-user", httpMethod: "DELETE", pathPattern: "/api/admin/users/{id}", requiredCode: "user:delete", description: "删除用户", source: 1, status: 0, reportedAt: null, createdAt: `${now}03 10:20:00`, updatedAt: `${now}06 11:45:00` },
    // yunanexus-file
    { id: 14, serviceName: "yunanexus-file", httpMethod: "GET", pathPattern: "/api/files", requiredCode: "", description: "获取文件列表", source: 0, status: 1, reportedAt: `${now}01 08:20:00`, createdAt: `${now}01 08:20:00`, updatedAt: `${now}01 08:20:00` },
    { id: 15, serviceName: "yunanexus-file", httpMethod: "POST", pathPattern: "/api/files/upload", requiredCode: "", description: "上传文件", source: 0, status: 1, reportedAt: `${now}01 08:20:00`, createdAt: `${now}01 08:20:00`, updatedAt: `${now}01 08:20:00` },
    { id: 16, serviceName: "yunanexus-file", httpMethod: "GET", pathPattern: "/api/files/{id}/download", requiredCode: "", description: "下载文件", source: 0, status: 1, reportedAt: `${now}01 08:20:00`, createdAt: `${now}01 08:20:00`, updatedAt: `${now}01 08:20:00` },
    { id: 17, serviceName: "yunanexus-file", httpMethod: "DELETE", pathPattern: "/api/files/{id}", requiredCode: "file:delete", description: "删除文件", source: 1, status: 1, reportedAt: null, createdAt: `${now}02 09:00:00`, updatedAt: `${now}02 09:00:00` },
    { id: 18, serviceName: "yunanexus-file", httpMethod: "GET", pathPattern: "/api/admin/files/all", requiredCode: "file:admin:list", description: "管理员查看所有文件", source: 1, status: 1, reportedAt: null, createdAt: `${now}04 16:30:00`, updatedAt: `${now}04 16:30:00` },
  ];
  return mocks;
}

// ---- 数据加载 ----
async function loadEndpoints() {
  loading.value = true;
  error.value = "";
  if (import.meta.dev) {
    await new Promise((r) => setTimeout(r, 300));
    endpoints.value = [...createMockEndpoints()];
    loading.value = false;
    return;
  }
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const qs = new URLSearchParams();
    if (filterService.value) qs.set("service", filterService.value);
    if (filterMethod.value) qs.set("method", filterMethod.value);
    if (filterPath.value) qs.set("path", filterPath.value);
    qs.set("page", String(page.value));
    qs.set("size", String(pageSize));
    const res = await fetch<{ code: number; data: { records: Endpoint[]; total: number }; msg: string }>(
      `/api/admin/endpoints?${qs.toString()}`,
    );
    if (res.code === 200) endpoints.value = res.data.records || [];
    else error.value = res.msg || "获取接口列表失败";
  } catch (e: any) {
    error.value = e?.data?.msg || e?.message || "请求失败";
  } finally {
    loading.value = false;
  }
}

// ---- 派生 ----
const services = computed(() => {
  const set = new Set(endpoints.value.map((e) => e.serviceName));
  return Array.from(set).sort();
});

const filteredEndpoints = computed(() => {
  let list = endpoints.value;
  if (filterService.value) list = list.filter((e) => e.serviceName === filterService.value);
  if (filterMethod.value) list = list.filter((e) => e.httpMethod === filterMethod.value);
  if (filterPath.value) list = list.filter((e) => e.pathPattern.toLowerCase().includes(filterPath.value.toLowerCase()));
  return list;
});

const totalPages = computed(() => Math.max(1, Math.ceil(filteredEndpoints.value.length / pageSize)));
const pagedEndpoints = computed(() => {
  const start = (page.value - 1) * pageSize;
  return filteredEndpoints.value.slice(start, start + pageSize);
});

// 当筛选条件变化时重置页码
watch([filterService, filterMethod, filterPath], () => {
  page.value = 1;
});

// ---- 创建/编辑 ----
async function submitForm() {
  dialogError.value = "";
  if (!form.serviceName.trim()) { dialogError.value = "请输入服务名称"; return; }
  if (!form.pathPattern.trim()) { dialogError.value = "请输入路径模式"; return; }
  dialogLoading.value = true;

  if (import.meta.dev) {
    await new Promise((r) => setTimeout(r, 400));
    if (isEditing.value && editingId.value !== null) {
      const idx = endpoints.value.findIndex((e) => e.id === editingId.value);
      if (idx > -1) {
        endpoints.value[idx] = {
          ...endpoints.value[idx],
          serviceName: form.serviceName.trim(),
          httpMethod: form.httpMethod,
          pathPattern: form.pathPattern.trim(),
          requiredCode: form.requiredCode.trim(),
          description: form.description.trim(),
          updatedAt: new Date().toISOString().replace("T", " ").substring(0, 19),
        } as Endpoint;
      }
    } else {
      const newEp: Endpoint = {
        id: Math.max(...endpoints.value.map((e) => e.id), 0) + 1,
        serviceName: form.serviceName.trim(),
        httpMethod: form.httpMethod,
        pathPattern: form.pathPattern.trim(),
        requiredCode: form.requiredCode.trim(),
        description: form.description.trim(),
        source: 1,
        status: 1,
        reportedAt: null,
        createdAt: new Date().toISOString().replace("T", " ").substring(0, 19),
        updatedAt: new Date().toISOString().replace("T", " ").substring(0, 19),
      };
      endpoints.value.unshift(newEp);
    }
    toast.success(isEditing.value ? "更新成功" : "创建成功");
    dialogLoading.value = false;
    closeDialog();
    return;
  }

  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const body: Record<string, string> = {
      serviceName: form.serviceName.trim(),
      httpMethod: form.httpMethod,
      pathPattern: form.pathPattern.trim(),
      requiredCode: form.requiredCode.trim(),
      description: form.description.trim(),
    };
    let res: { code: number; msg: string };
    if (isEditing.value && editingId.value !== null) {
      res = await fetch(`/api/admin/endpoints/${editingId.value}`, { method: "PUT", body });
    } else {
      res = await fetch("/api/admin/endpoints", { method: "POST", body });
    }
    if (res.code === 200) {
      toast.success(isEditing.value ? "更新成功" : "创建成功");
      closeDialog();
      await loadEndpoints();
    } else {
      dialogError.value = res.msg || "操作失败";
    }
  } catch (e: any) {
    dialogError.value = e?.data?.msg || e?.message || "请求失败";
  } finally {
    dialogLoading.value = false;
  }
}

// ---- 状态切换 ----
async function toggleStatus(ep: Endpoint) {
  if (import.meta.dev) {
    const idx = endpoints.value.findIndex((e) => e.id === ep.id);
    if (idx > -1) {
      endpoints.value[idx] = { ...endpoints.value[idx], status: ep.status === 1 ? 0 : 1 } as Endpoint;
    }
    toast.success(ep.status === 1 ? "已禁用" : "已启用");
    return;
  }
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; msg: string }>(`/api/admin/endpoints/${ep.id}/status`, { method: "PUT" });
    if (res.code === 200) {
      toast.success(ep.status === 1 ? "已禁用" : "已启用");
      await loadEndpoints();
    } else {
      toast.error(res.msg || "操作失败");
    }
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
    endpoints.value = endpoints.value.filter((e) => e.id !== deleteTarget.value!.id);
    toast.success("已删除");
    deleteLoading.value = false;
    closeDelete();
    return;
  }
  try {
    const { $fetch: _f } = useNuxtApp();
    const fetch = _f as typeof $fetch;
    const res = await fetch<{ code: number; msg: string }>(`/api/admin/endpoints/${deleteTarget.value.id}`, { method: "DELETE" });
    if (res.code === 200) {
      toast.success("已删除");
      closeDelete();
      await loadEndpoints();
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
const methodColors: Record<string, string> = { GET: "green", POST: "blue", PUT: "orange", DELETE: "red" };
const methodIcons: Record<string, string> = { GET: "lucide:arrow-down-left", POST: "lucide:plus-circle", PUT: "lucide:pencil", DELETE: "lucide:trash-2" };
const sourceLabel: Record<number, string> = { 0: "自动", 1: "手动" };

function goPage(p: number) {
  if (p < 1 || p > totalPages.value) return;
  page.value = p;
}

onMounted(loadEndpoints);
</script>

<template>
  <div class="apps-page">
    <section class="page-header fade-up">
      <div class="page-header-left">
        <div class="page-header-overline">Admin / Endpoints</div>
        <h1 class="page-header-title">接口端点管理</h1>
        <p class="page-header-description">管理后端 API 端点的注册、权限码与启停状态</p>
      </div>
      <button class="button button-primary" @click="openCreate">
        <Icon name="lucide:plus" size="15" />
        新建端点
      </button>
    </section>

    <!-- 筛选栏 -->
    <div class="endpoint-filters fade-up">
      <select v-model="filterService" class="form-input" style="width: 180px">
        <option value="">全部服务</option>
        <option v-for="s in services" :key="s" :value="s">{{ s }}</option>
      </select>
      <select v-model="filterMethod" class="form-input" style="width: 120px">
        <option value="">全部方法</option>
        <option value="GET">GET</option>
        <option value="POST">POST</option>
        <option value="PUT">PUT</option>
        <option value="DELETE">DELETE</option>
      </select>
      <div class="endpoint-search">
        <Icon name="lucide:search" size="14" class="endpoint-search-icon" />
        <input
          v-model="filterPath"
          class="form-input endpoint-search-input"
          placeholder="搜索路径…"
        />
      </div>
      <span class="endpoint-count">共 {{ filteredEndpoints.length }} 条</span>
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
        <button class="button button-primary" @click="loadEndpoints">重新加载</button>
      </div>
    </div>

    <!-- 空态 -->
    <div v-else-if="filteredEndpoints.length === 0" class="panel-card fade-up">
      <div class="panel-card-body" style="text-align: center; padding: 48px 24px; color: var(--color-font-assist)">
        <Icon name="lucide:plug" size="48" style="margin-bottom: 16px; opacity: 0.3" />
        <p style="font-size: 16px; margin-bottom: 8px">暂无匹配的接口端点</p>
        <p style="font-size: 13px">调整筛选条件或重新加载试试</p>
      </div>
    </div>

    <!-- 表格 -->
    <div v-else class="panel-card fade-up" style="padding: 0; overflow: hidden">
      <div class="endpoint-table-wrapper">
        <table class="endpoint-table">
          <thead>
            <tr>
              <th>服务名称</th>
              <th>方法</th>
              <th>路径模式</th>
              <th>权限码</th>
              <th>描述</th>
              <th>来源</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="ep in pagedEndpoints" :key="ep.id">
              <td>
                <span class="ep-service-badge">{{ ep.serviceName }}</span>
              </td>
              <td>
                <span :class="['ep-method-tag', `ep-method-${methodColors[ep.httpMethod] || 'gray'}`]">
                  <Icon :name="methodIcons[ep.httpMethod] || 'lucide:circle'" size="12" />
                  {{ ep.httpMethod }}
                </span>
              </td>
              <td>
                <code class="ep-path">{{ ep.pathPattern }}</code>
              </td>
              <td>
                <code v-if="ep.requiredCode" class="ep-code-tag">{{ ep.requiredCode }}</code>
                <span v-else class="ep-code-none">—</span>
              </td>
              <td class="ep-desc-cell">
                <span :title="ep.description">{{ ep.description || "—" }}</span>
              </td>
              <td>
                <span :class="['ep-source-tag', ep.source === 0 ? 'ep-source-auto' : 'ep-source-manual']">
                  {{ sourceLabel[ep.source] }}
                </span>
              </td>
              <td>
                <span :class="['ep-status-badge', ep.status === 1 ? 'ep-status-on' : 'ep-status-off']">
                  {{ ep.status === 1 ? "启用" : "禁用" }}
                </span>
              </td>
              <td>
                <div class="ep-actions">
                  <button class="button button-small" @click="openEdit(ep)" title="编辑">
                    <Icon name="lucide:pencil" size="13" />
                  </button>
                  <button class="button button-small" @click="toggleStatus(ep)" :title="ep.status === 1 ? '禁用' : '启用'">
                    <Icon :name="ep.status === 1 ? 'lucide:power-off' : 'lucide:power'" size="13" />
                  </button>
                  <button class="button button-small button-danger" @click="openDelete(ep)" title="删除">
                    <Icon name="lucide:trash-2" size="13" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页 -->
      <div v-if="totalPages > 1" class="pagination">
        <button class="pagination-btn" :disabled="page <= 1" @click="goPage(page - 1)">
          <Icon name="lucide:chevron-left" size="14" />
        </button>
        <template v-for="p in totalPages" :key="p">
          <button
            v-if="p <= 3 || p > totalPages - 3 || Math.abs(p - page) <= 1"
            :class="['pagination-btn', { active: p === page }]"
            @click="goPage(p)"
          >
            {{ p }}
          </button>
          <span v-else-if="p === 4 && page > 4" class="pagination-ellipsis">…</span>
          <span v-else-if="p === totalPages - 3 && page < totalPages - 3" class="pagination-ellipsis">…</span>
        </template>
        <button class="pagination-btn" :disabled="page >= totalPages" @click="goPage(page + 1)">
          <Icon name="lucide:chevron-right" size="14" />
        </button>
        <span class="pagination-info">第 {{ page }} / {{ totalPages }} 页</span>
      </div>
    </div>

    <!-- 创建/编辑弹窗 -->
    <Modal :show="dialogShow" :title="dialogTitle" width="480px" @close="closeDialog">
      <div class="dialog-form">
        <div v-if="dialogError" class="profile-edit-error" style="margin-bottom: 6px">{{ dialogError }}</div>

        <div class="form-field">
          <label>服务名称 <span style="color: var(--color-error)">*</span></label>
          <input v-model="form.serviceName" class="form-input" placeholder="例如 yunanexus-auth" :disabled="dialogLoading" />
        </div>

        <div class="form-field">
          <label>HTTP 方法</label>
          <select v-model="form.httpMethod" class="form-input" :disabled="dialogLoading">
            <option value="GET">GET</option>
            <option value="POST">POST</option>
            <option value="PUT">PUT</option>
            <option value="DELETE">DELETE</option>
          </select>
        </div>

        <div class="form-field">
          <label>路径模式 <span style="color: var(--color-error)">*</span></label>
          <input v-model="form.pathPattern" class="form-input" placeholder="例如 /api/user/profile" :disabled="dialogLoading" />
        </div>

        <div class="form-field">
          <label>权限码</label>
          <input v-model="form.requiredCode" class="form-input" placeholder="例如 user:read" :disabled="dialogLoading" />
          <span class="apply-hint">留空表示无需权限校验</span>
        </div>

        <div class="form-field">
          <label>描述</label>
          <textarea
            v-model="form.description"
            class="form-input"
            rows="3"
            placeholder="接口功能描述（可选）"
            style="height: auto; padding: 8px 12px; resize: vertical; font-family: inherit"
            :disabled="dialogLoading"
          />
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
    <Modal :show="deleteShow" title="确认删除" width="380px" @close="closeDelete">
      <div style="display: grid; gap: 8px">
        <p style="margin: 0; color: var(--color-font-secondary)">确定要删除以下接口端点吗？此操作不可撤销。</p>
        <div v-if="deleteTarget" class="delete-preview">
          <span :class="['ep-method-tag', `ep-method-${methodColors[deleteTarget.httpMethod] || 'gray'}`]">{{ deleteTarget.httpMethod }}</span>
          <code class="ep-path">{{ deleteTarget.pathPattern }}</code>
          <span style="font-size: 12px; color: var(--color-font-assist)">— {{ deleteTarget.serviceName }}</span>
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

/* ---- 筛选栏 ---- */
.endpoint-filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.endpoint-search {
  position: relative;
  flex: 1;
  min-width: 180px;
  max-width: 280px;
}

.endpoint-search-icon {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-font-assist);
  pointer-events: none;
}

.endpoint-search-input {
  padding-left: 32px !important;
}

.endpoint-count {
  font-size: 12px;
  color: var(--color-font-assist);
  white-space: nowrap;
}

/* ---- 表格 ---- */
.endpoint-table-wrapper {
  overflow-x: auto;
}

.endpoint-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.endpoint-table thead {
  background: var(--color-primary-background);
}

.endpoint-table th {
  padding: 10px 14px;
  text-align: left;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-font-assist);
  text-transform: uppercase;
  letter-spacing: 0.03em;
  white-space: nowrap;
  border-bottom: 1px solid var(--color-border);
}

.endpoint-table td {
  padding: 11px 14px;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-font-secondary);
  vertical-align: middle;
}

.endpoint-table tbody tr {
  transition: background 0.1s;
}

.endpoint-table tbody tr:hover {
  background: var(--color-primary-background);
}

.endpoint-table tbody tr:last-child td {
  border-bottom: none;
}

/* 服务名称 badge */
.ep-service-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 500;
  background: var(--color-primary-background);
  color: var(--color-font-secondary);
  border: 1px solid var(--color-border);
  white-space: nowrap;
}

/* HTTP Method 彩色标签 */
.ep-method-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 600;
  font-family: var(--font-mono);
  white-space: nowrap;
}

.ep-method-green {
  background: var(--color-success-soft);
  color: var(--color-success);
  border: 1px solid rgba(22, 163, 74, 0.18);
}

.ep-method-blue {
  background: var(--color-info-soft);
  color: var(--color-info);
  border: 1px solid rgba(2, 132, 199, 0.18);
}

.ep-method-orange {
  background: var(--color-warning-soft);
  color: var(--color-warning);
  border: 1px solid rgba(217, 119, 6, 0.18);
}

.ep-method-red {
  background: var(--color-error-soft);
  color: var(--color-error);
  border: 1px solid rgba(220, 38, 38, 0.18);
}

/* 路径 mono */
.ep-path {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-font);
  background: var(--color-primary-background);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  white-space: nowrap;
}

/* 权限码 */
.ep-code-tag {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-info-text);
  background: var(--color-info-soft);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  border: 1px solid rgba(2, 132, 199, 0.12);
  white-space: nowrap;
}

.ep-code-none {
  color: var(--color-font-assist);
  font-size: 12px;
}

/* 描述单元格 */
.ep-desc-cell span {
  display: block;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}

/* 来源标签 */
.ep-source-tag {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  white-space: nowrap;
}

.ep-source-auto {
  background: var(--color-emphasis-soft);
  color: var(--color-emphasis);
}

.ep-source-manual {
  background: var(--color-primary-background);
  color: var(--color-font-assist);
  border: 1px solid var(--color-border);
}

/* 状态 */
.ep-status-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

.ep-status-on {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.ep-status-off {
  background: var(--color-error-soft);
  color: var(--color-error);
}

/* 操作 */
.ep-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ---- 分页 ---- */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 14px 20px;
  border-top: 1px solid var(--color-border);
}

.pagination-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  padding: 0 6px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-family: var(--font-mono);
  color: var(--color-font-secondary);
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.1s;
}

.pagination-btn:hover:not(:disabled):not(.active) {
  background: var(--color-primary-background);
  border-color: var(--color-border);
}

.pagination-btn.active {
  background: var(--color-emphasis);
  color: #fff;
  border-color: var(--color-emphasis);
}

.pagination-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.pagination-ellipsis {
  width: 32px;
  text-align: center;
  font-size: 13px;
  color: var(--color-font-assist);
}

.pagination-info {
  font-size: 12px;
  color: var(--color-font-assist);
  margin-left: 10px;
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

/* ---- 删除预览 ---- */
.delete-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--color-primary-background);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
}

/* ---- 加载旋转 ---- */
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 响应式 */
@media (max-width: 900px) {
  .endpoint-filters {
    flex-direction: column;
    align-items: stretch;
  }

  .endpoint-filters .form-input,
  .endpoint-search {
    width: 100% !important;
    max-width: none;
  }

  .endpoint-search {
    max-width: none;
  }
}

@media (max-width: 768px) {
  .endpoint-table th:nth-child(5),
  .endpoint-table td:nth-child(5),
  .endpoint-table th:nth-child(6),
  .endpoint-table td:nth-child(6) {
    display: none;
  }
}
</style>
