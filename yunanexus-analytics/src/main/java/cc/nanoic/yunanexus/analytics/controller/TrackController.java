package cc.nanoic.yunanexus.analytics.controller;

import cc.nanoic.yunanexus.analytics.domain.TrackAccepted;
import cc.nanoic.yunanexus.analytics.domain.TrackEvent;
import cc.nanoic.yunanexus.analytics.service.TrackService;
import cc.nanoic.yunanexus.common.core.model.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 埋点事件上报接口（roadmap 10.1，api-contract 8.1）。<p>
 *
 * 接收前端 SDK 批量上报的事件数组，补全字段后异步投递；遵循「失败隔离」，
 * 即使 MQ 不可用也返回成功，客户端不因埋点失败阻塞主链路。按设计走独立域名、
 * 绕过网关，避免网关承载埋点解析压力。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/track")
public class TrackController {

    @Resource
    private TrackService trackService;

    /**
     * 批量上报埋点事件。
     */
    @PostMapping
    public Result<TrackAccepted> track(@Valid @RequestBody List<TrackEvent> events,
                                       HttpServletRequest request) {
        return Result.success(trackService.track(events, resolveClientIp(request)));
    }

    /**
     * 解析客户端真实 IP：优先取代理头，其次取远端地址。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
