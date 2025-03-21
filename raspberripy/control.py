import spidev
import time
import threading
import paho.mqtt.client as mqtt
import RPi.GPIO as GPIO

# broker 설정
# HiveMQ 브로커 정보
BROKER = "7f3e983375b34252860ac87937732983.s1.eu.hivemq.cloud"
PORT = 8883
USERNAME = "esgproj"
PASSWORD = "Qwerty01"

# Solenoid GPIO 설정
RELAY_PIN = 17
GPIO.setmode(GPIO.BCM)
GPIO.setup(RELAY_PIN, GPIO.OUT)
GPIO.output(RELAY_PIN, GPIO.LOW)

# Heating GPIO 설정 (1, 2, 3, 4번 릴레이)
RELAY_PIN_1 = 19  
RELAY_PIN_2 = 26 
RELAY_PIN_3 = 20  
RELAY_PIN_4 = 16 

GPIO.setup(RELAY_PIN_1, GPIO.OUT)
GPIO.setup(RELAY_PIN_2, GPIO.OUT)
GPIO.setup(RELAY_PIN_3, GPIO.OUT)
GPIO.setup(RELAY_PIN_4, GPIO.OUT)

GPIO.output(RELAY_PIN_1, GPIO.LOW)
GPIO.output(RELAY_PIN_2, GPIO.LOW)
GPIO.output(RELAY_PIN_3, GPIO.LOW)
GPIO.output(RELAY_PIN_4, GPIO.LOW)  

# SPI 설정
spi = spidev.SpiDev()
spi.open(0, 0)
spi.max_speed_hz = 1350000

# 밸브 상태 추적
valve_open = False
last_button_pressed = None  # 마지막으로 눌린 버튼 저장

# MQTT 콜백 함수
def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("Connected to HiveMQ Broker!")
        client.subscribe("sensor/temperature")  # 원하는 토픽 구독
        client.subscribe("solenoid/control")  # 추가
        client.subscribe("button/command")  # 추가
    else:
        print(f"Failed to connect, return code {rc}")

def on_message(client, userdata, msg):
    global valve_open, last_button_pressed
    topic = msg.topic
    payload = msg.payload.decode()
    print(f"Received message: {payload} on topic: {topic}")

    if topic == "solenoid/control":
        if payload == "valve_open":
            GPIO.output(RELAY_PIN, GPIO.HIGH)
            valve_open = True
            print("Solenoid Valve Opened")

            # 버튼이 하나도 안 눌린 상태면 charge 모드 자동 활성화
            if last_button_pressed is None:
                GPIO.output(RELAY_PIN_4, GPIO.HIGH)
                print("Relay 4 ON (Default: charge mode)")

        elif payload == "valve_close":
            GPIO.output(RELAY_PIN, GPIO.LOW)
            valve_open = False
            last_button_pressed = None  # 버튼 초기화

            # 모든 릴레이 OFF
            GPIO.output(RELAY_PIN_1, GPIO.LOW)
            GPIO.output(RELAY_PIN_2, GPIO.LOW)
            GPIO.output(RELAY_PIN_3, GPIO.LOW)
            GPIO.output(RELAY_PIN_4, GPIO.LOW)
            print("Solenoid Valve Closed, All Relays OFF")

    elif topic == "button/command":
        if not valve_open:  # 밸브가 닫혀 있으면 버튼 입력 무시
            print("Valve is closed. Buttons are disabled.")
            return

        # 버튼에 따라 릴레이 핀 상태 변경
        if payload == "1":
            GPIO.output(RELAY_PIN_1, GPIO.HIGH)
            GPIO.output(RELAY_PIN_2, GPIO.LOW)
            GPIO.output(RELAY_PIN_3, GPIO.LOW)
            GPIO.output(RELAY_PIN_4, GPIO.LOW)
            last_button_pressed = 1
            print("Relay 1 ON, Others OFF")
        elif payload == "2":
            GPIO.output(RELAY_PIN_1, GPIO.LOW)
            GPIO.output(RELAY_PIN_2, GPIO.HIGH)
            GPIO.output(RELAY_PIN_3, GPIO.LOW)
            GPIO.output(RELAY_PIN_4, GPIO.LOW)
            last_button_pressed = 2
            print("Relay 2 ON, Others OFF")
        elif payload == "3":
            GPIO.output(RELAY_PIN_1, GPIO.LOW)
            GPIO.output(RELAY_PIN_2, GPIO.LOW)
            GPIO.output(RELAY_PIN_3, GPIO.HIGH)
            GPIO.output(RELAY_PIN_4, GPIO.LOW)
            last_button_pressed = 3
            print("Relay 3 ON, Others OFF")
        elif payload == "charge":
            GPIO.output(RELAY_PIN_1, GPIO.LOW)
            GPIO.output(RELAY_PIN_2, GPIO.LOW)
            GPIO.output(RELAY_PIN_3, GPIO.LOW)
            GPIO.output(RELAY_PIN_4, GPIO.HIGH)
            last_button_pressed = None  # charge 모드에서는 버튼 선택 해제
            print("Relay 4 ON (Charge Mode), Others OFF")
        else:
            print("Invalid button command")

def read_channel(channel):
    adc = spi.xfer2([1, (8 + channel) << 4, 0])
    data = ((adc[1] & 3) << 8) + adc[2]
    return data

def convert_voltage(data):
    return (data * 5.0) / 1023

def convert_temperature(voltage):
    return voltage * 100  # LM35: 10mV/°C

def publish_temperature():
    while True:
        data = read_channel(0)  # CH0
        voltage = convert_voltage(data)
        temperature = convert_temperature(voltage)
        temperature_str = f"{temperature:.1f}"
        client.publish("sensor/temperature", temperature_str)
        print(f"Published temperature: {temperature_str}")
        time.sleep(1)  # 1초마다 온도 값 전송

# MQTT 설정
client = mqtt.Client()
client.username_pw_set(USERNAME, PASSWORD)  # 인증 추가
client.tls_set()  # TLS 설정
client.on_connect = on_connect
client.on_message = on_message
client.connect(BROKER, PORT, 60)

# 온도 브로드캐스트 시작
try:
    publish_temperature_thread = threading.Thread(target=publish_temperature)
    publish_temperature_thread.start()
    client.loop_forever()
except KeyboardInterrupt:
    spi.close()
    GPIO.cleanup()
