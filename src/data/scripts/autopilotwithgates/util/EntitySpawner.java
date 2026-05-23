package data.scripts.autopilotwithgates.util;

import com.fs.starfarer.api.Global;

import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Planets;

import com.fs.starfarer.api.impl.campaign.procgen.NameGenData;
import com.fs.starfarer.api.impl.campaign.procgen.ProcgenUsedNames;

import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.lwjgl.util.vector.Vector2f;

public class EntitySpawner {
    public static final WeightedRandomPicker<String> PLANET_TYPES = new WeightedRandomPicker<>();
    public static final Map<String, WeightedRandomPicker<String>> CONDITIONS = new HashMap<>();

    public static String[] getConditions(int numConditions, String planetType) {
        String[] result = new String[numConditions];
        WeightedRandomPicker<String> picker = CONDITIONS.get(planetType);

        if (numConditions > picker.getItems().size()) numConditions = picker.getItems().size();

        for (int i = 0; i < numConditions; i++) {
            result[i] = picker.pickAndRemove();
        }
        for (int i = 0; i < numConditions; i++) {
            picker.add(result[i]);
        }

        return result;
    }

    static {
        for (PlanetSpecAPI planetSpec : Global.getSettings().getAllPlanetSpecs()) {
            PLANET_TYPES.add(planetSpec.getPlanetType());

            WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
            CONDITIONS.put(planetSpec.getPlanetType(), picker);

            for (MarketConditionSpecAPI condSpec : Global.getSettings().getAllMarketConditionSpecs()) {
                boolean compatibleWithType = false;

                for (String tag : condSpec.getTags()) {
                    if (planetSpec.getTags().contains(tag)) {
                        compatibleWithType = true;
                        break;
                    }
                }

                if (compatibleWithType) {
                    picker.add(condSpec.getId());
                }
            }
        }
    }

    public static void addGateAtPlayerLocation(
        String name,
        StarSystemAPI system
    ) {
        Vector2f playerLocation =
            Global.getSector().getPlayerFleet().getLocation();

        CustomCampaignEntityAPI gate = system.addCustomEntity(
            null,
            name,
            "inactive_gate",
            "neutral"
        );

        placeEntity(system, playerLocation, gate);
    }

    public static PlanetAPI addPlanetAtPlayerLocation(
    ) {
        return addPlanetAtPlayerLocation(
            null,
            ProcgenUsedNames.pickName(NameGenData.TAG_PLANET, null, null).nameWithRomanSuffixIfAny,
            (StarSystemAPI) Global.getSector().getPlayerFleet().getContainingLocation(),
            randInt(50, 250)
        );
    }

    public static PlanetAPI addPlanetAtPlayerLocation(
        String id,
        String name,
        StarSystemAPI system,
        float radius
    ) {
        return addPlanetAtPlayerLocation(
            id,
            name,
            system,
            PLANET_TYPES.pick(),
            radius
        );
    }

    public static PlanetAPI addPlanetAtPlayerLocation(
        String id,
        String name,
        StarSystemAPI system,
        String type,
        float radius
    ) {
        Vector2f playerLocation =
            Global.getSector().getPlayerFleet().getLocation();

        if (system.getStar() == null) {
            PlanetAPI planet = system.addPlanet(
                id,
                null,
                name,
                type,
                0f,
                radius,
                playerLocation.x,
                playerLocation.y
            );

            planet.setLocation(playerLocation.x, playerLocation.y);

            return planet;
        }

        Vector2f starLocation = system.getStar().getLocation();

        float deltaX = playerLocation.x - starLocation.x;
        float deltaY = playerLocation.y - starLocation.y;

        float orbitAngle =
            (float) Math.toDegrees(Math.atan2(deltaY, deltaX));

        float orbitRadius = distanceBetween(
            playerLocation,
            starLocation
        );

        return system.addPlanet(
            id,
            system.getStar(),
            name,
            type,
            orbitAngle,
            radius,
            orbitRadius,
            200f
        );
    }

    public static MarketAPI addMarket(SectorEntityToken entity) {
        return addMarket(entity, Global.getSector().getPlayerFaction());
    }

    public static MarketAPI addMarket(SectorEntityToken entity, FactionAPI faction) {
        return addMarket(entity, faction, 3);
    }

    public static MarketAPI addMarket(SectorEntityToken entity, FactionAPI faction, int size) {
        return addMarket(entity, faction, size, null, null, null);
    }

    public static MarketAPI addMarket(SectorEntityToken entity, FactionAPI faction, int size, String[] conditions, String[] industries, String[] submarkets) {
        String marketId = entity.getId() + "_market";

        MarketAPI market = Global.getFactory().createMarket(
            marketId,
            entity.getName(),
            size
        );

        market.setPrimaryEntity(entity);
        market.setPlanetConditionMarketOnly(false);
        market.setFactionId(faction.getId());

        if (conditions == null) {
            if (entity instanceof PlanetAPI planet) {
                getConditions(randInt(2, size >= 2 ? 4 : 2), planet.getSpec().getPlanetType());
            } else {
                // TODO custom entity/station conds
            }
        }
        if (submarkets == null) {
            submarkets = new String[] {Submarkets.SUBMARKET_OPEN, Submarkets.SUBMARKET_STORAGE};
        }
        if (industries == null) {
            industries = new String[] {Industries.POPULATION, Industries.SPACEPORT};
        }

        for (String submarket : submarkets) market.addSubmarket(submarket);
        for (String ind : industries) market.addIndustry(ind);

        // market.getTariff().modifyFlat("generator", 0.3f);

        entity.setMarket(market);

        Global.getSector().getEconomy().addMarket(
            market,
            true
        );

        return market;
    }


    private static void placeEntity(
        StarSystemAPI system,
        Vector2f playerLocation,
        CustomCampaignEntityAPI entity
    ) {
        if (system.getStar() == null) {
            entity.setLocation(playerLocation.x, playerLocation.y);
            return;
        }

        Vector2f starLocation = system.getStar().getLocation();

        float deltaX = playerLocation.x - starLocation.x;
        float deltaY = playerLocation.y - starLocation.y;

        float orbitAngle =
            (float) Math.toDegrees(Math.atan2(deltaY, deltaX));

        float orbitRadius = distanceBetween(
            playerLocation,
            starLocation
        );

        entity.setCircularOrbit(
            system.getStar(),
            orbitAngle,
            orbitRadius,
            200f
        );
    }

    public static float distanceBetween(
        Vector2f positionA,
        Vector2f positionB
    ) {
        return (float) Math.sqrt(
            (positionA.x - positionB.x) * (positionA.x - positionB.x) +
            (positionA.y - positionB.y) * (positionA.y - positionB.y)
        );
    }

    public static int randInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
    }
}