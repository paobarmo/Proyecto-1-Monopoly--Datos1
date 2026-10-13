from machine import Pin
from time import sleep
import time
import random
import network
import socket

from mfrc522 import MFRC522


# ============================================================
# CONFIGURACION
# ============================================================

# CAMBIA ESTAS 3 COSAS

NOMBRE_WIFI = "TU_WIFI"
PASSWORD_WIFI = "TU_CONTRASENA_DE_WIFI"

# IP de la computadora donde corre Java
IP_SERVIDOR = "IP_DE_LA_LAPTOP"

# Debe ser el mismo puerto que usa Java
PUERTO_SERVIDOR = 5001


# ============================================================
# BOTON
# ============================================================

# Boton conectado:
#
# GP14 ---- BOTON ---- GND
#
# Sin tocar = 1
# Presionado = 0

boton = Pin(14, Pin.IN, Pin.PULL_UP)


# ============================================================
# DISPLAY 1
# ============================================================

# Orden:
# A, B, C, D, E, F, G

display1 = [
    Pin(2, Pin.OUT),   # A
    Pin(3, Pin.OUT),   # B
    Pin(4, Pin.OUT),   # C
    Pin(5, Pin.OUT),   # D
    Pin(6, Pin.OUT),   # E
    Pin(7, Pin.OUT),   # F
    Pin(8, Pin.OUT)    # G
]


# ============================================================
# DISPLAY 2
# ============================================================

display2 = [
    Pin(9, Pin.OUT),    # A
    Pin(10, Pin.OUT),   # B
    Pin(11, Pin.OUT),   # C
    Pin(12, Pin.OUT),   # D
    Pin(13, Pin.OUT),   # E
    Pin(22, Pin.OUT),   # F
    Pin(26, Pin.OUT)    # G
]


# ============================================================
# PATRONES DE LOS NUMEROS
# ============================================================

# 1 = segmento encendido
# 0 = segmento apagado
#
# Orden:
# A B C D E F G

numeros = {
    1: [0, 1, 1, 0, 0, 0, 0],
    2: [1, 1, 0, 1, 1, 0, 1],
    3: [1, 1, 1, 1, 0, 0, 1],
    4: [0, 1, 1, 0, 0, 1, 1],
    5: [1, 0, 1, 1, 0, 1, 1],
    6: [1, 0, 1, 1, 1, 1, 1]
}


# ============================================================
# RFID RC522
# ============================================================

lector = MFRC522(
    sck=18,
    mosi=19,
    miso=16,
    rst=20,
    cs=17,
    spi_id=0
)


# ============================================================
# MOSTRAR UN NUMERO
# ============================================================

def mostrar_numero(display, numero):

    patron = numeros[numero]

    for i in range(7):
        display[i].value(patron[i])


# ============================================================
# ANIMACION DE DADOS
# ============================================================

# Esto SOLO es visual.
#
# Estos numeros random NO son el resultado oficial.
#
# El resultado oficial lo manda Java.

def animacion_dados():

    # Parte rapida

    for i in range(12):

        n1 = random.randint(1, 6)
        n2 = random.randint(1, 6)

        mostrar_numero(display1, n1)
        mostrar_numero(display2, n2)

        sleep(0.05)


    # Ahora se va frenando

    tiempos = [
        0.08,
        0.10,
        0.15,
        0.20,
        0.28
    ]


    for tiempo in tiempos:

        n1 = random.randint(1, 6)
        n2 = random.randint(1, 6)

        mostrar_numero(display1, n1)
        mostrar_numero(display2, n2)

        sleep(tiempo)


# ============================================================
# CONECTAR WIFI
# ============================================================

def conectar_wifi():

    wifi = network.WLAN(network.STA_IF)

    wifi.active(True)


    if wifi.isconnected():

        print("Wi-Fi ya conectado")

        return wifi


    print("Conectando al Wi-Fi...")


    wifi.connect(
        NOMBRE_WIFI,
        PASSWORD_WIFI
    )


    while not wifi.isconnected():

        print(".")

        sleep(1)


    print("Wi-Fi conectado")

    print(
        "Datos de red:",
        wifi.ifconfig()
    )


    return wifi


# ============================================================
# ENVIAR AL SERVIDOR JAVA
# ============================================================

# Le mandamos un texto a Java.
#
# Ejemplos:
#
# TIRAR_DADOS
#
# RFID|123456789
#
# Java responde otro texto.

def enviar_a_java(mensaje):

    cliente = None


    try:

        cliente = socket.socket()

        cliente.settimeout(5)


        cliente.connect(
            (
                IP_SERVIDOR,
                PUERTO_SERVIDOR
            )
        )


        # Ponemos \n porque Java usa readLine()

        mensaje = mensaje + "\n"


        cliente.send(
            mensaje.encode()
        )


        respuesta = cliente.recv(256)


        respuesta = respuesta.decode()

        respuesta = respuesta.strip()


        return respuesta


    except Exception as error:

        print(
            "ERROR comunicando con Java:"
        )

        print(error)

        return None


    finally:

        if cliente is not None:

            cliente.close()


# ============================================================
# PEDIR LOS DADOS A JAVA
# ============================================================

def pedir_dados():

    print()

    print("BOTON PRESIONADO")

    print("Pidiendo dados a Java...")


    # La Pico NO decide los dados.
    #
    # Solo le pide al servidor que los tire.

    respuesta = enviar_a_java(
        "TIRAR_DADOS"
    )


    if respuesta is None:

        print("Java no respondio")

        return


    print(
        "Java respondio:",
        respuesta
    )


    # Java deberia mandar:
    #
    # DADOS|4|6

    partes = respuesta.split("|")


    if partes[0] == "DADOS":

        dado1 = int(partes[1])

        dado2 = int(partes[2])


        # Hacemos primero la animacion.

        animacion_dados()


        # Ahora mostramos EL RESULTADO REAL
        # que mando Java.

        mostrar_numero(
            display1,
            dado1
        )


        mostrar_numero(
            display2,
            dado2
        )


        print()

        print("--------------------")

        print(
            "Dado 1:",
            dado1
        )

        print(
            "Dado 2:",
            dado2
        )

        print(
            "Total:",
            dado1 + dado2
        )

        print("--------------------")


    elif partes[0] == "ERROR":

        print(
            "Servidor rechazo la tirada:"
        )

        print(respuesta)


# ============================================================
# CONVERTIR UID
# ============================================================

# El RFID puede dar algo como:
#
# [83, 174, 41, 77]
#
# Esto lo convierte en un solo numero.

def convertir_uid(uid):

    numero_uid = 0


    for numero in uid:

        numero_uid = (
            numero_uid * 256
            + numero
        )


    return numero_uid


# ============================================================
# EVITAR QUE LEA LA MISMA TARJETA MUCHAS VECES
# ============================================================

ultimo_uid = None

ultimo_tiempo_uid = 0


# ============================================================
# REVISAR RFID
# ============================================================

def revisar_rfid():

    global ultimo_uid
    global ultimo_tiempo_uid


    lector.init()


    # Preguntamos si hay una tarjeta.

    estado, tipo = lector.request(
        lector.REQIDL
    )


    if estado != lector.OK:

        return


    # Intentamos leer el UID.

    estado, uid = lector.SelectTagSN()


    if estado != lector.OK:

        return


    numero_uid = convertir_uid(uid)


    ahora = time.ticks_ms()


    # Evita mandar el mismo UID muchas veces
    # mientras la tarjeta sigue encima.

    if ultimo_uid == numero_uid:

        diferencia = time.ticks_diff(
            ahora,
            ultimo_tiempo_uid
        )


        if diferencia < 1500:

            return


    ultimo_uid = numero_uid

    ultimo_tiempo_uid = ahora


    print()

    print("====================")

    print("TARJETA DETECTADA")

    print(
        "UID:",
        numero_uid
    )

    print("====================")


    # Creamos un mensaje como:
    #
    # RFID|123456789

    mensaje = (
        "RFID|"
        + str(numero_uid)
    )


    respuesta = enviar_a_java(
        mensaje
    )


    if respuesta is not None:

        print(
            "Java respondio:"
        )

        print(respuesta)


# ============================================================
# CONECTAMOS WIFI
# ============================================================

wifi = conectar_wifi()


# ============================================================
# INICIO
# ============================================================

print()

print("============================")

print("     MONOPOLY ELECTRONICO")

print("============================")

print()

print("RFID listo")

print("Boton listo")

print("Displays listos")

print()

print(
    "Presiona el boton o acerca una tarjeta"
)


# ============================================================
# LOOP PRINCIPAL
# ============================================================

while True:


    # Revisamos constantemente
    # si acercaron RFID.

    revisar_rfid()


    # Revisamos si tocaron el boton.

    if boton.value() == 0:


        pedir_dados()


        # Esperamos hasta que suelten
        # fisicamente el boton.

        while boton.value() == 0:

            sleep(0.01)


        # Anti rebote.

        sleep(0.2)


    sleep(0.03)
