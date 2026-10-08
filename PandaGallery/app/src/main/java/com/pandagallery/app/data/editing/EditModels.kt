package com.pandagallery.app.data.editing

enum class EditorTab { TRANSFORM, FILTERS, TONE, DECORATIONS, AI_TOOLS }
enum class DecorationSubTool { DRAW, TEXT, STICKER, MOSAIC }
enum class AiSubTool { OBJECT_ERASER, PORTRAIT, REMASTER, SUBJECT_LIFT, GENERATIVE_EDIT }
enum class PortraitLightingMode(val displayName: String) {
    STUDIO("Studio"),
    HIGH_KEY_MONO("High-key Mono"),
    LOW_KEY_MONO("Low-key Mono"),
    BACKDROP_BLUR("Backdrop Blur"),
    COLOR_POINT("Color Point"),
    STAGE_LIGHT("Stage Light"),
    SPIN("Spin Bokeh"),
    ZOOM("Zoom Bokeh"),
    COMIC("Comic"),
    CARTOON_3D("3D Cartoon"),
    WATERCOLOR("Watercolor"),
    SKETCH("Sketch"),
}
enum class PenType(val displayName: String) {
    REGULAR("Pen"),
    CALLIGRAPHY("Calligraphy"),
    HIGHLIGHTER("Highlighter"),
    NEON("Neon"),
    PENCIL("Pencil"),
    MOSAIC_PIXEL("Mosaic Pixel"),
    MOSAIC_BLUR("Privacy Blur"),
}
enum class TextBackgroundStyle(val displayName: String) {
    NONE("None"),
    SOLID("Solid"),
    CAPSULE("Capsule"),
    OUTLINE("Outline"),
}
enum class TextStyleFont(val displayName: String) {
    DEFAULT("Default"),
    SERIF("Serif"),
    MONO("Mono"),
    CURSIVE("Cursive"),
}

enum class CropAspect(val label: String) {
    ORIGINAL("Original"),
    FREE("Free"),
    SQUARE("1:1"),
    FOUR_THREE("4:3"),
    THREE_FOUR("3:4"),
    SIXTEEN_NINE("16:9"),
    NINE_SIXTEEN("9:16"),
    TWO_THREE("2:3"),
    THREE_TWO("3:2"),
    FULL("Full"),
}

enum class ImageFilter(val displayName: String) {
    NONE("Original"),
    VIVID("Vivid"),
    WARM("Warm"),
    COOL("Cool"),
    SOFT("Soft"),
    MONO("Mono"),
    NOIR("Noir"),
    FILM("Film"),
    VINTAGE("Vintage"),
    FADE("Fade"),
    BLOSSOM("Blossom"),
    IVORY("Ivory"),
    FOREST("Forest"),
    BREEZE("Breeze"),
    SUNSET("Sunset"),
}

enum class CurvePreset(val displayName: String) {
    NONE("None"),
    FILMIC("Filmic"),
    PUNCHY("Punchy"),
    FADED("Faded"),
    HIGH_KEY("High Key"),
    DEEP_SHADOW("Deep Shadow"),
}

data class IntCrop(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

data class NormalizedCrop(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    fun sanitized(): NormalizedCrop {
        val safeLeft = left.coerceIn(0f, .95f)
        val safeTop = top.coerceIn(0f, .95f)
        return NormalizedCrop(
            left = safeLeft,
            top = safeTop,
            right = right.coerceIn(safeLeft + .05f, 1f),
            bottom = bottom.coerceIn(safeTop + .05f, 1f),
        )
    }
}

enum class CropShape(val displayName: String) {
    RECTANGLE("Rectangle"),
    ROUNDED_RECT("Rounded"),
    OVAL("Oval"),
}

enum class ExportFormat(val displayName: String, val mimeType: String, val extension: String) {
    JPEG("JPEG", "image/jpeg", "jpg"),
    WEBP("WEBP", "image/webp", "webp"),
}

data class CurveControlPoint(val x: Float, val y: Float)

data class AreaMosaic(
    val rect: NormalizedCrop = NormalizedCrop(0.3f, 0.3f, 0.7f, 0.7f),
    val shape: CropShape = CropShape.RECTANGLE,
    val isBlur: Boolean = false,
    val intensity: Float = 16f,
)

data class TextOverlay(
    val text: String,
    val position: NormalizedPoint = NormalizedPoint(.5f, .5f),
    val color: Long = 0xffffffff,
    val sizeFraction: Float = .075f,
    val backgroundStyle: TextBackgroundStyle = TextBackgroundStyle.NONE,
    val fontStyle: TextStyleFont = TextStyleFont.DEFAULT,
    val rotationDegrees: Float = 0f,
    val scale: Float = 1f,
    val textAlign: String = "Center",
)

data class StickerOverlay(
    val symbol: String,
    val position: NormalizedPoint = NormalizedPoint(.5f, .5f),
    val sizeFraction: Float = .16f,
    val rotationDegrees: Float = 0f,
    val scale: Float = 1f,
    val flipHorizontal: Boolean = false,
)

enum class HslColorChannel(val displayName: String, val colorHex: Long) {
    RED("Red", 0xFFFF3B30),
    ORANGE("Orange", 0xFFFF9500),
    YELLOW("Yellow", 0xFFFFCC00),
    GREEN("Green", 0xFF34C759),
    CYAN("Cyan", 0xFF32ADE6),
    BLUE("Blue", 0xFF007AFF),
    PURPLE("Purple", 0xFF5856D6),
    MAGENTA("Magenta", 0xFFAF52DE),
}

data class HslAdjustment(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f,
)

enum class CurveChannel(val displayName: String, val colorHex: Long) {
    MASTER("RGB", 0xFFFFFFFF),
    RED("Red", 0xFFFF3B30),
    GREEN("Green", 0xFF34C759),
    BLUE("Blue", 0xFF007AFF),
}

data class ImageEditState(
    val rotationDegrees: Int = 0,
    val straightenDegrees: Float = 0f,
    val perspectiveHorizontal: Float = 0f,
    val perspectiveVertical: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropAspect: CropAspect = CropAspect.ORIGINAL,
    val cropShape: CropShape = CropShape.RECTANGLE,
    val cropOffset: NormalizedPoint = NormalizedPoint(0f, 0f),
    val crop: NormalizedCrop = NormalizedCrop(),
    val lightBalance: Float = 0f,
    val brightness: Float = 0f,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val sharpness: Float = 0f,
    val definition: Float = 0f,
    val vignette: Float = 0f,
    val whiteBalanceTemperature: Float = 0f,
    val whiteBalanceTint: Float = 0f,
    val hueShift: Float = 0f,
    val curvePreset: CurvePreset = CurvePreset.NONE,
    val curveChannel: CurveChannel = CurveChannel.MASTER,
    val curveMaster: Float = 0f,
    val curveRed: Float = 0f,
    val curveGreen: Float = 0f,
    val curveBlue: Float = 0f,
    val curvePoints: Map<CurveChannel, List<CurveControlPoint>> = emptyMap(),
    val hslAdjustments: Map<HslColorChannel, HslAdjustment> = emptyMap(),
    val filter: ImageFilter = ImageFilter.NONE,
    val filterIntensity: Float = 1.0f,
    val autoEnhanced: Boolean = false,
    val markup: List<MarkupStroke> = emptyList(),
    val areaMosaics: List<AreaMosaic> = emptyList(),
    val eraseStrokes: List<MarkupStroke> = emptyList(),
    val eraseShadows: Boolean = false,
    val eraseReflections: Boolean = false,
    val textOverlays: List<TextOverlay> = emptyList(),
    val stickers: List<StickerOverlay> = emptyList(),
    val remasterDetailLevel: Float = 0f,
    val remasterSplitSliderFraction: Float = 0.5f,
    val portraitBlur: Float = 0f,
    val portraitLighting: PortraitLightingMode = PortraitLightingMode.STUDIO,
    val faceSmoothness: Float = 0f,
    val faceTone: Float = 0f,
    val faceEyeBrighten: Float = 0f,
    val faceSlim: Float = 0f,
    val faceRedEyeCorrection: Boolean = false,
    val faceRelightAngle: Float = 45f,
    val faceRelightIntensity: Float = 0f,
    val selectedFaceIndex: Int = 0,
    val isSubjectLifted: Boolean = false,
    val mosaicBlockSize: Float = 16f,
    val mosaicBlurRadius: Float = 15f,
    val selectedTextFont: TextStyleFont = TextStyleFont.DEFAULT,
    val selectedTextBgStyle: TextBackgroundStyle = TextBackgroundStyle.NONE,
    val documentCorners: DocumentCorners? = null,
    val documentEnhancement: DocumentEnhancementMode = DocumentEnhancementMode.ORIGINAL,
    val exportFormat: ExportFormat = ExportFormat.JPEG,
    val exportResolutionPercent: Int = 100,
    val stripLocationData: Boolean = false,
) {
    val hasChanges: Boolean get() = this != ImageEditState()
}

data class NormalizedPoint(val x: Float, val y: Float)

data class DocumentCorners(
    val topLeft: NormalizedPoint = NormalizedPoint(0.08f, 0.08f),
    val topRight: NormalizedPoint = NormalizedPoint(0.92f, 0.08f),
    val bottomRight: NormalizedPoint = NormalizedPoint(0.92f, 0.92f),
    val bottomLeft: NormalizedPoint = NormalizedPoint(0.08f, 0.92f),
) {
    fun toList(): List<NormalizedPoint> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun withUpdatedCorner(index: Int, point: NormalizedPoint): DocumentCorners {
        val clamped = NormalizedPoint(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
        return when (index) {
            0 -> copy(topLeft = clamped)
            1 -> copy(topRight = clamped)
            2 -> copy(bottomRight = clamped)
            3 -> copy(bottomLeft = clamped)
            else -> this
        }
    }
}

enum class DocumentEnhancementMode(val displayName: String) {
    ORIGINAL("Original Color"),
    CLEAN("Clean Document"),
    BLACK_AND_WHITE("B&W Scan"),
}

data class MarkupStroke(
    val points: List<NormalizedPoint>,
    val color: Long = 0xffff3b30,
    val widthFraction: Float = .008f,
    val penType: PenType = PenType.REGULAR,
    val opacity: Float = 1.0f,
)

enum class VideoSpeed(val multiplier: Float, val label: String) {
    QUARTER(0.25f, "0.25x (Slow)"),
    HALF(0.5f, "0.5x"),
    NORMAL(1.0f, "1.0x"),
    DOUBLE(2.0f, "2.0x"),
    QUAD(4.0f, "4.0x"),
}

enum class VideoExportResolution(val label: String, val height: Int) {
    FHD_1080P("1080p FHD", 1080),
    UHD_4K("4K UHD", 2160),
    HD_720P("720p HD", 720),
}

enum class BackgroundMusicTrack(val displayName: String) {
    NONE("None"),
    ACOUSTIC_BREEZE("Acoustic Breeze"),
    CHILL_LOFI("Chill Lo-Fi"),
    CINEMATIC_PULSE("Cinematic Pulse"),
    SUNSET_GROOVE("Sunset Groove"),
}

data class VideoEditState(
    val trimStartMs: Long = 0,
    val trimEndMs: Long = 0,
    val rotationDegrees: Int = 0,
    val mute: Boolean = false,
    val speed: VideoSpeed = VideoSpeed.NORMAL,
    val exportResolution: VideoExportResolution = VideoExportResolution.FHD_1080P,
    val backgroundMusic: BackgroundMusicTrack = BackgroundMusicTrack.NONE,
    val customAudioUri: String? = null,
    val customAudioTitle: String? = null,
    val bgmVolume: Float = 0.5f,
    val videoAudioVolume: Float = 1.0f,
    val audioDucking: Boolean = true,
    val audioEraserNoiseReduction: Boolean = false,
    val isInstantSlowMoActive: Boolean = false,
) {
    val hasBackgroundAudio: Boolean
        get() = backgroundMusic != BackgroundMusicTrack.NONE || !customAudioUri.isNullOrBlank()

    val effectiveBgmVolume: Float
        get() = if (audioDucking && !mute && videoAudioVolume > 0.05f) {
            (bgmVolume * 0.35f).coerceIn(0f, 1f)
        } else {
            bgmVolume.coerceIn(0f, 1f)
        }
}

internal fun normalizedTrimRange(startMs: Long, endMs: Long, durationMs: Long): LongRange {
    val duration = durationMs.coerceAtLeast(MINIMUM_TRIM_MS)
    val start = startMs.coerceIn(0, duration - MINIMUM_TRIM_MS)
    val end = endMs.coerceIn(start + MINIMUM_TRIM_MS, duration)
    return start..end
}

internal fun centeredCrop(width: Int, height: Int, aspect: CropAspect): IntCrop {
    if (aspect == CropAspect.ORIGINAL || aspect == CropAspect.FREE || aspect == CropAspect.FULL) {
        return IntCrop(0, 0, width, height)
    }
    val ratio = when (aspect) {
        CropAspect.SQUARE -> 1f
        CropAspect.FOUR_THREE -> 4f / 3f
        CropAspect.THREE_FOUR -> 3f / 4f
        CropAspect.SIXTEEN_NINE -> 16f / 9f
        CropAspect.NINE_SIXTEEN -> 9f / 16f
        CropAspect.TWO_THREE -> 2f / 3f
        CropAspect.THREE_TWO -> 3f / 2f
        CropAspect.ORIGINAL, CropAspect.FREE, CropAspect.FULL -> width.toFloat() / height
    }
    val current = width.toFloat() / height
    return if (current > ratio) {
        val targetWidth = (height * ratio).toInt().coerceAtMost(width)
        val left = (width - targetWidth) / 2
        IntCrop(left, 0, left + targetWidth, height)
    } else {
        val targetHeight = (width / ratio).toInt().coerceAtMost(height)
        val top = (height - targetHeight) / 2
        IntCrop(0, top, width, top + targetHeight)
    }
}

private const val MINIMUM_TRIM_MS = 500L
