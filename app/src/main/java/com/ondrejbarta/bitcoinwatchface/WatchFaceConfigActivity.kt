package com.ondrejbarta.bitcoinwatchface

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.wearable.complications.ComplicationProviderInfo
import android.support.wearable.complications.ProviderInfoRetriever
import android.support.wearable.view.WearableRecyclerView
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView


class WatchFaceConfigActivity : Activity(), View.OnClickListener {
    val COMPLICATION_CONFIG_REQUEST_CODE = 1001
    val UPDATE_COLORS_CONFIG_REQUEST_CODE = 1002

    enum class ComplicationLocation {
        BOTTOM
    }

    private var mWearableRecyclerView: WearableRecyclerView? = null
    private var mAdapter = null

    private val mLeftComplicationId = 0
    private val mRightComplicationId = 0

    // Selected complication id by user.
    private val mSelectedComplicationId = 0

    // ComponentName used to identify a specific service that renders the watch face.
    private val mWatchFaceComponentName: ComponentName? = null

    // Required to retrieve complication data from watch face for preview.
    private val mProviderInfoRetriever: ProviderInfoRetriever? = null

    private var mBottomComplicationBackground: ImageView? = null

    private var mBottomComplication: ImageButton? = null

    private var mDefaultAddComplicationDrawable: Drawable? = null


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_config);

        mDefaultAddComplicationDrawable = getDrawable(R.drawable.add_complication);

        // TODO: Step 3, initialize 1


        // Sets up left complication preview.
        mBottomComplicationBackground = findViewById(R.id.bottom_complication_background) as ImageView;
        mBottomComplication = findViewById(R.id.bottom_complication) as ImageButton;
        mBottomComplication!!.setOnClickListener(this);

        // Sets default as "Add Complication" icon.
        mBottomComplication!!.setImageDrawable(mDefaultAddComplicationDrawable);
        mBottomComplicationBackground!!.setVisibility(View.INVISIBLE);

    }


    override fun onClick(view: View) {
        if (view == mBottomComplication) {
            launchComplicationHelperActivity(ComplicationLocation.BOTTOM)
        }
    }

    private fun launchComplicationHelperActivity(complicationLocation: ComplicationLocation) {}


    fun updateComplicationViews(
        watchFaceComplicationId: Int, complicationProviderInfo: ComplicationProviderInfo?
    ) {
        if (watchFaceComplicationId == mLeftComplicationId) {
            if (complicationProviderInfo != null) {
                mBottomComplication!!.setImageIcon(complicationProviderInfo.providerIcon)
                mBottomComplicationBackground!!.setVisibility(View.VISIBLE)
            } else {
                mBottomComplication!!.setImageDrawable(mDefaultAddComplicationDrawable)
                mBottomComplicationBackground!!.setVisibility(View.INVISIBLE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        // TODO: Step 3, update views
    }
}