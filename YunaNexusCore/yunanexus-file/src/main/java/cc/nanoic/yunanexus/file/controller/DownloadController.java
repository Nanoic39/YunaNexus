package cc.nanoic.yunanexus.file.controller;

import cc.nanoic.yunanexus.file.entity.FileObject;
import cc.nanoic.yunanexus.file.entity.UserFile;
import cc.nanoic.yunanexus.file.service.FileService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/file")
public class DownloadController {

    private final FileService fileService;

    public DownloadController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/download/{fileUuid}")
    public ResponseEntity<byte[]> download(@PathVariable String fileUuid) {
        UserFile userFile = fileService.getByFileUuid(fileUuid);
        if (userFile == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] data = fileService.download(fileUuid);
        FileObject fileObject = fileService.getObjectByFileUuid(fileUuid);
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
}