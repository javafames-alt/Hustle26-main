package morfeov.coliseum.cosmetics.geckolib;

import morfeov.coliseum.cosmetics.geo.GeoModel;
import morfeov.coliseum.cosmetics.geo.GeoModelParser;
import morfeov.coliseum.cosmetics.model.CosmeticModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeckolibModelParser {
    private static final Logger LOGGER = LoggerFactory.getLogger("Coliseum-Cosmetics");

    public GeoModel parseModel(CosmeticModel cosmetic) {
        try {
            String rawJson = cosmetic.getRawModelJson();
            if (rawJson == null) {
                LOGGER.error("No raw model JSON available for cosmetic: {}", cosmetic.getName());
                return null;
            } else {
                GeoModel geoModel = GeoModelParser.parse(rawJson);
                if (geoModel == null) {
                    LOGGER.error("Failed to parse GeoModel for: {}", cosmetic.getName());
                    return null;
                } else {
                    LOGGER.info("Successfully parsed model for: {} (bones: {})", cosmetic.getName(), geoModel.topLevelBones.size());
                    return geoModel;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error parsing model for: " + cosmetic.getName(), e);
            return null;
        }
    }
}
