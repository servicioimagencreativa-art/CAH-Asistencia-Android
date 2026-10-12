package hn.org.cah.asistencia;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Interfaz 100 % Android nativa. Los datos no salen del dispositivo sin exportación explícita. */
public final class MainActivity extends Activity {
    private static final ZoneId HN=ZoneId.of("America/Tegucigalpa");
    private static final int NAVY=Color.rgb(19,45,68), TEAL=Color.rgb(7,126,122), BG=Color.rgb(244,247,249);
    private static final int MUTED=Color.rgb(91,105,119), RED=Color.rgb(170,54,55), WHITE=Color.WHITE;
    private static final int EXPORT_CSV=1201, EXPORT_BACKUP=1202, IMPORT_BACKUP=1203;
    private static final long SESSION_MAX_MS=10*60*1000L;
    private CahDb db;
    private String actor="",role="",screen="mark",selectedMonth="";
    private LinearLayout content;
    private long lastInteraction=0;
    private String pendingExport="";
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable autoLock=new Runnable() { @Override public void run() {
        if(!actor.isEmpty() && SystemClock.elapsedRealtime()-lastInteraction>=SESSION_MAX_MS){logout("Sesión cerrada por inactividad");}
        else if(!actor.isEmpty()) handler.postDelayed(this,30000);
    }};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().setStatusBarColor(NAVY);getWindow().setNavigationBarColor(NAVY);
        db=new CahDb(this);selectedMonth=YearMonth.now(HN).toString();
        showAuth();
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if(event.getAction()==android.view.MotionEvent.ACTION_DOWN && !actor.isEmpty())lastInteraction=SystemClock.elapsedRealtime();
        return super.dispatchTouchEvent(event);
    }
    @Override protected void onResume() {
        super.onResume();
        if(!actor.isEmpty() && lastInteraction>0 && SystemClock.elapsedRealtime()-lastInteraction>=SESSION_MAX_MS)
            logout("Sesión vencida");
    }
    @Override public void onBackPressed() {
        if(actor.isEmpty()){super.onBackPressed();return;}
        if(!"mark".equals(screen)){screen="mark";render();}
        else new AlertDialog.Builder(this).setTitle("Cerrar sesión").setMessage("¿Desea salir de su cuenta?")
                .setNegativeButton("Cancelar",null).setPositiveButton("Salir",(d,w)->logout("Sesión finalizada")).show();
    }
    private boolean admin(){return "admin".equals(role);}
    private void demandAdmin(){if(!admin())throw new SecurityException("Se requiere cuenta de administrador");}
    private void signedIn(String name,String assignedRole){
        actor=name;role=assignedRole;screen="mark";lastInteraction=SystemClock.elapsedRealtime();
        handler.removeCallbacks(autoLock);handler.postDelayed(autoLock,30000);render();
    }
    private void logout(String message){actor="";role="";pendingExport="";handler.removeCallbacks(autoLock);showAuth();if(message!=null)toast(message);}
    private static LocalDate today(){return LocalDate.now(HN);}
    private static String todayStr(){return today().toString();}
    private static String timeNow(){return ZonedDateTime.now(HN).format(DateTimeFormatter.ofPattern("HH:mm:ss",Locale.US));}
    private static String labelDate(String d){if(d==null||d.isEmpty())return "—";return d.substring(8,10)+"/"+d.substring(5,7)+"/"+d.substring(0,4);}
    private static String timeOf(String d){return d==null||d.length()<5?"—":d.substring(0,5);}
    private static String percent(int a,int b){return b==0?"—":String.format(Locale.forLanguageTag("es-HN"),"%.1f %%",100.0*a/b);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void error(Throwable e){String msg=e.getMessage()==null?"Ocurrió un problema al guardar los datos":e.getMessage();new AlertDialog.Builder(this).setTitle("No se completó la operación").setMessage(msg).setPositiveButton("Entendido",null).show();}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable background(int fill,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));return d;}
    private void pad(View v,int left,int top,int right,int bottom){v.setPadding(dp(left),dp(top),dp(right),dp(bottom));}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void space(LinearLayout parent,int px){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(px)));}
    private TextView text(String value,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setIncludeFontPadding(true);return t;}
    private void heading(String title,String detail){
        TextView t=text(title,25,NAVY,true);content.addView(t);
        if(detail!=null&&!detail.isEmpty()){TextView d=text(detail,13,MUTED,false);pad(d,0,3,0,9);content.addView(d);}space(content,8);
    }
    private TextView smallLabel(String label){TextView l=text(label,13,NAVY,true);pad(l,0,10,0,3);return l;}
    private EditText edit(LinearLayout parent,String title,String value,int type){
        parent.addView(smallLabel(title));EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(16);e.setTextColor(NAVY);
        e.setHintTextColor(MUTED);e.setInputType(type);e.setText(value);
        e.setBackground(background(Color.rgb(242,247,248),9));pad(e,12,12,12,12);
        parent.addView(e,new LinearLayout.LayoutParams(-1,dp(50)));return e;
    }
    private EditText secure(LinearLayout parent,String label){return edit(parent,label,"",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);}
    private EditText numericPin(LinearLayout parent,String label){return edit(parent,label,"",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);}
    private EditText date(LinearLayout parent,String label,String initial){
        EditText e=edit(parent,label,initial,InputType.TYPE_CLASS_DATETIME|InputType.TYPE_DATETIME_VARIATION_DATE);
        e.setFocusable(false);e.setOnClickListener(v->{
            LocalDate d;try{d=LocalDate.parse(e.getText().toString());}catch(Exception ex){d=today();}
            DatePickerDialog picker=new DatePickerDialog(this,(view,y,m,day)->e.setText(String.format(Locale.US,"%04d-%02d-%02d",y,m+1,day)),d.getYear(),d.getMonthValue()-1,d.getDayOfMonth());
            picker.show();
        });return e;
    }
    private Button button(String title,int fill,Runnable action){Button b=new Button(this);b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(WHITE);b.setBackgroundTintList(ColorStateList.valueOf(fill));pad(b,10,5,10,5);
        b.setOnClickListener(v->{try{action.run();}catch(Exception ex){error(ex);}});return b;
    }
    private void action(LinearLayout parent,String title,int fill,Runnable action){Button b=button(title,fill,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(51));lp.topMargin=dp(10);parent.addView(b,lp);}
    private LinearLayout card(){LinearLayout c=vertical();c.setBackground(background(WHITE,15));pad(c,16,15,16,18);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);content.addView(c,lp);return c;}
    private void note(LinearLayout parent,String txt){TextView t=text(txt,13,MUTED,false);pad(t,0,7,0,6);parent.addView(t);}
    private void bigStat(LinearLayout parent,String label,String val){LinearLayout line=horizontal();TextView l=text(label,14,MUTED,false),v=text(val,18,NAVY,true);
        line.addView(l,new LinearLayout.LayoutParams(0,-2,1));line.addView(v);pad(line,0,8,0,8);parent.addView(line);
        View hr=new View(this);hr.setBackgroundColor(Color.rgb(229,236,241));parent.addView(hr,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    private void layout(String subtitle){
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);sc.setClipToPadding(false);
        LinearLayout root=vertical();root.setFitsSystemWindows(true);sc.addView(root);
        LinearLayout header=vertical();header.setBackgroundColor(NAVY);pad(header,18,17,18,13);
        header.addView(text("CAH  /  CONTROL DE ASISTENCIA",18,WHITE,true));
        header.addView(text(subtitle,12,Color.rgb(188,222,226),false));root.addView(header);
        if(!actor.isEmpty()){
            LinearLayout bar=horizontal();bar.setBackgroundColor(WHITE);pad(bar,10,4,10,5);
            TextView who=text(actor+" · "+(admin()?"Administrador":"Usuario asignado"),12,NAVY,true);
            bar.addView(who,new LinearLayout.LayoutParams(0,-2,1));Button leave=button("Salir",MUTED,()->logout("Sesión finalizada"));bar.addView(leave);
            root.addView(bar);
            if(admin()){
                HorizontalScrollView h=new HorizontalScrollView(this);h.setHorizontalScrollBarEnabled(false);h.setBackgroundColor(WHITE);
                LinearLayout nav=horizontal();pad(nav,8,0,8,8);
                String[][] tabs={{"mark","Marcación"},{"staff","Colaboradores"},{"leave","Permisos / IHSS"},{"dashboard","Dashboard"},{"reports","Reportes"},{"admin","Administración"}};
                for(String[] tab:tabs){Button b=button(tab[1],tab[0].equals(screen)?TEAL:NAVY,()->{screen=tab[0];render();});
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(45));lp.setMargins(dp(3),0,dp(3),0);nav.addView(b,lp);}
                h.addView(nav);root.addView(h);
            }
        }
        content=vertical();pad(content,15,17,15,24);root.addView(content);
        setContentView(sc);
    }
    private void showAuth(){
        layout("Acceso seguro · datos guardados en este Android");
        if(!db.initialized()){
            heading("Configuración inicial","Crea las dos cuentas. Las contraseñas deben tener 8 caracteres como mínimo.");
            LinearLayout c=card();
            EditText a=edit(c,"Usuario administrador","administrador",InputType.TYPE_CLASS_TEXT);
            EditText ap=secure(c,"Contraseña administrador");
            EditText o=edit(c,"Usuario asignado","recepcion",InputType.TYPE_CLASS_TEXT);
            EditText op=secure(c,"Contraseña usuario asignado");
            action(c,"Crear cuentas y comenzar",TEAL,()->{
                String adminName=a.getText().toString().trim(),operatorName=o.getText().toString().trim();
                String pass=ap.getText().toString(),operatorPass=op.getText().toString();
                if(adminName.length()<3||operatorName.length()<3||adminName.equalsIgnoreCase(operatorName))throw new IllegalArgumentException("Introduce dos nombres de usuario diferentes (3 caracteres o más)");
                if(pass.length()<8||operatorPass.length()<8)throw new IllegalArgumentException("Las contraseñas necesitan 8 caracteres o más");
                db.setup(adminName,pass,operatorName,operatorPass);signedIn(adminName,"admin");
            });
            note(c,"No existen cuentas predeterminadas ni contraseña universal. Custodia los accesos y crea respaldos cifrados.");
        }else{
            heading("Iniciar sesión","Solo el administrador puede consultar permisos, incapacidades, indicadores y reportes.");
            LinearLayout c=card();EditText u=edit(c,"Usuario","",InputType.TYPE_CLASS_TEXT);
            EditText p=secure(c,"Contraseña");
            action(c,"Ingresar",TEAL,()->{
                String name=u.getText().toString().trim(),key="login:"+name.toLowerCase(Locale.ROOT);
                enforceLock(key);JSONObject user=db.user(name);
                if(user==null||!CahDb.verify(p.getText().toString(),user.optString("salt"),user.optString("hash"))){badAttempt(key);throw new IllegalArgumentException("Usuario o contraseña inválidos");}
                clearAttempt(key);signedIn(user.optString("username"),user.optString("role"));
            });
            note(c,"Acceso del usuario asignado: marcación únicamente. El resto de los módulos requiere sesión de administrador.");
        }
    }
    private SharedPreferences attempts(){return getSharedPreferences("access_limits",MODE_PRIVATE);}
    private void enforceLock(String key){
        long until=attempts().getLong(key+"_until",0);
        if(until>System.currentTimeMillis())throw new IllegalStateException("Acceso temporalmente bloqueado tras intentos fallidos. Intente más tarde.");
    }
    private void badAttempt(String key){SharedPreferences p=attempts();int n=p.getInt(key+"_count",0)+1;
        p.edit().putInt(key+"_count",n>=5?0:n).putLong(key+"_until",n>=5?System.currentTimeMillis()+15*60000L:0).apply();}
    private void clearAttempt(String key){attempts().edit().remove(key+"_count").remove(key+"_until").apply();}
    private void render(){
        if(actor.isEmpty()){showAuth();return;}
        if(!admin()&&!"mark".equals(screen))screen="mark";
        layout("Lunes a viernes 08:30–16:30  ·  Sábado 08:30–12:30");
        switch(screen){case "staff":demandAdmin();staffScreen();break;
            case "leave":demandAdmin();leaveScreen();break;
            case "dashboard":demandAdmin();dashboardScreen();break;
            case "reports":demandAdmin();reportsScreen();break;
            case "admin":demandAdmin();adminScreen();break;
            default:markScreen();}
    }
    private static String normalizedCode(String value){
        String x=value.trim().toUpperCase(Locale.ROOT).replace(" ","");
        x=x.replaceFirst("^CAH-?","");
        if(!x.matches("[0-9]{1,7}"))throw new IllegalArgumentException("Código inválido. Utiliza 0001 o CAH-0001");
        int number=Integer.parseInt(x);
        if(number<=0)throw new IllegalArgumentException("Número inválido");
        return String.format(Locale.US,"CAH-%04d",number);
    }
    private void markScreen(){
        heading("Entrada y salida","Identifícate con tu número y PIN. La hora proviene del dispositivo, en horario de Honduras.");
        LinearLayout intro=card();bigStat(intro,"Fecha",labelDate(todayStr()));
        int close=AttendanceRules.shiftEnd(today());bigStat(intro,"Horario del día",close<0?"Sin jornada":"08:30–"+AttendanceRules.hhmm(close));
        LinearLayout c=card();EditText code=edit(c,"Número de colaborador","",InputType.TYPE_CLASS_TEXT);
        EditText pin=numericPin(c,"PIN personal (4–6 dígitos)");
        action(c,"✓ REGISTRAR ENTRADA",TEAL,()->mark(code,pin,true));
        action(c,"↗ REGISTRAR SALIDA",NAVY,()->mark(code,pin,false));
        note(c,"Solo se permite una entrada y una salida por día. Los domingos no hay marcaciones ordinarias.");
        LinearLayout desc=card();desc.addView(text("Aviso sobre el tiempo posterior",16,NAVY,true));
        note(desc,"Los minutos después de las 4:30 p. m. (12:30 p. m. el sábado) se recopilan exclusivamente para revisión administrativa. No se clasifican ni liquidan como horas extras.");
        if(admin()){
            LinearLayout recent=card();recent.addView(text("Marcaciones recientes",16,NAVY,true));
            JSONArray a=db.query("SELECT e.code,e.name,p.day,p.entry,p.exit FROM punches p JOIN employees e ON e.id=p.employee_id ORDER BY p.day DESC,p.id DESC LIMIT 8");
            for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);note(recent,r.optString("code")+" · "+r.optString("name")+"\n"+labelDate(r.optString("day"))+"    Entrada "+timeOf(r.optString("entry"))+"    Salida "+timeOf(r.optString("exit")));}
        }
    }
    private void mark(EditText code,EditText pin,boolean entering){
        if(AttendanceRules.shiftEnd(today())<0)throw new IllegalStateException("Hoy no hay jornada ordinaria");
        String employeeCode=normalizedCode(code.getText().toString()),p=pin.getText().toString();
        JSONObject staff=db.employeeCode(employeeCode);
        String key="employee:"+employeeCode;
        enforceLock(key);
        if(staff==null||staff.optInt("active")!=1||!CahDb.verify(p,staff.optString("pin_salt"),staff.optString("pin_hash"))){
            badAttempt(key);throw new IllegalArgumentException("Número o PIN incorrectos, o colaborador inactivo");
        }
        clearAttempt(key);
        String day=todayStr();
        if(day.compareTo(staff.optString("joined"))<0||(!staff.optString("ended").isEmpty()&&day.compareTo(staff.optString("ended"))>0))
            throw new IllegalStateException("El colaborador no está habilitado para esta fecha");
        JSONArray leaves=db.leaves(staff.optInt("id"),day);
        for(int i=0;i<leaves.length();i++){String type=leaves.optJSONObject(i).optString("type");
            if("PERMISO_DIA".equals(type)||"IHSS".equals(type)||"SIN_JUSTIFICAR".equals(type))
                throw new IllegalStateException("Existe una incidencia de día completo. Consulte con administración antes de marcar.");
        }
        String type=db.punchNow(staff.optInt("id"),day,timeNow(),System.currentTimeMillis(),entering,actor);
        pin.setText("");code.setText("");toast(type+" guardada · "+staff.optString("name")+" · "+timeNow());
        render();
    }

    private void staffScreen(){
        heading("Colaboradores","Asignación automática de código CAH y conservación del historial.");
        LinearLayout c=card();action(c,"+ Agregar colaborador",TEAL,()->employeeForm(null));
        JSONArray people=db.employees();
        for(int i=0;i<people.length();i++){
            JSONObject e=people.optJSONObject(i);LinearLayout r=card();
            r.addView(text(e.optString("code")+"  ·  "+e.optString("name"),17,NAVY,true));
            note(r,e.optString("area")+" / "+e.optString("position")+"   ·   "+(e.optInt("active")==1?"Activo":"Inactivo"));
            action(r,"Editar / dar de baja / restablecer PIN",NAVY,()->employeeForm(e));
            action(r,"Ver reporte mensual",TEAL,()->{reportEmployeeId=e.optInt("id");screen="reports";render();});
        }
    }
    private void employeeForm(JSONObject current){
        demandAdmin();boolean editing=current!=null;
        layout("Gestión del personal · acceso exclusivo del administrador");
        heading(editing?"Editar colaborador":"Nuevo colaborador",editing?"Código "+current.optString("code"):"El código se genera automáticamente y no se reutiliza.");
        LinearLayout c=card();
        EditText name=edit(c,"Nombre completo",editing?current.optString("name"):"",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText area=edit(c,"Departamento / área",editing?current.optString("area"):"",InputType.TYPE_CLASS_TEXT);
        EditText pos=edit(c,"Cargo",editing?current.optString("position"):"",InputType.TYPE_CLASS_TEXT);
        EditText hired=date(c,"Fecha de ingreso",editing?current.optString("joined"):todayStr());
        EditText ended=editing?date(c,"Fecha de baja (si corresponde)",current.optString("ended").isEmpty()?todayStr():current.optString("ended")):null;
        CheckBox active=new CheckBox(this);active.setText("Colaborador activo");active.setChecked(!editing||current.optInt("active")==1);if(editing)c.addView(active);
        EditText pin=numericPin(c,editing?"Cambiar PIN (dejar vacío para conservar)":"PIN nuevo (4 a 6 dígitos)");
        action(c,editing?"Guardar cambios":"Registrar colaborador",TEAL,()->{
            String n=name.getText().toString().trim(),a=area.getText().toString().trim(),p=pos.getText().toString().trim(),codePin=pin.getText().toString();
            String joined=validateDay(hired.getText().toString());
            if(n.length()<3)throw new IllegalArgumentException("Ingresa el nombre completo");
            if((!editing||!codePin.isEmpty())&&!codePin.matches("[0-9]{4,6}"))throw new IllegalArgumentException("El PIN requiere de 4 a 6 dígitos");
            if(editing){String stop=active.isChecked()?"":validateDay(ended.getText().toString());
                if(!stop.isEmpty()&&stop.compareTo(joined)<0)throw new IllegalArgumentException("La fecha de baja no puede preceder la fecha de ingreso");
                db.updateEmployee(current.optInt("id"),n,a,p,joined,stop,active.isChecked(),codePin,actor);toast("Colaborador actualizado");
            }else{String code=db.createEmployee(n,a,p,joined,codePin,actor);toast("Colaborador agregado con número "+code);}
            screen="staff";render();
        });
        action(c,"Volver a colaboradores",NAVY,()->{screen="staff";render();});
    }
    private static String validateDay(String raw){
        String day=raw.trim();LocalDate.parse(day);return day;
    }

    private static final String[] LEAVE_NAMES={"Permiso por horas","Permiso por día completo","Incapacidad IHSS","Ausencia sin justificar"};
    private static final String[] LEAVE_TYPES={"PERMISO_HORAS","PERMISO_DIA","IHSS","SIN_JUSTIFICAR"};
    private static String leaveName(String type) {
        for(int i=0;i<LEAVE_TYPES.length;i++)if(LEAVE_TYPES[i].equals(type))return LEAVE_NAMES[i];return type;
    }
    private Spinner spinner(LinearLayout parent,String title,String[] options){
        parent.addView(smallLabel(title));Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);
        s.setAdapter(a);s.setBackground(background(Color.rgb(242,247,248),9));pad(s,7,7,7,7);
        parent.addView(s,new LinearLayout.LayoutParams(-1,dp(52)));return s;
    }
    private void leaveScreen(){
        heading("Permisos e incidencias","Solo administración puede registrar, consultar o anular estas novedades.");
        LinearLayout intro=card();note(intro,"Registra duración en minutos para permisos por horas; para permisos de día completo, incapacidad IHSS o ausencia sin justificar selecciona fechas de inicio y fin.");
        action(intro,"+ Registrar permiso, IHSS o incidencia",TEAL,this::leaveForm);
        JSONArray list=db.leavesRecent();
        for(int i=0;i<list.length();i++){
            JSONObject x=list.optJSONObject(i);LinearLayout item=card();
            item.addView(text(x.optString("code")+" · "+x.optString("name"),16,NAVY,true));
            String period=labelDate(x.optString("from_day"))+ (x.optString("from_day").equals(x.optString("to_day"))?"":" al "+labelDate(x.optString("to_day")));
            if("PERMISO_HORAS".equals(x.optString("type")))period+=" · "+AttendanceRules.hhmm(x.optInt("start_min"))+"–"+AttendanceRules.hhmm(x.optInt("end_min"))+" ("+(x.optInt("end_min")-x.optInt("start_min"))+" min)";
            else period+=" ("+(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(x.optString("from_day")),LocalDate.parse(x.optString("to_day")))+1)+" día(s) calendario)";
            note(item,leaveName(x.optString("type"))+" · "+period);
            note(item,"Motivo: "+x.optString("reason")+ (x.optString("reference").isEmpty()?"":"\nReferencia constancia: "+x.optString("reference")));
            note(item,"Registrado por: "+x.optString("created_by"));
            action(item,"Anular este registro",RED,()->new AlertDialog.Builder(this).setTitle("Anular registro")
                .setMessage("Esta acción quedará documentada en la bitácora. ¿Continuar?")
                .setNegativeButton("Cancelar",null).setPositiveButton("Anular",(di,which)->{
                    try{demandAdmin();db.deleteLeave(x.optInt("id"),actor);render();toast("Incidencia anulada");}
                    catch(Exception e){error(e);}
                }).show());
        }
        if(list.length()==0)note(content,"Aún no se han registrado permisos ni incapacidades.");
    }
    private void leaveForm(){
        demandAdmin();JSONArray employees=db.employees();
        if(employees.length()==0)throw new IllegalArgumentException("Primero agrega un colaborador");
        layout("Permisos / incapacidades · solo administrador");
        heading("Registrar incidencia","La autorización se conserva separada de los datos de marcación.");
        LinearLayout c=card();String[] names=new String[employees.length()];
        for(int i=0;i<employees.length();i++){JSONObject o=employees.optJSONObject(i);names[i]=o.optString("code")+" · "+o.optString("name");}
        Spinner staff=spinner(c,"Colaborador",names);
        Spinner type=spinner(c,"Tipo de incidencia",LEAVE_NAMES);
        EditText from=date(c,"Fecha inicial",todayStr()),to=date(c,"Fecha final",todayStr());
        EditText start=edit(c,"Hora desde (HH:mm)","08:30",InputType.TYPE_CLASS_DATETIME|InputType.TYPE_DATETIME_VARIATION_TIME);
        EditText end=edit(c,"Hora hasta (HH:mm)","09:30",InputType.TYPE_CLASS_DATETIME|InputType.TYPE_DATETIME_VARIATION_TIME);
        TextView duration=text("Duración: se calcula al guardar",13,TEAL,true);pad(duration,0,10,0,5);c.addView(duration);
        EditText reason=edit(c,"Motivo / observación administrativa","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText ref=edit(c,"Referencia de constancia IHSS (opcional; sin diagnóstico)","",InputType.TYPE_CLASS_TEXT);
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> a){}
            public void onItemSelected(android.widget.AdapterView<?> a,View v,int index,long id){
                boolean hourly=index==0;start.setVisibility(hourly?View.VISIBLE:View.GONE);
                end.setVisibility(hourly?View.VISIBLE:View.GONE);duration.setVisibility(hourly?View.VISIBLE:View.GONE);
                ref.setVisibility(index==2?View.VISIBLE:View.GONE);
                to.setEnabled(!hourly);
                if(hourly)to.setText(from.getText().toString());
            }
        });
        action(c,"Guardar incidencia",TEAL,()->{
            demandAdmin();int index=type.getSelectedItemPosition(),emp=employees.optJSONObject(staff.getSelectedItemPosition()).optInt("id");
            String category=LEAVE_TYPES[index],first=validateDay(from.getText().toString());
            String last=index==0?first:validateDay(to.getText().toString());
            LocalDate d1=LocalDate.parse(first),d2=LocalDate.parse(last);
            if(d2.isBefore(d1)||d1.plusDays(119).isBefore(d2))throw new IllegalArgumentException("Selecciona un período válido, máximo 120 días");
            if(index==3 && d2.isAfter(today()))throw new IllegalArgumentException("Las ausencias injustificadas no se registran en fechas futuras");
            String why=reason.getText().toString().trim(),reference=ref.getText().toString().trim();
            if(why.length()<3)throw new IllegalArgumentException("Agrega un motivo administrativo (3 caracteres como mínimo)");
            int a=-1,b=-1;
            if(index==0){
                a=AttendanceRules.mins(start.getText().toString().trim());b=AttendanceRules.mins(end.getText().toString().trim());
                int shiftEnd=AttendanceRules.shiftEnd(d1);
                if(shiftEnd<0)throw new IllegalArgumentException("No se permiten intervalos horarios en domingo");
                if(a<AttendanceRules.START||b>shiftEnd||b<=a)
                    throw new IllegalArgumentException("Las horas del permiso deben estar dentro de la jornada: 08:30–"+AttendanceRules.hhmm(shiftEnd));
            }
            JSONObject employee=db.employee(emp);
            if(first.compareTo(employee.optString("joined"))<0 ||
                    (!employee.optString("ended").isEmpty() && last.compareTo(employee.optString("ended"))>0))
                throw new IllegalArgumentException("El período queda fuera de las fechas de vinculación del empleado");
            if(db.overlappingLeave(emp,first,last,a,b,index==0))throw new IllegalArgumentException("Existe otro permiso/incidencia que se cruza con este intervalo");
            if(index!=0){
                for(LocalDate d=d1;!d.isAfter(d2);d=d.plusDays(1)) {
                    if(db.punch(emp,d.toString())!=null)throw new IllegalArgumentException("Ya existe una marcación el "+labelDate(d.toString())+". Verifica antes de guardar una incidencia de día completo.");
                }
            }
            db.addLeave(emp,category,first,last,a,b,why,reference,actor,Instant.now().toString());
            toast("Incidencia guardada y auditada");screen="leave";render();
        });
        action(c,"Cancelar",NAVY,()->{screen="leave";render();});
        note(c,"IHSS: registra únicamente duración, fechas y referencia de la constancia. No almacenes diagnósticos médicos en observaciones. El sistema no valida por sí mismo documentos del IHSS.");
    }

    private static final class DayInfo {
        LocalDate date;JSONObject mark;AttendanceRules.Day rules;String leaveType="",leaveDescription="";
        boolean evaluated;
    }
    private DayInfo dayInfo(JSONObject staff,LocalDate day){
        DayInfo info=new DayInfo();info.date=day;
        String dayString=day.toString();int id=staff.optInt("id");
        info.mark=db.punch(id,dayString);
        JSONArray items=db.leaves(id,dayString);
        List<AttendanceRules.Window> windows=new ArrayList<>();
        for(int k=0;k<items.length();k++){
            JSONObject x=items.optJSONObject(k),o=x;
            String type=o.optString("type");
            if("PERMISO_HORAS".equals(type)) {
                windows.add(new AttendanceRules.Window(o.optInt("start_min"),o.optInt("end_min")));
                info.leaveDescription+=(info.leaveDescription.isEmpty()?"":"; ")+"Permiso "+AttendanceRules.hhmm(o.optInt("start_min"))+"-"+AttendanceRules.hhmm(o.optInt("end_min"));
            }else{info.leaveType=type;info.leaveDescription=leaveName(type);}
        }
        int close=AttendanceRules.shiftEnd(day);
        int now=LocalTime.now(HN).getHour()*60+LocalTime.now(HN).getMinute();
        info.evaluated=day.isBefore(today())||(day.equals(today())&&close>=0&&now>=close);
        info.rules=AttendanceRules.evaluate(day,info.mark==null?"":info.mark.optString("entry"),
            info.mark==null?"":info.mark.optString("exit"),db.holiday(dayString),info.leaveType,windows,info.evaluated);
        return info;
    }
    private static final class Metrics {
        int scheduled,excluded,expected,present,onTime,late,lateMinutes,early,earlyMinutes,afterMinutes,unjustified,missingExit;
        int hoursPermissions,daysPermissions,ihssDays,registeredUnjustified;
    }
    private Metrics metrics(JSONObject staff,String month){
        Metrics m=new Metrics();YearMonth ym=YearMonth.parse(month);
        LocalDate first=ym.atDay(1),last=ym.atEndOfMonth();
        LocalDate join=LocalDate.parse(staff.optString("joined"));
        LocalDate end=staff.optString("ended").isEmpty()?LocalDate.MAX:LocalDate.parse(staff.optString("ended"));
        for(LocalDate d=first;!d.isAfter(last);d=d.plusDays(1)){
            if(d.isBefore(join)||d.isAfter(end)||AttendanceRules.shiftEnd(d)<0)continue;
            DayInfo item=dayInfo(staff,d);
            if(!item.evaluated)continue;
            if(item.rules.excluded){m.excluded++;if("IHSS".equals(item.leaveType))m.ihssDays++;
                else if("PERMISO_DIA".equals(item.leaveType))m.daysPermissions++;continue;}
            m.scheduled++;m.expected+=item.rules.expectedMinutes;
            if(item.rules.authorizedMinutes>0)m.hoursPermissions+=item.rules.authorizedMinutes;
            if(item.rules.present){m.present++;if(item.rules.late){m.late++;m.lateMinutes+=item.rules.lateMinutes;}else m.onTime++;}
            if(item.rules.early){m.early++;m.earlyMinutes+=item.rules.earlyMinutes;}
            m.afterMinutes+=item.rules.afterMinutes;
            if(item.rules.missingExit)m.missingExit++;
            if(item.rules.unjustified)m.unjustified++;
            if("SIN_JUSTIFICAR".equals(item.leaveType))m.registeredUnjustified++;
        }
        return m;
    }
    private void monthPicker(LinearLayout target){
        LinearLayout c=horizontal();Button prev=button("‹",NAVY,()->{selectedMonth=YearMonth.parse(selectedMonth).minusMonths(1).toString();render();});
        TextView value=text(selectedMonth,17,NAVY,true);value.setGravity(Gravity.CENTER);
        Button next=button("›",NAVY,()->{selectedMonth=YearMonth.parse(selectedMonth).plusMonths(1).toString();render();});
        c.addView(prev,new LinearLayout.LayoutParams(dp(62),dp(46)));
        c.addView(value,new LinearLayout.LayoutParams(0,-2,1));
        c.addView(next,new LinearLayout.LayoutParams(dp(62),dp(46)));target.addView(c);
        note(target,"Mes de Honduras · los días aún no finalizados no se clasifican como ausencias.");
    }
    private void dashboardScreen(){
        heading("Dashboard de asistencia","Exclusivo del administrador · el sistema no mide productividad profesional.");
        LinearLayout picker=card();monthPicker(picker);
        JSONArray people=db.employees();
        int staffCount=0,planned=0,present=0,late=0,unjustified=0,ihss=0,permDays=0,authorizedMinutes=0,after=0;
        ArrayList<String> summaries=new ArrayList<>();
        YearMonth ym=YearMonth.parse(selectedMonth);
        for(int i=0;i<people.length();i++){
            JSONObject e=people.optJSONObject(i);
            if(LocalDate.parse(e.optString("joined")).isAfter(ym.atEndOfMonth()) ||
               (!e.optString("ended").isEmpty()&&LocalDate.parse(e.optString("ended")).isBefore(ym.atDay(1))))continue;
            Metrics m=metrics(e,selectedMonth);staffCount++;
            planned+=m.scheduled;present+=m.present;late+=m.late;unjustified+=m.unjustified;
            ihss+=m.ihssDays;permDays+=m.daysPermissions;authorizedMinutes+=m.hoursPermissions;after+=m.afterMinutes;
            summaries.add(e.optString("code")+" · "+e.optString("name")+"\nAsistencia "+percent(m.present,m.scheduled)+" | Tardanzas "+m.late+" | Ausencias "+m.unjustified+" | Posterior "+m.afterMinutes+" min");
        }
        LinearLayout totals=card();totals.addView(text("Indicadores del mes",18,NAVY,true));
        bigStat(totals,"Colaboradores",String.valueOf(staffCount));
        bigStat(totals,"Asistencia (días)",present+" / "+planned);
        bigStat(totals,"Cumplimiento",percent(present,planned));
        bigStat(totals,"Tardanzas",String.valueOf(late));
        bigStat(totals,"Ausencias sin justificar",String.valueOf(unjustified));
        bigStat(totals,"Días de incapacidad IHSS",String.valueOf(ihss));
        bigStat(totals,"Días de permisos completos",String.valueOf(permDays));
        bigStat(totals,"Permisos horarios autorizados",authorizedMinutes+" min");
        bigStat(totals,"Minutos posteriores al cierre",String.valueOf(after));
        note(totals,"Tiempo posterior al horario: información para análisis administrativo. NO se considera ni calcula aquí como horas extras.");
        for(String info:summaries){LinearLayout c=card();c.addView(text(info,14,NAVY,false));}
        LinearLayout end=card();action(end,"Generar informe consolidado (CSV)",TEAL,()->createCsv(true));
    }

    private int reportEmployeeId=-1;
    private void reportsScreen(){
        heading("Cierre y reportes mensuales","Solo administrador · detalle de entrada, salida, permisos e incapacidades.");
        LinearLayout picker=card();monthPicker(picker);
        JSONArray all=db.employees();if(all.length()==0){note(content,"Agrega colaboradores para generar reportes.");return;}
        LinearLayout choose=card();String[] names=new String[all.length()];int selected=0;
        for(int i=0;i<all.length();i++){JSONObject e=all.optJSONObject(i);names[i]=e.optString("code")+" · "+e.optString("name");if(e.optInt("id")==reportEmployeeId)selected=i;}
        Spinner spinner=spinner(choose,"Colaborador",names);spinner.setSelection(selected);
        action(choose,"Ver ficha del empleado",NAVY,()->{reportEmployeeId=all.optJSONObject(spinner.getSelectedItemPosition()).optInt("id");render();});
        action(choose,"Exportar ficha mensual CSV",TEAL,()->{reportEmployeeId=all.optJSONObject(spinner.getSelectedItemPosition()).optInt("id");createCsv(false);});
        action(choose,"Exportar consolidado mensual CSV",NAVY,()->createCsv(true));
        if(reportEmployeeId<0)reportEmployeeId=all.optJSONObject(0).optInt("id");
        JSONObject employee=db.employee(reportEmployeeId);
        if(employee==null){reportEmployeeId=all.optJSONObject(0).optInt("id");employee=db.employee(reportEmployeeId);}
        final JSONObject reportedEmployee=employee;
        Metrics m=metrics(employee,selectedMonth);LinearLayout c=card();
        c.addView(text(employee.optString("code")+" · "+employee.optString("name"),17,NAVY,true));
        bigStat(c,"Jornadas evaluadas",String.valueOf(m.scheduled));
        bigStat(c,"Asistencias",String.valueOf(m.present));
        bigStat(c,"Puntualidad",percent(m.onTime,m.present));
        bigStat(c,"Tardanzas / minutos",m.late+" / "+m.lateMinutes);
        bigStat(c,"Ausencias sin justificar",String.valueOf(m.unjustified));
        bigStat(c,"Permisos completos",String.valueOf(m.daysPermissions));
        bigStat(c,"Días por incapacidad IHSS",String.valueOf(m.ihssDays));
        bigStat(c,"Permisos por horas (min)",String.valueOf(m.hoursPermissions));
        bigStat(c,"Salidas anticipadas / min",m.early+" / "+m.earlyMinutes);
        bigStat(c,"Salidas sin marcación",String.valueOf(m.missingExit));
        bigStat(c,"Minutos después del cierre",String.valueOf(m.afterMinutes));
        note(c,"Los permisos horarios se descuentan solo por su intervalo. Las incapacidades y permisos de día completo quedan fuera del denominador de asistencia. El tiempo posterior al cierre no es una liquidación de horas extras.");
        for(LocalDate date=YearMonth.parse(selectedMonth).atDay(1);!date.isAfter(YearMonth.parse(selectedMonth).atEndOfMonth());date=date.plusDays(1)){
            if(date.isAfter(today())||AttendanceRules.shiftEnd(date)<0)continue;
            if(date.isBefore(LocalDate.parse(employee.optString("joined")))||(!employee.optString("ended").isEmpty()&&date.isAfter(LocalDate.parse(employee.optString("ended")))))continue;
            DayInfo d=dayInfo(employee,date);LinearLayout r=card();
            r.addView(text(labelDate(date.toString())+" · "+d.rules.state.replace('_',' '),15,NAVY,true));
            note(r,"Entrada: "+timeOf(d.mark==null?"":d.mark.optString("entry"))+" · Salida: "+timeOf(d.mark==null?"":d.mark.optString("exit")));
            if(!d.leaveDescription.isEmpty())note(r,d.leaveDescription);
            if(d.rules.lateMinutes>0||d.rules.earlyMinutes>0||d.rules.afterMinutes>0)
                note(r,"Tardanza: "+d.rules.lateMinutes+" min · Salida anticipada: "+d.rules.earlyMinutes+" min · Después del cierre: "+d.rules.afterMinutes+" min");
            if(d.mark!=null)action(r,"Corregir marcación (auditoría)",NAVY,()->correctForm(reportedEmployee,d.mark));
        }
    }

    private void correctForm(JSONObject employee,JSONObject record){
        demandAdmin();layout("Rectificaciones · administrador");
        heading("Corregir marcación",employee.optString("code")+" · "+employee.optString("name")+" · "+labelDate(record.optString("day")));
        LinearLayout c=card();EditText entry=edit(c,"Entrada (HH:mm)",timeOf(record.optString("entry")),InputType.TYPE_CLASS_DATETIME|InputType.TYPE_DATETIME_VARIATION_TIME);
        EditText exit=edit(c,"Salida (HH:mm; vacío si no está registrada)",record.optString("exit").isEmpty()?"":timeOf(record.optString("exit")),InputType.TYPE_CLASS_DATETIME|InputType.TYPE_DATETIME_VARIATION_TIME);
        EditText reason=edit(c,"Motivo obligatorio para corregir","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        action(c,"Guardar corrección y motivo",TEAL,()->{
            String a=entry.getText().toString().trim(),b=exit.getText().toString().trim(),why=reason.getText().toString().trim();
            if(why.length()<5)throw new IllegalArgumentException("Indica un motivo de al menos 5 caracteres");
            int i=AttendanceRules.mins(a),j=b.isEmpty()?-1:AttendanceRules.mins(b);
            if(i<0||(!b.isEmpty()&&j<=i))throw new IllegalArgumentException("La salida debe ser posterior a la entrada. Usa HH:mm");
            db.correctPunch(record.optInt("id"),AttendanceRules.hhmm(i),b.isEmpty()?"":AttendanceRules.hhmm(j),why,actor);
            screen="reports";render();toast("Corrección registrada con auditoría");
        });
        action(c,"Cancelar",NAVY,()->{screen="reports";render();});
    }
    private static String csv(String value){
        String safe=value==null?"":value;
        // Evita inyección de fórmulas al abrir CSV en programas de hojas de cálculo.
        if(!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0))>=0)safe="'"+safe;
        return "\""+safe.replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\"";
    }
    private static void csvLine(StringBuilder b,String... values){
        for(int i=0;i<values.length;i++){if(i>0)b.append(';');b.append(csv(values[i]));}b.append("\r\n");
    }
    private void createCsv(boolean consolidated){
        demandAdmin();JSONArray staff=consolidated?db.employees():db.query("SELECT * FROM employees WHERE id=?",String.valueOf(reportEmployeeId));
        if(staff.length()==0)throw new IllegalArgumentException("No hay colaboradores para exportar");
        StringBuilder s=new StringBuilder("\ufeff");
        csvLine(s,"Código","Colaborador","Área","Fecha","Tipo de incidencia","Estado","Entrada","Salida","Minutos permiso horario","Tardanza min","Salida anticipada min","Posterior al cierre min","Sin justificar (sí/no)");
        YearMonth ym=YearMonth.parse(selectedMonth);
        for(int i=0;i<staff.length();i++){
            JSONObject person=staff.optJSONObject(i);
            for(LocalDate d=ym.atDay(1);!d.isAfter(ym.atEndOfMonth());d=d.plusDays(1)) {
                if(d.isAfter(today())||AttendanceRules.shiftEnd(d)<0)continue;
                if(d.isBefore(LocalDate.parse(person.optString("joined")))||(!person.optString("ended").isEmpty()&&d.isAfter(LocalDate.parse(person.optString("ended")))))continue;
                DayInfo day=dayInfo(person,d);
                csvLine(s,person.optString("code"),person.optString("name"),person.optString("area"),d.toString(),
                        day.leaveDescription,day.rules.state,
                        timeOf(day.mark==null?"":day.mark.optString("entry")),timeOf(day.mark==null?"":day.mark.optString("exit")),
                        String.valueOf(day.rules.authorizedMinutes),String.valueOf(day.rules.lateMinutes),String.valueOf(day.rules.earlyMinutes),
                        String.valueOf(day.rules.afterMinutes),day.rules.unjustified?"Sí":"No");
            }
        }
        pendingExport=s.toString();
        String filename="CAH_"+(consolidated?"Consolidado":("Empleado_"+reportEmployeeId))+"_"+selectedMonth+".csv";
        documentSave(EXPORT_CSV,"text/csv",filename);
    }

    private void adminScreen(){
        heading("Administración","Cuenta administradora · feriados, contraseñas, correcciones, auditoría y respaldos.");
        LinearLayout hol=card();hol.addView(text("Calendario de feriados",17,NAVY,true));
        EditText day=date(hol,"Fecha del feriado",todayStr());
        EditText why=edit(hol,"Descripción", "",InputType.TYPE_CLASS_TEXT);
        action(hol,"Registrar feriado",TEAL,()->{
            demandAdmin();String d=validateDay(day.getText().toString()),r=why.getText().toString().trim();
            if(r.length()<3)throw new IllegalArgumentException("Escribe una descripción válida");
            db.addHoliday(d,r,actor);render();toast("Feriado registrado");
        });
        JSONArray holidays=db.query("SELECT * FROM holidays ORDER BY day DESC LIMIT 40");
        for(int i=0;i<holidays.length();i++){
            JSONObject h=holidays.optJSONObject(i);
            note(hol,labelDate(h.optString("day"))+" · "+h.optString("reason"));
            Button del=button("Quitar feriado",RED,()->new AlertDialog.Builder(this).setMessage("¿Quitar el feriado "+labelDate(h.optString("day"))+"?")
                .setNegativeButton("Cancelar",null).setPositiveButton("Quitar",(dialog,w)->{
                    try{db.deleteHoliday(h.optInt("id"),actor);render();}catch(Exception ex){error(ex);}
                }).show());hol.addView(del);
        }
        LinearLayout passwords=card();passwords.addView(text("Cuentas de acceso",17,NAVY,true));
        JSONArray users=db.query("SELECT username,role FROM users ORDER BY role");
        String[] names=new String[users.length()];for(int i=0;i<users.length();i++)names[i]=users.optJSONObject(i).optString("username")+" ("+users.optJSONObject(i).optString("role")+")";
        Spinner person=spinner(passwords,"Cambiar contraseña de",names);
        EditText pass=secure(passwords,"Nueva contraseña (8+ caracteres)");
        action(passwords,"Cambiar contraseña",NAVY,()->{
            demandAdmin();String secret=pass.getText().toString();
            if(secret.length()<8)throw new IllegalArgumentException("Debe tener como mínimo 8 caracteres");
            JSONObject u=users.optJSONObject(person.getSelectedItemPosition());db.resetUserPassword(u.optString("username"),secret,actor);
            toast("Contraseña actualizada");pass.setText("");
        });
        LinearLayout backup=card();backup.addView(text("Respaldo local cifrado",17,NAVY,true));
        note(backup,"Exporta un archivo .cahbackup protegido mediante AES-256-GCM. Incluye datos de empleados, autorizaciones IHSS, historial, cuentas y contraseñas verificadas (hashes). Conserva el archivo y su clave bajo custodia institucional.");
        action(backup,"Exportar copia cifrada",TEAL,()->backupPassword(false));
        action(backup,"Restaurar copia cifrada",NAVY,()->backupPassword(true));
        note(backup,"Al restaurar se REEMPLAZAN todos los datos del dispositivo y se cierra la sesión. Los respaldos de la PWA anterior no son compatibles con este formato nativo.");
        LinearLayout audit=card();audit.addView(text("Bitácora de cambios",17,NAVY,true));
        JSONArray rows=db.query("SELECT * FROM audit ORDER BY id DESC LIMIT 80");
        for(int i=0;i<rows.length();i++){
            JSONObject a=rows.optJSONObject(i);note(audit,a.optString("at")+"\n"+a.optString("actor")+" · "+a.optString("action")+"\n"+a.optString("description"));
        }
    }
    private String importPassword="";
    private void backupPassword(boolean importFile){
        demandAdmin();LinearLayout view=vertical();pad(view,22,7,22,5);
        EditText pwd=secure(view,"Contraseña del respaldo (mínimo 12 caracteres)");
        EditText repeat=importFile?null:secure(view,"Repetir contraseña del respaldo");
        new AlertDialog.Builder(this).setTitle(importFile?"Abrir copia de seguridad":"Cifrar copia de seguridad")
                .setView(view).setNegativeButton("Cancelar",null).setPositiveButton("Continuar",(dialog,w)->{
                    try {
                        demandAdmin();String p=pwd.getText().toString();
                        if(p.length()<12)throw new IllegalArgumentException("La contraseña del respaldo requiere 12 caracteres como mínimo");
                        if(!importFile && !p.equals(repeat.getText().toString()))throw new IllegalArgumentException("Las contraseñas no coinciden");
                        if(importFile){importPassword=p;documentOpen();}
                        else {pendingExport=BackupCrypto.encrypt(db.dump().toString(),p);documentSave(EXPORT_BACKUP,"application/octet-stream","CAH_Respaldo_"+todayStr()+".cahbackup");}
                    }catch(Exception ex){error(ex);}
                }).show();
    }
    private void documentSave(int request,String mime,String title){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.setType(mime);
        intent.addCategory(Intent.CATEGORY_OPENABLE);intent.putExtra(Intent.EXTRA_TITLE,title);
        try{startActivityForResult(intent,request);}catch(ActivityNotFoundException e){throw new IllegalStateException("No se encontró un gestor de documentos");}
    }
    private void documentOpen(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(intent,IMPORT_BACKUP);}catch(ActivityNotFoundException e){throw new IllegalStateException("No se encontró un gestor de archivos");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null){pendingExport="";importPassword="";return;}
        Uri uri=data.getData();
        try {
            demandAdmin();
            if(request==EXPORT_CSV||request==EXPORT_BACKUP){
                try(OutputStream stream=getContentResolver().openOutputStream(uri,"w")){
                    if(stream==null)throw new IllegalStateException("No se pudo escribir archivo");
                    stream.write(pendingExport.getBytes(StandardCharsets.UTF_8));stream.flush();
                }
                if(request==EXPORT_BACKUP)db.audit(actor,"RESPALDO_EXPORTADO","Se generó una copia cifrada manual");
                toast("Archivo guardado correctamente");
            }else if(request==IMPORT_BACKUP){
                ByteArrayOutputStream output=new ByteArrayOutputStream();
                try(InputStream stream=getContentResolver().openInputStream(uri)){
                    if(stream==null)throw new IllegalStateException("No se pudo leer archivo");
                    byte[] chunk=new byte[8192];int n;
                    while((n=stream.read(chunk))!=-1){output.write(chunk,0,n);if(output.size()>20*1024*1024)throw new IllegalArgumentException("Respaldo supera 20 MB");}
                }
                JSONObject payload=BackupCrypto.decrypt(output.toString("UTF-8"),importPassword);
                new AlertDialog.Builder(this).setTitle("Confirmar restauración")
                    .setMessage("Los datos actuales serán reemplazados por el contenido del respaldo. Esta operación no se puede deshacer. ¿Continuar?")
                    .setNegativeButton("Cancelar",null)
                    .setPositiveButton("Restaurar",(dialog,w)->{
                        try{demandAdmin();db.restore(payload,actor);logout("Respaldo restaurado. Inicia sesión con las credenciales del respaldo.");}
                        catch(Exception ex){error(ex);}
                    }).show();
            }
        }catch(Exception ex){error(ex);}
        finally{pendingExport="";importPassword="";}
    }
}