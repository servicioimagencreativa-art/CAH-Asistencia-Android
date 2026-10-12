package hn.org.cah.asistencia;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;
import java.util.Base64;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Almacenamiento privado del teléfono. Solo la actividad autentica roles y accede a este helper. */
public final class CahDb extends SQLiteOpenHelper {
    private static final String DB="cah_asistencia_native.db";
    private static final String[] TABLES={"users","employees","punches","leaves","holidays","audit"};
    private static final SecureRandom RAND=new SecureRandom();
    public CahDb(Context context) { super(context, DB, null, 1); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE COLLATE NOCASE, role TEXT NOT NULL CHECK(role IN ('admin','operator')), salt TEXT NOT NULL, hash TEXT NOT NULL)");
        db.execSQL("CREATE TABLE employees (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT UNIQUE, name TEXT NOT NULL, area TEXT NOT NULL DEFAULT '', position TEXT NOT NULL DEFAULT '', joined TEXT NOT NULL, ended TEXT NOT NULL DEFAULT '', active INTEGER NOT NULL DEFAULT 1, pin_salt TEXT NOT NULL, pin_hash TEXT NOT NULL)");
        db.execSQL("CREATE TABLE punches (id INTEGER PRIMARY KEY AUTOINCREMENT, employee_id INTEGER NOT NULL REFERENCES employees(id), day TEXT NOT NULL, entry TEXT NOT NULL DEFAULT '', exit TEXT NOT NULL DEFAULT '', entry_utc INTEGER NOT NULL DEFAULT 0, exit_utc INTEGER NOT NULL DEFAULT 0, entry_by TEXT NOT NULL DEFAULT '', exit_by TEXT NOT NULL DEFAULT '', UNIQUE(employee_id,day))");
        db.execSQL("CREATE TABLE leaves (id INTEGER PRIMARY KEY AUTOINCREMENT, employee_id INTEGER NOT NULL REFERENCES employees(id), type TEXT NOT NULL CHECK(type IN ('PERMISO_HORAS','PERMISO_DIA','IHSS','SIN_JUSTIFICAR')), from_day TEXT NOT NULL, to_day TEXT NOT NULL, start_min INTEGER NOT NULL DEFAULT -1, end_min INTEGER NOT NULL DEFAULT -1, reason TEXT NOT NULL, reference TEXT NOT NULL DEFAULT '', created_by TEXT NOT NULL, created_at TEXT NOT NULL)");
        db.execSQL("CREATE TABLE holidays (id INTEGER PRIMARY KEY AUTOINCREMENT, day TEXT NOT NULL UNIQUE, reason TEXT NOT NULL)");
        db.execSQL("CREATE TABLE audit (id INTEGER PRIMARY KEY AUTOINCREMENT, at TEXT NOT NULL, actor TEXT NOT NULL, action TEXT NOT NULL, description TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_leave_staff_days ON leaves(employee_id,from_day,to_day)");
        db.execSQL("CREATE INDEX idx_punch_staff_day ON punches(employee_id,day)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        throw new IllegalStateException("Se requiere una migración antes de actualizar la base de datos");
    }

    public static String salt() { byte[] b=new byte[16];RAND.nextBytes(b);return Base64.getEncoder().encodeToString(b); }
    public static String hash(String secret,String salt) {
        PBEKeySpec spec=new PBEKeySpec(secret.toCharArray(),Base64.getDecoder().decode(salt),180000,256);
        try {
            byte[] r=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(r);
        } catch(Exception e) {throw new IllegalStateException("No se pudo crear el verificador",e);}
        finally {spec.clearPassword();}
    }
    public static boolean verify(String secret,String salt,String hashed) {
        try {return java.security.MessageDigest.isEqual(
            Base64.getDecoder().decode(hash(secret,salt)),Base64.getDecoder().decode(hashed));}
        catch (Exception e) {return false;}
    }
    private static JSONObject row(Cursor c) {
        JSONObject o=new JSONObject();
        try {
            for(int i=0;i<c.getColumnCount();i++) {
                if(c.isNull(i))o.put(c.getColumnName(i),JSONObject.NULL);
                else if(c.getType(i)==Cursor.FIELD_TYPE_INTEGER)o.put(c.getColumnName(i),c.getLong(i));
                else if(c.getType(i)==Cursor.FIELD_TYPE_FLOAT)o.put(c.getColumnName(i),c.getDouble(i));
                else o.put(c.getColumnName(i),c.getString(i));
            }
        }catch(JSONException e) {throw new IllegalStateException(e);}
        return o;
    }
    public JSONArray query(String sql,String... args) {
        JSONArray a=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery(sql,args)) { while(c.moveToNext())a.put(row(c)); }
        return a;
    }
    public JSONObject one(String sql,String... args) {
        JSONArray a=query(sql,args);return a.length()>0?a.optJSONObject(0):null;
    }
    public boolean initialized() { return one("SELECT id FROM users LIMIT 1")!=null; }
    public JSONObject user(String username) {return one("SELECT * FROM users WHERE username=?",username);}
    public JSONObject employee(int id) {return one("SELECT * FROM employees WHERE id=?",String.valueOf(id));}
    public JSONObject employeeCode(String code) {return one("SELECT * FROM employees WHERE code=?",code);}
    public JSONArray employees() {return query("SELECT * FROM employees ORDER BY id");}
    public JSONArray activeEmployees() {return query("SELECT * FROM employees WHERE active=1 ORDER BY id");}
    public JSONObject punch(int staffId,String day) {return one("SELECT * FROM punches WHERE employee_id=? AND day=?",String.valueOf(staffId),day);}
    public JSONArray leaves(int staffId,String day) {return query("SELECT * FROM leaves WHERE employee_id=? AND from_day<=? AND to_day>=? ORDER BY id",String.valueOf(staffId),day,day);}
    public JSONArray leavesRecent() {return query("SELECT l.*,e.code,e.name FROM leaves l JOIN employees e ON e.id=l.employee_id ORDER BY l.created_at DESC,l.id DESC LIMIT 250");}
    public boolean holiday(String day) {return one("SELECT id FROM holidays WHERE day=?",day)!=null;}
    public void addUser(String name,String role,String password) {
        String s=salt();ContentValues v=new ContentValues();
        v.put("username",name);v.put("role",role);v.put("salt",s);v.put("hash",hash(password,s));
        getWritableDatabase().insertOrThrow("users",null,v);
    }
    public void setup(String admin,String adminPassword,String operator,String operatorPassword) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            if(initialized())throw new IllegalStateException("La configuración inicial ya se realizó");
            addUser(admin,"admin",adminPassword);addUser(operator,"operator",operatorPassword);
            auditTx(db,"sistema","CONFIGURACIÓN_INICIAL","Cuentas administrador y usuario asignado creadas");
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    public void resetUserPassword(String username,String newPassword,String actor) {
        String s=salt();ContentValues v=new ContentValues();v.put("salt",s);v.put("hash",hash(newPassword,s));
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            if(db.update("users",v,"username=?",new String[]{username})!=1)throw new IllegalArgumentException("Usuario inexistente");
            auditTx(db,actor,"CLAVE_ACTUALIZADA","Contraseña restablecida para "+username);
            db.setTransactionSuccessful();
        }finally {db.endTransaction();}
    }
    public String createEmployee(String name,String area,String position,String joined,String pin,String actor) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            String s=salt();ContentValues v=new ContentValues();
            v.put("name",name);v.put("area",area);v.put("position",position);v.put("joined",joined);
            v.put("pin_salt",s);v.put("pin_hash",hash(pin,s));
            long id=db.insertOrThrow("employees",null,v);
            String code=String.format(Locale.US,"CAH-%04d",id);
            ContentValues update=new ContentValues();update.put("code",code);
            db.update("employees",update,"id=?",new String[]{String.valueOf(id)});
            auditTx(db,actor,"ALTA_COLABORADOR",code+" · "+name);
            db.setTransactionSuccessful();return code;
        }finally {db.endTransaction();}
    }
    public void updateEmployee(int id,String name,String area,String position,String joined,String ended,boolean active,String optionalPin,String actor) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ContentValues v=new ContentValues();v.put("name",name);v.put("area",area);v.put("position",position);
            v.put("joined",joined);v.put("ended",ended);v.put("active",active?1:0);
            if(!optionalPin.isEmpty()){String s=salt();v.put("pin_salt",s);v.put("pin_hash",hash(optionalPin,s));}
            if(db.update("employees",v,"id=?",new String[]{String.valueOf(id)})!=1)throw new IllegalArgumentException("Colaborador inexistente");
            auditTx(db,actor,"EDICIÓN_COLABORADOR","ID "+id+" · activo="+active+" · "+name+" · PIN cambiado="+(!optionalPin.isEmpty()));
            db.setTransactionSuccessful();
        }finally {db.endTransaction();}
    }
    public String punchNow(int id,String day,String time,long instant,boolean entering,String actor) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            JSONObject p=punch(id,day);
            if(entering) {
                if(p!=null && !p.optString("entry").isEmpty())throw new IllegalStateException("Entrada ya registrada hoy");
                if(p!=null)throw new IllegalStateException("Registro previo inconsistente; revise con administración");
                ContentValues v=new ContentValues();v.put("employee_id",id);v.put("day",day);
                v.put("entry",time);v.put("entry_utc",instant);v.put("entry_by",actor);
                db.insertOrThrow("punches",null,v);
            }else {
                if(p==null || p.optString("entry").isEmpty())throw new IllegalStateException("Primero debe registrar la entrada");
                if(!p.optString("exit").isEmpty())throw new IllegalStateException("Salida ya registrada hoy");
                if(instant<=p.optLong("entry_utc"))throw new IllegalStateException("La salida no puede ser anterior a la entrada");
                ContentValues v=new ContentValues();v.put("exit",time);v.put("exit_utc",instant);v.put("exit_by",actor);
                db.update("punches",v,"id=?",new String[]{String.valueOf(p.optInt("id"))});
            }
            String result=entering?"ENTRADA":"SALIDA";
            auditTx(db,actor,result,"ID "+id+" · "+day+" "+time);
            db.setTransactionSuccessful();return result;
        }finally {db.endTransaction();}
    }
    public boolean overlappingLeave(int employeeId,String from,String to,int a,int b,boolean hourly) {
        JSONArray found=query("SELECT * FROM leaves WHERE employee_id=? AND from_day<=? AND to_day>=?",String.valueOf(employeeId),to,from);
        for(int i=0;i<found.length();i++) {
            JSONObject x=found.optJSONObject(i);
            if(!hourly || !"PERMISO_HORAS".equals(x.optString("type")))return true;
            if(Math.max(a,x.optInt("start_min")) < Math.min(b,x.optInt("end_min")))return true;
        }
        return false;
    }
    public void addLeave(int employeeId,String type,String from,String to,int start,int end,String reason,String reference,String actor,String timestamp) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ContentValues v=new ContentValues();v.put("employee_id",employeeId);v.put("type",type);
            v.put("from_day",from);v.put("to_day",to);v.put("start_min",start);v.put("end_min",end);
            v.put("reason",reason);v.put("reference",reference);v.put("created_by",actor);v.put("created_at",timestamp);
            db.insertOrThrow("leaves",null,v);
            auditTx(db,actor,"INCIDENCIA_AGREGADA","ID "+employeeId+" · "+type+" · "+from+" a "+to+" · "+(start>=0?AttendanceRules.hhmm(start)+"-"+AttendanceRules.hhmm(end):"día(s) completo(s)")+" · "+reason+" · ref:"+reference);
            db.setTransactionSuccessful();
        }finally {db.endTransaction();}
    }
    public void deleteLeave(int id,String actor) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            JSONObject old=one("SELECT * FROM leaves WHERE id=?",String.valueOf(id));
            if(old==null)throw new IllegalStateException("Registro no encontrado");
            db.delete("leaves","id=?",new String[]{String.valueOf(id)});
            auditTx(db,actor,"INCIDENCIA_ANULADA",old.toString());
            db.setTransactionSuccessful();
        }finally {db.endTransaction();}
    }
    public void addHoliday(String day,String reason,String actor) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {ContentValues v=new ContentValues();v.put("day",day);v.put("reason",reason);
            db.insertOrThrow("holidays",null,v);
            auditTx(db,actor,"FERIADO_AGREGADO",day+" · "+reason);db.setTransactionSuccessful();
        }finally {db.endTransaction();}
    }
    public void deleteHoliday(int id,String actor) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            JSONObject x=one("SELECT * FROM holidays WHERE id=?",String.valueOf(id));
            if(x==null)throw new IllegalStateException("Feriado inexistente");
            db.delete("holidays","id=?",new String[]{String.valueOf(id)});
            auditTx(db,actor,"FERIADO_ANULADO",x.toString()); db.setTransactionSuccessful();
        }finally {db.endTransaction();}
    }
    public void audit(String actor,String action,String description) {
        auditTx(getWritableDatabase(),actor,action,description);
    }
    private static void auditTx(SQLiteDatabase db,String actor,String action,String description) {
        ContentValues v=new ContentValues();v.put("at",java.time.Instant.now().toString());
        v.put("actor",actor);v.put("action",action);v.put("description",description);
        db.insertOrThrow("audit",null,v);
    }
    public void correctPunch(int id,String entry,String exit,String reason,String actor) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            JSONObject old=one("SELECT * FROM punches WHERE id=?",String.valueOf(id));
            if(old==null)throw new IllegalArgumentException("Marcación no encontrada");
            ContentValues v=new ContentValues();v.put("entry",entry);v.put("exit",exit);
            // Los instantes históricos originales no se sobrescriben; se mantiene auditoría de edición.
            if(db.update("punches",v,"id=?",new String[]{String.valueOf(id)})!=1)
                throw new IllegalStateException("No se guardó la corrección");
            auditTx(db,actor,"CORRECCIÓN_MARCACIÓN","Antes: "+old.toString()+"; después: entrada="+entry+", salida="+exit+"; motivo: "+reason);
            db.setTransactionSuccessful();
        }finally {db.endTransaction();}
    }
    public JSONObject dump() {
        JSONObject root=new JSONObject();
        try {root.put("format","CAH_NATIVE_DATA_V1");root.put("version",1);
            for(String table:TABLES)root.put(table,query("SELECT * FROM "+table));
        }catch(JSONException e){throw new IllegalStateException(e);}
        return root;
    }
    /** Solo se restaura formato validado; operación atómica y conservando auditoría de restauración. */
    public void restore(JSONObject payload,String actor) {
        if(!"CAH_NATIVE_DATA_V1".equals(payload.optString("format"))||payload.optInt("version")!=1)
            throw new IllegalArgumentException("Formato de respaldo no compatible");
        for(String t:TABLES)if(payload.optJSONArray(t)==null)throw new IllegalArgumentException("Falta colección "+t);
        // Protege de archivos absurdamente grandes antes de reemplazar toda la base.
        for(String t:TABLES)if(payload.optJSONArray(t).length()>100000)throw new IllegalArgumentException("Archivo demasiado grande");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(int i=TABLES.length-1;i>=0;i--)db.delete(TABLES[i],null,null);
            for(String t:TABLES) {
                JSONArray rows=payload.optJSONArray(t);
                for(int i=0;i<rows.length();i++) {
                    JSONObject r=rows.optJSONObject(i);
                    if(r==null)throw new IllegalArgumentException("Fila inválida en "+t);
                    ContentValues v=new ContentValues();
                    java.util.Iterator<String> keys=r.keys();
                    while(keys.hasNext()) {
                        String key=keys.next();
                        if(!key.matches("[a-z_]+"))throw new IllegalArgumentException("Campo inesperado");
                        Object value=r.opt(key);
                        if(value==null || JSONObject.NULL.equals(value))v.putNull(key);
                        else if(value instanceof Integer)v.put(key,(Integer)value);
                        else if(value instanceof Long)v.put(key,(Long)value);
                        else if(value instanceof Double)v.put(key,(Double)value);
                        else if(value instanceof Boolean)v.put(key,(Boolean)value?1:0);
                        else if(value instanceof String)v.put(key,(String)value);
                        else throw new IllegalArgumentException("Valor inválido en respaldo");
                    }
                    db.insertOrThrow(t,null,v);
                }
            }
            if(one("SELECT id FROM users WHERE role='admin' LIMIT 1")==null ||
               one("SELECT id FROM users WHERE role='operator' LIMIT 1")==null)
                throw new IllegalArgumentException("El respaldo no contiene los dos perfiles necesarios");
            auditTx(db,actor,"RESTAURACIÓN","Respaldo cifrado importado y verificado");
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
}