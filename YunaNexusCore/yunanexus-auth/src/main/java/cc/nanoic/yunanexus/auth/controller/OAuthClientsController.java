package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.entity.OAuthClients;
import cc.nanoic.yunanexus.auth.entity.VO.OAuthClientAuthInfoVO;
import cc.nanoic.yunanexus.auth.service.OAuthClientsService;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oauth-clients")
public class OAuthClientsController {

    @Resource
    private OAuthClientsService oAuthClientsService;

    @GetMapping("/name")
    public Result<OAuthClientAuthInfoVO> getOAuthClientByClientName(@RequestParam("clientName") String clientName) {
        if (!StringUtils.hasText(clientName)) {
            return Result.fail(R.PARAM_ERROR, "clientName不能为空");
        }

        // TODO: 如果不是拥有指定权限的管理员，那么需要校验对该Client的所有权

        OAuthClients client = oAuthClientsService.findByClientName(clientName);
        if (client == null) {
            return Result.fail(R.NOT_FOUND, "客户端不存在或不可用");
        }

        OAuthClientAuthInfoVO vo = new OAuthClientAuthInfoVO();
        BeanUtils.copyProperties(client, vo);

        return Result.success(vo);
    }
}
