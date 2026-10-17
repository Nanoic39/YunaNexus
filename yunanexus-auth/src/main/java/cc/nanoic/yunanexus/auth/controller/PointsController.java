package cc.nanoic.yunanexus.auth.controller;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.service.PointsService;
import cc.nanoic.yunanexus.common.core.model.Result;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/auth/account/points")
public class PointsController {
 private final PointsService points;public PointsController(PointsService points){this.points=points;}
 @GetMapping public Result<?> account(@AuthenticationPrincipal AuthenticatedIdentity actor){return Result.success(points.account(actor));}
 @PostMapping("/check-in") public Result<?> checkIn(@AuthenticationPrincipal AuthenticatedIdentity actor){return Result.success(points.checkIn(actor));}
 @GetMapping("/ledger") public Result<?> ledger(@AuthenticationPrincipal AuthenticatedIdentity actor,@RequestParam(value="before",defaultValue="0") long before,@RequestParam(value="size",defaultValue="20") int size){return Result.success(points.ledger(actor,before,size));}
 @PostMapping("/transactions") public Result<?> change(@AuthenticationPrincipal AuthenticatedIdentity actor,@RequestBody PointsService.Change change){return Result.success(points.change(actor,change));}
}
