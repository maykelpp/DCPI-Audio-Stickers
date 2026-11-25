# DCPI Audio Stickers

Aplicación Android local para preparar proyectos multimedia y estudiar la compatibilidad de stickers con audio.

## Estado actual — Fase 5 en implementación (0.5.0-alpha1)

- Interfaz HTML empaquetada en Android WebView.
- Puente JavaScript/Kotlin y selector de archivos Android.
- Importación de imagen, vídeo y audio.
- Biblioteca local persistente mediante IndexedDB: conserva metadatos y blobs multimedia en el almacenamiento del WebView.
- Recuperación de proyectos al volver a abrir la aplicación, con aviso si falta algún blob multimedia.
- Guardado atómico de metadatos y archivos; los cambios frecuentes del editor guardan solo metadatos para evitar reescribir archivos grandes continuamente.
- Registro de referencias nativas independientes por proyecto para audio y vídeo, incluso cuando un proyecto contiene ambos.
- Guardado automático de recortes, volumen de previsualización y metadatos de exportación.
- Eliminación de proyectos y sus medios almacenados.
- Persistencia de los permisos URI de los archivos seleccionados para que el motor nativo pueda volver a acceder al origen después de reiniciar la app.
- Extracción de la pista de audio desde archivos de audio o vídeo.
- Recorte y exportación M4A con Media3 Transformer, recodificación AAC, volumen aplicado al archivo y perfiles de calidad solicitados (96, 160 y 256 kbps; el codificador puede ajustar la tasa final).
- Exportación MP4 desde un vídeo importado, con recorte, vídeo H.264 y audio AAC cuando el origen contiene audio; el volumen se aplica a esa pista.
- Guardado de resultados en Descargas/DCPI Audio Stickers.
- Compartir archivos M4A y MP4 mediante el menú nativo de Android.
- Biblioteca con búsqueda por nombre, archivo y formato; filtros de proyectos y exportaciones.
- Exportación MP4 conectada desde la interfaz al puente Android; el selector de calidad modifica el bitrate de audio AAC, no la calidad de vídeo.
- Forma de onda analizada en WebAudio para archivos compatibles.
- Manejo más seguro cuando el códec no declara tamaño máximo de entrada.

## Límites conocidos

- El M4A exportado es un archivo de audio, no un sticker sonoro de WhatsApp.
- WebM todavía no está implementado.
- MP4 usa el audio integrado en el vídeo; todavía no mezcla una pista de audio independiente importada en el proyecto.
- El procesamiento AAC depende de los decodificadores y codificadores disponibles en Android y debe probarse con distintos formatos.
- La persistencia de IndexedDB requiere validación final en dispositivo con archivos grandes y después de cerrar/abrir la app; el build de CI no sustituye esa prueba.
- Compatibilidad de códecs, calidad, ganancia, recortes, sincronización, duración y uso de memoria debe probarse en dispositivo con distintos formatos y fabricantes.
- No se afirma compatibilidad con el sistema de stickers sonoros de WhatsApp hasta disponer de un formato documentado y una prueba real.

## Compilar

Requiere JDK 17, Android SDK 35 y Gradle 8.9.

```bash
gradle assembleDebug --no-daemon
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Firma

No se ha creado ni sustituido ninguna clave de firma. Para actualizar una instalación existente se necesita exactamente el keystore original y su configuración segura. El repositorio no contiene un keystore versionado; el build de prueba de GitHub Actions genera un APK debug.

La integración de stickers sonoros en WhatsApp no se considera disponible hasta verificarla en la aplicación real.
