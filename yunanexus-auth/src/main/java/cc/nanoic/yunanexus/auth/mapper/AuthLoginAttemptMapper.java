package cc.nanoic.yunanexus.auth.mapper;

import cc.nanoic.yunanexus.auth.entity.AuthLoginAttempt;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录尝试记录 Mapper。<p>
 *
 * 每次登录（成功或失败）均落库一条，用于风控分析、审计与 90 天保留策略（ADR-020）。
 * 写入路径在登录 Service 中异步化，避免拖慢登录响应。
 *
 * @author yunanexus
 */
@Mapper
public interface AuthLoginAttemptMapper extends BaseMapper<AuthLoginAttempt> {
}
