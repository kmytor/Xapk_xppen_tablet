# 🛠️ SOLUCIÓN FINAL PARA ERRORES DE "DUPLICATE RESOURCES"

Si aún ves errores como:
```
Duplicate resources
colors.xml C:\Users\kmyto\.openclaw\workspace\XPPenInputMapper\app\src\main\res\values 1 problem
strings.xml C:\Users\kmyto\.openclaw\workspace\XPPenInputMapper\app\src\main\res\values 1 problem
```
Esto significa que existe **otra carpeta de recursos** (como `values-night`, `values-land`, `values-port`, etc.) que también contiene archivos `colors.xml` y/o `strings.xml` con los mismos nombres, causando el conflicto.

## ✅ PASO 1: ELIMINAR TODAS LAS CARPETAS DE RECURSOS DUPLICADOS
1. En el **explorador de archivos**, navega a:
   `C:\Users\kmyto\.openclaw\workspace\XPPenInputMapper\app\src\main\res`
2. **Busca todas las carpetas que empiecen con `values-`** (como `values-night`, `values-land`, `values-port`, `values-fr`, etc.).
3. **Elimínalas TODAS** (clic derecho → Eliminar). **NO elimines la carpeta llamada solo `values`** (esa es la necesaria).
   - Ejemplo de carpetas a eliminar: `values-night`, `values-land`, `values-port`, `values-fr`, `values-sw600dp`, etc.
   - Deja solo: `values`

### Verificación rápida:
Después de eliminar, en la carpeta `res` deberías ver solo estas carpetas (pueden haber más o menos, pero lo importante es que **no haya ninguna `values-*`** excepto `values`):
```
drawable
drawable-v24
layout
mipmap-anydpi-v26
mipmap-hdpi
mipmap-mdpi
mipmap-xhdpi
mipmap-xxhdpi
mipmap-xxxhdpi
values   ← Esta es la única que debe empezar con "values"
xml
```

## ✅ PASO 2: LIMPIAR EL PROYECTO
- En Android Studio, ve al menú: **Build → Clean Project**
- Espera a que muestre **"Clean complete"** en la parte inferior.

## ✅ PASO 3: SINCRONIZAR CON GRADLE FILES
- Haz clic en el icono de **elefante** (⇲) **"Sync Project with Gradle Files"** (o `File → Sync Project with Gradle Files`).
- Espera a que muestre **"Gradle sync finished in Xs"** en la parte inferior.
- **No pases al siguiente paso hasta ver ese mensaje de éxito.**

## ✅ PASO 4: CONSTRUIR EL APK
- Haz clic en **Build → Generate App Bundles or APKs → Build APK(s)**.
- Inmediatamente después, mira la parte inferior y haz clic en la pestaña **"Build"**.
- Espera 45-60 segundos.

## ✅ PASO 5: QUÉ BUSCAR EN LA PESTAÑA "BUILD"
- Si ves: `BUILD SUCCESSFUL in Xs` → ¡Éxito! Responde: **"ÉXITO: BUILD SUCCESSFUL en Xs"**
- Si ves texto en rojo que empieza con `FAILURE:` → copia y pega **EXACTAMENTE** ese texto de error aquí para que pueda ayudarte a solucionarlo.

## ✅ PASO 6: SI OBTUVES ÉXITO (BUILD SUCCESSFUL)
Entonces harás:
1. **Instalar en tu Xiaomi Pad Pro:**
   - Habilita Opciones de desarrollador y Depuración USB en la tablet
   - Conéctala vía USB a tu PC
   - Ejecuta: `adb install app/build/outputs/apk/debug/app-debug.apk`
     (O usa el botón Run en Android Studio)
2. **Concede el permiso INJECT_EVENTS** (como solicitaste):
   ```
   adb shell pm grant com.xppen.inputmapper android.permission.INJECT_EVENTS
   ```
   *Si falla:* Activa el Servicio de Accesibilidad:
   - Ve a *Settings → Accessibility → XP-Pen Input Mapper* → Actívalo

## ✅ PASO 7: LO MÁS CRÍTICO - OBTENER EL DESCRIPTOR HID
Para que la presión y los botones funcionen correctamente, **necesitamos el descriptor HID real de tu XP-Pen Deco LW**.

**Tienes el `Code_Tool (2).exe` - esta es la forma más fácil:**
1. Conecta tu tablet a una **PC con Windows** (por USB).
2. Ejecuta `Code_Tool (2).exe`.
3. Busca una opción como:
   - "Get Report Descriptor"
   - "HID Information"
   - "Descriptor View"
   - "Device Information"
4. **Guarda o copia la salida** – se verá como una serie de bytes hexadecimales separados por espacios, por ejemplo:
   ```
   05 0d 09 02 a1 01 09 20 a1 02 15 00 26 ff 7f 75 09 95 01 81 02 95 01 81 03 75 08 95 01 81 03 ...
   ```
   *(Este es solo un ejemplo – tu descriptor real será diferente y es exactamente lo que necesitamos)*

**Alternativa si prefieres Linux** (si tienes acceso):
```bash
sudo apt-get install hid-tools
hidrd-convert -o rdesc report /dev/hidrawX
```
(Necesitarás encontrar el dispositivo hidraw correcto, generalmente en `/dev/hidraw*`).

## 📤 QUÉ HACER DESPUÉS
Por favor, comparte conmigo:
1. El resultado de los pasos 1-5 (eliminar carpetas values-*, limpiar, sincronizar, construir): ¿Fue `BUILD SUCCESSFUL`? Si no, pega el error de la pestaña "Build".
2. Si lograste instalar y conceder el permiso (o usar el AccessibilityService).
3. **Sobre todo:** el descriptor HID que obtuviste usando tu `Code_Tool (2).exe` en una PC con Windows.

Una vez que tenga ese descriptor, actualizaré `HidReportParser.kt` para extraer correctamente presión, posición y botones de tu specific XP-Pen Deco LW, y entonces tendremos:
- Líneas sensibles a presión en Krita, etc.
- Control suave del cursor en Parsec/Sunshine (modo ratón)
- Soporte para cambiar entre modo lápiz y modo ratón
- Curva de presión ajustable (como solicitaste)
- Perfiles por aplicación (diferentes configuraciones para Krita vs Parsec, etc.)

Estamos muy cerca de tener tu APK personalizada funcionando exactamente como la necesitas. 🎩

**POR FAVOR, RESPONDE CON:**
- El resultado del Paso 5 (¿Fue BUILD SUCCESSFUL? Si no, pega el error exacto de la pestaña "Build")
- Cuando tengas el descriptor HID, compártelo aquí

Espero tu reporte. ¡Vamos a lograrlo!