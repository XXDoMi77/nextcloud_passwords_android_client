package com.dominikdomotor.nextcloudpasswords.ui.theme

import java.io.File
import org.junit.Ignore
import org.junit.Test

/**
 * Regenerates the static default palettes in `values/theme_colors.xml` and its night twin.
 *
 * Not a test - a tool, which is why it is `@Ignore`d and never runs in the suite. Those XML files are the palette the
 * app paints on Android 10 and on the first frame of every start, and they have to be byte-for-byte what
 * [PaletteGenerator] produces; `PaletteGeneratorTest` asserts exactly that and will fail if they drift. This is how to
 * make them agree again after a change to the generator or the default seed:
 * ```
 * ./gradlew :app:testDebugUnitTest --tests '*PaletteXmlDumpTest*' -Dtest.single.ignored=true
 * ```
 *
 * or simply remove the `@Ignore` for one run. It writes relative to the module directory, which is the working
 * directory a unit test gets.
 */
@Ignore("A generator, not a test. Remove this annotation for one run to regenerate theme_colors.xml.")
class PaletteXmlDumpTest {
    @Test
    fun regenerateStaticDefaults() {
        write("src/main/res/values/theme_colors.xml", dark = false)
        write("src/main/res/values-night/theme_colors.xml", dark = true)
    }

    private fun write(path: String, dark: Boolean) {
        val palette = PaletteGenerator.generate(PaletteGenerator.NEXTCLOUD_BLUE, dark, isAmoled = false)
        val body =
            palette.entries.joinToString("\n") { (name, value) ->
                "    <color name=\"$name\">#%08X</color>".format(value)
            }
        File(path).writeText(HEADER + "<resources>\n" + body + "\n</resources>\n")
    }

    private companion object {
        val HEADER =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <!--
                The palette slots, and their static defaults.

                Every value here is generated from the app's own #0082C9 by PaletteGenerator, and replaced at
                runtime by ThemeApplier once a seed is known. They are what the app paints with on Android 10,
                where there is no ResourcesLoader, and on the first frame of every start before the seed has
                been read.

                Do not hand-edit: regenerate with PaletteXmlDumpTest.
            -->

            """
                .trimIndent()
                .trimStart() + "\n"
    }
}
