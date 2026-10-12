package hn.org.cah.asistencia;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Reglas de asistencia; ningún cálculo convierte minutos posteriores en horas extras. */
public final class AttendanceRules {
    public static final int START = 8 * 60 + 30;
    public static final int GRACE = 5;
    private AttendanceRules() {}

    public static int shiftEnd(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SUNDAY ? -1 :
                (date.getDayOfWeek() == DayOfWeek.SATURDAY ? 12 * 60 + 30 : 16 * 60 + 30);
    }

    public static int mins(String time) {
        if (time == null || time.isEmpty()) return -1;
        LocalTime t = LocalTime.parse(time.length() > 5 ? time.substring(0, 5) : time);
        return t.getHour() * 60 + t.getMinute();
    }
    public static String hhmm(int m) { return String.format(java.util.Locale.US, "%02d:%02d", m/60, m%60); }

    /** Permiso horario dentro de una única jornada. */
    public static final class Window {
        public final int from, to;
        public Window(int from, int to) {
            if (to <= from) throw new IllegalArgumentException("Intervalo horario inválido");
            this.from=from; this.to=to;
        }
    }

    public static final class Day {
        public boolean scheduled, excluded, present, unjustified, missingExit, late, early, fullPermission;
        public int expectedMinutes, authorizedMinutes, lateMinutes, earlyMinutes, afterMinutes;
        public String state;
    }

    /**
     * Evaluar solamente jornadas finalizadas; ausencia del día actual no se infiere antes del cierre.
     * Permisos horarios reducen minutos exigibles, nunca marcan día completo automáticamente.
     */
    public static Day evaluate(LocalDate day, String entry, String exit, boolean holiday,
                               String fullType, List<Window> hourly, boolean finished) {
        Day r = new Day();
        int end = shiftEnd(day);
        r.scheduled = end >= 0;
        if (end < 0 || holiday) {
            r.excluded=true; r.state=holiday?"FERIADO":"SIN_JORNADA"; return r;
        }
        r.fullPermission = "PERMISO_DIA".equals(fullType) || "IHSS".equals(fullType);
        if (r.fullPermission) {
            r.excluded=true;
            r.state="IHSS".equals(fullType)?"INCAPACIDAD_IHSS":"PERMISO_DIA_COMPLETO";
            return r;
        }
        List<Window> bounded = new ArrayList<>();
        for (Window w : hourly) {
            int a = Math.max(START,w.from), b=Math.min(end,w.to);
            if(b>a) bounded.add(new Window(a,b));
        }
        int allowed = allowedBetween(START,end,bounded);
        r.authorizedMinutes=allowed;
        r.expectedMinutes=(end-START)-allowed;
        int in=mins(entry), out=mins(exit);
        r.present = in>=0;
        r.missingExit=r.present && out<0 && finished;
        if (r.present) {
            // Tolerancia sólo aplica si el retraso no ha sido autorizado por permiso.
            int raw = Math.max(0, in-START);
            r.lateMinutes=Math.max(0,raw-allowedBetween(START,Math.min(in,end),bounded));
            r.late=r.lateMinutes>GRACE;
            if (r.late && r.lateMinutes<=GRACE) r.late=false;
            if (out>=0) {
                r.earlyMinutes=Math.max(0,(end-Math.max(START,out))-
                        allowedBetween(Math.max(START,out),end,bounded));
                r.early=r.earlyMinutes>0;
                r.afterMinutes=Math.max(0,out-end);
            }
            r.state=r.missingExit?"FALTA_SALIDA":r.late?"TARDANZA":r.early?"SALIDA_ANTICIPADA":"PRESENTE";
        } else {
            r.unjustified=finished && r.expectedMinutes>0;
            r.state=!finished?"PENDIENTE":r.unjustified?"AUSENCIA_SIN_JUSTIFICAR":"PERMISO_HORARIO";
        }
        if ("SIN_JUSTIFICAR".equals(fullType) && !r.present && finished) {
            r.unjustified=true; r.state="AUSENCIA_SIN_JUSTIFICAR";
        }
        return r;
    }

    /** Longitud de unión de intervalos (evita descontar dos veces un solapamiento). */
    public static int allowedBetween(int a,int b,List<Window> windows) {
        if (b<=a) return 0;
        boolean[] allowed=new boolean[b-a];
        for(Window w:windows) {
            for(int m=Math.max(a,w.from);m<Math.min(b,w.to);m++) allowed[m-a]=true;
        }
        int total=0; for(boolean on:allowed) if(on)total++;
        return total;
    }
}