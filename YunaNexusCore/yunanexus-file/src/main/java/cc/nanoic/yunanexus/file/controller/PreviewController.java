package cc.nanoic.yunanexus.file.controller;

import cc.nanoic.yunanexus.file.service.PreviewService;
import cc.nanoic.yunanexus.file.service.PreviewService.PreviewResult;
import cc.nanoic.yunanexus.common.web.auth.RequirePermission;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/file/preview")
public class PreviewController {

    private final PreviewService previewService;

    public PreviewController(PreviewService previewService) {
        this.previewService = previewService;
    }

    @RequirePermission
    @GetMapping("/{fileUuid}")
    public ResponseEntity<byte[]> preview(@PathVariable String fileUuid) {
        PreviewResult result = previewService.getPreview(fileUuid);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.getContentType()))
                .contentLength(result.getData().length)
                .body(result.getData());
    }
}