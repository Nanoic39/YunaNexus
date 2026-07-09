package cc.nanoic.yunanexus.file.controller;

import cc.nanoic.yunanexus.common.web.auth.PermissionContext;
import cc.nanoic.yunanexus.common.web.auth.RequirePermission;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.file.entity.FileObject;
import cc.nanoic.yunanexus.file.entity.UserFile;
import cc.nanoic.yunanexus.file.service.FileService;
import cc.nanoic.yunanexus.file.service.UserRemoteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/file/avatar")
public class AvatarController {

    private final FileService fileService;
    private final UserRemoteService userRemoteService;

    public AvatarController(FileService fileService, UserRemoteService userRemoteService) {
        this.fileService = fileService;
        this.userRemoteService = userRemoteService;
    }

    @RequirePermission
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        try {
            byte[] globalId = PermissionContext.getGlobalId();
            byte[] data = file.getBytes();
            UserFile userFile = fileService.upload(globalId, null, file.getOriginalFilename(), file.getContentType(),
                    data, 3);
            userRemoteService.updateAvatar(globalId, userFile.getFileUuid());
            Map<String, Object> result = new HashMap<>();
            result.put("fileUuid", userFile.getFileUuid());
            result.put("fileSize", userFile.getFileSize());
            return Result.success(result);
        } catch (Exception e) {
            return Result.fail(R.SERVER_ERROR, e.getMessage());
        }
    }

    @RequirePermission
    @GetMapping("/{fileUuid}")
    public byte[] get(@PathVariable String fileUuid) {
        FileObject fileObject = fileService.getObjectByFileUuid(fileUuid);
        if (fileObject == null) {
            throw new RuntimeException("头像不存在");
        }
        return fileService.download(fileUuid);
    }
}