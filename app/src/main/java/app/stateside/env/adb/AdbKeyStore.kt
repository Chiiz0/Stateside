package app.stateside.env.adb

import android.content.Context
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

class AdbKeyStore(context: Context) {

    private val storageDir: File =
        context.createDeviceProtectedStorageContext().noBackupFilesDir
    private val privateKeyFile = File(storageDir, "stateside_adb_rsa.pk8")
    private val certFile = File(storageDir, "stateside_adb_cert.der")

    @Volatile
    private var cachedKey: PrivateKey? = null

    @Volatile
    private var cachedCert: Certificate? = null

    @Synchronized
    fun getOrCreateIdentity(): Pair<PrivateKey, Certificate> {
        val existingKey = cachedKey
        val existingCert = cachedCert
        if (existingKey != null && existingCert != null) {
            return existingKey to existingCert
        }

        if (privateKeyFile.exists() && certFile.exists() &&
            privateKeyFile.length() > 0L && certFile.length() > 0L
        ) {
            runCatching {
                val keySpec = PKCS8EncodedKeySpec(privateKeyFile.readBytes())
                val kf = KeyFactory.getInstance("RSA")
                val priv = kf.generatePrivate(keySpec)
                val cf = CertificateFactory.getInstance("X.509")
                val cert = cf.generateCertificate(ByteArrayInputStream(certFile.readBytes()))
                cachedKey = priv
                cachedCert = cert
                return priv to cert
            }
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val keyPair = kpg.generateKeyPair()
        val cert = generateSelfSignedCertificate(keyPair)

        privateKeyFile.writeBytes(keyPair.private.encoded)
        runCatching {
            privateKeyFile.setReadable(false, false)
            privateKeyFile.setReadable(true, true)
            privateKeyFile.setWritable(false, false)
            privateKeyFile.setWritable(true, true)
        }
        certFile.writeBytes(cert.encoded)

        cachedKey = keyPair.private
        cachedCert = cert
        return keyPair.private to cert
    }

    private fun generateSelfSignedCertificate(keyPair: KeyPair): Certificate {
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 86_400_000L)
        val notAfter = Date(now + 10L * 365 * 86_400_000L)
        val subject = X500Name("CN=StatesideLocalAdb")
        val serial = BigInteger(64, SecureRandom()).abs().max(BigInteger.ONE)
        val sigAlgId = AlgorithmIdentifier(
            PKCSObjectIdentifiers.sha256WithRSAEncryption,
            DERNull.INSTANCE
        )

        val tbsGen = V3TBSCertificateGenerator().apply {
            setSerialNumber(ASN1Integer(serial))
            setSignature(sigAlgId)
            setIssuer(subject)
            setStartDate(Time(notBefore))
            setEndDate(Time(notAfter))
            setSubject(subject)
            setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(keyPair.public.encoded))
        }
        val tbsCert = tbsGen.generateTBSCertificate()

        // 直接使用 Android 系统原生 JCA 签名，彻底规避 R8 混淆下 BouncyCastleProvider 反射查找失败问题
        val signatureBytes = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(tbsCert.getEncoded("DER"))
            sign()
        }

        val certVector = ASN1EncodableVector(3).apply {
            add(tbsCert)
            add(sigAlgId)
            add(DERBitString(signatureBytes))
        }
        val derBytes = DERSequence(certVector).getEncoded("DER")
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(derBytes))
    }
}
