package cc.nanoic.yunanexus.file.controller;

import cc.nanoic.yunanexus.common.web.auth.PermissionContext;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.file.entity.UserFile;
import cc.nanoic.yunanexus.file.service.FileService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/file")
public class UploadController {

    private final FileService fileService;

    public UploadController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false, defaultValue = "1") Integer fileCategory) {
        try {
            byte[] globalId = PermissionContext.getGlobalId();
            byte[] data = file.getBytes();
            UserFile userFile = fileService.upload(globalId, folderId, file.getOriginalFilename(),
                    file.getContentType(), data, fileCategory);
            Map<String, Object> result = new HashMap<>();
            result.put("fileUuid", userFile.getFileUuid());
            result.put("fileName", userFile.getFileName());
            result.put("fileSize", userFile.getFileSize());
            result.put("fileHash", userFile.getFileHash());
            return Result.success(result);
        } catch (Exception e) {
            return Result.fail(R.SERVER_ERROR, e.getMessage());
        }
    }
}