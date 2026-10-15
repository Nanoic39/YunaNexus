package cc.nanoic.yunanexus.file.controller;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.file.service.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 分享能力与提取码只在 POST 请求体传递，禁止出现在查询参数和日志中。 */
@RestController
@RequestMapping("/api/v1/file")
public class FileShareController {
    @ModelAttribute
    public void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");response.setHeader("Referrer-Policy","no-referrer");}

    private final FileShareService shares;
    private final PersonalFileService files;
    public FileShareController(FileShareService shares,PersonalFileService files){this.shares=shares;this.files=files;}
    public record Create(long fileId,int days,String code){}
    public record Redeem(String shareId,String code){}
    @GetMapping("/me/shares")
    public Result<List<FileShareService.View>> list(@RequestHeader(value="X-User-Id",required=false) Long id){return Result.success(shares.list(PersonalFileController.user(id)));}
    @PostMapping("/me/shares")
    public Result<FileShareService.View> create(@RequestHeader(value="X-User-Id",required=false) Long id,@RequestBody Create body){
        return Result.success(shares.create(PersonalFileController.user(id),body.fileId(),body.days(),body.code()));
    }
    @PostMapping("/me/shares/{shareId}/revoke")
    public Result<Void> revoke(@RequestHeader(value="X-User-Id",required=false) Long id,@PathVariable("shareId") String shareId){shares.revoke(PersonalFileController.user(id),shareId);return Result.success();}
    public record Info(String fileName,Long size){}
    @PostMapping("/public/share/info")
    public Result<Info> info(@RequestBody Redeem body){var s=shares.redeem(body.shareId(),body.code());var f=files.requireOwned(s.getFileId(),s.getUserId());return Result.success(new Info(f.getFileName(),f.getSize()));}
    @PostMapping("/public/share/download")
    public ResponseEntity<InputStreamResource> download(@RequestBody Redeem body){var s=shares.redeem(body.shareId(),body.code());return PersonalFileController.attachment(files.download(s.getFileId(),s.getUserId()));}
}
