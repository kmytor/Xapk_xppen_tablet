package com.xppen.inputmapper.daemon

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.WorkerThread
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo

/**
 * ADB Wireless Debugging manager built on libadb-android.
 *
 * The only way to run a shell daemon (UID 2000) without root/Shizuku is to connect to the
 * on-device `adbd` exposed by Wireless Debugging (Android 11+). Android 11+ requires a one-time
 * 6-digit "pairing" step, which libadb-android implements via [pair].
 *
 * Flow:
 *  1. User enables Developer options -> Wireless debugging -> "Pair device with pairing code".
 *     The tablet shows IP, connect port, pairing port and a 6-digit code.
 *  2. App calls [pair] once (saved for later connections).
 *  3. App connects to the connect-port and launches InputDaemonServer via `app_process`
 *     on a `shell:` stream, running with UID 2000 (Shell) -> full stylus pressure + buttons.
 */
class WirelessAdbManager(private val context: Context) : AbsAdbConnectionManager() {

    companion object {
        private const val TAG = "UsbInputService"
        private const val KEY_DIR = "adb_keys"
        private const val PRIVATE_KEY_FILE = "adbkey"
        private const val CERT_FILE = "adbkey.pub.der"
        private const val PREFS_NAME = "WirelessAdb"
        private const val KEY_HOST = "host"
        private const val KEY_CONNECT_PORT = "connect_port"
        private const val KEY_PAIR_PORT = "pair_port"
        private const val KEY_PAIRED = "paired"
        private const val DEVICE_NAME = "XPPenInputMapper"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyDir = File(context.filesDir, KEY_DIR).apply { mkdirs() }

    private val privateKeyFile = File(keyDir, PRIVATE_KEY_FILE)
    private val certFile = File(keyDir, CERT_FILE)

    private var privateKey: PrivateKey
    private var certificate: X509Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        setHostAddress("127.0.0.1")
        val loaded = loadOrGenerateKeyPair()
        privateKey = loaded.first
        certificate = loaded.second
    }

    val savedHost: String? get() = prefs.getString(KEY_HOST, null)
    val savedConnectPort: Int get() = prefs.getInt(KEY_CONNECT_PORT, -1)
    val savedPairPort: Int get() = prefs.getInt(KEY_PAIR_PORT, -1)
    val isPaired: Boolean get() = prefs.getBoolean(KEY_PAIRED, false)

    /** Pair with the on-device adbd using the 6-digit code shown by Wireless debugging. */
    @WorkerThread
    fun pairAndSave(host: String, pairPort: Int, connectPort: Int, code: String): Boolean {
        return try {
            val ok = pair(host, pairPort, code)
            if (ok) {
                prefs.edit()
                    .putString(KEY_HOST, host)
                    .putInt(KEY_CONNECT_PORT, connectPort)
                    .putInt(KEY_PAIR_PORT, pairPort)
                    .putBoolean(KEY_PAIRED, true)
                    .apply()
                Log.i(TAG, "ADB pairing succeeded against $host:$pairPort")
            }
            ok
        } catch (e: Throwable) {
            Log.e(TAG, "ADB pairing failed: ${e.message}", e)
            false
        }
    }

    /**
     * Connect to the paired adbd and launch InputDaemonServer via app_process.
     * Returns true only if the local daemon accepts a connection afterwards.
     */
    @WorkerThread
    fun connectAndLaunchDaemon(): Boolean {
        val host = savedHost ?: return reconnectSaved()
        val connectPort = savedConnectPort
        if (connectPort <= 0) return false
        return connectHostAndLaunch(host, connectPort)
    }

    /** Attempt to reconnect using the last known host/port (no pairing needed). */
    @WorkerThread
    private fun reconnectSaved(): Boolean {
        val host = prefs.getString(KEY_HOST, null) ?: return false
        val port = prefs.getInt(KEY_CONNECT_PORT, -1)
        if (port <= 0) return false
        return connectHostAndLaunch(host, port)
    }

    @WorkerThread
    private fun connectHostAndLaunch(host: String, connectPort: Int): Boolean {
        try {
            if (!isConnected()) {
                val connected = connect(host, connectPort)
                if (!connected) {
                    Log.e(TAG, "ADB connect to $host:$connectPort failed")
                    return false
                }
            }
            Log.i(TAG, "ADB connected to $host:$connectPort, launching daemon...")

            val apkPath = context.packageCodePath
            // Launch the daemon detached so the shell: session can close immediately.
            val launch =
                "CLASSPATH=$apkPath /system/bin/app_process /system/bin " +
                    "com.xppen.inputmapper.daemon.InputDaemonServer > /dev/null 2>&1 &"
            runShellSync("$launch\necho DAEMON_LAUNCHED\n")

            Thread.sleep(400)
            val alive = LocalDaemonClient.instance.tryConnect()
            if (alive) {
                Log.i(TAG, "InputDaemonServer is running and reachable!")
            }
            return alive
        } catch (e: Throwable) {
            Log.e(TAG, "connectAndLaunch failed: ${e.message}", e)
            return false
        }
    }

    /** Runs a shell command synchronously, reading until the stream closes (or timeout). */
    @WorkerThread
    private fun runShellSync(command: String): String? {
        val stream: AdbStream = openStream("shell:$command")
        val input = stream.openInputStream()
        return try {
            val buf = ByteArray(4096)
            val sb = StringBuilder()
            val deadline = System.currentTimeMillis() + 8000
            while (System.currentTimeMillis() < deadline) {
                if (stream.available() <= 0 && stream.isClosed) break
                val n = input.read(buf, 0, buf.size)
                if (n < 0) break
                if (n > 0) sb.append(String(buf, 0, n, Charsets.UTF_8))
            }
            sb.toString().trim()
        } finally {
            try { input.close() } catch (ignored: Exception) {}
            try { stream.close() } catch (ignored: Exception) {}
        }
    }

    // --- Key pair management -------------------------------------------------

    /**
     * Loads the persisted RSA key pair + X.509 certificate, or generates and persists a new one.
     * Returns a Pair(PrivateKey, X509Certificate).
     */
    private fun loadOrGenerateKeyPair(): Pair<PrivateKey, X509Certificate> {
        try {
            if (privateKeyFile.exists() && certFile.exists()) {
                val kf = KeyFactory.getInstance("RSA")
                val priv = kf.generatePrivate(
                    PKCS8EncodedKeySpec(privateKeyFile.readBytes())
                )
                val cf = CertificateFactory.getInstance("X.509")
                val cert = cf.generateCertificate(certFile.inputStream()) as X509Certificate
                return priv to cert
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not load stored ADB key pair, regenerating: ${e.message}")
        }
        return generateAndStore()
    }

    private fun generateAndStore(): Pair<PrivateKey, X509Certificate> {
        try {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
            val keyPair: KeyPair = kpg.generateKeyPair()
            val pub = keyPair.public
            val priv = keyPair.private

            val cert = buildCertificate(priv, pub)

            privateKeyFile.writeBytes(priv.encoded)
            certFile.writeBytes(cert.encoded)
            return priv to cert
        } catch (e: Throwable) {
            throw RuntimeException("Failed to generate ADB key pair", e)
        }
    }

    /**
     * Builds a self-signed X.509 certificate for the given RSA public key using the
     * sun.security.x509 classes bundled by sun-security-android (same approach as README).
     */
    private fun buildCertificate(privateKey: PrivateKey, publicKey: java.security.PublicKey): X509Certificate {
        val subject = "CN=$DEVICE_NAME"
        val algorithmName = "SHA512withRSA"
        val notBefore = Date(System.currentTimeMillis() - 86400000L)
        val notAfter = Date(System.currentTimeMillis() + 365L * 86400000L)

        val extensions = CertificateExtensions()
        extensions.set(
            "SubjectKeyIdentifier",
            SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier)
        )
        extensions.set(
            "PrivateKeyUsage",
            PrivateKeyUsageExtension(notBefore, notAfter)
        )

        val x500Name = X500Name(subject)
        val info = X509CertInfo()
        info.set("version", CertificateVersion(2))
        info.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Integer.MAX_VALUE))
        info.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithmName)))
        info.set("subject", CertificateSubjectName(x500Name))
        info.set("key", CertificateX509Key(publicKey))
        info.set("validity", CertificateValidity(notBefore, notAfter))
        info.set("issuer", CertificateIssuerName(x500Name))
        info.set("extensions", extensions)

        val impl = X509CertImpl(info)
        impl.sign(privateKey, algorithmName)
        return impl
    }

    override fun getPrivateKey(): PrivateKey = privateKey

    override fun getCertificate(): Certificate = certificate

    override fun getDeviceName(): String = DEVICE_NAME
}
