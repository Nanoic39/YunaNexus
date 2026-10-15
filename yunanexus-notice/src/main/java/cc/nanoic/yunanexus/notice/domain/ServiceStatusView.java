package cc.nanoic.yunanexus.notice.domain;

import java.util.List;

/** 门户服务状态，key 与前端 svc-* 条目对应。@author yunanexus */
public record ServiceStatusView(String key, String name, String status,
                                double availability, List<String> history) {
}
