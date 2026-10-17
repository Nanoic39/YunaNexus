package cc.nanoic.yunanexus.file.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.file.service.PublicFileService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/file")
public class PublicFileController {
    @ModelAttribute
    public void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    private final PublicFileService files;
    public PublicFileController(PublicFileService files){this.files=files;}
    public record Change(boolean publicAccess) {}
    @GetMapping("/me/{fileId}/visibility")
    public Result<?> visibility(@PathVariable("fileId") long id,@RequestHeader(value="X-User-Id",required=false) Long owner){
        return Result.success(files.visibility(id,PersonalFileController.user(owner)));
    }
    @PostMapping("/me/{fileId}/visibility")
    public Result<?> change(@PathVariable("fileId") long id,@RequestHeader(value="X-User-Id",required=false) Long owner,@RequestBody Change body){
        return Result.success(files.set(id,PersonalFileController.user(owner),body.publicAccess()));
    }
    @GetMapping("/public/assets/{token}")
    public ResponseEntity<InputStreamResource> content(@PathVariable("token") String token) throws IOException {
        var file=files.open(token);
        var stream=new BufferedInputStream(file.content());
        String type;
        try { stream.mark(16); byte[] prefix=stream.readNBytes(12); stream.reset(); type=rasterType(prefix); }
        catch(IOException|RuntimeException e){stream.close();throw e;}
        boolean inline=type!=null;
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL,"no-store")
            .header("X-Content-Type-Options","nosniff")
            .header("Content-Security-Policy","sandbox; default-src 'none'")
            .header("Cross-Origin-Resource-Policy","cross-origin")
            .header(HttpHeaders.CONTENT_DISPOSITION,(inline?ContentDisposition.inline():ContentDisposition.attachment()).filename(file.fileName(),StandardCharsets.UTF_8).build().toString())
            .contentType(inline?MediaType.parseMediaType(type):MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(file.totalSize()).body(new InputStreamResource(stream));
    }
    // Never render uploaded HTML/SVG/scripts on the authenticated platform origin.
    static String rasterType(byte[] b){
        if(b.length>=8 && b[0]==(byte)137 && b[1]==80 && b[2]==78 && b[3]==71 && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10)return "image/png";
        if(b.length>=3 && b[0]==(byte)255 && b[1]==(byte)216 && b[2]==(byte)255)return "image/jpeg";
        if(b.length>=6 && (new String(b,0,6,StandardCharsets.US_ASCII).equals("GIF87a")||new String(b,0,6,StandardCharsets.US_ASCII).equals("GIF89a")))return "image/gif";
        if(b.length>=12 && new String(b,0,4,StandardCharsets.US_ASCII).equals("RIFF") && new String(b,8,4,StandardCharsets.US_ASCII).equals("WEBP"))return "image/webp";
        return null;
    }
}
