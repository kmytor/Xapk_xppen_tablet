# Registro de Cambios y Solicitudes - Xapk XP-Pen Tablet

Este archivo documenta todas las solicitudes del usuario y los cambios implementados en el proyecto.

---

## Solicitudes del usuario

| # | Solicitud | Estado |
| :--- | :--- | :--- |
| 1 | La conexión por USB no funciona | ✅ Revisado y ampliado |
| 2 | La app obliga a conectarse a red sin necesidad | ✅ Corregido (eliminado arranque automático de red) |
| 3 | Los botones de "Hiraku" no se activan | ✅ Revisado (ver notas) |
| 4 | Botones del lápiz y tableta deben quedar configurables | ✅ Confirmado |
| 5 | Registrar los cambios en un `.md` | ✅ Este archivo |
| 6 | La tableta usa un **receptor USB** (no Bluetooth directo) conectado a un hub | ✅ Considerado |
| 7 | Presión del lápiz no funciona en apps de dibujo | ✅ Resuelto con Shizuku |

---

## Cambios implementados en código

### 1. Soporte de receptores USB / hubs
**Archivo:** `app/src/main/java/com/xppen/inputmapper/usb/UsbInputService.kt`

Se amplió `isXpenDevice()` para reconocer más Vendor IDs (receptores USB y hubs), no solo el ID de la tableta.

### 2. Eliminación de la dependencia forzada de red
**Archivo:** `app/src/main/java/com/xppen/inputmapper/usb/UsbInputService.kt`

- Se eliminó el arranque automático del daemon (`startDaemonAutomatically`) en `onCreate`.

**Archivo:** `app/src/main/java/com/xppen/inputmapper/ui/SettingsActivity.kt`

- Se eliminó el chequeo automático del daemon en `onResume`.

### 3. Color faltante que impedía compilar
**Archivo:** `app/src/main/res/values/colors.xml`

- Se añadió `purple_200` (error `resource color/purple_200 not found`).

### 4. Configuración de compilación (Gradle / JDK)
**Archivo:** `gradle/wrapper/gradle-wrapper.properties`

- Gradle actualizado a `8.13` y eliminado el `distributionSha256Sum` que causaba error de verificación.

**Archivo:** `app/build.gradle.kts`

- `kotlinOptions.jvmTarget` unificado a `11` (antes 1.8, Java usaba 11).

### 5. Corrección de presión del lápiz (14 bits)
**Archivo:** `app/src/main/java/com/xppen/inputmapper/input/HidReportParser.kt`

- Parser de presión ampliado a **14 bits** (0–16383) en lugar de 13 bits (0–8191): `pressure = rawPressureVal & 0x3FFF`.
- Constante `MAX_PRESSURE` actualizada de `8191` a `16383`.
- Afecta a los tres formatos de reporte: XP-Pen estándar (0x07), UGEE v2 (0x02) y UC-Logic v2 (0x08).

---

## Configuración de entorno (no es código)

### Shizuku (para presión real sin Wi-Fi)
- Shizuku **13.5** instalado y **activo** (arrancado por USB/ADB, no requiere Wi-Fi).
- App `com.xppen.inputmapper` **autorizada** en Shizuku.
- El **motor autónomo** (`InputDaemonServer`) queda corriendo a través de Shizuku.

**Comando para reactivar Shizuku tras un reinicio:**
```
adb shell /data/app/~~8a_mkryejdPYD38OK3xqZA==/moe.shizuku.privileged.api-kmpOBLtLnar08wSJNcVWjQ==/lib/arm64/libshizuku.so
```

### Android Studio
- Gradle JDK: **17** (Embedded JDK).
- Gradle: **8.13**.
- Objetivo de JVM: **11**.

---

## Notas / Pendientes

- **Botones "Hiraku":** no existe ninguna función con ese nombre en el código. Probablemente se refiera a los botones de acción del motor (Emparejar / Reconectar) o a los botones físicos de la tableta. Los botones físicos y del lápiz siguen siendo configurables en Ajustes.
- **Pressure:** la ruta de accesibilidad (sin Shizuku) no soporta presión. Con Shizuku activo, la app usa el daemon y sí inyecta presión real.
- **Tras reiniciar el tablet:** Shizuku debe reactivarse con el comando anterior.
