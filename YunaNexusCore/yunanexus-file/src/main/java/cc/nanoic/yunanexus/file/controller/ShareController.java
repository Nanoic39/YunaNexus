package cc.nanoic.yunanexus.file.controller;

import cc.nanoic.yunanexus.common.web.auth.PermissionContext;
import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.file.entity.FileObject;
import cc.nanoic.yunanexus.file.entity.FileShare;
import cc.nanoic.yunanexus.file.entity.FileShareTarget;
import cc.nanoic.yunanexus.file.entity.UserFile;
import cc.nanoic.yunanexus.file.service.FileService;
import cc.nanoic.yunanexus.file.service.ShareService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/file/share")
public class ShareController {

    private final ShareService shareService;
    private final FileService fileService;

    public ShareController(ShareService shareService, FileService fileService) {
        this.shareService = shareService;
        this.fileService = fileService;
    }

    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestParam List<String> targetUuids,
            @RequestParam List<Integer> targetTypes,
            @RequestParam(required = false) String extractCode,
            @RequestParam(required = false, defaultValue = "1") Integer needLogin,
            @RequestParam(required = false, defaultValue = "0") Integer allowPreview,
            @RequestParam(required = false, defaultValue = "0") Long maxViewCount,
            @RequestParam(required = false, defaultValue = "0") Long maxDownloadCount,
            @RequestParam(required = false) Long expireSeconds) {
        byte[] globalId = PermissionContext.getGlobalId();
        LocalDateTime expireAt = null;
        if (expireSeconds != null && expireSeconds > 0) {
            expireAt = LocalDateTime.now().plusSeconds(expireSeconds);
        }
        FileShare share = shareService.createShare(globalId, targetUuids, targetTypes,
                extractCode, needLogin, allowPreview, maxViewCount, maxDownloadCount, expireAt);
        Map<String, Object> result = new HashMap<>();
        result.put("shareCode", share.getShareCode());
        result.put("shareUuid", share.getShareUuid());
        result.put("expireAt", share.getExpireAt() != null ? share.getExpireAt().toString() : null);
        result.put("targetCount", targetUuids.size());
        return Result.success(result);
    }

    @GetMapping("/{shareCode}")
    public Result<Map<String, Object>> detail(@PathVariable String shareCode,
            @RequestParam(required = false) String extractCode) {
        FileShare share = shareService.verifyExtractCode(shareCode, extractCode);
        List<FileShareTarget> targets = shareService.getShareTargets(share.getId());
        List<Map<String, Object>> targetList = new ArrayList<>();
        for (FileShareTarget t : targets) {
            Map<String, Object> item = new HashMap<>();
            item.put("targetType", t.getTargetType());
            item.put("targetUuid", t.getTargetUuid());
            if (t.getTargetType() == 0) {
                UserFile f = fileService.getByFileUuid(t.getTargetUuid());
                item.put("name", f != null ? f.getFileName() : "");
                item.put("size", f != null ? f.getFileSize() : 0);
            } else {
                item.put("name", t.getTargetUuid());
                item.put("size", 0);
            }
            targetList.add(item);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("shareCode", share.getShareCode());
        result.put("needLogin", share.getNeedLogin());
        result.put("allowPreview", share.getAllowPreview());
        result.put("hasExtractCode", share.getExtractCode() != null && !share.getExtractCode().isEmpty());
        result.put("maxViewCount", share.getMaxViewCount());
        result.put("maxDownloadCount", share.getMaxDownloadCount());
        result.put("viewCount", share.getViewCount());
        result.put("downloadCount", share.getDownloadCount());
        result.put("expireAt", share.getExpireAt() != null ? share.getExpireAt().toString() : null);
        result.put("targets", targetList);
        return Result.success(result);
    }

    @GetMapping("/{shareCode}/download")
    public ResponseEntity<byte[]> download(@PathVariable String shareCode,
            @RequestParam(required = false) String extractCode,
            @RequestParam(required = false) String fileUuid) {
        FileShare share = shareService.verifyExtractCode(shareCode, extractCode);
        shareService.recordDownload(shareCode);
        String downloadUuid = fileUuid;
        if (downloadUuid == null) {
            List<FileShareTarget> targets = shareService.getShareTargets(share.getId());
            downloadUuid = targets.stream()
                    .filter(t -> t.getTargetType() == 0)
                    .findFirst()
                    .map(FileShareTarget::getTargetUuid)
                    .orElse(null);
        }
        if (downloadUuid == null) {
            return ResponseEntity.notFound().build();
        }
        UserFile userFile = fileService.getByFileUuid(downloadUuid);
        if (userFile == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] data = fileService.download(downloadUuid);
        FileObject fileObject = fileService.getObjectByFileUuid(downloadUuid);
        String mimeType = fileObject != null ? fileObject.getFileMime() : "application/octet-stream";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .contentLength(data.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(userFile.getFileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(data);
    }

    @GetMapping("/my")
    public Result<Map<String, Object>> myShares(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        byte[] globalId = PermissionContext.getGlobalId();
        var result = shareService.listMyShares(globalId, page, size);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("size", result.getSize());
        return Result.success(data);
    }

    @PostMapping("/cancel")
    public Result<?> cancel(@RequestParam String shareCode) {
        shareService.cancelShare(shareCode, PermissionContext.getGlobalId());
        return Result.success(null);
    }
}