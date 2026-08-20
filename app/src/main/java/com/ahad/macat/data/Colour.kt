package com.ahad.macat.data

/**
 * The colour tag on an item: guessed from its photo by [ColourDetector] and correctable by hand,
 * so the palette is deliberately small and wardrobe-shaped rather than a full colour space.
 *
 * [swatch] is the ARGB colour of the dot shown next to the label. Colours with
 * [detectable] `false` are only ever chosen by hand — metallics read as plain yellow or grey in
 * RGB, so guessing them would be wrong more often than right.
 */
enum class Colour(val label: String, val swatch: Long, val detectable: Boolean = true) {
  BLACK("Black", 0xFF1A1A1A),
  WHITE("White", 0xFFF5F5F5),
  GREY("Grey", 0xFF9E9E9E),
  BEIGE("Beige", 0xFFD9C7A7),
  BROWN("Brown", 0xFF7B4B2A),
  RED("Red", 0xFFD32F2F),
  PINK("Pink", 0xFFEC7FA6),
  ORANGE("Orange", 0xFFEF7C1B),
  YELLOW("Yellow", 0xFFF2C200),
  GREEN("Green", 0xFF3C9A46),
  BLUE("Blue", 0xFF2F6FD0),
  PURPLE("Purple", 0xFF8449C4),
  GOLD("Gold", 0xFFC9A227, detectable = false),
  SILVER("Silver", 0xFFBFC5CA, detectable = false);

  /**
   * Black, white, grey and beige are what backgrounds are made of — floors, walls, bedsheets — so
   * a real colour is allowed to outvote them even from a minority of pixels.
   */
  val chromatic: Boolean
    get() = this != BLACK && this != WHITE && this != GREY && this != BEIGE

  companion object {
    /** Share of pixels a chromatic colour needs before it beats an achromatic majority. */
    private const val CHROMATIC_SHARE = 0.25f

    /** The closest palette colour to one pixel, by hue/saturation/value bands. */
    fun classify(r: Int, g: Int, b: Int): Colour {
      val max = maxOf(r, g, b)
      val min = minOf(r, g, b)
      val value = max / 255f
      val saturation = if (max == 0) 0f else (max - min) / max.toFloat()
      val hue = hue(r, g, b, max, min)
      return when {
        value < 0.18f -> BLACK
        saturation < 0.12f -> if (value > 0.80f) WHITE else GREY
        // Warm but washed out: cream, sand, camel — not a colour in its own right.
        saturation < 0.30f && hue >= 20f && hue < 60f -> if (value < 0.45f) BROWN else BEIGE
        hue < 15f || hue >= 345f -> if (value > 0.75f && saturation < 0.55f) PINK else RED
        hue < 45f -> if (value < 0.55f) BROWN else ORANGE
        hue < 70f -> if (value < 0.50f) BROWN else YELLOW
        hue < 165f -> GREEN
        hue < 255f -> BLUE
        hue < 290f -> PURPLE
        else -> PINK
      }
    }

    /**
     * The colour of a photo, from its ARGB [pixels]. A chromatic colour holding at least
     * [CHROMATIC_SHARE] of the pixels wins, so a pink shoe on a pale floor reads pink; otherwise
     * the plain majority wins, so a black shoe on a black floor still reads black.
     */
    fun dominant(pixels: IntArray): Colour? {
      if (pixels.isEmpty()) return null
      val votes = IntArray(entries.size)
      for (pixel in pixels) {
        val colour = classify((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF)
        votes[colour.ordinal]++
      }
      val chromatic = entries.filter { it.chromatic }.maxBy { votes[it.ordinal] }
      if (votes[chromatic.ordinal] >= pixels.size * CHROMATIC_SHARE) return chromatic
      return entries.maxBy { votes[it.ordinal] }
    }

    /** Hue in degrees, 0..360. */
    private fun hue(r: Int, g: Int, b: Int, max: Int, min: Int): Float {
      val delta = (max - min).toFloat()
      if (delta == 0f) return 0f
      val hue =
        when (max) {
          r -> 60f * (((g - b) / delta) % 6f)
          g -> 60f * (((b - r) / delta) + 2f)
          else -> 60f * (((r - g) / delta) + 4f)
        }
      return if (hue < 0f) hue + 360f else hue
    }
  }
}
