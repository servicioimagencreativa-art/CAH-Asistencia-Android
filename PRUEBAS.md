# Pruebas realizadas y pendientes

**Verificado en este entorno:**

- Compilación y ejecución con `javac` del motor independiente de reglas horarias: **9 casos correctos**.
- Compilación sintáctica de las 4 clases Java nativas frente a un conjunto temporal de interfaces Android simuladas (**sin errores de Java**). El conjunto temporal NO está incluido en la entrega ni sustituye el SDK real.
- Se examinaron permisos de roles y resguardos de almacenamiento local, historial y cifrado de respaldos en el código. No equivale a una auditoría de seguridad.

**Pendiente:**

- **No se generó APK**: SDK de Android/Gradle no disponibles y sin acceso a repositorios en este entorno.
- Prueba de compilación real AGP 8.7.3 / Gradle 8.9 / Android SDK 35.
- Pruebas de UI en móviles Android 8 a 15, rotación, accesibilidad y reanudación de sesión.
- Pruebas end-to-end con datos reales, correcciones, exportación/restore SAF, cambios de hora del dispositivo y validaciones de política institucional.
- Auditoría de privacidad de datos IHSS y prevención de uso indebido de credenciales.

No utilices la aplicación para decisiones disciplinarias o nómina sin revisión humana y validación legal/operativa.