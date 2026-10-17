package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.file.domain.FileRange;
import cc.nanoic.yunanexus.file.domain.FileView;
import cc.nanoic.yunanexus.file.mapper.PublicFileMapper;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

/** Explicit, revocable anonymous publication; private files are never inferred public. */
@Service
public class PublicFileService {
    private final PersonalFileService personal;
    private final PublicFileMapper publications;
    public record Visibility(long fileId, boolean publicAccess, String publicUrl) {}
    public PublicFileService(PersonalFileService personal, PublicFileMapper publications) {
        this.personal=personal; this.publications=publications;
    }
    public Visibility visibility(long fileId,long owner) {
        personal.requireOwned(fileId,owner);
        var row=personal.inShard(0,()->publications.find(fileId,owner));
        return new Visibility(fileId,row!=null,row==null?null:PersonalFileService.publicPath(row.token()));
    }

    /** 批量回填公开状态，避免列表逐条查询。 */
    public void fill(List<FileView> views, long owner) {
        if (views.isEmpty()) return;
        var tokens = new HashMap<Long, String>();
        var ids = views.stream().map(FileView::getFileId).toList();
        for (var row : personal.inShard(0, () -> publications.findMany(owner, ids)))
            tokens.put(row.fileId(), row.token());
        for (var view : views) {
            String token = tokens.get(view.getFileId());
            view.setPublicAccess(token != null);
            view.setPublicUrl(token == null ? null : PersonalFileService.publicPath(token));
        }
    }
    public Visibility set(long fileId,long owner,boolean enabled) {
        personal.requireOwned(fileId,owner);
        personal.inShard(0,()-> {
            if(enabled) {
                byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
                publications.publish(fileId,owner,Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            } else publications.revoke(fileId,owner);
            return null;
        });
        return visibility(fileId,owner);
    }
    public FileRange open(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw missing();
        var row=personal.inShard(0,()->publications.resolve(token));
        if(row==null) throw missing();
        // Recheck metadata on every request: deleting a published file immediately revokes access.
        return personal.download(row.fileId(),row.userId());
    }
    private static BizException missing(){return new BizException(ErrorCode.FILE_NOT_FOUND);}
}
