package com.parseus.codecinfo.codecinfo

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.*
import android.util.Range
import androidx.annotation.RequiresApi
import com.parseus.codecinfo.*
import com.parseus.codecinfo.codecinfo.colorformats.*
import com.parseus.codecinfo.codecinfo.profilelevels.*
import com.parseus.codecinfo.codecinfo.profilelevels.VP9Levels.*

    private val platformSupportedTypes = arrayOf(
            "audio/3gpp",
            "audio/amr-mb",
            "audio/amr-wb",
            "audio/flac",
            "audio/g711-alaw",
            "audio/g711-mlaw",
            "audio/gsm",
            "audio/mp4a-latm",
            "audio/mpeg",
            "audio/opus",
            "audio/raw",
            "audio/vorbis")

    private val framerateResolutions = arrayOf(
            intArrayOf(176, 144), intArrayOf(320, 240), intArrayOf(640, 480),
            intArrayOf(720, 576), intArrayOf(1280, 720), intArrayOf(1920, 1080),
            intArrayOf(3840, 2160)
    )
    private val framerateClasses = arrayOf(
            "144p", "240p", "480p", "576p", "720p", "1080p", "4K"
    )

    private var mediaCodecInfos: Array<MediaCodecInfo> = emptyArray()
    private var audioCodecSimpleInfoList: ArrayList<CodecSimpleInfo> = ArrayList()
    private var videoCodecSimpleInfoList: ArrayList<CodecSimpleInfo> = ArrayList()


    fun getSimpleCodecInfoList(isAudio: Boolean): ArrayList<CodecSimpleInfo> {
        if (isAudio && audioCodecSimpleInfoList.isNotEmpty()) {
            return audioCodecSimpleInfoList
        } else if (!isAudio && videoCodecSimpleInfoList.isNotEmpty()) {
            return videoCodecSimpleInfoList
        }

        if (mediaCodecInfos.isEmpty()) {
            mediaCodecInfos = if (SDK_INT >= LOLLIPOP) {
                MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            } else {
                @Suppress("DEPRECATION")
                Array(MediaCodecList.getCodecCount()) { i -> MediaCodecList.getCodecInfoAt(i)}
            }
        }

        val codecSimpleInfoList = ArrayList<CodecSimpleInfo>()

        for (mediaCodecInfo in mediaCodecInfos) {
            for (codecId in mediaCodecInfo.supportedTypes) {
                try {
                    mediaCodecInfo.getCapabilitiesForType(codecId)
                } catch (e: Exception) {
                    // Some devices (e.g. Kindle Fire HD) can report a codec in the supported list
                    // but don't really implement it (or it's buggy). In this case just skip this.
                    continue
                }

                val isAudioCodec = mediaCodecInfo.isAudioCodec()

                if (isAudio == isAudioCodec) {
                    val codecSimpleInfo = CodecSimpleInfo(codecId, mediaCodecInfo.name, isAudioCodec,
                            mediaCodecInfo.isEncoder)
                    codecSimpleInfoList.add(codecSimpleInfo)
                }
            }
        }

        val comparator: Comparator<CodecSimpleInfo> = compareBy({it.codecId}, {it.codecName})
        codecSimpleInfoList.sortWith(comparator)

        if (isAudio) {
            audioCodecSimpleInfoList = codecSimpleInfoList
        } else {
            videoCodecSimpleInfoList = codecSimpleInfoList
        }

        return codecSimpleInfoList
    }

    fun getDetailedCodecInfo(context: Context, codecId: String, codecName: String): HashMap<String, String> {
        val mediaCodecInfo = mediaCodecInfos.first { it.name == codecName }
        val capabilities = mediaCodecInfo.getCapabilitiesForType(codecId)
        val isAudio = mediaCodecInfo.isAudioCodec()
        val isEncoder = mediaCodecInfo.isEncoder

        val codecInfoMap = LinkedHashMap<String, String>()

        codecInfoMap[context.getString(R.string.hardware_acceleration)] =
                mediaCodecInfo.isHardwareAccelerated().toString()

        if (SDK_INT >= M) {
            codecInfoMap[context.getString(R.string.max_instances)] = capabilities.maxSupportedInstances.toString()
        }

        if (isAudio) {
            if (SDK_INT >= LOLLIPOP) {
                getAudioCapabilities(context, codecId, capabilities, codecInfoMap)
            }
        } else {
            getVideoCapabilities(context, codecName, capabilities, codecInfoMap)

            if (!isEncoder) {
                if (SDK_INT >= KITKAT) {
                    codecInfoMap[context.getString(R.string.adaptive_playback)] =
                            capabilities.isFeatureSupported(
                                    MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback).toString()
                }

                if (SDK_INT >= O) {
                    codecInfoMap[context.getString(R.string.partial_frames)] =
                            capabilities.isFeatureSupported(
                                    MediaCodecInfo.CodecCapabilities.FEATURE_PartialFrame).toString()
                }

                if (SDK_INT >= LOLLIPOP) {
                    codecInfoMap[context.getString(R.string.secure_playback)] =
                            capabilities.isFeatureSupported(
                                    MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback).toString()
                }
            } else {
                if (SDK_INT >= N) {
                    codecInfoMap[context.getString(R.string.intra_refresh)] =
                            capabilities.isFeatureSupported(
                                    MediaCodecInfo.CodecCapabilities.FEATURE_IntraRefresh).toString()
                }
            }
        }

        if (!isEncoder && SDK_INT >= LOLLIPOP) {
            codecInfoMap[context.getString(R.string.tunneled_playback)] =
                    capabilities.isFeatureSupported(
                            MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback).toString()
        }

        if (isEncoder && SDK_INT >= LOLLIPOP) {
            val encoderCapabilities = capabilities.encoderCapabilities
            val bitrateModesString =
                    "${context.getString(R.string.cbr)}: " +
                            "${encoderCapabilities.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)}" +
                            "\n${context.getString(R.string.cq)}: " +
                            "${encoderCapabilities.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)}" +
                            "\n${context.getString(R.string.vbr)}: " +
                            "${encoderCapabilities.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)}"
            codecInfoMap[context.getString(R.string.bitrate_modes)] = bitrateModesString

            val complexityLower = encoderCapabilities.complexityRange.lower
            val complexityUpper = encoderCapabilities.complexityRange.upper
            var defaultComplexity = 0

            val defaultMediaFormat = capabilities.defaultFormat
            if (defaultMediaFormat.containsKey(MediaFormat.KEY_COMPLEXITY)) {
                defaultComplexity = defaultMediaFormat.getInteger(MediaFormat.KEY_COMPLEXITY)
            }

            val complexityRangeString = if (complexityLower != complexityUpper) {
                "$complexityLower — $complexityUpper (${context.getString(R.string.range_default)}: $defaultComplexity)"
            } else {
                "$complexityLower"
            }

            codecInfoMap[context.getString(R.string.complexity_range)] = complexityRangeString

            @Suppress("UNCHECKED_CAST")
            val qualityRange = if (SDK_INT >= P) {
                encoderCapabilities.qualityRange
            } else {
                // Before P quality range was actually available, but hidden as a private API.
                try {
                    val qualityRangeMethod = encoderCapabilities::class.java
                            .getDeclaredMethod("getQualityRange")
                    qualityRangeMethod.invoke(encoderCapabilities) as Range<Int>
                } catch (e: Exception) { null }
            }

            var defaultQuality = 0

            if (defaultMediaFormat.containsKey("quality")) {
                defaultQuality = defaultMediaFormat.getInteger("quality")
            }
            
            qualityRange?.let {
                val qualityRangeLower = qualityRange.lower
                val qualityRangeUpper = qualityRange.upper

                val qualityRangeString = if (qualityRangeLower != qualityRangeUpper) {
                    "$qualityRangeLower — $qualityRangeUpper (${context.getString(R.string.range_default)}: $defaultQuality)"
                } else {
                    "$qualityRangeLower"
                }

                codecInfoMap[context.getString(R.string.quality_range)] = qualityRangeString
            }
        }

        val profileString = if (codecId.contains("mp4a-latm") || codecId.contains("wma")) {
            context.getString(R.string.profiles)
        } else {
            context.getString(R.string.profile_levels)
        }

        getProfileLevels(context, codecId, codecName, capabilities)?.let {
            codecInfoMap[profileString] = it
        }

        return codecInfoMap
    }

    @RequiresApi(LOLLIPOP)
    private fun getAudioCapabilities(context: Context, codecId: String,
                                     capabilities: MediaCodecInfo.CodecCapabilities,
                                     codecInfoMap: HashMap<String, String>) {
        val audioCapabilities = capabilities.audioCapabilities

        codecInfoMap[context.getString(R.string.input_channels)] =
                adjustMaxInputChannelCount(codecId, audioCapabilities.maxInputChannelCount, capabilities).toString()

        val bitrateRange = audioCapabilities.bitrateRange
        val bitrateRangeString = if (bitrateRange.lower == bitrateRange.upper || bitrateRange.lower == 1) {
            bitrateRange.upper.toBytesPerSecond()
        } else {
            "${bitrateRange.lower.toBytesPerSecond()} \u2014 ${bitrateRange.upper.toBytesPerSecond()}"
        }
        codecInfoMap[context.getString(R.string.bitrate_range)] = bitrateRangeString

        val sampleRates = audioCapabilities.supportedSampleRateRanges
        val sampleRatesString = if (sampleRates.size > 1) {
            val rates = StringBuilder(sampleRates[0].upper.toKiloHertz().toString())

            for (rate in 1 until sampleRates.size) {
                rates.append(", ").append(sampleRates[rate].upper.toKiloHertz())
            }

            rates.append(" KHz")
            rates.toString()
        } else {
            if (sampleRates[0].lower == sampleRates[0].upper) {
                "${sampleRates[0].upper.toKiloHertz()} KHz"
            } else {
                "${sampleRates[0].lower.toKiloHertz()}, ${sampleRates[0].upper.toKiloHertz()} KHz"
            }
        }
        codecInfoMap[context.getString(R.string.sample_rates)] = sampleRatesString
    }

    /**
     * Tries to adjust max input channel count for non-platform codecs.
     *
     * AudioCapabilities incorrectly assumes that non-platform codecs support only one input channel.
     * This function provides a somewhat better, assumed guess.
     */
    private fun adjustMaxInputChannelCount(codecId: String, maxChannelCount: Int,
                                           capabilities: MediaCodecInfo.CodecCapabilities): Int {
        if (maxChannelCount > 1 || (SDK_INT >= O && maxChannelCount > 0)) {
            // The maximum channel count looks like it's been set correctly.
            return maxChannelCount
        }

        if (codecId in platformSupportedTypes) {
            // Platform code should have set a default.
            return maxChannelCount
        }

        if (SDK_INT < P) {
            /*
                mCapabilitiesInfo, a private MediaFormat instance hidden in MediaCodecInfo,
                can actually provide max input channel count (as well as other useful info).
                Unfortunately, with P restricting non-API usage via reflection, I can only hope
                that everything will work fine on newer versions.
             */
            try {
                val capabilitiesInfo = capabilities::class.java.getDeclaredField("mCapabilitiesInfo")
                capabilitiesInfo.isAccessible = true
                val mediaFormat = capabilitiesInfo.get(capabilities) as MediaFormat

                if (mediaFormat.containsKey("max-channel-count")) {
                    return mediaFormat.getString("max-channel-count").toInt()
                }
            } catch (e: Exception) {}
        }

        // The maximum channel count looks incorrect. Adjust it to an assumed default.
        return when (codecId) {
            "audio/ac3" -> 6
            "audio/eac3" -> 16
            // Default to the platform limit, which is 30.
            else -> 30
        }
    }

    private fun getVideoCapabilities(context: Context, codecName: String,
                                     capabilities: MediaCodecInfo.CodecCapabilities,
                                     codecInfoMap: HashMap<String, String>) {
        if (SDK_INT >= LOLLIPOP) {
            val videoCapabilities = capabilities.videoCapabilities

            val bitrateRange = videoCapabilities.bitrateRange
            val framerates = videoCapabilities.supportedFrameRates
            val maxWidth = videoCapabilities.supportedWidths.upper
            val maxHeight = videoCapabilities.getSupportedHeightsFor(maxWidth).upper

            val maxResolution = "${maxWidth}x$maxHeight"
            codecInfoMap[context.getString(R.string.max_resolution)] = maxResolution

            codecInfoMap[context.getString(R.string.max_bitrate)] = bitrateRange.upper.toBytesPerSecond()

            val framerateString = if (framerates.lower == framerates.upper) {
                "${framerates.upper} ${context.getString(R.string.frames_per_second)}"
            } else {
                "${framerates.lower} \u2014 ${framerates.upper} ${context.getString(R.string.frames_per_second)}"
            }
            codecInfoMap[context.getString(R.string.frame_rate)] = framerateString

            codecInfoMap[context.getString(R.string.max_frame_rate_per_resolution)] =
                    getFrameRatePerResolutions(context, videoCapabilities)
        }

        addColorFormats(capabilities, codecName, context, codecInfoMap)
    }

    private fun addColorFormats(capabilities: MediaCodecInfo.CodecCapabilities, codecName: String,
                                context: Context, codecInfoMap: HashMap<String, String>) {
        val colorFormats = capabilities.colorFormats
        val colorFormatStrings = Array(colorFormats.size) { it ->
            var colorFormat = when {
                codecName.contains("brcm", true) -> BroadcomColorFormat.from(colorFormats[it])
                codecName.contains("qcom", true) || codecName.contains("qti", true)
                    || codecName.contains("ittiam", true)
                -> QualcommColorFormat.from(colorFormats[it])
                codecName.contains("OMX.SEC", true) || codecName.contains("Exynos", true)
                -> SamsungColorFormat.from(colorFormats[it])
                codecName.contains("OMX.MTK", true) -> MediaTekColorFormat.from(colorFormats[it])
                codecName.contains("OMX.IMG", true) -> IMGColorFormat.from(colorFormats[it])
                codecName.contains("Marvell", true) -> MarvellColorFormat.from(colorFormats[it])
                codecName.contains("Nvidia", true) -> NvidiaColorFormat.from(colorFormats[it])
                codecName.contains("OMX.ST", true) -> SonyColorFormat.from(colorFormats[it])
                codecName.contains("Renesas", true) -> RenesasColorFormat.from(colorFormats[it])
                codecName.contains("OMX.TI", true) || codecName.contains("INTEL", true)
                -> OtherColorFormat.from(colorFormats[it])
                else -> null
            }

            // When in doubt, use a standard color formats from the SDK...
            if (colorFormat == null) {
                colorFormat = MediaCodecColorFormat.from(colorFormats[it])
            }

            // ...unless it's a standard OpenMAX IL color format that's not defined in the SDK
            // (at least MSVDX/Topaz codecs tend to use some of those)
            if (colorFormat == null) {
                colorFormat = OpenMAXILColorFormat.from(colorFormats[it])
            }

            colorFormat
                    ?: "${context.getString(R.string.unknown)} (${colorFormats[it].toHexHstring()})"
        }.toSortedSet()
        codecInfoMap[context.getString(R.string.color_profiles)] = colorFormatStrings.joinToString("\n")
    }

    @RequiresApi(LOLLIPOP)
    private fun getFrameRatePerResolutions(context: Context,
                                           videoCapabilities: MediaCodecInfo.VideoCapabilities): String {
        val capabilities = StringBuilder()
        var maxFrameRate: Double
        val fpsString = context.getString(R.string.frames_per_second)

        framerateResolutions.forEachIndexed { index, resolution ->
            if (videoCapabilities.isSizeSupported(resolution[0], resolution[1])) {
                maxFrameRate = videoCapabilities.getSupportedFrameRatesFor(resolution[0], resolution[1]).upper
                capabilities.append("${framerateClasses[index]}: ${"%.1f".format(maxFrameRate)} $fpsString\n")
            }
        }

        capabilities.setLength(capabilities.length - 1) // Remove the last \n

        return capabilities.toString()
    }

    @SuppressLint("NewApi")
    private fun getProfileLevels(context: Context, codecId: String, codecName: String,
                                 capabilities: MediaCodecInfo.CodecCapabilities): String? {
        val profileLevels = capabilities.profileLevels
        val stringBuilder = StringBuilder()
        val unknownString = context.getString(R.string.unknown)
        var profile: String?
        var level: String? = ""

        // On versions L and M, VP9 codecCapabilities do not advertise profile level support.
        // In this case, estimate the level from MediaCodecInfo.VideoCapabilities instead.
        // Assume VP9 is not supported before L.
        if (SDK_INT in LOLLIPOP..M && codecId.endsWith("vp9")) {
            val vp9Level = getMaxVP9ProfileLevel(capabilities.videoCapabilities)
            // Assume all platforms before N only support VP9 profile 0.
            profile = VP9Profiles.VP9Profile0.name
            level = VP9Levels.from(vp9Level)!!
            stringBuilder.append("$profile: $level")

            return stringBuilder.toString()
        } else if (profileLevels == null || profileLevels.isEmpty()) {
            return null
        }

        val comparator: Comparator<MediaCodecInfo.CodecProfileLevel> = compareBy{it.profile}
        profileLevels.sortedWith(comparator)
        profileLevels.reversed().distinctBy { it.profile }.reversed().forEach {
            when {
                codecId.contains("mp4a-latm") -> {
                    profile = AACProfiles.from(it.profile)
                }
                codecId.contains("avc") -> {
                    var extension = " "

                    if (codecName.contains("qcom", true) || codecName.contains("qti", true)) {
                        extension = "QOMX"
                    }

                    profile = AVCProfiles.from(it.profile, extension)
                    level = AVCLevels.from(it.level)
                }
                codecId.contains("avs") -> {
                    profile = AVSProfiles.from(it.profile)
                    level = AVSLevels.from(it.level)
                }
                codecId.contains("dolby-vision") -> {
                    profile = DolbyVisionProfiles.from(it.profile)
                    level = DolbyVisionLevels.from(it.level)
                }
                (codecId.contains("3gpp") && !codecName.contains("mpeg4", true))
                        || codecId.contains("sorenson") || codecId.contains("flv") -> {
                    profile = H263Profiles.from(it.profile)
                    level = H263Levels.from(it.level)
                }
                codecId.contains("hevc") -> {
                    profile = HEVCProfiles.from(it.profile)
                    level = HEVCLevels.from(it.level)
                }
                codecId.endsWith("mpeg") || codecId.contains("mpeg2") -> {
                    var extension = " "

                    if (codecName.contains("Renesas", true)) {
                        extension = "OMF_MC"
                    }

                    profile = MPEG2Profiles.from(it.profile, extension)
                    level = MPEG2Levels.from(it.level, extension)
                }
                codecId.contains("mp4v-es") || codecId.contains("divx")
                        || codecId.contains("xvid") || codecId.endsWith("mp4")
                        || (codecId.contains("3gpp") && codecName.contains("mpeg4", true)) -> {
                    var extension = " "

                    if (codecName.contains("qcom", true) || codecName.contains("qti", true)) {
                        extension = "QOMX"
                    } else if (codecName.contains("OMX.SEC", true) || codecName.contains("Exynos", true)) {
                        extension = "OMX_SEC"
                    } else if (codecName.contains("Renesas", true)) {
                        extension = "OMF_MC"
                    }

                    profile = MPEG4Profiles.from(it.profile)
                    level = MPEG4Levels.from(it.level, extension)
                }
                codecId.contains("mvc") -> {
                    profile = MVCProfiles.from(it.profile)
                    level = MVCLevels.from(it.level)
                }
                codecId.contains("vc1") || codecId.contains("asf") || codecId.endsWith("wmv9") -> {
                    profile = VC1Profiles.from(it.profile)
                    level = VC1Levels.from(it.level)
                }
                codecId.contains("vp6") -> {
                    profile = VP6Profiles.from(it.profile)
                    level = null
                }
                codecId.contains("vp8") -> {
                    profile = VP8Profiles.from(it.profile)
                    level = VP8Levels.from(it.level)
                }
                codecId.contains("vp9") -> {
                    profile = VP9Profiles.from(it.profile)
                    level = VP9Levels.from(it.level)
                }
                codecId.contains("wma") -> {
                    profile = WMAProfiles.from(it.profile)
                }
                codecId.contains("wmv") -> {
                    profile = WMVProfiles.from(it.profile)
                    level = WMVLevels.from(it.level)
                }
                else -> {
                    profile = null
                    level = null
                }
            }

            if (profile == null) {
                profile = unknownString
            }

            if (level == null) {
                level = unknownString
            }

            stringBuilder.append(if (level!!.isNotEmpty())
                "$profile (${it.profile.toHexHstring()}): $level (${it.level.toHexHstring()})\n"
                else "$profile (${it.profile.toHexHstring()}\n")
        }

        stringBuilder.setLength(stringBuilder.length - 1) // Remove the last \n
        return stringBuilder.toString()
    }

    /**
     * Needed on M and older to get correct information about VP9 support.
     */
    @RequiresApi(LOLLIPOP)
    private fun getMaxVP9ProfileLevel(capabilities: MediaCodecInfo.VideoCapabilities): Int {
        // https://www.webmproject.org/vp9/levels
        val bitrateMapping = arrayOf(
                intArrayOf(200, VP9Level1.value), intArrayOf(800, VP9Level11.value),
                intArrayOf(1800, VP9Level2.value), intArrayOf(3600, VP9Level21.value),
                intArrayOf(7200, VP9Level3.value), intArrayOf(12000, VP9Level31.value),
                intArrayOf(18000, VP9Level4.value), intArrayOf(30000, VP9Level41.value),
                intArrayOf(60000, VP9Level5.value), intArrayOf(120000, VP9Level51.value),
                intArrayOf(180000, VP9Level52.value))
        var maxBitrateRange = 0

        for (entry in bitrateMapping) {
            val bitrate = entry[0]
            val level = entry[1]
            if (capabilities.bitrateRange.contains(bitrate)) {
                maxBitrateRange = level
            }
        }

        return maxBitrateRange
    }