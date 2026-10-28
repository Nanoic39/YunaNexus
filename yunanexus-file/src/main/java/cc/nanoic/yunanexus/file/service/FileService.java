package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.common.core.model.PageResult;
import cc.nanoic.yunanexus.file.domain.ChunkInitRequest;
import cc.nanoic.yunanexus.file.domain.ChunkInitResult;
import cc.nanoic.yunanexus.file.domain.ChunkUploadStatus;
import cc.nanoic.yunanexus.file.domain.FileRange;
import cc.nanoic.yunanexus.file.domain.FileView;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件中心服务（roadmap 7.3 / 7.4）。<p>
 *
 * 元数据按 {@code fileId} 分库；列表查询叠加数据范围（L4 行级权限试点）。
 *
 * @author yunanexus
 */
public interface FileService {

    /**
     * 上传文件：校验 → 领取全局文件 ID → 决策分片 → 内容落存储 → 元数据落库。
     *
     * @param file   上传文件
     * @param userId 上传者用户 ID（网关注入）
     * @return 文件元数据视图
     */
    FileView upload(MultipartFile file, long userId);

    /**
     * 下载文件内容（须在数据范围内可见），支持 HTTP Range 区间。
     *
     * @param fileId 全局文件 ID
     * @param userId 当前用户 ID
     * @param start  区间起始偏移；{@code null} 表示下载完整文件
     * @param end    区间结束偏移（含）；{@code null} 表示到文件末尾
     * @return 文件名 / MIME / 总大小 / 区间 / 内容流（调用方负责关闭）
     */
    FileRange download(long fileId, long userId, Long start, Long end);

    /**
     * 初始化分片上传：校验 → 秒传查重 → 生成上传会话（含预分配文件 ID）。
     *
     * @param request 初始化请求（文件名 / 分片数 / 总大小 / 可选 SHA-256）
     * @param userId  上传者用户 ID
     * @return 上传会话 ID 或秒传结果
     */
    ChunkInitResult initChunkUpload(ChunkInitRequest request, long userId);

    /**
     * 上传单个分片（幂等覆盖，支持失败重传）。
     *
     * @param uploadId 上传会话 ID
     * @param index    分片下标（从 0 开始）
     * @param chunk    分片内容
     */
    void uploadChunk(String uploadId, int index, MultipartFile chunk);

    /**
     * 查询分片上传进度（断点续传）。
     *
     * @param uploadId 上传会话 ID
     * @return 已上传分片下标
     */
    ChunkUploadStatus chunkStatus(String uploadId);

    /**
     * 完成分片上传：合并分片 → 内容查重 → 元数据落库。
     *
     * @param uploadId 上传会话 ID
     * @param userId   上传者用户 ID
     * @return 文件元数据视图
     */
    FileView completeChunkUpload(String uploadId, long userId);

    /**
     * 放弃分片上传，清理临时分片。
     *
     * @param uploadId 上传会话 ID
     */
    void abortChunkUpload(String uploadId);

    /**
     * 跨应用转移文件（仅官方前端可调用）。
     *
     * @param fileId      文件 ID
     * @param userId      发起人
     * @param targetAppId 目标应用；空表示转回官方前端
     * @param copy        true 复制（占用配额），false 移动（不复制存储）
     * @return 结果文件元数据
     */
    cc.nanoic.yunanexus.file.domain.FileView transferApp(long fileId, long userId,
                                                          String targetAppId, boolean copy);

    /**
     * 上传系统物料（头像等）：归平台所有、系统内公开、不计个人配额。
     *
     * @param file   文件
     * @param userId 上传人（仅作操作留痕，不作为归属）
     * @return 文件视图
     */
    cc.nanoic.yunanexus.file.domain.FileView uploadSystemAsset(
            org.springframework.web.multipart.MultipartFile file, long userId);

    /** 回写恶意文件扫描结果（A5） */
    void reportScanResult(long fileId, String status);

    /**
     * 清理过期的分片上传会话（临时分片 + 会话元数据），由定时任务周期调用。
     *
     * @return 实际清理的会话数量
     */
    int cleanupExpiredChunkUploads();

    /** 分片会话只允许创建者读写或放弃。 */
    void requireChunkOwner(String uploadId, long userId);

    /**
     * 删除文件（逻辑删除元数据 + 物理删除存储内容，须在数据范围内可见）。
     *
     * @param fileId 全局文件 ID
     * @param userId 当前用户 ID
     */
    void delete(long fileId, long userId);

    /** 严格本人删除，不依赖部门角色数据范围。 */
    void deleteOwned(long fileId,long userId);

    /**
     * 分页列出当前用户在数据范围内可见的文件（跨物理库扫描后内存分页）。
     *
     * @param userId 当前用户 ID
     * @param page   页码（从 1 起）
     * @param size   每页大小（上限 100）
     * @return 可见文件分页结果
     */
    PageResult<FileView> listVisible(long userId, int page, int size);
}
