# Despierta ⏰

Despertador para Android que **solo se apaga cuando le muestras a la cámara uno de tus objetos** (por ejemplo, el papel de baño). Así te obliga a levantarte de la cama.

## Cómo funciona
1. Elige la hora y el tono.
2. Cuando suena, aparecen **7 objetos de la casa elegidos al azar** (máximo 2 por zona: baño, cocina, limpieza, sala). Ninguno es de la recámara, así que hay que salir del cuarto.
3. Apunta la cámara a cualquiera de ellos; cuando el teléfono lo reconoce unos instantes, la alarma se apaga.

El reconocimiento se hace en el propio teléfono (MobileNet V2 + TensorFlow Lite), **sin internet**.
Por seguridad, la alarma se calla sola a los 30 minutos.

## Descargar el APK
Cada vez que se sube un cambio a `main`, GitHub Actions compila la app automáticamente.
El APK aparece en la sección **Releases** del repositorio (y como artefacto en la pestaña **Actions**).

## Permisos
Cámara, notificaciones, alarmas exactas y pantalla completa (Android 14+: si aparece el botón
"Permitir alarma en pantalla completa", tócalo y actívalo).
