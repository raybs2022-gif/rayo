# Rayo ⚡ para Android (flotante sobre todas tus apps, con internet)

Esto es el proyecto de una app Android. GitHub la convierte en un archivo instalable (.apk) gratis, en la nube.
No instalas nada en ninguna computadora: solo necesitas un navegador para subir los archivos.

## Paso 1: subir el proyecto a GitHub (desde cualquier computadora con navegador)
1. Descomprime este zip.
2. Entra a https://github.com, crea una cuenta gratis y pulsa **New repository**. Ponle de nombre `rayo` y créalo.
3. En el repositorio nuevo pulsa **uploading an existing file**.
4. Arrastra TODO el contenido de la carpeta descomprimida (los archivos y las carpetas `app` y `.github`) a la página.
   Debe subirse también la carpeta `.github`, porque ahí está la receta para construir la app.
5. Abajo pulsa **Commit changes**.

## Paso 2: construir la app (5 a 10 minutos)
1. En tu repositorio entra a la pestaña **Actions**.
2. Se iniciará solo una tarea llamada "Construir APK de Rayo". Si no, ábrela y pulsa **Run workflow**.
3. Espera a que termine con una marca verde ✅.
4. Abre esa tarea y baja hasta **Artifacts**. Descarga **rayo-apk** (es un zip).
   Si sale una ❌, entra a la tarea, copia el mensaje de error y mándamelo.

## Paso 3: instalar en el celular
1. Pasa el zip al celular o descárgalo desde el navegador del celular con tu cuenta de GitHub.
2. Descomprímelo (app Archivos o "Mis archivos") y toca `app-debug.apk`.
3. Android pedirá permiso para "instalar apps desconocidas": acéptalo para esa app (Archivos o Chrome).
   Si aparece un aviso de Play Protect, elige **Instalar de todos modos**.

## Paso 4: activar a Rayo
1. Abre la app **Rayo**.
2. Pulsa **Activar Rayo flotante**.
3. Android abrirá una pantalla: activa **Permitir mostrar sobre otras apps** para Rayo y vuelve atrás.
4. Pulsa **Activar Rayo flotante** otra vez. Rayo aparece flotando y paseando por toda la pantalla.

## Uso
- **Tócalo** para abrir el chat. **Arrástralo** para moverlo.
- Botones: 💵 Dólar, ⛅ Clima de Lima, 📰 Noticias de Perú, 🌎 Sismos. También puedes escribir una pregunta y busca en Wikipedia.
- 🏃 pausa o reanuda el paseo. ✕ cierra el chat.
- Para quitarlo: baja la barra de notificaciones y pulsa **Cerrar Rayo**, o usa **Quitar Rayo** dentro de la app.

## Lo que debes saber
- Es gratis y no usa ninguna clave. Las respuestas salen de fuentes públicas (dólar de referencia, Open-Meteo, Google Noticias, USGS y Wikipedia). No es una inteligencia artificial que conversa.
- El dólar es el de referencia internacional, no el del mercado paralelo.
- Solo funciona en Android. En iPhone no existe forma de flotar sobre otras apps.
- La app no está en Play Store: es una instalación manual de tu propio proyecto.
