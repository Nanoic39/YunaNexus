package cc.nanoic.yunanexus.file.controller;

import cc.nanoic.yunanexus.common.web.auth.PermissionContext;
import cc.nanoic.yunanexus.common.web.auth.RequirePermission;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.file.entity.UserFile;
import cc.nanoic.yunanexus.file.service.ChunkUploadService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/file/chunk")
public class ChunkUploadController {

    private final ChunkUploadService chunkUploadService;

    public ChunkUploadController(ChunkUploadService chunkUploadService) {
        this.chunkUploadService = chunkUploadService;
    }

    @RequirePermission
    @PostMapping("/init")
    public Result<Map<String, Object>> init(@RequestParam String fileName,
            @RequestParam long fileSize,
            @RequestParam(required = false) String fileExt,
            @RequestParam(required = false, defaultValue = "application/octet-stream") String fileMime,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false, defaultValue = "1") Integer fileCategory) {
        byte[] globalId = PermissionContext.getGlobalId();
        if (fileExt == null || fileExt.isEmpty()) {
            int dot = fileName.lastIndexOf('.');
            fileExt = dot > 0 ? fileName.substring(dot + 1).toLowerCase() : "";
        }
        return Result.success(chunkUploadService.init(globalId, folderId,
                fileName, fileSize, fileExt, fileMime, fileCategory));
    }

    @RequirePermission
    @PostMapping("/upload")
    public Result<?> upload(@RequestParam String uploadId,
            @RequestParam int chunkIndex,
            @RequestParam("file") MultipartFile file) {
        try {
            byte[] data = file.getBytes();
            chunkUploadService.uploadChunk(uploadId, chunkIndex, data);
            return Result.success(null);
        } catch (Exception e) {
            return Result.fail(R.SERVER_ERROR, e.getMessage());
        }
    }

    @RequirePermission
    @PostMapping("/complete")
    public Result<Map<String, Object>> complete(@RequestParam String uploadId) {
        UserFile userFile = chunkUploadService.complete(uploadId);
        Map<String, Object> result = new HashMap<>();
        result.put("fileUuid", userFile.getFileUuid());
        result.put("fileName", userFile.getFileName());
        result.put("fileSize", userFile.getFileSize());
        return Result.success(result);
    }

    @RequirePermission
    @PostMapping("/abort")
    public Result<?> abort(@RequestParam String uploadId) {
        chunkUploadService.abort(uploadId, PermissionContext.getGlobalId());
        return Result.success(null);
    }
}