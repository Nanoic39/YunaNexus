package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.entity.OAuthClients;
import cc.nanoic.yunanexus.auth.mapper.OAuthClientsMapper;
import cc.nanoic.yunanexus.auth.service.OAuthClientsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OAuthClientsServiceImpl implements OAuthClientsService {
    @Resource
    private OAuthClientsMapper oAuthClientsMapper;

    @Override
    public OAuthClients findByClientName(String clientName) {
        // 判空防止空查
        if(!StringUtils.hasText(clientName)) {
            return null;
        }
        return  oAuthClientsMapper.selectOne(
                new LambdaQueryWrapper<OAuthClients>()
                        .eq(OAuthClients::getClientName, clientName) // 根据名称查询
                        .last("LIMIT 1")
        );
    }

    @Override
    public OAuthClients findByUuid(String uuid) {
        if(!StringUtils.hasText(uuid)) {
            return null;
        }
        return oAuthClientsMapper.selectOne(
                new LambdaQueryWrapper<OAuthClients>()
                        .eq(OAuthClients::getUuid, uuid)
                        .last("LIMIT 1")
        );
    }
}
