<script setup lang="ts">
import mascot from "~/assets/mascot/YunaImageMascotQQ.jpg";
import { useMyProfile } from "~/composables/useMyProfile";

definePageMeta({ layout: "default" });

const { currentUuid, isLoggedIn } = useAuth();
const { profile, loading, error, fetch: refreshProfile } = useMyProfile();

interface ResourceVO {
  code: string;
  name: string;
  icon: string;
  path: string;
}

const showedBadges = computed<ResourceVO[]>(() => {
  if (!profile.value?.showcaseBadges) return [];
  try {
    return JSON.parse(profile.value.showcaseBadges);
  } catch {
    return [];
  }
});

function formatDate(str: string): string {
  if (!str) return "";
  return new Date(str).toLocaleDateString("zh-CN");
}

function formatBirthday(str: string): string {
  if (!str) return "未设置";
  return str.substring(0, 10);
}

function formatSize(bytes: number): string {
  if (bytes === 0) return "0 B";
  if (bytes < 0) return "--";
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + " KB";
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + " MB";
  return (bytes / 1073741824).toFixed(1) + " GB";
}

const genderLabel: Record<string, string> = {
  男: "男",
  女: "女",
  未知: "保密",
};
const genderOptions = ["男", "女", "未知"];

const storage = ref({ used: 0, max: 0, unlimited: false });

const editing = ref(false);
const editForm = ref({ nickname: "", gender: "", birthday: "", bio: "" });
const editSaving = ref(false);
const editError = ref("");

const avatarUploading = ref(false);
const avatarFile = ref<File | null>(null);
const avatarPreview = ref("");
const avatarError = ref("");
const fileInputRef = ref<HTMLInputElement>();

function startEdit() {
  if (!profile.value) return;
  editForm.value = {
    nickname: profile.value.nickname || "",
    gender: profile.value.gender || "",
    birthday: profile.value.birthday || "",
    bio: profile.value.bio || "",
  };
  editError.value = "";
  avatarError.value = "";
  avatarFile.value = null;
  avatarPreview.value = "";
  editing.value = true;
}

function cancelEdit() {
  editing.value = false;
  editError.value = "";
  avatarError.value = "";
  avatarFile.value = null;
  avatarPreview.value = "";
}

function triggerAvatarUpload() {
  fileInputRef.value?.click();
}

function onAvatarChange(e: Event) {
  const target = e.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;
  avatarError.value = "";
  const MAX_SIZE = 2 * 1024 * 1024;
  if (file.size > MAX_SIZE) {
    avatarError.value = "图片大小不能超过 2MB";
    target.value = "";
    return;
  }
  avatarFile.value = file;
  avatarPreview.value = URL.createObjectURL(file);
  target.value = "";
}

function currentAvatarSrc(): string {
  if (avatarPreview.value) return avatarPreview.value;
  if (profile.value?.avatarUuid)
    return `/api/file/avatar/${profile.value.avatarUuid}`;
  return mascot;
}

async function saveEdit() {
  const { $fetch: authFetch } = useNuxtApp();
  const _fetch = authFetch as typeof $fetch;
  editSaving.value = true;
  editError.value = "";
  avatarError.value = "";

  try {
    const res = await _fetch<{ code: number; msg: string }>(
      "/api/user/profile",
      {
        method: "PUT",
        body: editForm.value,
      },
    );
    if (res.code !== 200) {
      editError.value = res.msg || "保存失败";
      editSaving.value = false;
      return;
    }
  } catch (e: any) {
    editError.value = e?.data?.msg || e?.message || "保存失败";
    editSaving.value = false;
    return;
  }

  // 再上传头像（如有选择）
  if (avatarFile.value) {
    avatarUploading.value = true;
    try {
      const form = new FormData();
      form.append("file", avatarFile.value);
      const res = await _fetch<{
        code: number;
        msg: string;
        data: { fileUuid: string };
      }>("/api/file/avatar/upload", {
        method: "POST",
        body: form,
      });
      if (res.code !== 200) {
        avatarError.value = res.msg || "头像上传失败";
        editSaving.value = false;
        avatarUploading.value = false;
        return;
      }
    } catch (e: any) {
      const msg = e?.data?.msg || e?.message || "头像上传失败";
      avatarError.value = msg.includes("Maximum upload size")
        ? "图片大小超过服务器限制"
        : msg;
      editSaving.value = false;
      avatarUploading.value = false;
      return;
    }
    avatarUploading.value = false;
  }

  editing.value = false;
  editSaving.value = false;
  avatarFile.value = null;
  avatarPreview.value = "";
  await refreshProfile();
}

onMounted(async () => {
  if (isLoggedIn.value && !profile.value) {
    refreshProfile();
  }
  const { $fetch: _f } = useNuxtApp();
  const fetch = _f as typeof $fetch;
  try {
    const res = await fetch<{
      code: number;
      data: {
        usedStorage: number;
        maxTotalStorage: number;
        totalStorageUnlimited: boolean;
      };
    }>("/api/file/storage/summary");
    if (res.code === 200 && res.data) {
      storage.value = {
        used: res.data.usedStorage,
        max: res.data.maxTotalStorage,
        unlimited: res.data.totalStorageUnlimited,
      };
    }
  } catch {
    /* ignore */
  }
});
</script>

<template>
  <div class="profile-page">
    <div v-if="loading" class="profile-loading">加载中…</div>

    <div v-else-if="error" class="profile-error">
      <p>{{ error }}</p>
      <button class="button button-primary" @click="refreshProfile">
        重新加载
      </button>
    </div>

    <template v-else-if="profile">
      <section class="page-header profile-page-header fade-up">
        <div class="page-header-left">
          <div class="page-header-overline">My Profile</div>
          <h1 class="page-header-title">我的</h1>
        </div>
      </section>

      <section class="profile-hero panel-card fade-up">
        <div
          class="profile-avatar"
          :class="{ 'profile-avatar-editable': editing }"
          @click="editing && triggerAvatarUpload()"
        >
          <img
            :src="currentAvatarSrc()"
            :alt="profile.nickname"
            class="profile-avatar-img"
          />
          <div v-if="editing" class="profile-avatar-mask">
            {{ avatarPreview ? "换一张" : "更换头像" }}
          </div>
          <input
            ref="fileInputRef"
            type="file"
            accept="image/*"
            class="profile-avatar-input"
            @change="onAvatarChange"
          />
        </div>
        <div class="profile-hero-body">
          <h2 class="profile-nickname">{{ profile.nickname }}</h2>
          <p class="profile-bio">
            {{ profile.bio || "这个人很懒，什么都没写" }}
          </p>
          <div class="profile-meta">
            <span class="profile-meta-item">
              <Icon name="lucide:hash" size="14" />
              {{ currentUuid }}
            </span>
            <span class="profile-meta-item">
              <Icon name="lucide:users" size="14" />
              {{ genderLabel[profile.gender] || profile.gender || "保密" }}
            </span>
            <span class="profile-meta-item">
              <Icon name="lucide:calendar" size="14" />
              {{ formatBirthday(profile.birthday) }}
            </span>
            <span class="profile-meta-item">
              <Icon name="lucide:clock-3" size="14" />
              {{ formatDate(profile.updatedAt) || "暂无记录" }}
            </span>
          </div>
        </div>
      </section>

      <section class="stat-row fade-up">
        <div class="stat-card">
          <div class="stat-card-number">{{ profile.exp || 0 }}</div>
          <div class="stat-card-label">经验值</div>
        </div>
        <div class="stat-card">
          <div class="stat-card-number">{{ profile.coin || 0 }}</div>
          <div class="stat-card-label">硬币</div>
        </div>
        <div class="stat-card">
          <div class="stat-card-number">{{ formatSize(storage.used) }}</div>
          <div class="stat-card-label">已用空间</div>
        </div>
        <div class="stat-card">
          <div class="stat-card-number">
            {{ storage.unlimited ? "无限制" : formatSize(storage.max) }}
          </div>
          <div class="stat-card-label">总空间</div>
        </div>
      </section>

      <section class="panel-grid fade-up">
        <div class="panel-card">
          <div class="panel-card-header">账户资料</div>
          <!-- 查看模式 -->
          <div v-if="!editing" class="panel-card-body profile-info-grid">
            <div class="profile-info-item">
              <span class="profile-info-label">昵称</span>
              <strong>{{ profile.nickname || "未设置" }}</strong>
            </div>
            <div class="profile-info-item">
              <span class="profile-info-label">性别</span>
              <strong>{{
                genderLabel[profile.gender] || profile.gender || "保密"
              }}</strong>
            </div>
            <div class="profile-info-item">
              <span class="profile-info-label">生日</span>
              <strong>{{ formatBirthday(profile.birthday) }}</strong>
            </div>
            <div class="profile-info-item">
              <span class="profile-info-label">最后更新</span>
              <strong>{{ formatDate(profile.updatedAt) || "暂无记录" }}</strong>
            </div>
            <div class="profile-info-item profile-info-item-wide">
              <span class="profile-info-label">个人简介</span>
              <strong>{{ profile.bio || "这个人很懒，什么都没写" }}</strong>
            </div>
            <div
              class="profile-info-item profile-info-item-wide"
              style="padding-top: 8px"
            >
              <button class="button button-primary" @click="startEdit">
                编辑资料
              </button>
            </div>
          </div>
          <!-- 编辑模式 -->
          <div v-else class="panel-card-body profile-edit-body">
            <div v-if="editError" class="profile-edit-error">
              {{ editError }}
            </div>
            <div v-if="avatarError" class="profile-edit-error">
              {{ avatarError }}
            </div>
            <div class="profile-edit-fields">
              <label class="auth-field">
                <span>昵称</span>
                <input v-model="editForm.nickname" maxlength="24" />
              </label>
              <label class="auth-field">
                <span>性别</span>
                <SelectInput
                  v-model="editForm.gender"
                  :options="genderOptions"
                />
              </label>
              <label class="auth-field">
                <span>生日</span>
                <input v-model="editForm.birthday" type="date" />
              </label>
              <label class="auth-field profile-edit-field-wide">
                <span>个人简介</span>
                <textarea v-model="editForm.bio" rows="3" maxlength="200" />
              </label>
            </div>
            <div class="profile-edit-actions">
              <button class="button" @click="cancelEdit">取消</button>
              <button
                class="button button-primary"
                :disabled="editSaving"
                @click="saveEdit"
              >
                {{ editSaving ? "保存中…" : "保存" }}
              </button>
            </div>
          </div>
        </div>

        <div class="panel-card">
          <div class="panel-card-header">徽章展示</div>
          <div v-if="showedBadges.length" class="badges-grid">
            <div
              v-for="badge in showedBadges"
              :key="badge.code"
              class="badge-item"
            >
              <div class="badge-icon">
                <Icon :name="`lucide:${badge.icon || 'award'}`" size="20" />
              </div>
              <span class="badge-name">{{ badge.name }}</span>
              <span class="badge-code">{{ badge.code }}</span>
            </div>
          </div>
          <div v-else class="profile-empty-panel">当前没有展示中的徽章</div>
        </div>
      </section>
    </template>

    <div v-else class="profile-empty">
      <p>请先登录后查看</p>
      <NuxtLink to="/login" class="button button-primary">去登录</NuxtLink>
    </div>
  </div>
</template>

<style scoped>
.profile-avatar {
  position: relative;
  flex-shrink: 0;
  width: 96px;
  height: 96px;
}

.profile-avatar-editable {
  cursor: pointer;
}

.profile-avatar-editable:hover .profile-avatar-mask {
  opacity: 1;
}

.profile-avatar-input {
  display: none;
}

.profile-avatar-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-xl);
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.15s;
}

.profile-edit-body {
  display: grid;
  gap: 14px;
}

.profile-edit-error {
  padding: 8px 12px;
  border-radius: var(--radius-md);
  background: rgba(216, 57, 49, 0.06);
  color: #b31f1f;
  font-size: 13px;
}

.profile-edit-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.profile-edit-fields .auth-field {
  display: grid;
  gap: 4px;
}

.profile-edit-fields .auth-field span {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-font);
}

.profile-edit-fields input,
.profile-edit-fields textarea {
  width: 100%;
  height: 36px;
  padding: 0 10px;
  font-size: 13px;
  font-family: inherit;
  color: var(--color-font);
  background: var(--color-primary-background);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  outline: none;
  box-sizing: border-box;
}

.profile-edit-fields textarea {
  height: auto;
  min-height: 72px;
  padding: 8px 10px;
  resize: vertical;
}

.profile-edit-fields input:focus,
.profile-edit-fields textarea:focus {
  border-color: var(--color-emphasis);
}

.profile-edit-field-wide {
  grid-column: 1 / -1;
}

.profile-edit-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

@media (max-width: 640px) {
  .profile-edit-fields {
    grid-template-columns: 1fr;
  }
}
</style>
