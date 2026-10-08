package com.pandagallery.app.data.compression

import android.media.MediaCodecList
import android.media.MediaFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageFormatSupport @Inject constructor() {
    val isAvifEncodingSupported: Boolean by lazy {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
            codec.isEncoder && runCatching {
                codec.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AV1)
                    .videoCapabilities
                    .isSizeSupported(AVIF_TILE_SIZE, AVIF_TILE_SIZE)
            }.getOrDefault(false)
        }
    }

    private companion object {
        const val AVIF_TILE_SIZE = 512
    }
}
