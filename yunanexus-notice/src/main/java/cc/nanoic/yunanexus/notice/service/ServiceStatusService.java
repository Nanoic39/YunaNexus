package cc.nanoic.yunanexus.notice.service;

import cc.nanoic.yunanexus.notice.domain.ServiceStatusSummary;
import cc.nanoic.yunanexus.notice.domain.ServiceStatusView;

/** 门户服务运行状态查询。@author yunanexus */
public interface ServiceStatusService {

    ServiceStatusView getStatus(String name);

    ServiceStatusSummary getSummary();

    ServiceStatusSummary getSummary(int hours);

    ServiceStatusView getHistory(String name, int hours);
}
