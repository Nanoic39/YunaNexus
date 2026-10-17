package cc.nanoic.yunanexus.auth.controller;
import cc.nanoic.yunanexus.auth.entity.ApplicationCategory;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.service.ApplicationTrustService;
import cc.nanoic.yunanexus.common.core.model.Result;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class ApplicationTrustController {
 private final ApplicationTrustService trust;
 public ApplicationTrustController(ApplicationTrustService trust){this.trust=trust;}
 public record Classification(ApplicationCategory category){}
 @PostMapping("/api/v1/auth/admin/clients/{clientCode}/classification")
 public Result<?> classify(@AuthenticationPrincipal AuthenticatedIdentity actor,@PathVariable("clientCode") String client,@RequestBody Classification body){
  trust.classify(actor,client,body.category());return Result.success();
 }
}
