package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.file.domain.FileRange;
import cc.nanoic.yunanexus.file.mapper.PublicFileMapper;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.Base64;

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
        return new Visibility(fileId,row!=null,row==null?null:"/api/v1/file/public/assets/"+row.token());
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
