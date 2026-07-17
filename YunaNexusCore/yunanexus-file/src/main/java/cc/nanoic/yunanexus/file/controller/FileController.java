package cc.nanoic.yunanexus.file.controller;

import cc.nanoic.yunanexus.common.web.auth.PermissionContext;
import cc.nanoic.yunanexus.common.web.auth.RequirePermission;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.file.entity.UserFile;
import cc.nanoic.yunanexus.file.entity.VO.UserFileVO;
import cc.nanoic.yunanexus.file.service.FileService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/file")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @RequirePermission
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String folderId,
            @RequestParam(required = false) String keyword) {
        byte[] globalId = PermissionContext.getGlobalId();
        Long folderIdLong = null;
        if (folderId != null && !folderId.isBlank()) {
            folderIdLong = Long.valueOf(folderId);
        }
        Page<UserFileVO> result = fileService.listFiles(globalId, folderIdLong, keyword, page, size);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("size", result.getSize());
        return Result.success(data);
    }

    @RequirePermission
    @GetMapping("/detail")
    public Result<UserFile> detail(@RequestParam String fileUuid) {
        UserFile userFile = fileService.getByFileUuid(fileUuid);
        if (userFile == null) {
            return Result.fail(R.NOT_FOUND, "文件不存在");
        }
        return Result.success(userFile);
    }

    @RequirePermission
    @PostMapping("/delete")
    public Result<?> delete(@RequestParam String fileUuid) {
        fileService.softDelete(fileUuid, PermissionContext.getGlobalId());
        return Result.success(null);
    }

    @RequirePermission
    @PostMapping("/rename")
    public Result<?> rename(@RequestParam String fileUuid, @RequestParam String newName) {
        fileService.rename(fileUuid, newName);
        return Result.success(null);
    }

    @RequirePermission
    @PostMapping("/move")
    public Result<?> move(@RequestParam String fileUuid, @RequestParam Long targetFolderId) {
        fileService.move(fileUuid, targetFolderId);
        return Result.success(null);
    }
}
