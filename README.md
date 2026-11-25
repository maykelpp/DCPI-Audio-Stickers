# DCPI Audio Stickers

Aplicación Android local para preparar proyectos multimedia y estudiar la compatibilidad de stickers con audio.

## Estado actual — Fase 1
- Interfaz HTML original empaquetada en Android WebView.
- Puente JavaScript/Kotlin para consultar versión y plataforma.
- Selector nativo de archivos mediante Android Storage Access Framework.
- Build debug automatizado con GitHub Actions.

## Compilar
Requiere JDK 17, Android SDK 35 y Gradle 8.9 o compatible con Android Gradle Plugin 8.7.3.

```bash
gradle assembleDebug --no-daemon
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Fases
1. Base Android, WebView, puente nativo e importación.
2. Motor multimedia: extracción, recorte, forma de onda y exportación.
3. Biblioteca persistente y gestión local de proyectos.
4. Compartir, pruebas Android 10–16 y validación independiente del formato de sticker sonoro.

La integración de stickers sonoros en WhatsApp no se considera disponible hasta verificarla en la aplicación real.
