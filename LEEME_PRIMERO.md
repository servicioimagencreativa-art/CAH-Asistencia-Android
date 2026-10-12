# CAH Control de Asistencia — Android NATIVO 2.0

**Colegio de Arquitectos de Honduras · Proyecto Java para Android Studio.**

> **Importante:** Este paquete es el proyecto fuente nativo, **NO un APK compilado ni probado en un teléfono real**. Aquí no se dispone del SDK de Android ni de Gradle, por lo que no se pudo generar el instalador. El módulo de cálculo pasó 9 pruebas automatizadas Java y los archivos Java pasaron una comprobación de compilación usando interfaces simuladas; esta verificación NO sustituye compilar con el SDK real ni probar en Android.

## Qué contiene

- **App nativa real:** Activity, controles Android y SQLiteOpenHelper; **no usa WebView, HTML, Chrome, PWA ni conexión permanente**.
- **Autenticación:** primera ejecución crea `administrador` y `usuario asignado` con contraseñas definidas por el CAH. **No hay contraseña fija predeterminada.** Bloqueo de 15 minutos tras cinco intentos erróneos, por cuenta/código.
- **Operador asignado:** pantalla de entrada/salida con código CAH-0001, CAH-0002... y PIN único de 4–6 cifras. No muestra permisos, IHSS, KPI ni dashboard.
- **Administrador:** colaboradores, altas/bajas, restablecer PIN, marcaciones, incidencias, feriados, corrección auditada, métricas individuales y globales, reportes CSV y respaldo/restauración cifrado.
- **Base de datos SQLite privada del teléfono.** Se conserva hasta que se desinstala/borran datos o se pierde el dispositivo. Sin sincronización entre celulares.
- **Respaldo manual:** archivo `.cahbackup` protegido por contraseña >=12 caracteres, cifrado AES-256-GCM con clave derivada PBKDF2-SHA-256. El archivo no debe extraviarse y la clave no puede recuperarse automáticamente.

## Compilar el APK en Windows, Mac o Linux usando Android Studio

1. Instala **Android Studio** desde https://developer.android.com/studio.
2. Abre `File > Open` y selecciona la carpeta `CAH_Asistencia_Android_Nativo` que contiene `settings.gradle`. **No abras solamente `app/`.**
3. Acepta la configuración/sincronización Gradle. Requiere Internet para descargar las dependencias oficiales: **Android Gradle Plugin 8.7.3, Gradle 8.9, JDK 17, Android SDK API 35**. Android Studio puede ofrecer instalar estos componentes. Este ZIP no incorpora el `gradle-wrapper.jar`; si Android Studio requiere distribución Gradle, usa una instalación local de **Gradle 8.9** y selecciona esa distribución en las opciones de Gradle (o ejecuta `gradle wrapper --gradle-version 8.9` en la carpeta raíz antes de sincronizar).
4. Abre `Build > Build Bundle(s) / APK(s) > Build APK(s)` (el nombre puede variar con la versión).
5. Resultado esperado: `app/build/outputs/apk/debug/app-debug.apk`.
6. Copia ese archivo a un móvil Android **8.0 o posterior** e instálalo. Android puede pedir permiso para instalar desde esa fuente. En producción utiliza un **APK firmado de lanzamiento** con clave institucional; el APK de depuración es solo para pruebas.

## Opción alternativa: GitHub Actions

Si sabes utilizar GitHub, publica el proyecto en un **repositorio privado** y ejecuta manualmente el flujo de `.github/workflows/compilar-apk.yml` desde la pestaña **Actions**. El flujo instala Android SDK, Gradle y compila un APK de depuración; después permite descargarlo como artefacto. Este procedimiento tampoco se ha ejecutado aquí y depende del servicio GitHub.

## Cómo empezar desde cero

1. Al abrir por primera vez define nombre y contraseña del **administrador** y del **usuario asignado** (ambas al menos 8 caracteres).
2. Entra como administrador y visita **Colaboradores**; agrega nombre, departamento, cargo, fecha de ingreso y PIN. El sistema asigna automáticamente el código `CAH-0001`, seguido por `CAH-0002`.
3. Para marcar, usa la pantalla **Marcación**, coloca el número y el PIN; selecciona **Entrada** o **Salida**. Solo se permite una entrada y una salida por día y se verifica el horario del dispositivo.
4. En **Permisos / IHSS**, registra incidencias **solo con la cuenta de administrador**.
5. En **Dashboard** consulta porcentajes, faltas, puntualidad, minutos posteriores al cierre, IHSS y permisos. En **Reportes**, selecciona mes y colaborador; exporta el CSV individual o consolidado.
6. En **Administración** registra feriados, gestiona contraseñas, exporta respaldos cifrados y revisa bitácora.

## Módulo de permisos e incidencias (administrador)

| Tipo | Qué pide | Efecto en asistencia mensual |
| --- | --- | --- |
| **Permiso por horas** | Colaborador, fecha, `HH:mm` desde/hasta y motivo. Calcula minutos. | Solo descontará los minutos autorizados del tramo programado. **No** convierte un permiso parcial en día entero de ausencia justificada. |
| **Permiso por día completo** | Fechas desde/hasta y motivo; intervalo de hasta 120 días. | Cada jornada cubierta se registra como **permiso de día completo** y se excluye del cálculo de ausencias injustificadas. |
| **Incapacidad IHSS** | Fechas desde/hasta, motivo administrativo y referencia opcional de constancia **sin diagnóstico**. | Cada jornada cubierta queda identificada como **incapacidad IHSS** y excluida de ausencias injustificadas. El software **no valida** certificados del IHSS. |
| **Ausencia sin justificar** | Fecha(s), trabajador y observación obligatoria. | La jornada queda identificada como **ausencia sin justificar**. Los días anteriores sin marcación ni permiso también se cuentan como ausencias potenciales, sujetas a revisión. |

Se impiden permisos que se solapen; no se puede registrar una incidencia de día completo sobre una marcación existente. Las anulaciones y correcciones registran quién, cuándo y el motivo/estado anterior; la bitácora es editable por alguien con acceso técnico a la base de datos, no es un registro criptográficamente inalterable. Las incapacidades pueden abarcar domingos, pero solo las jornadas programadas afectan al KPI.

## Horarios y metodología

| Día | Inicio | Fin |
| --- | --- | --- |
| Lunes a viernes | 08:30 | 16:30 |
| Sábado | 08:30 | 12:30 |
| Domingo | Sin jornada ordinaria | |

- Tolerancia de puntualidad configurable en código: **5 minutos**; una entrada posterior al margen se cuenta como tardanza desde el inicio programado descontando permisos horarios aplicables.
- Se contabiliza **salida anticipada** si el empleado se retira antes de terminar el horario, descontando los intervalos de permiso autorizados.
- Se contabilizan los **minutos posteriores al cierre** exclusivamente para revisión administrativa; **NO** se generan ni liquidan horas extras. Esta configuración técnica **no determina** si algún tiempo constituye hora extraordinaria conforme a la legislación hondureña; esa cuestión debe revisarla el CAH con asesoría laboral.
- El sistema **no descuenta almuerzo, descansos o trabajo efectivo** de la diferencia entre marcaciones.
- Si aún no terminó la jornada, el sistema no considera automáticamente ausente a quien no ha marcado.
- Los feriados **no se incluyen** en el denominador.
- La asistencia y la puntualidad **no son indicadores suficientes de productividad ni desempeño técnico**.

## Límites y seguridad antes de producción

1. **Un solo teléfono institucional:** no hay servidor ni sincronización entre dispositivos. Si necesitas recepción + dirección en teléfonos distintos, se requiere backend central con autenticación real y servidor horario.
2. **El reloj depende del Android:** una manipulación del reloj del sistema podría alterar marcaciones. No está validado por servidor.
3. **Privacidad:** SQLite está en almacenamiento privado de la app, pero la base de datos **no está cifrada en reposo**. Protege el dispositivo con contraseña, actualizaciones y bloqueo automático; solo recolecta la información IHSS estrictamente necesaria. Evita escribir datos de diagnóstico en el campo motivo.
4. Los archivos **CSV no están cifrados**; no los compartas por canales no autorizados. Los respaldos `.cahbackup` sí están cifrados, bajo responsabilidad del administrador.
5. Los PIN de 4–6 cifras son limitados frente a ataques técnicos contra copias de base de datos; considera autenticación más robusta y controles externos en una versión institucional.
6. Desinstalar la app o borrar datos puede eliminar toda la información; programa respaldos cifrados frecuentes y ensaya restaurarlos en un dispositivo de prueba.
7. **Migración desde PWA 1.0:** los archivos JSON de la versión de navegador **no se importan** automáticamente. Esta versión inicia su propia base SQLite.
8. Los horarios, permisos y decisiones laborales deben validarse con el reglamento interno del CAH y la normativa vigente. Esta herramienta no realiza nómina ni sanciones automáticas.

## Organización de archivos

- `app/src/main/java/hn/org/cah/asistencia/MainActivity.java`: pantallas, controles de rol y lógica de aplicación.
- `CahDb.java`: base SQLite, historial, contraseñas/PIN, auditoría y restauración transaccional.
- `AttendanceRules.java`: reglas de horario, permisos, faltas, KPI y minutos posteriores.
- `BackupCrypto.java`: AES-256-GCM para copias locales.
- `app/src/main/AndroidManifest.xml`, `app/src/main/res/`: configuración y tema Android.
- `tests/AttendanceRulesTest.java`: pruebas de las reglas que se pueden ejecutar sin Android.

### Pruebas locales de reglas (PC con JDK 17)

```sh
mkdir -p build/local-test
javac -d build/local-test app/src/main/java/hn/org/cah/asistencia/AttendanceRules.java tests/AttendanceRulesTest.java
java -cp build/local-test AttendanceRulesTest
```

**Estado de entrega:** código Android nativo y proyecto listos para intentar compilar en Android Studio; pendiente integración y pruebas en dispositivo y despliegue institucional.