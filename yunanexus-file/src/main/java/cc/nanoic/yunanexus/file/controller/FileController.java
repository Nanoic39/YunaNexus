package cc.nanoic.yunanexus.file.controller;

import cc.nanoic.yunanexus.common.audit.annotation.AuditAction;
import cc.nanoic.yunanexus.common.core.model.PageResult;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.file.domain.ChunkInitRequest;
import cc.nanoic.yunanexus.file.domain.ChunkInitResult;
import cc.nanoic.yunanexus.file.domain.ChunkUploadStatus;
import cc.nanoic.yunanexus.file.domain.FileRange;
import cc.nanoic.yunanexus.file.domain.FileView;
import cc.nanoic.yunanexus.file.service.FileService;
import cc.nanoic.yunanexus.file.service.PublicFileService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 文件中心外部接口（roadmap 7.3 / 7.4）。<p>
 *
 * 经网关访问：网关注入 {@code X-User-Id}（全局用户 ID），列表查询依此叠加数据范围过滤。<br>
 * 下载支持 HTTP Range（{@code 206 Partial Content}）；大文件走分片上传（初始化 / 逐片 /
 * 进度 / 合并 / 放弃），初始化时可基于内容指纹秒传。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/file")
public class FileController {

    @jakarta.annotation.Resource
    private cc.nanoic.yunanexus.file.service.UploadRateLimiter rateLimiter;

    /** 网关下发的用户 ID 请求头（与 GatewayProperties.userIdHeader 一致） */
    private static final String USER_ID_HEADER = "X-User-Id";

    /** HTTP Range 前缀 */
    private static final String RANGE_PREFIX = "bytes=";

    @Resource
    private FileService fileService;

    @Resource
    private PublicFileService publicFileService;

    /**
     * 上传文件（单次上传，受 {@code yunanexus.file.upload.max-size} 限制）。
     *
     * @param publicAccess 是否同时发布为可匿名访问的公开文件
     * @return 文件元数据（含全局文件 ID）
     */
    @AuditAction(module = "file", action = "UPLOAD", risk = "HIGH")
    @PostMapping("/upload")
    public Result<FileView> upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "publicAccess", defaultValue = "false") boolean publicAccess,
                                   @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        long owner = requireUserId(userId);
        rateLimiter.acquire(owner, clientIp());
        FileView view = fileService.upload(file, owner);
        if (publicAccess) {
            var visibility = publicFileService.set(view.getFileId(), owner, true);
            view.setPublicAccess(true);
            view.setPublicUrl(visibility.publicUrl());
        }
        return Result.success(view);
    }

    /**
     * 下载文件内容，支持 HTTP Range 区间（断点续传 / 视频拖动）。
     *
     * @param range 形如 {@code bytes=0-1023} 或 {@code bytes=1024-}；缺省返回完整文件
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable("fileId") long fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        long[] parsed = parseRange(range);
        Long start = parsed == null ? null : parsed[0];
        Long end = (parsed == null || parsed[1] < 0) ? null : parsed[1];
        FileRange fileRange = fileService.download(fileId, requireUserId(userId), start, end);

        long contentLength = Math.max(fileRange.end() - fileRange.start() + 1, 0);
        ResponseEntity.BodyBuilder builder = fileRange.partial()
                ? ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                : ResponseEntity.ok();
        builder.header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileRange.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(parseContentType(fileRange.contentType()))
                .contentLength(contentLength);
        if (fileRange.partial()) {
            builder.header(HttpHeaders.CONTENT_RANGE,
                    "bytes " + fileRange.start() + "-" + fileRange.end() + "/" + fileRange.totalSize());
        }
        return builder.body(new InputStreamResource(fileRange.content()));
    }

    /**
     * 初始化分片上传：秒传命中直接返回元数据，否则返回上传会话 ID。
     */
    @PostMapping("/chunk/init")
    public Result<ChunkInitResult> chunkInit(@Valid @RequestBody ChunkInitRequest request,
                                             @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        long owner = requireUserId(userId);
        rateLimiter.acquire(owner, clientIp());
        return Result.success(fileService.initChunkUpload(request, owner));
    }

    /**
     * 上传单个分片（幂等覆盖，支持失败重传）。
     */
    @PostMapping("/chunk/upload")
    public Result<Void> chunkUpload(@RequestParam("uploadId") String uploadId,
                                    @RequestParam("index") int index,
                                    @RequestParam("chunk") MultipartFile chunk,
                                    @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        long owner = requireUserId(userId);
        rateLimiter.acquire(owner, clientIp());
        fileService.requireChunkOwner(uploadId, owner);
        fileService.uploadChunk(uploadId, index, chunk);
        return Result.success();
    }

    /**
     * 查询分片上传进度（断点续传）。
     */
    @GetMapping("/chunk/status")
    public Result<ChunkUploadStatus> chunkStatus(@RequestParam("uploadId") String uploadId,
                                                 @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        fileService.requireChunkOwner(uploadId, requireUserId(userId));
        return Result.success(fileService.chunkStatus(uploadId));
    }

    /**
     * 完成分片上传：合并分片 → 内容查重 → 元数据落库。
     */
    @PostMapping("/chunk/complete")
    public Result<FileView> chunkComplete(@RequestParam("uploadId") String uploadId,
                                          @RequestParam(value = "publicAccess", defaultValue = "false") boolean publicAccess,
                                          @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        long owner = requireUserId(userId);
        FileView view = fileService.completeChunkUpload(uploadId, owner);
        if (publicAccess) {
            var visibility = publicFileService.set(view.getFileId(), owner, true);
            view.setPublicAccess(true);
            view.setPublicUrl(visibility.publicUrl());
        }
        return Result.success(view);
    }

    /**
     * 放弃分片上传，清理临时分片。
     */
    @PostMapping("/chunk/abort")
    public Result<Void> chunkAbort(@RequestParam("uploadId") String uploadId,
                                   @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        fileService.requireChunkOwner(uploadId, requireUserId(userId));
        fileService.abortChunkUpload(uploadId);
        return Result.success();
    }

    /**
     * 删除文件（逻辑删除元数据 + 引用计数归零后物理删除内容）。
     */
    @AuditAction(module = "file", action = "DELETE", risk = "HIGH")
    @PostMapping("/delete/{fileId}")
    public Result<Void> delete(@PathVariable("fileId") long fileId,
                               @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        fileService.delete(fileId, requireUserId(userId));
        return Result.success();
    }

    /**
     * 分页查询当前用户在数据范围内可见的文件列表。
     *
     * @param page 页码（从 1 起，默认 1）
     * @param size 每页大小（默认 20，上限 100）
     */
    @GetMapping("/list")
    public Result<PageResult<FileView>> list(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        long owner = requireUserId(userId);
        var result = fileService.listVisible(owner, page, size);
        publicFileService.fill(result.getRecords(), owner);
        return Result.success(result);
    }

    /**
     * 解析 HTTP Range 请求头，返回 {@code [start, end]}；{@code end < 0} 表示到文件末尾。<p>
     * 不支持多区间（含逗号）与后缀区间（{@code bytes=-N}），此时返回 {@code null} 按整文件处理。
     */
    private long[] parseRange(String range) {
        if (range == null || range.isBlank()) {
            return null;
        }
        String value = range.trim();
        if (!value.startsWith(RANGE_PREFIX)) {
            return null;
        }
        String spec = value.substring(RANGE_PREFIX.length()).trim();
        if (spec.isEmpty() || spec.contains(",")) {
            return null;
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String startPart = spec.substring(0, dash).trim();
        String endPart = spec.substring(dash + 1).trim();
        if (startPart.isEmpty()) {
            return null;
        }
        try {
            long start = Long.parseLong(startPart);
            long end = endPart.isEmpty() ? -1 : Long.parseLong(endPart);
            return new long[]{start, end};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 扫描服务回写结果（内部接口）：clean 标记通过，infected 直接隔离。
     *
     * @param fileId 文件 ID
     * @param status clean / infected
     */
    @PostMapping("/internal/scan-result")
    public Result<Void> reportScanResult(@RequestParam("fileId") long fileId,
                                         @RequestParam("status") String status) {
        fileService.reportScanResult(fileId, status);
        return Result.success();
    }

    /**
     * 跨应用转移文件（仅官方前端）。move 改归属不占配额，copy 复制并占用配额。
     *
     * @param fileId      文件 ID
     * @param targetAppId 目标应用；空表示转回官方前端
     * @param copy        是否复制
     * @param userId      当前用户
     * @return 结果文件
     */
    @PostMapping("/transfer/{fileId}")
    public Result<cc.nanoic.yunanexus.file.domain.FileView> transfer(
            @PathVariable("fileId") long fileId,
            @RequestParam(value = "targetAppId", required = false) String targetAppId,
            @RequestParam(value = "copy", defaultValue = "false") boolean copy,
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        return Result.success(fileService.transferApp(fileId, requireUserId(userId), targetAppId, copy));
    }

    /** 取客户端 IP：优先代理头，取第一个非空段 */
    private String clientIp() {
        org.springframework.web.context.request.ServletRequestAttributes attrs =
                (org.springframework.web.context.request.ServletRequestAttributes)
                        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        jakarta.servlet.http.HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少有效的 " + USER_ID_HEADER + " 请求头");
        }
        return userId;
    }

    private MediaType parseContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
