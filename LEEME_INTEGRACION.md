# Monopoly + Raspberry Pi Pico W: dados y RFID conectados

## Qué se logró

La aplicación Java se inicia con `MonopolyHardwareApp.java`, una clase NUEVA que conserva las clases originales del juego. La Pico envía los mensajes del código recibido (`TIRAR_DADOS` y `RFID|UID`). El puente solicita el resultado a `ServidorHardware` y lo distribuye así:

```
PICO W   ─── TCP 5001 ──► PicoHardwareBridge.java
  ▲                             │
  │    DADOS|d1|d2 / RFID_OK|uid │ TCP 5000 (127.0.0.1)
  └─────────────────────────────┤
                                ▼
                        ServidorHardware.java
                        (código ORIGINAL)
                                │
                ┌───────────────┴──────────────────┐
                ▼                                  ▼
         GamePanel.applyRoll(d1+d2,          HardwareCardReader
                     onFinished)                     │
                │                                  ▼
         movimiento + cambio                CardReader.readCardId()
                de turno                            │
                                                   ▼
                                               Game → Bank
                                         valida UID antes de cobrar
```

**Importante:** `ServidorHardware.java` es el que genera los dos números OFICIALES; la Pico solamente los muestra y la app avanza su suma. El RFID únicamente identifica al jugador; nunca almacena su saldo.

### Archivos originales: NO modificados

- `MonopolyApp.java`
- `Bank.java`
- `Game.java`
- `GamePanel.java`
- `CardReader.java`
- `ServidorHardware.java` (mismo contenido que `ServidorHardware.JAVA`, nombre con extensión `.java` requerido por Java).

Los otros archivos de tu juego, como `Board`, `Player`, `PlayerSelectionPanel`, `MainMenuPanel` y clases del tablero, siguen siendo necesarios, pero **no se incluyeron porque no se recibieron**. No sustituyas estas clases por archivos de prueba.

### Archivos NUEVOS

| Archivo | Función |
|---|---|
| `MonopolyHardwareApp.java` | Abre tu Monopoly y conecta la partida con el hardware. Ejecuta ESTE main. |
| `PicoHardwareBridge.java` | Escucha a la Pico en 5001, reenvía a `ServidorHardware` en 5000 y notifica a Java. |
| `HardwareCardReader.java` | Implementa `CardReader` usando los UID de la Pico; espera tarjeta al autorizar transacciones. |
| `PruebaPico.java` | Permite probar TCP enviando comandos desde una segunda terminal. |
| `main_pico_integrado.py` | Copia alternativa de tu script de la Pico. Solo se preparó la conexión al puerto 5001 y se sustituyeron datos privados de Wi-Fi/IP por marcadores de configuración. |
| `mfrc522.py` | Biblioteca del lector RC522 recibida en tus archivos. |

## Paso 1: prepara la carpeta del proyecto Java (VS Code)

Pon los archivos **nuevos** `MonopolyHardwareApp.java`, `PicoHardwareBridge.java`, `HardwareCardReader.java`, `PruebaPico.java` al lado de tus clases Java actuales. Si no existe, añade también `ServidorHardware.java` desde este paquete (no se modifica su contenido).

**No reemplaces** las clases que ya tienes: `Game.java`, `GamePanel.java`, `Bank.java`, `CardReader.java`, `MonopolyApp.java` (las copias del ZIP son solo referencia).

Asegúrate de mantener las otras clases originales del juego en la misma carpeta o proyecto Java: tablero, jugador, casillas, pantallas, etc.

## Paso 2: prepara la Raspberry Pi Pico W (Thonny)

Tu código `main.py` original tiene `PUERTO_SERVIDOR = 5000`. **El programa integrado usa 5001 para la Pico** porque 5000 debe quedar reservado al `ServidorHardware` original. No es necesario modificar la lógica de dados o RFID: es un dato de conexión.

Opción A: cambia únicamente la configuración `PUERTO_SERVIDOR = 5001` en tu script `main.py` actual, que ya tiene tu Wi-Fi/IP reales.

Opción B: usa `main_pico_integrado.py` (archivo nuevo, sin claves personales). Completa los **tres marcadores**: nombre del Wi-Fi, contraseña y dirección IP de la computadora que ejecutará Java. Si debe arrancar automáticamente en la Pico, guárdalo en la Pico como `main.py`. Pon `mfrc522.py` en la Pico si no lo tienes instalado.

**No compartas en GitHub tu contraseña de Wi-Fi**, tampoco versiones de `main.py` que la contengan.

Conecta PC y Pico a una red que permita comunicación entre ellos. Si Windows Firewall pregunta, permite el puerto **TCP 5001** en la red privada. La IP configurada debe ser la **IP local actual de la computadora** donde se ejecuta Java.

## Paso 3: ejecuta

1. Abre el proyecto Java completo en VS Code.
2. Ejecuta como clase principal **`MonopolyHardwareApp`**, NO `MonopolyApp` ni `ServidorHardware` por separado.
3. En terminal debe aparecer `SERVIDOR HARDWARE INICIADO` (5000) y `PUENTE LISTO` (5001).
4. Comienza una partida desde la interfaz gráfica.
5. Presiona el botón de la Pico: se recibirá `TIRAR_DADOS`, el servidor generará `DADOS|d1|d2`, la Pico mostrará ambos números y `GamePanel.applyRoll(d1 + d2, onFinished)` moverá al jugador.
6. Cuando una acción requiera tarjeta, la consola de Java mostrará `ACERCA TU TARJETA RFID (45 segundos)...`. Acerca la tarjeta del jugador correcto en ese momento. Si coincide, `Bank` autoriza; si no, no hace el cobro.

Si todo el código Java está en una sola carpeta y sin `package`, también puedes compilar desde terminal:

```sh
javac *.java
java MonopolyHardwareApp
```

## Paso 4: verifica los UID reales

Tu `Game.java` actual registra estos identificadores:

| Jugador | UID registrado | Estado |
|---|---|---|
| 1 | `57482001` | Valor configurado en Game |
| 2 | `2209842189` | Valor configurado en Game |
| 3 | `123` | **Prueba, sustituir por UID real** |
| 4 | `321` | **Prueba, sustituir por UID real** |

**Bank no necesita modificaciones**. Ya toma el valor de `CardReader.readCardId()` y lo compara con el UID registrado en `Game`. Para los jugadores 3 y 4 sí deberás configurar UID reales en tu `Game` cuando dispongas de esas tarjetas; no puede inventarlos el puente.

## Cómo probar SIN conectar la Pico

Con `MonopolyHardwareApp` ejecutándose, **después de iniciar una partida**, abre otra terminal en la carpeta compilada y envía:

```sh
java PruebaPico TIRAR_DADOS
```

Respuesta esperada: `DADOS|1..6|1..6`. La ficha debe avanzar la suma y cambiar el turno después de la animación.

Para probar un UID:

```sh
java PruebaPico "RFID|57482001"
```

Respuesta esperada: `RFID_OK|57482001`. **Esto no cobra por sí mismo.** Solo autoriza cuando una acción de `Bank` está esperando tarjeta. El escaneo hecho antes de un pago no se guarda para autorizar pagos futuros.

Si presionas el botón antes de comenzar la partida, el puente responde `ERROR|PARTIDA_NO_INICIADA`. Si vuelves a tirar mientras un movimiento está en curso, responde `ERROR|TIRADA_EN_PROCESO`.

## Qué fue probado y qué queda pendiente

Prueba de integración Java realizada usando `Game.java`, `GamePanel.java`, `CardReader.java`, `Bank.java`, `ServidorHardware.java` **reales**, además de clases auxiliares para las dependencias que no recibimos. Con Java 21 compilaron y aprobaron **13 comprobaciones**: respuestas TCP, suma y movimiento, callback final, cambio de turno, rechazo de segunda tirada durante movimiento, UID recibido, cobro autorizado por UID correcto, rechazo de UID incorrecto, saldo no modificado y formato RFID inválido.

No se ha probado físicamente con tu Pico/RC522 ni compilado tu proyecto GUI completo porque siguen faltando algunas clases de tu aplicación. No se modificaron ni se recrearon dichas clases en este ZIP.

El PDF exige además un servidor principal que administre turnos, propiedades y transacciones, actualización de múltiples clientes y al menos dos computadoras; **este adaptador solamente conecta dados y RFID**. `ServidorHardware` sigue siendo el servidor del módulo físico, no sustituye al servidor distribuido del juego.

**Nota sobre turnos:** aquí se bloquea una segunda tirada mientras la ficha está moviéndose. La Pico no identifica por sí sola qué persona pulsa el botón, ni es una implementación completa de las validaciones de un servidor multijugador exigidas en el PDF. Estas validaciones corresponden al servidor central del juego.

## Si algo falla

- `Address already in use` en 5000: ya está abierto otro `ServidorHardware`; cierra la instancia anterior y vuelve a lanzar únicamente `MonopolyHardwareApp`.
- `Address already in use` en 5001: otro programa está escuchando; cierra la instancia anterior del puente.
- Pico dice `ERROR comunicando con Java`: verifica Wi-Fi, IP del PC, firewall y `PUERTO_SERVIDOR = 5001`.
- Dice `PARTIDA_NO_INICIADA`: debes ingresar a la pantalla de juego antes de lanzar.
- RFID recibido pero no cambia saldo: el banco solo verifica durante una transacción que invoque `Bank.addMoney/removeMoney/transfer`; escanear tarjeta no crea un pago automático.
- RFID incorrecto: verifica que el UID decimal de la consola de la Pico sea idéntico al registrado para ese jugador en `Game.java`.
