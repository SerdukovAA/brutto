import org.bitcoinj.core.Address
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.Script.ScriptType
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

val  long: AtomicLong = AtomicLong();

fun generateBitcoinKeyPair(): Pair<String, String> {
    // Используем MainNetParams для основного сетевого параметра
    val networkParameters: NetworkParameters = MainNetParams.get()

    // Генерируем новый ECKey
    val ecKey = ECKey()

    // Получаем приватный ключ в формате WIF
    val privateKeyWIF = ecKey.getPrivateKeyAsWiF(networkParameters)

    // Получаем публичный ключ в формате биткоин-адреса
    val bitcoinAddress = Address.fromKey(networkParameters, ecKey, ScriptType.P2PKH).toString()

    return Pair(privateKeyWIF, bitcoinAddress)
}

fun worker(id: Int, outputFile: String, btcAddresses: Map<String, Double>, lock: ReentrantLock) {
    while (true) {
        val (privateKey, publicAddress) = generateBitcoinKeyPair()
        print("\r Publicaddress: $publicAddress Privatekey: $privateKey ")

        if(btcAddresses.containsKey(publicAddress)){
            println("\n ($id) Match Found! Privatekey: $privateKey Publicaddress: $publicAddress Balance: ${btcAddresses.get(publicAddress)}")
            lock.lock()
            try {
                File(outputFile).appendText("$privateKey:$publicAddress:${btcAddresses.get(publicAddress)}\n")
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
