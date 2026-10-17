package cc.nanoic.yunanexus.auth.service;
import java.time.LocalDate;
/** Server-owned reward rule: 10 base, +2 per consecutive day, bonus capped at 12. */
public final class CheckInPolicy {
 private CheckInPolicy(){}
 public static int nextStreak(LocalDate previous,int streak,LocalDate today){
  if(previous!=null && previous.isAfter(today))throw new IllegalStateException("Check-in clock moved backwards");
  if(today.equals(previous))return streak;
  return today.minusDays(1).equals(previous)?Math.addExact(streak,1):1;
 }
 public static long reward(int streak){if(streak<1)throw new IllegalArgumentException("Invalid streak");return 10L+2L*Math.min(streak-1,6);}
}
