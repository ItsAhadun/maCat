package com.ahad.macat.data

import androidx.room.TypeConverter

/**
 * A colour tag on an item: guessed from its photo by [ColourDetector] and correctable by hand,
 * so the palette is deliberately small and wardrobe-shaped rather than a full colour space.
 *
 * An item carries a *list* of these — a patterned dress or a two-tone trainer is not one colour,
 * and pretending otherwise made the tag wrong for a good share of a real wardrobe.
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

  /** Grey, white and black are what any washed-out pixel becomes, so they are held to a higher bar. */
  private val tagShare: Float
    get() = if (this == BLACK || this == WHITE || this == GREY) ACHROMATIC_TAG_SHARE else TAG_SHARE

  companion object {
    /** Share of the item's pixels a colour needs before it counts as one of the item's colours. */
    private const val TAG_SHARE = 0.15f

    /** How many tags one item can be given: enough for a pattern, few enough to stay readable. */
    private const val MAX_TAGS = 3

    /**
     * The share an *achromatic* colour needs instead, which is much higher.
     *
     * Grey, white and black are where every washed-out pixel ends up — dim light, shadow, a
     * metallic finish, a JPEG artefact — so they turn up in quantity on almost any photo and
     * would otherwise be tagged on almost every item. Making them clear a far higher bar keeps
     * them for items that really are grey, white or black, which plenty are.
     */
    private const val ACHROMATIC_TAG_SHARE = 0.35f

    /**
     * How far apart the channels must be before a pixel counts as having a hue at all.
     *
     * A saturation ratio alone is not enough: a cool grey floor tile like (150, 152, 166) is 10%
     * saturated and lands in the blue band, which is why grey tiles, black patent and silver
     * glitter were all coming back BLUE. Ten points of channel spread is not a colour, whatever
     * the ratio says.
     *
     * Measured on real wardrobe photos rather than guessed: grey floors run 6–13 points of spread
     * at the median, and muted real colours — rose gold, blush, teal — run 18–30. Sixteen splits
     * them. Set it any higher and those colours all collapse into grey.
     */
    private const val ACHROMATIC_DELTA = 16

    /**
     * How far a pixel has to sit from the backdrop's own colour to be counted as the item's.
     *
     * Squared RGB distance, so no square roots per pixel. Half of a typical wardrobe is grey,
     * white or black and so is half of a typical floor, so the backdrop cannot be excluded by
     * *colour* — a silver shoe on a grey tile is genuinely grey and would be left with nothing.
     * It is excluded by *pixel*: floor-coloured pixels drop out, and the shoe's own greys, which
     * are lighter or darker than the floor, stay.
     */
    private const val BACKDROP_DISTANCE_SQ = 60 * 60

    /** Below this share of the crop surviving, the rejection is assumed to have eaten the item. */
    private const val MIN_ITEM_SHARE = 0.1f

    /** The closest palette colour to one pixel, by hue/saturation/value bands. */
    fun classify(r: Int, g: Int, b: Int): Colour {
      val max = maxOf(r, g, b)
      val min = minOf(r, g, b)
      val value = max / 255f
      val saturation = if (max == 0) 0f else (max - min) / max.toFloat()
      val hue = hue(r, g, b, max, min)
      return when {
        value < 0.18f -> BLACK
        saturation < 0.12f || (max - min) < ACHROMATIC_DELTA ->
          if (value > 0.80f) WHITE else GREY
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
     * The colours of an item, most of it first.
     *
     * [item] is the middle of the photo, where the thing being catalogued is; [backdrop] is the
     * frame around it, which is the floor, the bed or the wall. The centre crop is never all
     * item — a shoe lies at an angle and leaves floor in every corner — so the floor is removed
     * pixel by pixel first: anything within [BACKDROP_DISTANCE_SQ] of the backdrop's own colour
     * is not the item and does not vote.
     *
     * Everything left holding at least [TAG_SHARE] of what survives is a tag, so a red-and-white
     * dress comes back with both. If almost nothing survives — a black shoe on a black floor, or
     * an item that fills the frame — the crop is used whole, because "no colour at all" is the
     * worse answer.
     */
    fun tags(item: IntArray, backdrop: IntArray): List<Colour> {
      if (item.isEmpty()) return emptyList()
      val itemOnly = item.withoutBackdrop(backdrop)
      val counted = if (itemOnly.size >= item.size * MIN_ITEM_SHARE) itemOnly else item
      val votes = tally(counted)
      val tagged =
        entries
          .filter { votes[it.ordinal] >= counted.size * it.tagShare }
          .sortedByDescending { votes[it.ordinal] }
          .take(MAX_TAGS)
      return tagged.ifEmpty { listOfNotNull(winner(votes)) }
    }

    /** The pixels that do not look like [backdrop]'s own colour. */
    private fun IntArray.withoutBackdrop(backdrop: IntArray): IntArray {
      if (backdrop.isEmpty()) return this
      val (br, bg, bb) = backdrop.medianColour()
      return filter { pixel ->
        val dr = ((pixel shr 16) and 0xFF) - br
        val dg = ((pixel shr 8) and 0xFF) - bg
        val db = (pixel and 0xFF) - bb
        dr * dr + dg * dg + db * db > BACKDROP_DISTANCE_SQ
      }
        .toIntArray()
    }

    /** The backdrop's colour, per channel. Median rather than mean: grout lines and shadows. */
    private fun IntArray.medianColour(): Triple<Int, Int, Int> {
      fun median(shift: Int): Int {
        val channel = IntArray(size) { (this[it] shr shift) and 0xFF }
        channel.sort()
        return channel[size / 2]
      }
      return Triple(median(16), median(8), median(0))
    }

    /** How many pixels fall into each palette colour, indexed by [Colour.ordinal]. */
    private fun tally(pixels: IntArray): IntArray {
      val votes = IntArray(entries.size)
      for (pixel in pixels) {
        val colour = classify((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF)
        votes[colour.ordinal]++
      }
      return votes
    }

    private fun winner(votes: IntArray): Colour? =
      entries.maxByOrNull { votes[it.ordinal] }?.takeIf { votes[it.ordinal] > 0 }

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

/**
 * Stores an item's colours in the single TEXT column it has had since v2, as a comma-separated
 * list of constant names.
 *
 * Reusing the column is what keeps this off the migration list entirely: a row written when an
 * item could only have one colour says `PINK`, which reads back as a one-item list, and a row
 * written now says `PINK,WHITE`. Nobody's catalogue is rewritten, and a downgrade would read the
 * first name and drop the rest rather than crash. Null and empty both mean untagged, which is why
 * the field is nullable — the column has always been.
 */
class ColourListConverter {
  @TypeConverter
  fun toStored(colours: List<Colour>?): String? =
    colours?.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name }

  @TypeConverter
  fun fromStored(stored: String?): List<Colour>? =
    stored
      ?.split(",")
      ?.mapNotNull { name -> Colour.entries.firstOrNull { it.name == name } }
      ?.takeIf { it.isNotEmpty() }
}
