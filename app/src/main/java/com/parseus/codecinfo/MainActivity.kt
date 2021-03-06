package com.parseus.codecinfo

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ShareCompat
import androidx.fragment.app.DialogFragment
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.parseus.codecinfo.adapters.PagerAdapter
import com.parseus.codecinfo.codecinfo.getDetailedCodecInfo
import com.parseus.codecinfo.codecinfo.getSimpleCodecInfoList
import com.parseus.codecinfo.fragments.CodecDetailsFragment
import com.samsung.android.sdk.SsdkVendorCheck
import com.samsung.android.sdk.gesture.Sgesture
import com.samsung.android.sdk.gesture.SgestureHand
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private var gestureHand: SgestureHand? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        val config = RateThisApp.Config(3, 5)
        RateThisApp.init(config)
        RateThisApp.onCreate(this)
        RateThisApp.showRateDialogIfNeeded(this)

        val tabs = tabLayout
        val viewPager = pager.apply {
            val pagerAdapter = PagerAdapter(this@MainActivity, supportFragmentManager)
            adapter = pagerAdapter
            addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabs))
        }
        tabs.addOnTabSelectedListener(object: TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {}
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewPager.currentItem = tab?.position ?: 0
            }
        })
        tabs.setupWithViewPager(viewPager)

        initializeSamsungGesture(viewPager)

        if (isInTwoPaneMode()) {
            return
        }

        if (savedInstanceState != null) {
            supportFragmentManager.executePendingTransactions()
            val fragmentById = supportFragmentManager.findFragmentById(R.id.codecDetailsFragment)
            fragmentById?.let { supportFragmentManager.beginTransaction().remove(fragmentById).commit() }
        }
    }

    private fun initializeSamsungGesture(pager: ViewPager) {
        if (SsdkVendorCheck.isSamsungDevice()) {
            try {
                val gesture = Sgesture()
                gesture.initialize(this)

                if (gesture.isFeatureEnabled(Sgesture.TYPE_HAND_PRIMITIVE)) {
                    gestureHand = SgestureHand(Looper.getMainLooper(), gesture)
                    gestureHand?.start(Sgesture.TYPE_HAND_PRIMITIVE) { info ->
                        if (info.angle in 225..315) {        // to the left
                            tabLayout.setScrollPosition(0, 0f, true)
                            pager.currentItem = 0
                        } else if (info.angle in 45..135) {  // to the right
                            tabLayout.setScrollPosition(1, 0f, true)
                            pager.currentItem = 1
                        }
                    }
                }
            } catch (e: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gestureHand?.stop()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)

        if (isInTwoPaneMode()) {
            supportFragmentManager.findFragmentByTag("SINGLE_PANE_DETAILS")?.let {
                val dialog = (it as DialogFragment).dialog
                if (dialog != null && dialog.isShowing) {
                    it.dismiss()
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        val id = item?.itemId

        if (id == R.id.menu_item_share) {
            val fragmentById = supportFragmentManager.findFragmentById(R.id.codecDetailsFragment) as CodecDetailsFragment?
            val codecId = fragmentById?.codecId
            val codecName = fragmentById?.codecName

            val codecShareOptions = if (isInTwoPaneMode()
                    && (codecId != null && codecName != null)) {
                arrayOf(
                        getString(R.string.codec_list),
                        getString(R.string.codec_all_info),
                        getString(R.string.codec_details_selected))
            } else {
                arrayOf(
                        getString(R.string.codec_list),
                        getString(R.string.codec_all_info))
            }

            var alertDialog: AlertDialog? = null
            val builder = AlertDialog.Builder(this)
            builder.setTitle(R.string.choose_share)
            builder.setSingleChoiceItems(codecShareOptions, -1) { _, option ->
                launchShareIntent(option)
                alertDialog!!.dismiss()
            }
            alertDialog = builder.create()
            alertDialog.show()

            return true
        } else if (id == R.id.menu_item_about_app) {
            val alertDialogBuilder = AlertDialog.Builder(this)
            val dialogView = layoutInflater.inflate(R.layout.about_app_dialog, null)
            alertDialogBuilder.setView(dialogView)
            val alertDialog = alertDialogBuilder.create()

            val okButton: View = dialogView.findViewById(R.id.ok_button)
            okButton.setOnClickListener { alertDialog.dismiss() }

            try {
                val versionTextView: TextView = dialogView.findViewById(R.id.version_text_view)
                versionTextView.text = getString(R.string.app_version, packageManager.getPackageInfo(packageName, 0).versionName)
            } catch (e : Exception) {}

            alertDialog.show()
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.clear()
        menuInflater.inflate(R.menu.app_bar_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun launchShareIntent(option: Int) {
        val codecStringBuilder = StringBuilder()

        when (option) {
            0 -> {
                codecStringBuilder.append("${getString(R.string.codec_list)}:\n\n")
                val codecSimpleInfoList = getSimpleCodecInfoList(true)
                codecSimpleInfoList.addAll(getSimpleCodecInfoList(false))

                for (info in codecSimpleInfoList) {
                    codecStringBuilder.append("$info\n")
                }
            }

            1 -> {
                codecStringBuilder.append("${getString(R.string.codec_list)}:\n")
                val codecSimpleInfoList = getSimpleCodecInfoList(true)
                codecSimpleInfoList.addAll(getSimpleCodecInfoList(false))

                for (info in codecSimpleInfoList) {
                    codecStringBuilder.append("\n$info\n")
                    val codecInfoMap = getDetailedCodecInfo(this, info.codecId, info.codecName)

                    for (key in codecInfoMap.keys) {
                        val stringToAppend = if (key != getString(R.string.bitrate_modes)
                            && key != getString(R.string.color_profiles)
                            && key != getString(R.string.profile_levels)
                            && key != getString(R.string.max_frame_rate_per_resolution)) {
                            "$key: ${codecInfoMap[key]}\n"
                        } else {
                            "$key:\n${codecInfoMap[key]}\n"
                        }
                        codecStringBuilder.append(stringToAppend)
                    }
                }
            }

            2 -> {
                val fragmentById = supportFragmentManager.findFragmentById(R.id.codecDetailsFragment) as CodecDetailsFragment
                val codecId = fragmentById.codecId
                val codecName = fragmentById.codecName

                val codecInfoMap = getDetailedCodecInfo(this, codecId!!, codecName!!)
                codecStringBuilder.append("${getString(R.string.codec_details)}: $codecName\n\n")

                for (key in codecInfoMap.keys) {
                    val stringToAppend = if (key != getString(R.string.bitrate_modes)
                            && key != getString(R.string.color_profiles)
                            && key != getString(R.string.profile_levels)
                            && key != getString(R.string.max_frame_rate_per_resolution)) {
                        "$key: ${codecInfoMap[key]}\n"
                    } else {
                        "$key:\n${codecInfoMap[key]}\n"
                    }
                    codecStringBuilder.append(stringToAppend)
                }
            }
        }

        ShareCompat.IntentBuilder.from(this).setType("text/plain")
                .setText(codecStringBuilder.toString()).startChooser()
    }

}
