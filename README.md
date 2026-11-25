# DCPI Audio Stickers

Aplicación Android local para preparar proyectos multimedia y estudiar la compatibilidad de stickers con audio.

## Estado actual — Fase 2 (0.2.0-alpha1)
- Interfaz HTML original empaquetada en Android WebView.
- Puente JavaScript/Kotlin y selector de archivos Android.
- Extracción de la pista de audio desde archivos de audio o vídeo.
- Recorte de audio por rango y exportación M4A sin recodificar la pista fuente.
- Guardado del resultado en Descargas/DCPI Audio Stickers.
- Compartir el M4A mediante el menú nativo de Android.
- Forma de onda real para archivos de audio compatibles, analizada en WebAudio.
- La exportación de vídeo MP4/WebM todavía no está implementada.

## Compilar
Requiere JDK 17, Android SDK 35 y Gradle 8.9.

```bash
gradle assembleDebug --no-daemon
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Fases
1. Base Android, WebView, puente nativo e importación.
2. Motor M4A: extracción, recorte, forma de onda y compartir.
3. Biblioteca persistente y gestión local de proyectos.
4. Exportación audiovisual, pruebas Android 10–16 y validación independiente del formato de sticker sonoro.

## Firma
No se ha creado ni sustituido ninguna clave de firma en esta fase. La firma de actualización de una instalación existente debe conservarse usando exactamente su keystore original y su configuración segura. El repositorio no contiene un keystore versionado; el build de prueba de GitHub Actions genera un APK debug.

La integración de stickers sonoros en WhatsApp no se considera disponible hasta verificarla en la aplicación real.
