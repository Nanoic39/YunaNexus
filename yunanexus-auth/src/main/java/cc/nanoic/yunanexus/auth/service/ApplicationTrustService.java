package cc.nanoic.yunanexus.auth.service;
import cc.nanoic.yunanexus.auth.client.RootAdminClient;
import cc.nanoic.yunanexus.auth.entity.ApplicationCategory;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationTrustService {
 private final JdbcTemplate db; private final RootAdminClient roles;
 public ApplicationTrustService(JdbcTemplate db,RootAdminClient roles){this.db=db;this.roles=roles;}
 @Transactional
 public void classify(AuthenticatedIdentity actor,String client,ApplicationCategory category){
  if(actor==null||!"web".equals(actor.clientCode()))throw new BizException(ErrorCode.FORBIDDEN);
  var root=roles.superCheck(actor.userId());
  if(root==null||root.getCode()!=200||!Boolean.TRUE.equals(root.getData()))throw new BizException(ErrorCode.FORBIDDEN);
  if(client==null||client.length()>64||category==null)throw new BizException(ErrorCode.BAD_REQUEST);
  if("web".equals(client)&&category!=ApplicationCategory.OFFICIAL_NATIVE)throw new BizException(ErrorCode.FORBIDDEN,"不可降级核心门户");
  var old=db.queryForList("SELECT application_category FROM auth_client WHERE client_code=? FOR UPDATE",client);
  if(old.size()!=1)throw new BizException(ErrorCode.NOT_FOUND);
  String previous=old.getFirst().get("application_category").toString();
  if(previous.equals(category.name()))return;
  db.update("UPDATE auth_client SET application_category=? WHERE client_code=?",category.name(),client);
  db.update("INSERT INTO auth_application_trust_audit(client_code,old_category,new_category,actor_user_id) VALUES(?,?,?,?)",client,previous,category.name(),actor.userId());
 }
}
