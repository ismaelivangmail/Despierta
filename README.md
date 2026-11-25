# Despierta ⏰

Despertador para Android que **solo se apaga cuando le muestras a la cámara uno de tus objetos** (por ejemplo, el papel de baño). Así te obliga a levantarte de la cama.

## Cómo funciona
1. Elige la hora, el tono y hasta **3 objetos** (por si alguno no lo encuentras).
2. Cuando suena, se abre la cámara. Apunta a cualquiera de tus 3 objetos.
3. Cuando el teléfono lo reconoce durante unos instantes, la alarma se apaga.

El reconocimiento se hace en el propio teléfono (MobileNet V2 + TensorFlow Lite), **sin internet**.
Por seguridad, la alarma se calla sola a los 30 minutos.

## Descargar el APK
Cada vez que se sube un cambio a `main`, GitHub Actions compila la app automáticamente.
El APK aparece en la sección **Releases** del repositorio (y como artefacto en la pestaña **Actions**).

## Permisos
Cámara, notificaciones, alarmas exactas y pantalla completa (Android 14+: si aparece el botón
"Permitir alarma en pantalla completa", tócalo y actívalo).
