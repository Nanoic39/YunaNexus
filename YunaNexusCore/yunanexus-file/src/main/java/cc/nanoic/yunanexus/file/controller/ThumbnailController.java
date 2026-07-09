package cc.nanoic.yunanexus.file.controller;

import cc.nanoic.yunanexus.file.service.ThumbnailService;
import cc.nanoic.yunanexus.common.web.auth.RequirePermission;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/file/thumbnail")
public class ThumbnailController {

    private final ThumbnailService thumbnailService;

    public ThumbnailController(ThumbnailService thumbnailService) {
        this.thumbnailService = thumbnailService;
    }

    @RequirePermission
    @GetMapping("/{fileUuid}")
    public ResponseEntity<byte[]> thumbnail(@PathVariable String fileUuid) {
        byte[] data = thumbnailService.getThumbnail(fileUuid);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .contentLength(data.length)
                .body(data);
    }
}