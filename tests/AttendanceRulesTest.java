import hn.org.cah.asistencia.AttendanceRules;
import java.time.LocalDate;
import java.util.List;
public class AttendanceRulesTest {
 static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
 public static void main(String[] args) {
  LocalDate mon=LocalDate.parse("2026-10-05"), sat=LocalDate.parse("2026-10-10"),sun=LocalDate.parse("2026-10-11");
  check(AttendanceRules.shiftEnd(mon)==990,"Lunes 16:30");
  check(AttendanceRules.shiftEnd(sat)==750,"Sábado 12:30");
  check(AttendanceRules.shiftEnd(sun)==-1,"Domingo libre");
  var a=AttendanceRules.evaluate(mon,"08:30","17:05",false,"",List.of(),true);
  check(a.afterMinutes==35 && !a.early && !a.late,"35 minutos después del cierre, no extras");
  var p=AttendanceRules.evaluate(mon,"10:00","16:30",false,"",List.of(new AttendanceRules.Window(510,600)),true);
  check(p.authorizedMinutes==90 && p.lateMinutes==0 && !p.late,"Permiso horario exime retraso autorizado");
  var h=AttendanceRules.evaluate(mon,"","",false,"IHSS",List.of(),true);
  check(h.excluded && !h.unjustified,"IHSS no es ausencia sin justificar");
  var u=AttendanceRules.evaluate(mon,"","",false,"",List.of(new AttendanceRules.Window(510,600)),true);
  check(u.unjustified,"Permiso de 90 minutos no justifica ausencia todo el día");
  var t=AttendanceRules.evaluate(mon,"08:36","16:30",false,"",List.of(),true);
  check(t.late && t.lateMinutes==6,"Seis minutos es llegada tarde");
  var off=AttendanceRules.evaluate(mon,"","",true,"",List.of(),true);
  check(off.excluded && !off.unjustified,"Feriado excluido");
  var early=AttendanceRules.evaluate(mon,"08:30","15:30",false,"",List.of(new AttendanceRules.Window(930,990)),true);
  check(!early.early && early.earlyMinutes==0,"Salida anticipada cubierta por permiso al cierre");
  var partial=AttendanceRules.evaluate(mon,"09:00","16:30",false,"",List.of(new AttendanceRules.Window(510,540)),true);
  check(partial.lateMinutes==0,"Llegada 09:00 autorizada");
  System.out.println("OK: 9 pruebas de reglas de asistencia");
 }
}