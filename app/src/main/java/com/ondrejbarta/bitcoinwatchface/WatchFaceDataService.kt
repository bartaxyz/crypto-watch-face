package com.ondrejbarta.bitcoinwatchface

import android.icu.text.NumberFormat
import android.icu.text.SimpleDateFormat
import drewcarlson.coingecko.CoinGeckoService
import drewcarlson.coingecko.models.coins.MarketChart
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.network.sockets.*
import java.util.*
import kotlin.math.abs


data class MarketChartCache(
    val currency: WatchFaceDataService.Currency,
    val vsCurrency: WatchFaceDataService.VsCurrency,
    val days: Int,
    val timestamp: Long,
    val marketChart: MarketChart
)

open class WatchFaceDataService {
    lateinit var marketChart: MarketChart
    var chartData: MutableList<Float> = mutableListOf()
    var chartDataMax: Float = -1f
    var chartDataMin: Float = -1f
    var firstChartValue: Float = 0f
    var lastChartValue: Float = 0f
    var chartDataReduced: MutableList<Float> = mutableListOf()

    var fetchFailed = false;

    var currentCurrency: Currency = Currency.bitcoin
    var currentVsCurrency: VsCurrency = VsCurrency.EUR
    var currentDays: Int = 1

    var cache = mutableListOf<MarketChartCache>()

    enum class Currency {
        bitcoin, ethereum, dogecoin
    }

    enum class VsCurrency {
        USD, EUR
    }

    fun cleanData() {
        chartData = mutableListOf()
        chartDataMax = -1f
        chartDataMin = -1f
        firstChartValue = 0f
        lastChartValue = 0f
        chartDataReduced = mutableListOf()
    }

    // 30 minutes
    var cacheInvalidationTime = (30 * 60 * 1000).toLong();

    fun cacheLookup(
        currency: Currency,
        vsCurrency: VsCurrency,
        days: Int
    ): MarketChartCache? {
        val indexesToRemove = mutableListOf<Int>();

        for ((index, marketChartCache) in cache.withIndex()) {
            if (
                marketChartCache.currency === currency &&
                marketChartCache.vsCurrency === vsCurrency &&
                marketChartCache.days == days
            ) {
                // Check if timestamp isn't older than 10 minutes
                if (System.currentTimeMillis() - marketChartCache.timestamp  < cacheInvalidationTime) {
                    return marketChartCache
                } else {
                    cache.removeAt(index);
                }
            }
        }

        return null
    }

    fun getLastSyncMessage(): String {
        if (fetchFailed) {
            return "Fetch failed, will try again in 1 minute";
        }

        val marketChartCache = cacheLookup(currentCurrency, currentVsCurrency, days = 1);

        if (marketChartCache == null) {
            return "Fetching data...";
        }

        val formatter = SimpleDateFormat("h:mm a");
        val date = Date(marketChartCache.timestamp)
        val dateString = formatter.format(date);

        return "Last sync at " + dateString;
    }

    fun cacheInsert(
        currency: Currency,
        vsCurrency: VsCurrency,
        days: Int,
        marketChart: MarketChart
    ) {
        cache.add(
            MarketChartCache(
                currency = currency,
                vsCurrency = vsCurrency,
                days = days,
                marketChart = marketChart,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun fetchChartData(
        currency: Currency = Currency.bitcoin,
        vsCurrency: VsCurrency = VsCurrency.USD,
        days: Int = 1
    ) {
        if (currentCurrency !== currency || currentVsCurrency !== vsCurrency || currentDays !== days) {
            cleanData()
        }

        currentCurrency = currency
        currentVsCurrency = vsCurrency
        currentDays = days

        val httpClient = HttpClient(OkHttp) {
            expectSuccess = false
        }
        val coinGecko = CoinGeckoService(httpClient)

        val cachedMarketChart = cacheLookup(
            currency,
            vsCurrency,
            days
        )

        if (cachedMarketChart !== null) {
            marketChart = cachedMarketChart.marketChart
        } else {
            try {
                marketChart = coinGecko.getCoinMarketChartById(
                    currency.toString(),
                    vsCurrency.toString(),
                    days
                );
            } catch (error: Exception) {
                fetchFailed = true;
                return;
            }

            cacheInsert(
                currency,
                vsCurrency,
                days,
                marketChart
            )
        }

        chartData = mutableListOf()
        chartDataReduced = mutableListOf()

        val prices = marketChart.prices

        firstChartValue = prices[0][1].toFloat()
        lastChartValue = prices[prices.lastIndex][1].toFloat()

        for ((index, dataPoint) in prices.withIndex()) {
            val dataPointValue = dataPoint[1].toFloat()
            chartData.add(dataPointValue)

            if (index % 4 == 0) {
                chartDataReduced.add(dataPointValue)
            }
            if (chartDataMax == -1f) {
                chartDataMax = dataPointValue;
            }
            if (chartDataMin == -1f) {
                chartDataMin = dataPointValue;
            }
            if (chartDataMax < dataPointValue) {
                chartDataMax = dataPointValue
            }
            if (chartDataMin > dataPointValue) {
                chartDataMin = dataPointValue
            }
        }
    }

    fun getCurrencyLabel(): String {
        if (currentCurrency == Currency.bitcoin) {
            return "BTC";
        } else if (currentCurrency == Currency.ethereum) {
            return "ETH";
        } else if (currentCurrency == Currency.dogecoin) {
            return "DOGE";
        }
        return currentCurrency.toString();
    }

    fun getFormattedVsCurrencyPrice(): String {
        val format: NumberFormat = NumberFormat.getCurrencyInstance()
        format.setMaximumFractionDigits(2)
        format.setCurrency(
            android.icu.util.Currency.getInstance(currentVsCurrency.toString())
        )
        if (chartData.isEmpty()) {
            return format.format(0).replace('0', '-');
        }
        return format.format(chartData[chartData.lastIndex])
    }
    fun getFormattedVsCurrencyDelta(): String {
        val format: NumberFormat = NumberFormat.getCurrencyInstance()
        format.setMaximumFractionDigits(2)
        format.setCurrency(
            android.icu.util.Currency.getInstance(currentVsCurrency.toString())
        )
        var formattedValue: String = "-";
        if (!chartData.isEmpty()){
            formattedValue = format.format(
                abs(chartData[0] - chartData[chartData.lastIndex])
            );
        } else {
            formattedValue = format.format(0).replace('0', '-')
        }
        return "(" + getTrendingSign() + formattedValue + ")"
    }

    fun getTrendingSign(): String {
        if (firstChartValue > lastChartValue) {
            return "-";
        } else if (lastChartValue > firstChartValue) {
            return "+";
        }
        return "";
    }
}