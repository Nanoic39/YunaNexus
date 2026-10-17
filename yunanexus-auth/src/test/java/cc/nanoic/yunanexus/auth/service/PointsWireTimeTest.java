package cc.nanoic.yunanexus.auth.service;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PointsWireTimeTest {
 @Test void bothJdbcDateTypesProduceAnInstantIncludingZeroSeconds(){
  for(Object value:List.of(LocalDateTime.of(2026,10,17,15,59),java.sql.Timestamp.valueOf(LocalDateTime.of(2026,10,17,15,59)))){
   var db=mock(JdbcTemplate.class);var row=new LinkedHashMap<String,Object>();row.put("createdAt",value);
   when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row));
   var service=new PointsService(db,Clock.systemUTC(),mock(OAuthApplicationService.class));
   var result=service.ledger(new AuthenticatedIdentity("external","web","sid",1),0,20);
   assertEquals("2026-10-17T15:59:00Z",result.getFirst().get("createdAt"));
   assertDoesNotThrow(()->Instant.parse(result.getFirst().get("createdAt").toString()));
  }
 }
}
