import { useAuthApi } from "./useAuthApi";

type ResultEnvelope<T> = {
  code: number;
  msg: string;
  tip: string | null;
  timestamp: number;
  data: T;
};

export type UserManagedFileItem = {
  fileUuid: string;
  fileName: string;
  originName?: string | null;
  fileSize: number;
  fileExt?: string | null;
  fileMime?: string | null;
  fileCategory?: number | null;
  publicStatus?: number | null;
  serviceName?: string | null;
  folderId?: number | null;
  createTime?: string | null;
  deletedAt?: string | null;
  recycleExpireAt?: string | null;
};

export type FileUploadProgress = {
  phase: "preparing" | "uploading" | "merging" | "done";
  loadedBytes: number;
  totalBytes: number;
  percent: number;
  chunkIndex?: number;
  totalChunks?: number;
  text: string;
};

const resolveFetchErrorMessage = (error: unknown, fallback: string) => {
  if (!error || typeof error !== "object") {
    return fallback;
  }

  const maybeError = error as {
    data?: {
      message?: string;
      msg?: string;
      tip?: string;
      error?: string;
    };
    message?: string;
  };

  return (
    maybeError.data?.tip ||
    maybeError.data?.msg ||
    maybeError.data?.message ||
    maybeError.data?.error ||
    maybeError.message ||
    fallback
  );
};

const parseDownloadFileName = (
  contentDisposition: string | null,
  fallback: string,
) => {
  if (!contentDisposition) {
    return fallback;
  }

  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1]);
  }

  const plainMatch = contentDisposition.match(/filename="?([^"]+)"?/i);
  if (plainMatch?.[1]) {
    return plainMatch[1];
  }

  return fallback;
};

const DIRECT_UPLOAD_THRESHOLD_BYTES = 500 * 1024 * 1024;
const CHUNK_SIZE_BYTES = 16 * 1024 * 1024;

const createProgressPayload = (
  phase: FileUploadProgress["phase"],
  loadedBytes: number,
  totalBytes: number,
  chunkIndex?: number,
  totalChunks?: number,
): FileUploadProgress => {
  const safeTotal = totalBytes > 0 ? totalBytes : 1;
  const percent = Math.max(0, Math.min(100, Math.round((loadedBytes / safeTotal) * 100)));
  const text =
    phase === "merging"
      ? "正在合并文件…"
      : phase === "done"
        ? "上传完成"
        : totalChunks && totalChunks > 1
          ? `上传中 ${chunkIndex ?? 0}/${totalChunks} · ${percent}%`
          : `上传中 ${percent}%`;
  return { phase, loadedBytes, totalBytes, percent, chunkIndex, totalChunks, text };
};

export const useFileApi = () => {
  const authApi = useAuthApi();

  const getAuthorizationHeader = () => {
    if (!authApi.accessToken.value) {
      throw new Error("请先登录");
    }
    return `${authApi.tokenType.value || "Bearer"} ${authApi.accessToken.value}`;
  };

  const requestResult = async <T>(
    url: string,
    options: Record<string, unknown> = {},
  ) => {
    try {
      return await $fetch<ResultEnvelope<T>>(url, {
        ...options,
        headers: {
          Authorization: getAuthorizationHeader(),
          ...((options.headers as Record<string, string> | undefined) ?? {}),
        },
      });
    } catch (error) {
      throw new Error(resolveFetchErrorMessage(error, "请求失败，请稍后重试"));
    }
  };

  const listFiles = async (folderId?: number | null) => {
    return await requestResult<UserManagedFileItem[]>("/api/file/list", {
      method: "GET",
      query: folderId == null ? undefined : { folderId },
    });
  };

  const listRecycleFiles = async () => {
    return await requestResult<UserManagedFileItem[]>(
      "/api/file/recycle/list",
      {
        method: "GET",
      },
    );
  };

  const uploadByXhr = async (
    url: string,
    formData: FormData,
    totalBytes: number,
    onProgress?: (progress: FileUploadProgress) => void,
  ) => {
    if (!import.meta.client) {
      throw new Error("当前环境不支持上传");
    }

    return await new Promise<ResultEnvelope<Record<string, unknown>>>((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open("POST", url, true);
      xhr.setRequestHeader("Authorization", getAuthorizationHeader());
      xhr.upload.onprogress = (event) => {
        if (!event.lengthComputable) {
          return;
        }
        onProgress?.(createProgressPayload("uploading", event.loaded, totalBytes));
      };
      xhr.onload = () => {
        try {
          const response = JSON.parse(xhr.responseText || "null") as ResultEnvelope<Record<string, unknown>>;
          if (xhr.status >= 200 && xhr.status < 300) {
            resolve(response);
            return;
          }
          reject(new Error(response?.tip || response?.msg || "文件上传失败"));
        } catch {
          reject(new Error(xhr.responseText || "文件上传失败"));
        }
      };
      xhr.onerror = () => reject(new Error("文件上传失败"));
      xhr.send(formData);
    });
  };

  const uploadFile = async (
    file: File,
    folderId?: number | null,
    onProgress?: (progress: FileUploadProgress) => void,
  ) => {
    onProgress?.(createProgressPayload("preparing", 0, file.size));

    if (file.size <= DIRECT_UPLOAD_THRESHOLD_BYTES) {
      const formData = new FormData();
      formData.append("file", file);
      if (folderId != null) {
        formData.append("folderId", String(folderId));
      }
      const result = await uploadByXhr("/api/file/upload", formData, file.size, onProgress);
      onProgress?.(createProgressPayload("done", file.size, file.size));
      return result;
    }

    const initResult = await requestResult<Record<string, unknown>>("/api/file/chunk/init", {
      method: "POST",
      body: {
        fileName: file.name,
        fileSize: file.size,
        contentType: file.type || "application/octet-stream",
        folderId: folderId ?? null,
      },
    });
    const uploadId = String(initResult.data?.uploadId || "");
    const totalChunks = Number(initResult.data?.totalChunks || 0);
    if (!uploadId || !totalChunks) {
      throw new Error("分片上传初始化失败");
    }

    let uploadedBytes = 0;
    for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex += 1) {
      const start = chunkIndex * CHUNK_SIZE_BYTES;
      const end = Math.min(file.size, start + CHUNK_SIZE_BYTES);
      const chunkSize = end - start;
      const formData = new FormData();
      formData.append("uploadId", uploadId);
      formData.append("chunkIndex", String(chunkIndex));
      formData.append("file", file.slice(start, end), `${file.name}.part${chunkIndex}`);
      await uploadByXhr("/api/file/chunk/upload", formData, file.size, (progress) => {
        onProgress?.(createProgressPayload(
          "uploading",
          uploadedBytes + Math.min(progress.loadedBytes, chunkSize),
          file.size,
          chunkIndex + 1,
          totalChunks,
        ));
      });
      uploadedBytes += chunkSize;
    }

    onProgress?.(createProgressPayload("merging", file.size, file.size, totalChunks, totalChunks));
    const result = await requestResult<Record<string, unknown>>("/api/file/chunk/complete", {
      method: "POST",
      body: { uploadId },
    });
    onProgress?.(createProgressPayload("done", file.size, file.size, totalChunks, totalChunks));
    return result;
  };

  const deleteFile = async (fileUuid: string) => {
    return await requestResult<null>("/api/file/delete", {
      method: "POST",
      body: { fileUuid },
    });
  };

  const restoreFile = async (fileUuid: string) => {
    return await requestResult<null>("/api/file/restore", {
      method: "POST",
      body: { fileUuid },
    });
  };

  const downloadFile = async (
    item: UserManagedFileItem,
    onProgress?: (progress: string) => void,
  ) => {
    if (!import.meta.client) {
      return;
    }
    const fallbackName = item.originName || item.fileName || "download";
    if (item.fileSize > DIRECT_UPLOAD_THRESHOLD_BYTES) {
      const totalChunks = Math.ceil(item.fileSize / CHUNK_SIZE_BYTES);
      const chunks: Blob[] = [];
      for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex += 1) {
        const start = chunkIndex * CHUNK_SIZE_BYTES;
        const end = Math.min(item.fileSize - 1, start + CHUNK_SIZE_BYTES - 1);
        onProgress?.(`下载中 ${chunkIndex + 1}/${totalChunks}`);
        const response = await fetch(`/api/file/download/chunk?fileUuid=${encodeURIComponent(item.fileUuid)}&start=${start}&end=${end}`, {
          method: "GET",
          headers: { Authorization: getAuthorizationHeader() },
        });
        if (!response.ok) {
          const rawText = await response.text();
          throw new Error(rawText || "文件下载失败");
        }
        chunks.push(await response.blob());
      }
      const blob = new Blob(chunks);
      const objectUrl = window.URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = objectUrl;
      anchor.download = fallbackName;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.URL.revokeObjectURL(objectUrl);
      return;
    }

    const response = await fetch(`/api/file/download/${encodeURIComponent(item.fileUuid)}`, {
      method: "GET",
      headers: { Authorization: getAuthorizationHeader() },
    });

    if (!response.ok) {
      const rawText = await response.text();
      try {
        const data = JSON.parse(rawText) as { tip?: string; msg?: string };
        throw new Error(data.tip || data.msg || "文件下载失败");
      } catch {
        throw new Error(rawText || "文件下载失败");
      }
    }

    const blob = await response.blob();
    const fileName = parseDownloadFileName(
      response.headers.get("content-disposition"),
      fallbackName,
    );

    const objectUrl = window.URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = objectUrl;
    anchor.download = fileName;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    window.URL.revokeObjectURL(objectUrl);
  };

  return {
    listFiles,
    listRecycleFiles,
    uploadFile,
    deleteFile,
    restoreFile,
    downloadFile,
  };
};
