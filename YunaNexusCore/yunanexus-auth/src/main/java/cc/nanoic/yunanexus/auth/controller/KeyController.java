package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.config.AuthProperties;
import cc.nanoic.yunanexus.auth.entity.VO.RsaPublicKeyVO;
import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import cn.hutool.core.io.FileUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

@RestController
@RequestMapping("/key")
public class KeyController {

    @Resource
    private AuthProperties authProperties;

    @GetMapping("/public")
    public Result<RsaPublicKeyVO> getRsaPublicKey() {
        String keyPath = authProperties.getRsa().getKeyPath();
        File publicFile = new File(keyPath, "id_rsa.pub");
        if (!publicFile.exists()) {
            throw new BusinessException(R.SERVER_ERROR, "RSA密钥未成功初始化");
        }
        RsaPublicKeyVO rsaPublicKeyVO = new RsaPublicKeyVO();
        rsaPublicKeyVO.setPublicKey(FileUtil.readUtf8String(publicFile));
        return Result.success(rsaPublicKeyVO);
    }
}
