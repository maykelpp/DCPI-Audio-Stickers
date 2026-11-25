# DCPI Audio Stickers — lista de validación en Android

La compilación de CI comprueba Kotlin/Gradle y la sintaxis del JavaScript. No sustituye las pruebas reales en un teléfono.

## Instalación y apertura
- [ ] Instalar el APK en Android 10 o posterior.
- [ ] Abrir la app, navegar por todas las pestañas y volver atrás.
- [ ] Cerrar y abrir la app; comprobar que la biblioteca se recupera.
- [ ] Reiniciar el teléfono y comprobar acceso a proyectos ya guardados.

## Importación y biblioteca
- [ ] Importar un audio M4A y un MP3.
- [ ] Importar un vídeo MP4 con audio y un vídeo sin audio.
- [ ] Crear un proyecto con vídeo y audio por separado.
- [ ] Buscar proyectos por nombre y por formato.
- [ ] Probar filtros Todo, Exportados y Proyectos.
- [ ] Eliminar un proyecto y comprobar que desaparece también tras reiniciar.

## Editor y exportación
- [ ] Reproducir, pausar y mover el cabezal.
- [ ] Ajustar inicio y final del recorte; probar límites mínimos y máximos.
- [ ] Exportar M4A con 96, 160 y 256 kbps.
- [ ] Comprobar que el volumen exportado coincide con el nivel elegido.
- [ ] Exportar MP4 y comprobar imagen, audio, duración y sincronización.
- [ ] Comprobar que MP4 usa el audio integrado del vídeo y no promete mezclar la pista externa.
- [ ] Probar archivos largos y cancelar/esperar una exportación lenta.
- [ ] Comprobar archivos en Descargas/DCPI Audio Stickers.

## Compartir y estabilidad
- [ ] Compartir M4A y abrirlo con una app compatible.
- [ ] Compartir MP4 y abrirlo con un reproductor compatible.
- [ ] Repetir exportaciones y comprobar que no aparecen archivos vacíos.
- [ ] Probar falta de espacio, archivo sin pista de audio y formato incompatible.
- [ ] Vigilar cierres inesperados y uso de memoria con archivos grandes.

## Límites funcionales
- WebM no está implementado.
- MP4 no mezcla audio importado por separado.
- M4A es un archivo de audio normal, no un sticker sonoro nativo de WhatsApp.
- La compatibilidad real con el flujo de stickers de WhatsApp no está confirmada.
