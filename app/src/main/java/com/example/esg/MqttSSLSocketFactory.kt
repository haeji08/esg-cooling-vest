package com.example.esg

import javax.net.ssl.*
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory

class MqttSSLSocketFactory : SSLSocketFactory() {
    private val sslContext: SSLContext

    init {
        sslContext = SSLContext.getInstance("TLS")
        val trustManagers = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers() = arrayOf<java.security.cert.X509Certificate>()

            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}

            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        })

        sslContext.init(null, trustManagers, SecureRandom())
    }

    override fun createSocket(): Socket = sslContext.socketFactory.createSocket()

    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket =
        sslContext.socketFactory.createSocket(s, host, port, autoClose)

    override fun createSocket(host: String?, port: Int): Socket =
        sslContext.socketFactory.createSocket(host, port)

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        sslContext.socketFactory.createSocket(host, port, localHost, localPort)

    override fun createSocket(address: InetAddress?, port: Int): Socket =
        sslContext.socketFactory.createSocket(address, port)

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        sslContext.socketFactory.createSocket(address, port, localAddress, localPort)

    override fun getDefaultCipherSuites(): Array<String> {
        TODO("Not yet implemented")
    }

    override fun getSupportedCipherSuites(): Array<String> {
        TODO("Not yet implemented")
    }
}
