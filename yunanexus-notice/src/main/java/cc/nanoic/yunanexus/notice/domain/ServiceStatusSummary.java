package cc.nanoic.yunanexus.notice.domain;

import java.util.List;

/** 平台状态及各服务的等权平均可用率。@author yunanexus */
public record ServiceStatusSummary(String status, double availability, List<ServiceStatusView> services) {
}
