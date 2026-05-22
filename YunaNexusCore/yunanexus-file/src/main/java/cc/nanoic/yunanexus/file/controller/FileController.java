package cc.nanoic.yunanexus.file.controller;

import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.file.service.FileChunkService;
import cc.nanoic.yunanexus.file.service.FileService;
import cc.nanoic.yunanexus.file.storage.StorageObjectResource;
import cc.nanoic.yunanexus.file.support.CurrentUserResolver;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/file")
public class FileController {
    @Resource
    private FileService fileService;

    @Resource
    private CurrentUserResolver currentUserResolver;

    @Resource
    private FileChunkService fileChunkService;

    /**
     * 文件分片初始化
     * 
     * @param authorization Token
     * @param body          文件主体
     * @return
     */
    @PostMapping("/chunk/init")
    public Result<?> initChunkUpload(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileChunkService.initChunkUpload(userId, body));
    }

    /**
     * 上传文件
     * 
     * @param authorization Token
     * @param file          文件
     * @param folderId      文件夹 ID
     * @param fileCategory  文件分类
     * @param publicStatus  公开状态
     * @param serviceName   服务名称
     * @param oauthAppUuid  OAuth 应用 UUID
     * @return 上传结果
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<?> upload(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "fileCategory", required = false) Integer fileCategory,
            @RequestParam(value = "publicStatus", required = false) Integer publicStatus,
            @RequestParam(value = "serviceName", required = false) String serviceName,
            @RequestParam(value = "oauthAppUuid", required = false) String oauthAppUuid) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.upload(
                userId,
                file,
                folderId,
                fileCategory,
                publicStatus,
                serviceName,
                oauthAppUuid));
    }

    /**
     * 分片上传
     *
     * @param authorization Token
     * @param uploadId      上传Id
     * @param chunkIndex    Chunk索引
     * @param file          具体文件内容（分片后）
     * @return 上传结果
     */
    @PostMapping(value = "/chunk/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<?> uploadChunk(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("file") MultipartFile file) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileChunkService.uploadChunk(userId, uploadId, chunkIndex, file));
    }

    /**
     * 获取用户文件列表
     * 
     * @param authorization Token
     * @param folderId      文件夹 ID
     * @return 文件列表
     */
    @GetMapping("/list")
    public Result<?> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "folderId", required = false) Long folderId) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.listUserFiles(userId, folderId));
    }

    /**
     * 上传完成后合并所有Chunk
     * 
     * @param authorization Token
     * @param body          uploadId
     * @return 合并结果
     */
    @PostMapping("/chunk/complete")
    public Result<?> completeChunkUpload(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileChunkService.completeChunkUpload(userId, String.valueOf(body.get("uploadId"))));
    }

    /**
     * 获取用户文件夹列表
     * 
     * @param authorization Token
     * @param parentId      文件夹 ID
     * @return 文件夹列表
     */
    @GetMapping("/folder/list")
    public Result<?> listFolders(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "parentId", required = false) Long parentId) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.listUserFolders(userId, parentId));
    }

    @PostMapping("/folder/create")
    public Result<?> createFolder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Long userId = currentUserResolver.requireUserId(authorization);
        Long parentId = body.get("parentId") == null ? null : Long.parseLong(String.valueOf(body.get("parentId")));
        return Result.success(fileService.createFolder(userId, parentId, String.valueOf(body.get("folderName"))));
    }

    @PostMapping("/folder/rename")
    public Result<?> renameFolder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.renameFolder(userId, String.valueOf(body.get("folderUuid")),
                String.valueOf(body.get("folderName"))));
    }

    @PostMapping("/folder/move")
    public Result<?> moveFolder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Long userId = currentUserResolver.requireUserId(authorization);
        Object targetParentUuid = body.get("targetParentUuid");
        return Result.success(fileService.moveFolder(userId, String.valueOf(body.get("folderUuid")),
                targetParentUuid == null ? null : String.valueOf(targetParentUuid)));
    }

    @PostMapping("/folder/delete")
    public Result<?> deleteFolder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Long userId = currentUserResolver.requireUserId(authorization);
        fileService.deleteFolder(userId, String.valueOf(body.get("folderUuid")));
        return Result.success("目录已移入回收站");
    }

    /**
     * 获取用户回收站文件列表
     * 
     * @param authorization Token
     * @return 回收站文件列表
     */
    @GetMapping("/recycle/list")
    public Result<?> listRecycle(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.listRecycleFiles(userId));
    }

    /**
     * 获取用户存储摘要
     *
     * @param authorization Token
     * @return 结果
     */
    @GetMapping("/storage/summary")
    public Result<?> storageSummary(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.getUserStorageSummary(userId));
    }

    /**
     * 获取文件详情
     * 
     * @param authorization Token
     * @param fileUuid      文件 UUID
     * @return 文件详情
     */
    @GetMapping("/detail")
    public Result<?> detail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("fileUuid") String fileUuid) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.getFileDetail(userId, fileUuid));
    }

    /**
     * 删除文件
     * 
     * @param authorization Token
     * @param body
     * @return 删除结果
     */
    @PostMapping("/delete")
    public Result<?> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Long userId = currentUserResolver.requireUserId(authorization);
        fileService.moveToRecycle(userId, String.valueOf(body.get("fileUuid")));
        return Result.success("文件已移入回收站");
    }

    @PostMapping("/rename")
    public Result<?> rename(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.renameFile(userId, String.valueOf(body.get("fileUuid")),
                String.valueOf(body.get("fileName"))));
    }

    @PostMapping("/move")
    public Result<?> move(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Long userId = currentUserResolver.requireUserId(authorization);
        Object targetFolderUuid = body.get("targetFolderUuid");
        return Result.success(fileService.moveFile(userId, String.valueOf(body.get("fileUuid")),
                targetFolderUuid == null ? null : String.valueOf(targetFolderUuid)));
    }

    @GetMapping("/share/list")
    public Result<?> listShares(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("fileUuid") String fileUuid) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.listUserFileShares(userId, fileUuid));
    }

    @PostMapping("/share/create")
    public Result<?> createShare(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.createUserFileShare(
                userId,
                String.valueOf(body.get("fileUuid")),
                body.get("extractCode") == null ? null : String.valueOf(body.get("extractCode")),
                parseDateTime(body.get("expireAt")),
                parseLong(body.get("maxDownloadCount")),
                parseInteger(body.get("viewAuthMode")),
                parseInteger(body.get("downloadAuthMode"))));
    }

    @PostMapping("/share/revoke")
    public Result<?> revokeShare(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Long userId = currentUserResolver.requireUserId(authorization);
        fileService.revokeUserFileShare(userId, String.valueOf(body.get("shareUuid")));
        return Result.success("分享已取消");
    }

    /**
     * 恢复文件
     * 
     * @param authorization Token
     * @param fileUuid      文件 UUID
     * @return 恢复结果
     */
    @PostMapping("/restore")
    public Result<?> restore(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Long userId = currentUserResolver.requireUserId(authorization);
        fileService.restore(userId, String.valueOf(body.get("fileUuid")));
        return Result.success("文件已恢复");
    }

    /**
     * 上传用户头像
     * 
     * @param authorization Token
     * @param file          文件
     * @return 头像 UUID
     */
    @PostMapping(value = "/avatar/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<?> uploadAvatar(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.uploadAvatar(userId, file));
    }

    /**
     * 下载文件
     * 
     * @param authorization Token
     * @param fileUuid      文件 UUID
     * @return 文件流
     */
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("fileUuid") String fileUuid) {
        Long userId = currentUserResolver.requireUserId(authorization);
        var object = fileService.downloadUserFile(userId, fileUuid);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (object.contentType() != null && !object.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(object.contentType());
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(object.downloadName()).build().toString())
                .contentLength(object.contentLength())
                .body(new InputStreamResource(object.inputStream()));
    }

    @GetMapping("/folder/download")
    public ResponseEntity<StreamingResponseBody> downloadFolder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("folderUuid") String folderUuid) {
        Long userId = currentUserResolver.requireUserId(authorization);
        StreamingResponseBody body = outputStream -> fileService.streamFolderAsZip(userId, folderUuid, outputStream);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("folder-" + folderUuid + ".zip").build().toString())
                .body(body);
    }

    /**
     * 分片下载
     *
     * @param authorization Token
     * @param fileUuid      文件 uuid
     * @param start         文件起始字节
     * @param end           文件结束字节
     * @return Chunk结果
     */
    @GetMapping("/download/chunk")
    public ResponseEntity<byte[]> downloadChunk(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("fileUuid") String fileUuid,
            @RequestParam("start") long start,
            @RequestParam("end") long end) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return fileChunkService.downloadChunk(userId, fileUuid, start, end);
    }

    /**
     * 下载公共文件
     * 
     * @param fileUuid 文件 UUID
     * @return 文件流
     */
    @GetMapping("/public/{fileUuid}")
    public ResponseEntity<InputStreamResource> publicFile(@PathVariable("fileUuid") String fileUuid) {
        var object = fileService.getPublicFile(fileUuid);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (object.contentType() != null && !object.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(object.contentType());
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(object.downloadName()).build().toString())
                .contentLength(object.contentLength())
                .body(new InputStreamResource(object.inputStream()));
    }

    @GetMapping("/share/info")
    public Result<?> shareInfo(@RequestParam("shareCode") String shareCode) {
        return Result.success(fileService.getUserFileShareInfo(shareCode));
    }

    @PostMapping("/share/access")
    public Result<?> shareAccess(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        return Result.success(fileService.accessUserFileShare(
                authorization,
                String.valueOf(body.get("shareCode")),
                body.get("extractCode") == null ? null : String.valueOf(body.get("extractCode"))));
    }

    @GetMapping("/share/preview")
    public ResponseEntity<?> sharePreview(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "extractCode", required = false) String extractCode,
            HttpServletRequest request) {
        var object = fileService.previewUserFileShare(authorization, shareCode, extractCode);
        return resolveRangeResponse(request, object);
    }

    @GetMapping("/share/download")
    public ResponseEntity<?> shareDownload(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "extractCode", required = false) String extractCode,
            HttpServletRequest request) {
        var object = fileService.downloadUserFileShare(authorization, shareCode, extractCode);
        return resolveRangeResponse(request, object);
    }

    private ResponseEntity<?> resolveRangeResponse(HttpServletRequest request, StorageObjectResource object) {
        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            return handleRangeRequest(rangeHeader, object);
        }
        return buildFullStreamResponse(object);
    }

    private ResponseEntity<InputStreamResource> buildFullStreamResponse(StorageObjectResource object) {
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (object.contentType() != null && !object.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(object.contentType());
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(object.downloadName()).build().toString())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentLength(object.contentLength())
                .body(new InputStreamResource(object.inputStream()));
    }

    private ResponseEntity<byte[]> handleRangeRequest(String rangeHeader, StorageObjectResource object) {
        long totalLength = object.contentLength();
        String rangeStr = rangeHeader.substring("bytes=".length());
        String[] parts = rangeStr.split("-");
        long start = Long.parseLong(parts[0]);
        long end = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : totalLength - 1;

        if (start >= totalLength) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + totalLength)
                    .build();
        }

        end = Math.min(end, totalLength - 1);
        int rangeLength = (int) (end - start + 1);

        try (InputStream is = object.inputStream()) {
            is.skipNBytes(start);
            byte[] buffer = new byte[rangeLength];
            int totalRead = 0;
            while (totalRead < rangeLength) {
                int bytesRead = is.read(buffer, totalRead, rangeLength - totalRead);
                if (bytesRead == -1) {
                    break;
                }
                totalRead += bytesRead;
            }
            byte[] result = totalRead == rangeLength ? buffer : Arrays.copyOf(buffer, totalRead);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.CONTENT_RANGE,
                            "bytes " + start + "-" + (start + totalRead - 1) + "/" + totalLength)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.inline().filename(object.downloadName()).build().toString())
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(totalRead)
                    .body(result);
        } catch (Exception e) {
            throw new RuntimeException("读取文件流失败", e);
        }
    }

    /**
     * 下载用户头像
     * 
     * @param avatarUuid 头像 UUID
     * @return 头像文件流
     */
    @GetMapping("/avatar/{avatarUuid}")
    public ResponseEntity<InputStreamResource> avatarFile(@PathVariable("avatarUuid") String avatarUuid) {
        var object = fileService.getAvatarPublicFile(avatarUuid);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (object.contentType() != null && !object.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(object.contentType());
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(object.contentLength())
                .body(new InputStreamResource(object.inputStream()));
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return LocalDateTime.parse(String.valueOf(value));
    }

    private Long parseLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Integer parseInteger(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
