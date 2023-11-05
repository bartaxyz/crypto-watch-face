package com.ondrejbarta.cryptowatchface

import android.icu.text.NumberFormat
import android.icu.text.SimpleDateFormat
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.ktor.utils.io.errors.*
import okhttp3.*
import java.util.*
import kotlin.math.abs

class MarketChart(
    val prices: List<List<String>>
    /*
    @SerialName("market_caps")
    val marketCaps: List<List<String>>,
    @SerialName("total_volumes")
    val totalVolumes: List<List<String>>
     */
)

data class MarketChartCache(
    val currency: WatchFaceDataService.Currency,
    val vsCurrency: WatchFaceDataService.VsCurrency,
    val days: Int,
    val timestamp: Long,
    val marketChart: MarketChart
)

open class WatchFaceDataService {
    private val httpClient = OkHttpClient()
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val gistJsonAdapter = moshi.adapter<MarketChart>(MarketChart::class.java)

    var marketChart: MarketChart? = null
    var chartData: MutableList<Float> = mutableListOf()
    var chartDataMax: Float = -1f
    var chartDataMin: Float = -1f
    var firstChartValue: Float = 0f
    var lastChartValue: Float = 0f
    var chartDataReduced: MutableList<Float> = mutableListOf()

    var fetching = false;
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

        cache.removeIf {
            it.currency === currency &&
            it.vsCurrency === vsCurrency &&
            it.days == days &&
            System.currentTimeMillis() - it.timestamp > cacheInvalidationTime
        }


        for ((index, marketChartCache) in cache.withIndex()) {
            if (
                marketChartCache.currency === currency &&
                marketChartCache.vsCurrency === vsCurrency &&
                marketChartCache.days == days &&
                // Check if timestamp isn't older than 10 minutes
                System.currentTimeMillis() - marketChartCache.timestamp  < cacheInvalidationTime
            ) {
                return marketChartCache
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
        if (fetching) return;

        if (currentCurrency !== currency || currentVsCurrency !== vsCurrency || currentDays !== days) {
            cleanData()
        }

        currentCurrency = currency
        currentVsCurrency = vsCurrency
        currentDays = days

        val url = "https://api.coingecko.com/api/v3/coins/" +
                currency.toString() +
                "/market_chart?vs_currency=" +
                vsCurrency.toString() +
                "&days=" + days.toString()
        val request = Request.Builder().url(url).build();
        val cachedMarketChart = cacheLookup(
            currency,
            vsCurrency,
            days
        )

        if (cachedMarketChart !== null) {
            marketChart = cachedMarketChart.marketChart

            processMarketChart()
        } else {
            fetching = true;

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                    fetchFailed = true;
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        fetching = false;

                        Log.i("BOOHOO_RESPONSE", "We got a response")

                        if (!response.isSuccessful) {
                            fetchFailed = true
                            return
                        }

                        val responseBody = response.body?.string();

                        if (responseBody == null) {
                            fetchFailed = true
                            return
                        }

                        Log.i("BOOHOO_RESPONSE", responseBody)

                        marketChart = gistJsonAdapter.fromJson(responseBody)

                        marketChart?.let { cacheInsert(currency, vsCurrency, days, it) }

                        processMarketChart()
                    }
                }
            })
        }
    }

    fun processMarketChart() {
        if (marketChart == null) return;

        chartData = mutableListOf()
        chartDataReduced = mutableListOf()

        val prices = marketChart!!.prices

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