package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.entity.OAuthClients;

public interface OAuthClientsService {
    /**
     * 按客户端名称查询
     */
    OAuthClients findByClientName(String clientName);

    /**
     * 按客户端UUID查询
     */
    OAuthClients findByUuid(String uuid);
}
