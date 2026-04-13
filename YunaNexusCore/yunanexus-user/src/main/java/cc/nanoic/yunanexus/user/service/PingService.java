package cc.nanoic.yunanexus.user.service;

import cc.nanoic.yunanexus.user.entity.ServiceVersion;
import com.baomidou.mybatisplus.extension.service.IService;

public interface PingService extends IService<ServiceVersion> {
    /**
     * 获取最新版本信息
     * @return ServiceVersion
     */
    ServiceVersion getCurrentVersion();
}
