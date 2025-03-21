package com.example.esg

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.esg.databinding.ActivityMainBinding
import info.mqtt.android.service.MqttAndroidClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var client: MqttAndroidClient
    private val scheme = "ssl" // TLS 보안 연결
    private val host = "7f3e983375b34252860ac87937732983.s1.eu.hivemq.cloud"
    private val port = "8883"
    private val serverUri = "$scheme://$host:$port"

    private var isValveOpen = false
    private var selectedButton: Int? = null
    private val handler = Handler(Looper.getMainLooper())
    private val resetButtonRunnable = Runnable {
        resetAllButtons()
        if (isValveOpen) { // 밸브가 열린 상태일 때만 charge 모드 활성화
            sendMessage("button/command", "charge")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        client = MqttAndroidClient(this, serverUri, MqttClient.generateClientId())
        connect()
        setupUI()
    }

    private fun connect() {
        val option = MqttConnectOptions().apply {
            userName = "esgproj"
            password = "Qwerty01".toCharArray()
            isAutomaticReconnect = true
            isCleanSession = true

            try {
                this.socketFactory = MqttSSLSocketFactory()
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("MQTT", "SSL 설정 오류: ${e.message}")
            }
        }

        client.connect(option, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d("MQTT", "연결 성공")
                subscribeToTemperature()
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("MQTT", "연결 실패: ${exception?.message}")
            }
        })
    }

    private fun subscribeToTemperature() {
        try {
            // 온도 센서에서 받아온 값 표시
            client.subscribe("sensor/temperature", 1) { topic, message ->
                val temperature = message.toString()
                Log.d("MQTT", "Received temperature: $temperature")
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.tvTemperatue.text = "$temperature"
                }
            }
        } catch (e: Exception) {
            Log.e("MQTT", "Failed to subscribe to temperature: ${e.message}")
        }
    }

    private fun setupUI() {
        binding.button.setOnClickListener {
            isValveOpen = !isValveOpen
            val message = if (isValveOpen) "valve_open" else "valve_close"
            sendMessage("solenoid/control", message)
            setButtonState()
        }

        binding.button2.setOnClickListener { setToggleButtonColor(1) }
        binding.button3.setOnClickListener { setToggleButtonColor(2) }
        binding.button4.setOnClickListener { setToggleButtonColor(3) }
    }


    private fun setButtonState() {
        binding.button.text = if (isValveOpen) "끄기" else "켜기"
        val color = if (isValveOpen) R.color.red else R.color.green
        binding.button.setBackgroundColor(ContextCompat.getColor(this, color))

        if (!isValveOpen) {
            resetAllButtons()
        }
    }

    private fun setToggleButtonColor(buttonId: Int) {
        if (!isValveOpen) return

        selectedButton = buttonId
        sendMessage("button/command", buttonId.toString()) // 버튼 선택 시 charge 해제
        handler.removeCallbacks(resetButtonRunnable)
        handler.postDelayed(resetButtonRunnable, 10000) // 10초 후 자동 리셋 (충전 상태로 들어감)

        binding.button2.setBackgroundColor(ContextCompat.getColor(this, if (buttonId == 1) R.color.green else R.color.gray))
        binding.button3.setBackgroundColor(ContextCompat.getColor(this, if (buttonId == 2) R.color.green else R.color.gray))
        binding.button4.setBackgroundColor(ContextCompat.getColor(this, if (buttonId == 3) R.color.green else R.color.gray))
    }


    private fun resetAllButtons() {
        selectedButton = null
        binding.button2.setBackgroundColor(ContextCompat.getColor(this, R.color.gray))
        binding.button3.setBackgroundColor(ContextCompat.getColor(this, R.color.gray))
        binding.button4.setBackgroundColor(ContextCompat.getColor(this, R.color.gray))
    }

    private fun sendMessage(topic: String, payload: String) {
        try {
            val message = MqttMessage(payload.toByteArray())
            client.publish(topic, message)
            Log.d("MQTT", "Message sent: $payload to topic: $topic")
        } catch (e: Exception) {
            Log.e("MQTT", "Failed to send message: ${e.message}")
        }
    }
}