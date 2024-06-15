import org.bitcoinj.core.Base58
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECPrivateKey
import org.bouncycastle.jce.interfaces.ECPublicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.encoders.Hex
import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

val  long: AtomicLong = AtomicLong();

fun generateKeyAndAddress(): Pair<String, String> {
    Security.addProvider(BouncyCastleProvider())
    val ecSpec = ECNamedCurveTable.getParameterSpec("P-256")
    val g = KeyPairGenerator.getInstance("ECDSA", "BC")
    g.initialize(ECGenParameterSpec("P-256"), SecureRandom())

    val pair = g.generateKeyPair()
    val privateKey = pair.private as ECPrivateKey
    val publicKey = pair.public as ECPublicKey

    val publicKeyBytes = publicKey.q.getEncoded(false).copyOfRange(1, 65)
    val address = publicKeyToAddress(publicKeyBytes)

    return Pair(Hex.toHexString(privateKey.d.toByteArray()), address)
}

fun publicKeyToAddress(publicKeyBytes: ByteArray): String {
    val sha256 = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
    val ripemd160 = RIPEMD160Digest()
    ripemd160.update(sha256, 0, sha256.size)
    val ripemd160Result = ByteArray(ripemd160.digestSize)
    ripemd160.doFinal(ripemd160Result, 0)

    val networkVersion = byteArrayOf(0x00)
    val addressBytes = networkVersion + ripemd160Result
    val checksum = sha256Checksum(addressBytes)
    val fullAddress = addressBytes + checksum

    return Base58.encode(fullAddress)
}

fun sha256Checksum(input: ByteArray): ByteArray {
    val firstSHA = MessageDigest.getInstance("SHA-256").digest(input)
    val secondSHA = MessageDigest.getInstance("SHA-256").digest(firstSHA)
    return secondSHA.copyOfRange(0, 4)
}

fun worker(id: Int, outputFile: String, btcAddresses: Map<String, Double>, lock: ReentrantLock) {
    while (true) {
        val (privateKey, publicAddress) = generateKeyAndAddress()
        print("\r (${long.incrementAndGet()}) Publicaddress: $publicAddress Privatekey: $privateKey ")

        if(btcAddresses.containsKey(publicAddress)){
            println("($id) Match Found! Privatekey: $privateKey Publicaddress: $publicAddress Balance: ${btcAddresses.get(publicAddress)}")
            println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
            lock.lock()
            try {
                File(outputFile).appendText("$privateKey:$publicAddress\n")
            } finally {
                lock.unlock()
            }
        }

    }
}


fun main(args: Array<String>) {
    val numThreads = Runtime.getRuntime().availableProcessors()
    val outputFile = "out.txt"

    // Mmdrza.Com
    val btcAddresses = parseCsvToMap("Latest_Rich_P2PKH_Bitcoin_Address_Balance.csv") ///https://github.com/Pymmdrza/Rich-Address-Wallet/tree/main

    val lock = ReentrantLock()
    val threadPool = Executors.newFixedThreadPool(numThreads)

    repeat(numThreads) { id ->
        threadPool.submit { worker(id, outputFile, btcAddresses, lock) }
    }

    threadPool.shutdown()
}

fun parseCsvToMap(filePath: String): Map<String, Double> {
    val addressBalanceMap = mutableMapOf<String, Double>()
    File(filePath).useLines { lines ->
        lines.drop(1) // Пропускаем заголовок
            .forEach { line ->
                val (address, balance) = line.split(",")
                addressBalanceMap[address] = balance.toDouble()
            }
    }
    return addressBalanceMap
}
