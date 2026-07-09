package cc.nanoic.yunanexus.file.controller;

import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.common.web.auth.RequirePermission;
import cc.nanoic.yunanexus.file.entity.DTO.QuotaResult;
import cc.nanoic.yunanexus.file.service.QuotaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/file/storage")
public class StorageController {

    private final QuotaService quotaService;

    public StorageController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @RequirePermission
    @GetMapping("/summary")
    public Result<QuotaResult> summary() {
        return Result.success(quotaService.getQuota());
    }
}