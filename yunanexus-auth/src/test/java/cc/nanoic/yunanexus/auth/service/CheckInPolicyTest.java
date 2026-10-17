package cc.nanoic.yunanexus.auth.service;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class CheckInPolicyTest {
 @Test void initialContinuousAndGap(){var day=LocalDate.of(2026,10,17);assertEquals(1,CheckInPolicy.nextStreak(null,0,day));assertEquals(4,CheckInPolicy.nextStreak(day.minusDays(1),3,day));assertEquals(1,CheckInPolicy.nextStreak(day.minusDays(2),3,day));assertEquals(3,CheckInPolicy.nextStreak(day,3,day));}
 @Test void bonusCappedAndClockReversalRejected(){assertEquals(10,CheckInPolicy.reward(1));assertEquals(12,CheckInPolicy.reward(2));assertEquals(22,CheckInPolicy.reward(1000));assertThrows(IllegalArgumentException.class,()->CheckInPolicy.reward(0));assertThrows(IllegalStateException.class,()->CheckInPolicy.nextStreak(LocalDate.now().plusDays(1),2,LocalDate.now()));}
}
