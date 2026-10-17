package cc.nanoic.yunanexus.auth.service;
import cc.nanoic.yunanexus.auth.client.RootAdminClient;
import cc.nanoic.yunanexus.auth.entity.ApplicationCategory;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ApplicationTrustServiceTest {
 JdbcTemplate db=mock(JdbcTemplate.class);RootAdminClient roles=mock(RootAdminClient.class);ApplicationTrustService service=new ApplicationTrustService(db,roles);
 AuthenticatedIdentity actor=new AuthenticatedIdentity("root","web","sid",1);
 @Test void ordinaryAdminAndApplicationIdentityCannotClassify(){when(roles.superCheck(1)).thenReturn(Result.success(false));assertThrows(BizException.class,()->service.classify(actor,"oauth_test",ApplicationCategory.OFFICIAL_THIRD_PARTY));assertThrows(BizException.class,()->service.classify(new AuthenticatedIdentity("app","oauth_test","sid",1),"oauth_test",ApplicationCategory.OFFICIAL_NATIVE));verifyNoInteractions(db);}
 @Test void superClassificationHasVerifiedAuditActor(){when(roles.superCheck(1)).thenReturn(Result.success(true));when(db.queryForList(anyString(),eq("oauth_test"))).thenReturn(List.of(Map.of("application_category","THIRD_PARTY")));service.classify(actor,"oauth_test",ApplicationCategory.OFFICIAL_THIRD_PARTY);verify(db).update("UPDATE auth_client SET application_category=? WHERE client_code=?","OFFICIAL_THIRD_PARTY","oauth_test");verify(db).update("INSERT INTO auth_application_trust_audit(client_code,old_category,new_category,actor_user_id) VALUES(?,?,?,?)","oauth_test","THIRD_PARTY","OFFICIAL_THIRD_PARTY",1L);}
 @Test void unknownClassificationNeverAllowsPasswordOrCredit(){for(String category:Arrays.asList(null,"","official_native","THIRD_PARTY","unknown")){assertFalse(ApplicationCategory.allowsPassword(category));assertFalse(ApplicationCategory.allowsCredit(category));}assertTrue(ApplicationCategory.allowsPassword("OFFICIAL_NATIVE"));assertTrue(ApplicationCategory.allowsCredit("OFFICIAL_THIRD_PARTY"));}
}
