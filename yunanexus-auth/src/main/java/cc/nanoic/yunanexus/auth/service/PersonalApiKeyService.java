package cc.nanoic.yunanexus.auth.service;
import cc.nanoic.yunanexus.auth.entity.PersonalApiKey;
import cc.nanoic.yunanexus.auth.mapper.*;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/** 个人脚本密钥：一次展示明文，逐次验证状态、有效期和精确路由范围。 */
@Service
public class PersonalApiKeyService {
    private final PersonalApiKeyMapper mapper;
    private final AuthUserMapper users;
    @org.springframework.beans.factory.annotation.Autowired private AuthenticationService authentication;
    private final AuthPlatformMapper grants;
    private final AuthClientMapper clients;
    private static final Set<String> SCOPES=Set.of("profile:read","files:read","files:write");
    public PersonalApiKeyService(PersonalApiKeyMapper mapper,AuthUserMapper users,AuthPlatformMapper grants,AuthClientMapper clients){
        this.mapper=mapper;this.users=users;this.grants=grants;this.clients=clients;
    }
    public record View(String id,String name,String prefix,List<String> scopes,LocalDateTime createdAt,LocalDateTime expiresAt,LocalDateTime lastUsedAt,boolean revoked){}
    public record Created(View key,String token){}
    public record Verified(long userId,int shardIndex){}
    private View view(PersonalApiKey k){return new View(k.getId(),k.getName(),k.getTokenPrefix(),List.of(k.getScopes().split(",")),k.getCreatedAt(),k.getExpiresAt(),k.getLastUsedAt(),Integer.valueOf(1).equals(k.getRevoked()));}
    public List<View> list(long userId){return mapper.list(userId).stream().map(this::view).toList();}
    @Transactional
    public Created create(long userId,String name,List<String> scopes,int days){
        if(name==null || name.isBlank() || name.trim().length()>64 || days<1 || days>90 || scopes==null || scopes.isEmpty() || scopes.size()>3 || !SCOPES.containsAll(scopes))
            throw new BizException(ErrorCode.BAD_REQUEST,"名称、权限或有效期无效（1~90 天）");
        if(mapper.lockOwner(userId)==null) throw denied();
        var context=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null && context!=null && context.getPrincipal() instanceof cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity i)authentication.requireSession(i.sid());
        mapper.prune(userId);
        var history=mapper.list(userId);
        if(history.size()>=100 || history.stream().filter(k->Integer.valueOf(0).equals(k.getRevoked()) && k.getExpiresAt().isAfter(LocalDateTime.now())).count()>=20)
            throw new BizException(ErrorCode.BAD_REQUEST,"最多 20 个有效密钥、100 条近期记录");
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
        String token="ynk_"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        PersonalApiKey key=new PersonalApiKey();key.setId(UUID.randomUUID().toString().replace("-",""));key.setUserId(userId);
        key.setName(name.trim());key.setTokenHash(hash(token));key.setTokenPrefix(token.substring(0,12));key.setScopes(String.join(",",new TreeSet<>(scopes)));
        key.setCreatedAt(LocalDateTime.now());key.setExpiresAt(key.getCreatedAt().plusDays(days));key.setRevoked(0);
        mapper.insert(key);return new Created(view(key),token);
    }
    public void revoke(long userId,String id){if(mapper.revoke(id,userId)==0) throw denied();}
    public Verified verify(String token,String method,String path){
        if(token==null || !token.matches("ynk_[A-Za-z0-9_-]{43}")) throw denied();
        PersonalApiKey key=mapper.find(hash(token));
        if(key==null || !key.getExpiresAt().isAfter(LocalDateTime.now()) || !Integer.valueOf(0).equals(key.getRevoked()) || !allows(Set.of(key.getScopes().split(",")),method,path)) throw denied();
        var user=users.selectByUserId(key.getUserId());var grant=grants.selectByUserIdAndClient(key.getUserId(),"web");var client=clients.selectByClientCode("web");
        if(user==null || Integer.valueOf(1).equals(user.getPasswordResetRequired()) || !Integer.valueOf(1).equals(user.getStatus()) || (user.getLockedUntil()!=null && user.getLockedUntil().isAfter(LocalDateTime.now()))
           || grant==null || !Integer.valueOf(1).equals(grant.getStatus()) || client==null || !Integer.valueOf(1).equals(client.getStatus())) throw denied();
        mapper.used(key.getId());return new Verified(user.getUserId(),user.getShardIndex());
    }
    public static boolean allows(Set<String> scopes,String method,String path){
        if(path==null) return false;
        if("GET".equals(method)) {
            if("/api/v1/user/profile".equals(path)) return scopes.contains("profile:read");
            if("/api/v1/file/me".equals(path) || path.matches("/api/v1/file/me/[1-9][0-9]*/download")) return scopes.contains("files:read");
        }
        if("POST".equals(method) && ("/api/v1/file/me/upload".equals(path) || path.matches("/api/v1/file/me/[1-9][0-9]*/delete"))) return scopes.contains("files:write");
        return false;
    }
    static String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private BizException denied(){return new BizException(ErrorCode.UNAUTHORIZED,"密钥无效、已失效或无此接口权限");}
}
