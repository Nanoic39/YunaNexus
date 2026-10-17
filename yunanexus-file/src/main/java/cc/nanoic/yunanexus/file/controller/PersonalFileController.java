package cc.nanoic.yunanexus.file.controller;

import cc.nanoic.yunanexus.common.core.model.*;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.file.domain.*;
import cc.nanoic.yunanexus.file.service.*;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/** 用户门户文件操作：所有读写均按本人身份约束。 */
@RestController
@RequestMapping("/api/v1/file/me")
public class PersonalFileController {
    @ModelAttribute
    public void noCache(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }

    @jakarta.annotation.Resource private PublicFileService publicFiles;

    private final PersonalFileService personal;
    private final FileService files;
    private final AvatarService avatars;

    public PersonalFileController(
            PersonalFileService personal, FileService files, AvatarService avatars) {
        this.personal = personal;
        this.files = files;
        this.avatars = avatars;
    }

    static long user(Long id) {
        if (id == null || id <= 0) throw new BizException(ErrorCode.UNAUTHORIZED);
        return id;
    }

    @GetMapping
    public Result<PageResult<FileView>> list(
            @RequestHeader(value = "X-User-Id", required = false) Long id,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "search", defaultValue = "") String search) {
        var result=personal.list(user(id),page,size,search.trim());
        for(var file:result.getRecords()) {
            var visibility=publicFiles.visibility(file.getFileId(),user(id));
            file.setPublicAccess(visibility.publicAccess());file.setPublicUrl(visibility.publicUrl());
        }
        return Result.success(result);
    }

    @PostMapping("/upload")
    public Result<FileView> upload(
            @RequestHeader(value = "X-User-Id", required = false) Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value="publicAccess",defaultValue="false") boolean publicAccess) {
        var view=files.upload(file,user(id));
        if(publicAccess){
            var visibility=publicFiles.set(view.getFileId(),user(id),true);
            view.setPublicAccess(true); view.setPublicUrl(visibility.publicUrl());
        }
        return Result.success(view);
    }

    @PostMapping("/avatar")
    public Result<FileView> avatar(
            @RequestHeader(value = "X-User-Id", required = false) Long id,
            @RequestParam("file") MultipartFile file) {
        return Result.success(avatars.upload(file, user(id)));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<InputStreamResource> download(
            @RequestHeader(value = "X-User-Id", required = false) Long id,
            @PathVariable("fileId") long fileId) {
        return attachment(personal.download(fileId, user(id)));
    }

    public record Rename(String fileName) {}

    @PostMapping("/{fileId}/rename")
    public Result<FileView> rename(
            @RequestHeader(value = "X-User-Id", required = false) Long id,
            @PathVariable("fileId") long fileId,
            @RequestBody Rename body) {
        return Result.success(personal.rename(fileId, user(id), body.fileName()));
    }

    @PostMapping("/{fileId}/delete")
    public Result<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false) Long id,
            @PathVariable("fileId") long fileId) {
        personal.delete(fileId, user(id));
        return Result.success();
    }

    static ResponseEntity<InputStreamResource> attachment(FileRange file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.totalSize())
                .body(new InputStreamResource(file.content()));
    }
}
