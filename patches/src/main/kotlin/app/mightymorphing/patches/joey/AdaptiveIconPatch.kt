package app.mightymorphing.patches.joey

import app.mightymorphing.patches.shared.Constants.JOEY
import app.morphe.patcher.patch.resourcePatch

private const val FIXED_ICON = "adaptive_icon/ic_launcher_monochrome_fv4.xml"
private const val ICON_PATH = "res/drawable/ic_launcher_monochrome_fv4.xml"

/**
 * In Joey's icon the colors are inverted: the logo uses the background color, while the background
 * uses the accent color. Google's adaptive icon guidelines expect the monochrome layer to be a
 * single-color logo on a transparent background. The patch replaces the monochrome layer with the
 * corrected colors and a single filled path, so themed icons follow the guideline.
 */
@Suppress("unused")
val adaptiveIconPatch = resourcePatch(
    name = "Fix adaptive icon",
    description = "Fixes the inverted colors of the adaptive icon's monochrome layer so it follows Google's adaptive icon guidelines.",
    default = true,
) {
    compatibleWith(JOEY)

    execute {
        val icon = get(ICON_PATH)
        if (!icon.isFile) error("Joey's monochrome icon $ICON_PATH was not found")
        icon.writeText(loadFixedIcon())
    }
}

/** Loads the replacement icon from the patch bundle. */
private fun loadFixedIcon(): String {
    val classLoader = object {}.javaClass.classLoader
    return classLoader.getResourceAsStream(FIXED_ICON)?.use { it.readBytes().decodeToString() }
        ?: error("Bundled icon $FIXED_ICON was not found")
}
