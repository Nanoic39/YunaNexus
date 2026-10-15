package cc.nanoic.yunanexus.auth.controller;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.service.*;
import cc.nanoic.yunanexus.auth.mapper.*;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

/** 个人授权与密钥管理，身份取自认证中心自身验证后的会话。 */
@RestController
@RequestMapping("/api/v1/auth/account")
public class PersonalAccountController {
    private final AuthPlatformMapper grants;
    private final AuthClientMapper clients;
    private final SessionService sessions;
    private final PersonalApiKeyService keys;
    public PersonalAccountController(AuthPlatformMapper grants,AuthClientMapper clients,SessionService sessions,PersonalApiKeyService keys){this.grants=grants;this.clients=clients;this.sessions=sessions;this.keys=keys;}
    private long user(AuthenticatedIdentity identity){if(identity==null) throw new BizException(ErrorCode.UNAUTHORIZED);return identity.userId();}
    public record Grant(String clientCode,String clientName,String redirectUri,Integer status,LocalDateTime authorizedAt,boolean essential){}
    @GetMapping("/applications")
    public Result<List<Grant>> applications(@AuthenticationPrincipal AuthenticatedIdentity identity){
        long id=user(identity);
        return Result.success(grants.listOwned(id).stream().map(g->{var c=clients.selectByClientCode(g.getClientCode());return new Grant(g.getClientCode(),c==null?g.getClientCode():c.getClientName(),c==null?null:c.getRedirectUri(),g.getStatus(),g.getAuthorizedAt(),"web".equals(g.getClientCode()));}).toList());
    }
    @PostMapping("/applications/{clientCode}/revoke")
    public Result<Void> revoke(@AuthenticationPrincipal AuthenticatedIdentity identity,@PathVariable("clientCode") String clientCode){
        long id=user(identity);
        if("web".equals(clientCode)) throw new BizException(ErrorCode.BAD_REQUEST,"门户基础授权不可撤销，请使用退出所有设备管理门户会话");
        if(grants.updateStatus(id,clientCode,0)==0) throw new BizException(ErrorCode.BAD_REQUEST,"授权不存在");
        // 先提交授权撤销，阻止刷新/新会话，再逐个撤销现有客户端令牌。
        for(String sid:sessions.listSidsByUser(id)){var s=sessions.get(sid);if(s!=null && s.getUserId()==id && clientCode.equals(s.getClientCode())) sessions.remove(sid);}
        return Result.success();
    }
    public record CreateKey(String name,List<String> scopes,int days){}
    @GetMapping("/api-keys")
    public Result<List<PersonalApiKeyService.View>> keys(@AuthenticationPrincipal AuthenticatedIdentity identity){return Result.success(keys.list(user(identity)));}
    @PostMapping("/api-keys")
    public Result<PersonalApiKeyService.Created> create(@AuthenticationPrincipal AuthenticatedIdentity identity,@RequestBody CreateKey body){return Result.success(keys.create(user(identity),body.name(),body.scopes(),body.days()));}
    @PostMapping("/api-keys/{id}/revoke")
    public Result<Void> revokeKey(@AuthenticationPrincipal AuthenticatedIdentity identity,@PathVariable("id") String id){keys.revoke(user(identity),id);return Result.success();}
}
