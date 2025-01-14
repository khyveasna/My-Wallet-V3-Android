package com.blockchain.datamanagers.fees

import com.blockchain.fees.FeeType
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import io.reactivex.Single
import org.web3j.utils.Convert
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import java.math.BigInteger

fun FeeDataManager.getFeeOptions(cryptoCurrency: CryptoCurrency): Single<out NetworkFees> =
    when (cryptoCurrency) {
        CryptoCurrency.BTC -> btcFeeOptions.map {
            BitcoinLikeFees(
                it.regularFee,
                it.priorityFee
            )
        }
        CryptoCurrency.BCH -> bchFeeOptions.map {
            BitcoinLikeFees(
                it.regularFee,
                it.priorityFee
            )
        }
        CryptoCurrency.ETHER -> ethFeeOptions.map {
            EthereumFees(
                it.regularFee,
                it.priorityFee,
                it.gasLimit
            )
        }
        CryptoCurrency.XLM -> xlmFeeOptions.map {
            XlmFees(
                CryptoValue.lumensFromStroop(it.regularFee.toBigInteger()),
                CryptoValue.lumensFromStroop(it.priorityFee.toBigInteger())
            )
        }
        CryptoCurrency.PAX -> ethFeeOptions.map {
            EthereumFees(
                it.regularFee,
                it.priorityFee,
                it.gasLimitContract
            )
        }
    }.singleOrError()

sealed class NetworkFees

data class BitcoinLikeFees(
    private val regularFeePerByte: Long,
    private val priorityFeePerByte: Long
) : NetworkFees() {

    val regularFeePerKb: BigInteger = (regularFeePerByte * 1000).toBigInteger()

    val priorityFeePerKb: BigInteger = (priorityFeePerByte * 1000).toBigInteger()
}

data class EthereumFees(
    private val gasPriceRegularGwei: Long,
    private val gasPricePriorityGwei: Long,
    private val gasLimitGwei: Long
) : NetworkFees() {

    val absoluteRegularFeeInWei: CryptoValue =
        CryptoValue.etherFromWei((gasPriceRegularGwei * gasLimitGwei).gweiToWei())

    val absolutePriorityFeeInWei: CryptoValue =
        CryptoValue.etherFromWei((gasPricePriorityGwei * gasLimitGwei).gweiToWei())

    val gasPriceRegularInWei: BigInteger = gasPriceRegularGwei.gweiToWei()

    val gasPricePriorityInWei: BigInteger = gasPricePriorityGwei.gweiToWei()

    val gasLimitInGwei: BigInteger = gasLimitGwei.toBigInteger()
}

data class XlmFees(
    val regularPerOperationFee: CryptoValue,
    val priorityPerOperationFee: CryptoValue
) : NetworkFees()

fun XlmFees.feeForType(feeType: FeeType): CryptoValue = when (feeType) {
    FeeType.Regular -> this.regularPerOperationFee
    FeeType.Priority -> this.priorityPerOperationFee
}

fun Long.gweiToWei(): BigInteger =
    Convert.toWei(this.toBigDecimal(), Convert.Unit.GWEI).toBigInteger()
