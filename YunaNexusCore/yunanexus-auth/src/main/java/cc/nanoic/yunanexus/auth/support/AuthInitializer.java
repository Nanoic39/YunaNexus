package cc.nanoic.yunanexus.auth.support;

import cc.nanoic.yunanexus.auth.config.AuthProperties;
import cc.nanoic.yunanexus.auth.entity.AdminInitKey;
import cc.nanoic.yunanexus.auth.entity.OAuthClient;
import cc.nanoic.yunanexus.auth.entity.Roles;
import cc.nanoic.yunanexus.auth.entity.UserRoles;
import cc.nanoic.yunanexus.auth.mapper.AdminInitKeyMapper;
import cc.nanoic.yunanexus.auth.mapper.OAuthClientMapper;
import cc.nanoic.yunanexus.auth.mapper.RolesMapper;
import cc.nanoic.yunanexus.auth.mapper.UserRolesMapper;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Array;
import java.util.List;

@Component
public class AuthInitializer {
    // 日志
    private static final Logger logger = LoggerFactory.getLogger(AuthInitializer.class);

    @Resource
    private AuthProperties authProperties;

    @Resource
    private OAuthClientMapper oAuthClientMapper;

    @Resource
    private RolesMapper rolesMapper;

    @Resource
    private AdminInitKeyMapper adminInitKeyMapper;

    @Resource
    private UserRolesMapper userRolesMapper;

    @PostConstruct
    public void init() {
        initDefaultRoles();
        initBuiltinClients();
        initAdminKey();
    }

    // 初始化默认角色
    private void initDefaultRoles() {
        if (rolesMapper.selectCount(new LambdaQueryWrapper<>()) > 0) {
            logger.info("已存在默认角色（跳过）");
            return;
        }
        insertRole("SUPER_ADMIN", 99, "*:*:*:*"); // 所有权限
        insertRole("ADMIN", 60, "core:*:*:manage"); // 全局管理
        insertRole("USER", 1, "core:*:self:read", "core:*:self:edit");
        logger.info("默认角色初始化完成");
    }

    // 插入角色
    private void insertRole(String name, Integer level, String... permissions) {
        Roles role = new Roles();
        role.setName(name);
        role.setLevel(level);
        role.setPermissions(JSON.toJSONString(permissions)); // List转换为String格式
        role.setStatus(1);
        rolesMapper.insert(role);
    }

    // 初始化客户端
    private void initBuiltinClients() {
        if (authProperties.getBuiltinClientList() == null) {
            logger.info("没有需要创建的默认客户端（跳过）");
            return;
        }
        // 循环插入数据
        for (AuthProperties.BuiltinClient builtinClient : authProperties.getBuiltinClientList()) {
            // 查询数据是否存在
            OAuthClient oAuthClient = oAuthClientMapper.selectOne(
                    new LambdaQueryWrapper<OAuthClient>()
                            .eq(OAuthClient::getClientName, builtinClient.getClientName())
                            .last("LIMIT 1")
            );
            // 如果已经存在
            if (oAuthClient != null) {
                logger.info("OAuth客户端已存在：{}", builtinClient.getClientName());
                continue;
            }

            // 生成uuid
            String uuid = IdUtil.fastSimpleUUID();
            // 生成密钥
            String rawSecret = RandomUtil.randomString(32);
            String encodedSecret = BCrypt.hashpw(rawSecret);

            OAuthClient client = createInitOAuthClient(builtinClient, uuid, encodedSecret);

            oAuthClientMapper.insert(client);

            logger.info("OAuth客户端注册成功：{} | UUID={} | RAW_SECRET={}", builtinClient.getClientName(), uuid, rawSecret);
        }
    }

    private void initAdminKey() {
        Roles role = rolesMapper.selectOne(
                new LambdaQueryWrapper<Roles>()
                        .eq(Roles::getName, "SUPER_ADMIN")
                        .last("LIMIT 1")
        );
        if (role == null) {
            logger.info("角色表没有对应的超级管理员角色");
            return; // 角色表没有对应的超级管理员角色
        }
        Long count = userRolesMapper.selectCount(
                new LambdaQueryWrapper<UserRoles>()
                        .eq(UserRoles::getRoleId, role.getId())
                        .eq(UserRoles::getStatus, 1));
        if (count > 0) {
            logger.info("已经存在绑定过超级管理员的用户");
            return; // 已经存在绑定过超级管理员的用户
        }
        AdminInitKey unused = adminInitKeyMapper.selectOne(
                new LambdaQueryWrapper<AdminInitKey>()
                        .isNull(AdminInitKey::getUsedBy)
                        .last("LIMIT 1"));
        if (unused != null) {
            logger.info("管理员初始化密钥已存在: {}", unused.getInitKey());
            return; // 已经存在初始化密钥
        }

        // 初始化时生成密钥
        String initKey = RandomUtil.randomString(32);
        AdminInitKey key = new AdminInitKey();
        key.setInitKey(initKey);
        adminInitKeyMapper.insert(key);

        logger.info("管理员初始化密钥已生成: {}", initKey);
    }

    // 创建初始化客户端数据
    private static OAuthClient createInitOAuthClient(AuthProperties.BuiltinClient builtinClient, String uuid, String encodedSecret) {
        OAuthClient client = new OAuthClient();
        client.setUuid(uuid);
        client.setClientName(builtinClient.getClientName());
        client.setClientSecret(encodedSecret);
        client.setClientType(1); // 客户端类型，官方时=1
        client.setGrantTypes(builtinClient.getGrantTypes());
        client.setScope(builtinClient.getScope());
        client.setRedirectUri(builtinClient.getRedirectUri());
        client.setDescription("内置客户端无需申请描述"); //
        client.setAuditStatus(1); // 审核状态（0：待审核，1：通过，2：拒绝）
        client.setApplicantGlobalId(null); // 官方客户端无需申请人
        client.setAuditorGlobalId(null); // 官方客户端无需审核
        client.setAuditOpinion("内置客户端无需审核意见");
        client.setStatus(1); // 默认启用
        return client;
    }
}
