package cc.nanoic.yunanexus.auth.support;

import cc.nanoic.yunanexus.auth.config.AuthProperties;
import cc.nanoic.yunanexus.auth.entity.AdminInitKey;
import cc.nanoic.yunanexus.auth.entity.OAuthClient;
import cc.nanoic.yunanexus.auth.entity.ResourceEntity;
import cc.nanoic.yunanexus.auth.entity.Roles;
import cc.nanoic.yunanexus.auth.entity.UserRoles;
import cc.nanoic.yunanexus.auth.mapper.AdminInitKeyMapper;
import cc.nanoic.yunanexus.auth.mapper.OAuthClientMapper;
import cc.nanoic.yunanexus.auth.mapper.ResourceMapper;
import cc.nanoic.yunanexus.auth.mapper.RolesMapper;
import cc.nanoic.yunanexus.auth.mapper.UserRolesMapper;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.asymmetric.RSA;
import cn.hutool.crypto.digest.BCrypt;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;

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

    @Resource
    private ResourceMapper resourceMapper;

    @PostConstruct
    public void init() {
        initDefaultRoles();
        initDefaultResources();
        initBuiltinClients();
        initAdminKey();
        initRsaKeyPair();
        initAesKey();
    }

    // 初始化默认角色（幂等：已有角色跳过，只添加缺失的）
    private void initDefaultRoles() {
        upsertRole("SUPER_ADMIN", 99, "*:*:*:*"); // 所有权限
        upsertRole("ADMIN", 60, "core:*:*:manage"); // 全局管理
        upsertRole("MODERATOR", 30, "admin:users:read", "admin:users:write", "admin:apps:read", "admin:apps:write",
                "core:admin:moderate:*");
        upsertRole("USER", 1, "core:*:self:read", "core:*:self:edit");
        upsertRole("VIP", 10, "core:file:vip:upload", "core:file:vip:download", "core:identity:vip:badge");
        logger.info("默认角色初始化完成");
    }

    // 幂等插入角色：按 name 检测，已存在则跳过
    private void upsertRole(String name, Integer level, String... permissions) {
        Roles existing = rolesMapper.selectOne(
                new LambdaQueryWrapper<Roles>()
                        .eq(Roles::getName, name)
                        .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        Roles role = new Roles();
        role.setName(name);
        role.setLevel(level);
        role.setPermissions(JSON.toJSONString(permissions));
        role.setStatus(1);
        rolesMapper.insert(role);
    }

    private void initDefaultResources() {
        int sort = 0;
        // 仪表盘 (目录)
        long dashboardId = upsertResource(null, "仪表盘", "core:dashboard", 0, "dashboard", "/", null, sort += 10);
        // 文件管理 (目录)
        long filesId = upsertResource(null, "文件管理", "core:file", 0, "folder", "/files", null, sort += 10);
        // 应用管理 (目录)
        long appsId = upsertResource(null, "应用管理", "core:apps", 0, "box", null, null, sort += 10);
        upsertResource(appsId, "我的应用", "core:apps:list", 1, "layout", "/apps", null, sort += 10);
        upsertResource(appsId, "创建应用", "core:apps:create", 1, "plus", "/apps/apply", null, sort += 10);

        // 管理中心 (目录 - 仅管理员可见)
        long adminId = upsertResource(null, "管理中心", "core:admin", 0, "shield", null, null, sort += 10);
        upsertResource(adminId, "应用审核", "core:oauth:audit", 1, "clipboard-check", "/admin/apps", null, sort += 10);
        upsertResource(adminId, "用户管理", "core:admin:users:read", 1, "users", "/admin/users", null, sort += 10);
        upsertResource(adminId, "角色管理", "core:admin:roles:read", 1, "shield-check", "/admin/roles", null, sort += 10);
        upsertResource(adminId, "资源管理", "core:admin:resources:read", 1, "list", "/admin/resources", null, sort += 10);
        upsertResource(adminId, "接口端点", "core:admin:endpoints:read", 1, "plug", "/admin/endpoints", null, sort += 10);

        // 个人 (目录)
        upsertResource(null, "个人中心", null, 0, "user", "/profile", null, sort += 10);
        // 设置 (目录)
        upsertResource(null, "系统设置", null, 0, "settings", "/settings", null, sort += 10);

        // 按钮权限
        upsertResource(null, "保存资料", "core:user:self:edit", 2, null, null, null, sort += 10);
        upsertResource(null, "上传头像", "core:file:avatar:upload", 2, null, null, null, sort += 10);
        upsertResource(null, "管理用户", "admin:users:write", 2, null, null, null, sort += 10);
        upsertResource(null, "分配角色", "admin:users:roles:assign", 2, null, null, null, sort += 10);
        upsertResource(null, "管理角色", "admin:system:roles:write", 2, null, null, null, sort += 10);
        upsertResource(null, "管理资源", "admin:system:resources:write", 2, null, null, null, sort += 10);
        upsertResource(null, "管理端点", "admin:system:endpoints:write", 2, null, null, null, sort += 10);
        upsertResource(null, "审核应用", "admin:oauth:audit", 2, null, null, null, sort += 10);
        upsertResource(null, "删除应用", "admin:oauth:client:delete", 2, null, null, null, sort += 10);

        logger.info("默认资源数据初始化完成");
    }

    /**
     * 按 code 检测是否已有资源，有则返回已有ID，无则插入
     */
    private long upsertResource(Long parentId, String name, String code, int type, String icon, String path,
            String component, int sortNo) {
        // 按 code 查已有资源（code 为 null 时按 name 查）
        ResourceEntity existing = null;
        if (code != null) {
            existing = resourceMapper.selectOne(
                    new LambdaQueryWrapper<ResourceEntity>()
                            .eq(ResourceEntity::getCode, code)
                            .last("LIMIT 1"));
        } else {
            existing = resourceMapper.selectOne(
                    new LambdaQueryWrapper<ResourceEntity>()
                            .eq(ResourceEntity::getName, name)
                            .isNull(ResourceEntity::getCode)
                            .last("LIMIT 1"));
        }
        if (existing != null) {
            return existing.getId();
        }
        ResourceEntity r = new ResourceEntity();
        r.setParentId(parentId != null ? parentId : 0L);
        r.setName(name);
        r.setCode(code);
        r.setType(type);
        r.setIcon(icon);
        r.setPath(path);
        r.setComponent(component);
        r.setSortNo(sortNo);
        r.setVisible(1);
        resourceMapper.insert(r);
        return r.getId() != null ? r.getId() : 0L;
    }

    // 初始化客户端
    private void initBuiltinClients() {
        if (authProperties.getBuiltinClients() == null) {
            logger.info("没有需要创建的默认客户端（跳过）");
            return;
        }
        // 循环插入数据
        for (AuthProperties.BuiltinClients builtinClients : authProperties.getBuiltinClients()) {
            // 查询数据是否存在
            OAuthClient oAuthClient = oAuthClientMapper.selectOne(
                    new LambdaQueryWrapper<OAuthClient>()
                            .eq(OAuthClient::getClientName, builtinClients.getClientName())
                            .last("LIMIT 1"));
            // 如果已经存在
            if (oAuthClient != null) {
                logger.info("OAuth客户端已存在：{}", builtinClients.getClientName());
                continue;
            }

            // 生成uuid
            String uuid = IdUtil.fastSimpleUUID();
            // 生成密钥
            String rawSecret = RandomUtil.randomString(32);
            String encodedSecret = BCrypt.hashpw(rawSecret);

            OAuthClient client = createInitOAuthClient(builtinClients, uuid, encodedSecret);

            oAuthClientMapper.insert(client);

            logger.info("OAuth客户端注册成功：{} | UUID={} | RAW_SECRET={}", builtinClients.getClientName(), uuid, rawSecret);
        }
    }

    // 初始化超管认证密钥
    private void initAdminKey() {
        Roles role = rolesMapper.selectOne(
                new LambdaQueryWrapper<Roles>()
                        .eq(Roles::getName, "SUPER_ADMIN")
                        .last("LIMIT 1"));
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

    // 不涉及数据库内的加解密，仅用于传输链路中安全性
    // 所以可以反复调用创建新文件，只要保证一次请求中不变化即可
    private void initRsaKeyPair() {
        String keyPath = authProperties.getRsa().getKeyPath();
        File privateKeyFile = new File(keyPath, "id_rsa");
        File publicKeyFile = new File(keyPath, "id_rsa.pub");
        // 公私钥成对存在时才跳过，否则创建新的
        if (privateKeyFile.exists() && publicKeyFile.exists()) {
            return;
        }
        RSA rsa = new RSA();
        FileUtil.mkdir(keyPath);
        FileUtil.writeUtf8String(rsa.getPrivateKeyBase64(), privateKeyFile);
        FileUtil.writeUtf8String(rsa.getPublicKeyBase64(), publicKeyFile);
        logger.info("RSA密钥对已生成, 路径: {}, 公钥指纹: {}", keyPath, rsa.getPublicKeyBase64().substring(0, 20));
    }

    // 创建初始化客户端数据
    private static OAuthClient createInitOAuthClient(AuthProperties.BuiltinClients builtinClients, String uuid,
            String encodedSecret) {
        OAuthClient client = new OAuthClient();
        client.setUuid(uuid);
        client.setClientName(builtinClients.getClientName());
        client.setClientSecret(encodedSecret);
        client.setClientType(1); // 客户端类型，官方时=1
        client.setGrantTypes(builtinClients.getGrantTypes());
        client.setScope(builtinClients.getScope());
        client.setRedirectUri(builtinClients.getRedirectUri());
        client.setDescription("内置客户端无需申请描述"); //
        client.setAuditStatus(1); // 审核状态（0：待审核，1：通过，2：拒绝）
        client.setApplicantGlobalId(null); // 官方客户端无需申请人
        client.setAuditorGlobalId(null); // 官方客户端无需审核
        client.setAuditOpinion("内置客户端无需审核意见");
        client.setStatus(1); // 默认启用
        return client;
    }

    // 初始化Aes
    private void initAesKey() {
        String keyPath = authProperties.getAes().getKeyPath();
        File aesKeyFile = new File(keyPath, "aes.key");
        if (aesKeyFile.exists()) {
            return;
        }
        FileUtil.mkdir(keyPath);
        byte[] key = RandomUtil.randomBytes(16);
        FileUtil.writeBytes(key, aesKeyFile);
        logger.info("AES密钥已生成，路径: {}", aesKeyFile.getAbsolutePath());
    }

}
