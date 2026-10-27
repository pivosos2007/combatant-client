/* Dev-only font registration. Source Sans 3 is Adobe Spectrum's documented fallback,
 * not the restricted Adobe Clean Spectrum VF. Licensed under SIL OFL 1.1. */
package combatant.client.render.engine.text.dev;

import combatant.client.render.engine.text.FontInfo;
import combatant.client.util.resources.asset.FontAsset;
import combatant.client.util.resources.asset.FontCatalog;

@FontCatalog(namespace = "combatant", root = "font")
public enum AdobeSpectrumFonts {
    @FontAsset(value = "source_sans3_regular.ttf", family = "SourceSans3", type = FontInfo.Type.Regular, order = 302)
    SOURCE_SANS_3_REGULAR,
    @FontAsset(value = "source_sans3_semibold.ttf", family = "SourceSans3Semibold", type = FontInfo.Type.Regular, order = 303)
    SOURCE_SANS_3_SEMIBOLD
}
