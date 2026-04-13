package cc.nanoic.yunanexus.user.service.impl;

import cc.nanoic.yunanexus.user.entity.ServiceVersion;
import cc.nanoic.yunanexus.user.mapper.ServiceVersionMapper;
import cc.nanoic.yunanexus.user.service.PingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PingServiceImpl extends ServiceImpl<ServiceVersionMapper, ServiceVersion> implements PingService {

    @Value("${spring.application.name}")
    private String ServiceName;

    /**
     * 获取本服务最新版本信息
     * @return ServiceVersion
     */
    @Override
    public ServiceVersion getCurrentVersion() {
        LambdaQueryWrapper<ServiceVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ServiceVersion::getStatus, 1);
        return getOne(wrapper);
    }
}
