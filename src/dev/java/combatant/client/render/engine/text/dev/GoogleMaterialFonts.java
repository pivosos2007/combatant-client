/* Dev source set only. Roboto originates from googlefonts/roboto-2 (Apache-2.0). */
package combatant.client.render.engine.text.dev;

import combatant.client.render.engine.text.FontInfo;
import combatant.client.util.resources.asset.FontAsset;
import combatant.client.util.resources.asset.FontCatalog;

@FontCatalog(namespace = "combatant", root = "font")
public enum GoogleMaterialFonts {
    @FontAsset(value = "roboto_regular.ttf", family = "Roboto", type = FontInfo.Type.Regular, order = 300)
    ROBOTO_REGULAR,
    @FontAsset(value = "roboto_medium.ttf", family = "RobotoMedium", type = FontInfo.Type.Regular, order = 301)
    ROBOTO_MEDIUM
}
