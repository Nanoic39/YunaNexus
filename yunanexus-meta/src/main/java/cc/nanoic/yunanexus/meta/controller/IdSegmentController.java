package cc.nanoic.yunanexus.meta.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.meta.service.IdSegmentService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 号段接口（API v1）。<p>
 *
 * 对外仅暴露全局唯一内部 ID 的获取接口。契约见 docs/api-contract.md 2.1。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/segment")
public class IdSegmentController {

    @Resource
    private IdSegmentService idSegmentService;

    @PostMapping("/next")
    public Result<Long> nextId(@RequestParam("bizTag") String bizTag) {
        if (bizTag == null || bizTag.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "bizTag 不能为空");
        }
        long id = idSegmentService.nextId(bizTag.trim());
        return Result.success(id);
    }
}
