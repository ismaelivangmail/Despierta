# Despierta ⏰

Despertador para Android que **solo se apaga cuando le muestras a la cámara uno de tus objetos** (por ejemplo, el papel de baño). Así te obliga a levantarte de la cama.

## Cómo funciona
- Lista de alarmas estilo reloj de Samsung: varias alarmas, nombre, días de la semana, sonido y vibración.
- Para apagarla, al sonar puedes elegir:
  1. 🧻 **Objetos**: mostrar a la cámara uno de 7 objetos de la casa elegidos al azar (ninguno de la recámara).
  2. 🧭 **Despertador virtual** (opcional): aparece en una dirección al azar; gira con el teléfono hasta verlo y camina hacia él 1–5 m. Usa giroscopio/brújula y contador de pasos, funciona sin ARCore.
  3. 👣 **Contar pasos** (opcional): caminar de 10 a 100 pasos.

El reconocimiento de objetos se hace en el propio teléfono (MobileNet V2 + TensorFlow Lite), **sin internet**.
Por seguridad, la alarma se calla sola a los 30 minutos.

## Descargar el APK
Cada vez que se sube un cambio a `main`, GitHub Actions compila la app automáticamente.
El APK aparece en la sección **Releases** del repositorio (y como artefacto en la pestaña **Actions**).

## Permisos
Cámara, notificaciones, alarmas exactas y pantalla completa (Android 14+: si aparece el botón
"Permitir alarma en pantalla completa", tócalo y actívalo).
