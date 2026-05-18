package cc.nanoic.yunanexus.file.controller;

import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.file.service.FileService;
import cc.nanoic.yunanexus.file.support.CurrentUserResolver;
import jakarta.annotation.Resource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/file")
public class FileController {
    @Resource
    private FileService fileService;

    @Resource
    private CurrentUserResolver currentUserResolver;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<?> upload(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "fileCategory", required = false) Integer fileCategory,
            @RequestParam(value = "publicStatus", required = false) Integer publicStatus,
            @RequestParam(value = "serviceName", required = false) String serviceName,
            @RequestParam(value = "oauthAppUuid", required = false) String oauthAppUuid
    ) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.upload(
                userId,
                file,
                folderId,
                fileCategory,
                publicStatus,
                serviceName,
                oauthAppUuid
        ));
    }

    @GetMapping("/list")
    public Result<?> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "folderId", required = false) Long folderId
    ) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.listUserFiles(userId, folderId));
    }

    @GetMapping("/folder/list")
    public Result<?> listFolders(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "parentId", required = false) Long parentId
    ) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.listUserFolders(userId, parentId));
    }

    @GetMapping("/recycle/list")
    public Result<?> listRecycle(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.listRecycleFiles(userId));
    }

    @GetMapping("/detail")
    public Result<?> detail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("fileUuid") String fileUuid
    ) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.getFileDetail(userId, fileUuid));
    }

    @PostMapping("/delete")
    public Result<?> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("fileUuid") String fileUuid
    ) {
        Long userId = currentUserResolver.requireUserId(authorization);
        fileService.moveToRecycle(userId, fileUuid);
        return Result.success("文件已移入回收站");
    }

    @PostMapping("/restore")
    public Result<?> restore(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("fileUuid") String fileUuid
    ) {
        Long userId = currentUserResolver.requireUserId(authorization);
        fileService.restore(userId, fileUuid);
        return Result.success("文件已恢复");
    }

    @PostMapping(value = "/avatar/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<?> uploadAvatar(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file
    ) {
        Long userId = currentUserResolver.requireUserId(authorization);
        return Result.success(fileService.uploadAvatar(userId, file));
    }

    @GetMapping("/public/{fileUuid}")
    public ResponseEntity<InputStreamResource> publicFile(@PathVariable String fileUuid) {
        var object = fileService.getPublicFile(fileUuid);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (object.contentType() != null && !object.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(object.contentType());
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(object.downloadName()).build().toString())
                .contentLength(object.contentLength())
                .body(new InputStreamResource(object.inputStream()));
    }

    @GetMapping("/avatar/{avatarUuid}")
    public ResponseEntity<InputStreamResource> avatarFile(@PathVariable String avatarUuid) {
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
}