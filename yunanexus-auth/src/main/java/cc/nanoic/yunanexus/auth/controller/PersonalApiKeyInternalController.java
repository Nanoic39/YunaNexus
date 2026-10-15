package cc.nanoic.yunanexus.auth.controller;
import cc.nanoic.yunanexus.auth.service.PersonalApiKeyService;
import cc.nanoic.yunanexus.common.core.model.Result;
import org.springframework.web.bind.annotation.*;
/** 仅容器私网调用；边缘与网关均禁止路由 /internal。 */
@RestController
public class PersonalApiKeyInternalController {
    private final PersonalApiKeyService keys;
    public PersonalApiKeyInternalController(PersonalApiKeyService keys){this.keys=keys;}
    public record Verify(String token,String method,String path){}
    @PostMapping("/internal/auth/api-key/verify")
    public Result<PersonalApiKeyService.Verified> verify(@RequestBody Verify body){return Result.success(keys.verify(body.token(),body.method(),body.path()));}
}
