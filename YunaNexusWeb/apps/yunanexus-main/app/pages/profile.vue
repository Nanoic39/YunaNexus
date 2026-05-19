<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useAppToast } from "../composables/useAppToast";
import AppButton from "../components/form/AppButton.vue";
import AppFormField from "../components/form/AppFormField.vue";
import AppInput from "../components/form/AppInput.vue";
import AppInputMenu from "../components/form/AppInputMenu.vue";
import AppAvatarEditorDialog from "../components/feedback/AppAvatarEditorDialog.vue";

useHead({
  title: "个人资料",
});

const authApi = useAuthApi();
const route = useRoute();
const toast = useAppToast();

const genderItems = ["未知", "男", "女"];

const profileForm = reactive({
  nickname: "",
  gender: "未知",
  birthday: "",
});

const profileMessage = ref("");
const profileError = ref("");
const avatarError = ref("");
const avatarMessage = ref("");
const savingProfile = ref(false);
const uploadingAvatar = ref(false);
const avatarDialogOpen = ref(false);
const pageReady = ref(false);

const isAuthenticated = computed(() => !!authApi.accessToken.value);
const pageResolved = computed(() => pageReady.value && authApi.sessionReady.value);
const user = computed(() => authApi.currentUser.value);
const authGroups = computed(
  () => authApi.permissionSnapshot.value?.roles?.filter(Boolean) || [],
);
const avatarUrl = computed(() =>
  user.value?.avatarUuid ? `/api/file/avatar/${user.value.avatarUuid}` : "",
);
const avatarText = computed(() =>
  (profileForm.nickname || user.value?.uuid || "U").slice(0, 1).toUpperCase(),
);

const syncForm = () => {
  profileForm.nickname = user.value?.nickname || "";
  profileForm.gender = user.value?.gender || "未知";
  profileForm.birthday = user.value?.birthday || "";
};

watch(
  () => authApi.currentUser.value,
  () => {
    syncForm();
  },
  { immediate: true },
);

watch(
  [() => authApi.sessionReady.value, () => authApi.accessToken.value],
  async ([ready, token]) => {
    if (import.meta.client && ready && !token && route.path === "/profile") {
      await navigateTo("/login");
    }
  },
  { immediate: true },
);

onMounted(() => {
  pageReady.value = true;
});

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

const openAvatarPicker = () => {
  avatarError.value = "";
  avatarMessage.value = "";
  avatarDialogOpen.value = true;
};

const submitProfile = async () => {
  profileMessage.value = "";
  profileError.value = "";

  if (!profileForm.nickname.trim()) {
    profileError.value = "昵称不能为空";
    return;
  }

  savingProfile.value = true;

  try {
    const result = await authApi.updateProfile({
      nickname: profileForm.nickname.trim(),
      gender: profileForm.gender.trim() || "未知",
      birthday: profileForm.birthday || null,
    });

    if (result.code === 200) {
      const message = result.msg || "个人资料已更新";
      profileMessage.value = message;
      toast.success(message);
      syncForm();
      return;
    }

    profileError.value = result.tip || result.msg || "个人资料更新失败";
    toast.error(profileError.value);
  } catch (error) {
    profileError.value =
      error instanceof Error ? error.message : "个人资料更新失败";
    toast.error(profileError.value);
  } finally {
    savingProfile.value = false;
  }
};

const handleAvatarConfirm = async (file: File) => {
  avatarError.value = "";
  avatarMessage.value = "";
  uploadingAvatar.value = true;

  try {
    const result = await authApi.uploadAvatar(file);

    if (result.code === 200) {
      const message = result.msg || "头像已更新";
      avatarMessage.value = message;
      toast.success(message);
      avatarDialogOpen.value = false;
      return;
    }

    avatarError.value = result.tip || result.msg || "头像上传失败";
    toast.error(avatarError.value);
  } catch (error) {
    avatarError.value = error instanceof Error ? error.message : "头像上传失败";
    toast.error(avatarError.value);
  } finally {
    uploadingAvatar.value = false;
  }
};
</script>

<template>
  <section class="profile-page">
    <header class="profile-page-header">
      <div>
        <h1 class="profile-page-title">个人资料</h1>
        <p class="profile-page-subtitle">统一管理头像、基础资料与账户信息。</p>
      </div>
    </header>

    <div v-if="pageResolved" class="profile-page-grid">
      <article class="profile-card profile-overview">
        <div class="profile-avatar-wrap">
          <div class="profile-avatar">
            <img
              v-if="avatarUrl"
              :src="avatarUrl"
              alt="用户头像"
              class="profile-avatar-image"
            />
            <template v-else>{{ avatarText }}</template>
          </div>

          <AppButton
            variant="secondary"
            :loading="uploadingAvatar"
            @click="openAvatarPicker"
          >
            更换头像
          </AppButton>

          <p v-if="avatarMessage" class="profile-success">
            {{ avatarMessage }}
          </p>
          <p v-if="avatarError" class="profile-error">
            {{ avatarError }}
          </p>
          <p class="profile-helper">
            支持 `jpg / jpeg / png / webp`，默认最大 `5MB`。
          </p>
        </div>

        <div class="profile-meta-list">
          <div class="profile-meta-item">
            <span class="profile-meta-label">用户 UUID</span>
            <strong class="profile-meta-value">{{
              user?.uuid || "未获取到"
            }}</strong>
          </div>
          <div class="profile-meta-item">
            <span class="profile-meta-label">身份组</span>
            <div class="profile-tag-list">
              <span
                v-for="role in authGroups.length ? authGroups : ['暂无身份组']"
                :key="role"
                class="profile-tag"
              >
                {{ role }}
              </span>
            </div>
          </div>
          <div class="profile-meta-item">
            <span class="profile-meta-label">创建时间</span>
            <strong class="profile-meta-value">{{
              formatDateTime(user?.createTime)
            }}</strong>
          </div>
          <div class="profile-meta-item">
            <span class="profile-meta-label">最近更新</span>
            <strong class="profile-meta-value">{{
              formatDateTime(user?.updateTime)
            }}</strong>
          </div>
        </div>
      </article>

      <article class="profile-card profile-editor">
        <header class="profile-section-header">
          <h2>编辑资料</h2>
          <p>修改昵称、性别和生日，头像也统一在本页完成。</p>
        </header>

        <form class="profile-form" @submit.prevent="submitProfile">
          <AppFormField
            label="昵称"
            required
            :error="
              !profileForm.nickname.trim() && profileError ? profileError : ''
            "
          >
            <AppInput
              v-model="profileForm.nickname"
              :maxlength="30"
              placeholder="请输入昵称"
              autocomplete="nickname"
            />
          </AppFormField>

          <AppFormField label="性别" helper="可选择预设值，也可以自行输入。">
            <AppInputMenu
              v-model="profileForm.gender"
              :items="genderItems"
              :maxlength="10"
              placeholder="请选择或输入性别"
            />
          </AppFormField>

          <AppFormField label="生日" helper="留空表示暂不设置生日。">
            <AppInput
              v-model="profileForm.birthday"
              type="date"
              autocomplete="bday"
            />
          </AppFormField>

          <p v-if="profileMessage" class="profile-success">
            {{ profileMessage }}
          </p>
          <p
            v-if="profileError && profileForm.nickname.trim()"
            class="profile-error"
          >
            {{ profileError }}
          </p>

          <div class="profile-form-actions">
            <AppButton type="submit" :loading="savingProfile">
              保存资料
            </AppButton>
          </div>
        </form>
      </article>
    </div>
    <div v-else class="profile-page-skeleton" aria-hidden="true">
      <article class="profile-card profile-skeleton-card" />
      <article class="profile-card profile-skeleton-card" />
    </div>
    <AppAvatarEditorDialog
      :open="avatarDialogOpen"
      :uploading="uploadingAvatar"
      :error-message="avatarError"
      @close="avatarDialogOpen = false"
      @confirm="handleAvatarConfirm"
    />
  </section>
</template>

<style scoped lang="scss">
.profile-page {
  display: grid;
  gap: 20px;
}

.profile-page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.profile-page-title {
  margin: 0;
  color: var(--yn-color-text-primary);
  font-size: 24px;
  font-weight: 700;
}

.profile-page-subtitle {
  margin: 6px 0 0;
  color: var(--yn-color-text-secondary);
  font-size: 14px;
}

.profile-page-grid {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 20px;
}

.profile-card {
  border: 1px solid var(--yn-color-border-subtle);
  border-radius: var(--yn-radius-large);
  background: var(--yn-color-surface);
  box-shadow: var(--yn-shadow-card);
  padding: 20px;
}

.profile-overview {
  display: grid;
  gap: 20px;
  align-content: start;
}

.profile-avatar-wrap {
  display: grid;
  justify-items: start;
  gap: 12px;
}

.profile-avatar {
  display: inline-flex;
  width: 96px;
  height: 96px;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: calc(var(--yn-radius-large) + 4px);
  background: var(--yn-color-primary);
  color: #ffffff;
  font-size: 28px;
  font-weight: 700;
}

.profile-avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-meta-list {
  display: grid;
  gap: 16px;
}

.profile-meta-item {
  display: grid;
  gap: 6px;
}

.profile-meta-label {
  color: var(--yn-color-text-tertiary);
  font-size: 12px;
  font-weight: 600;
}

.profile-meta-value {
  color: var(--yn-color-text-primary);
  font-size: 14px;
  font-weight: 600;
  word-break: break-all;
}

.profile-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.profile-tag {
  border: 1px solid var(--yn-color-border-medium);
  border-radius: var(--yn-radius-small);
  background: var(--yn-color-surface-raised);
  padding: 4px 8px;
  color: var(--yn-color-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.profile-section-header h2 {
  margin: 0;
  color: var(--yn-color-text-primary);
  font-size: 18px;
}

.profile-section-header p {
  margin: 8px 0 0;
  color: var(--yn-color-text-secondary);
  font-size: 13px;
}

.profile-editor {
  display: grid;
  gap: 20px;
}

.profile-form {
  display: grid;
  gap: 16px;
}

.profile-form-actions {
  display: flex;
  justify-content: flex-start;
}

.profile-success,
.profile-error,
.profile-helper {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}

.profile-success {
  color: #15803d;
}

.profile-error {
  color: #b91c1c;
}

.profile-helper {
  color: var(--yn-color-text-tertiary);
}

.profile-page-skeleton {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 20px;
}

.profile-skeleton-card {
  min-height: 320px;
  background: color-mix(in srgb, var(--yn-color-surface-raised) 72%, var(--yn-color-surface));
}

@media (max-width: 960px) {
  .profile-page-grid,
  .profile-page-skeleton {
    grid-template-columns: 1fr;
  }
}
</style>
