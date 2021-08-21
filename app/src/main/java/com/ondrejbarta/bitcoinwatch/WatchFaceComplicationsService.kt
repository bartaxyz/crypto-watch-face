package com.ondrejbarta.bitcoinwatch;


import android.support.wearable.complications.ComplicationData;

class WatchFaceComplicationsService {
    companion object {
        val BOTTOM_COMPLICATION_ID = 0

        val COMPLICATION_IDS = intArrayOf(
            BOTTOM_COMPLICATION_ID
        )

        val COMPLICATION_SUPPORTED_TYPES = arrayOf(
            intArrayOf(
                ComplicationData.TYPE_RANGED_VALUE,
                ComplicationData.TYPE_ICON,
                ComplicationData.TYPE_SHORT_TEXT,
                ComplicationData.TYPE_SMALL_IMAGE
            )
        )
    }
}

