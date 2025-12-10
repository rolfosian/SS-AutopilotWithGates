package data.scripts.autopilotwithgates.util;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
// import com.fs.starfarer.api.impl.campaign.rulecmd.missions.GateCMD;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

public class GateFinder {
    private static final Logger logger = Logger.getLogger(GateFinder.class);
    public static void print(Object... args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            sb.append(args[i] instanceof String ? (String) args[i] : String.valueOf(args[i]));
            if (i < args.length - 1) sb.append(' ');
        }
        logger.info(sb.toString());
    }

    public static float LY_DIST_TOLERANCE = 0.2f;

    public static boolean playerFleetIsCloser(SectorEntityToken playerFleet, SectorEntityToken toCheck, SectorEntityToken target) {
        return Misc.getDistanceLY(playerFleet, target) - LY_DIST_TOLERANCE < Misc.getDistanceLY(toCheck, target);
    }

    public static int getCombinedFuelCost(
        CampaignFleetAPI playerFleet,
        SectorEntityToken entryGate,
        SectorEntityToken exitGate,
        SectorEntityToken ultimateTarget
    ) {
        float fuelPerLY = playerFleet.getLogistics().getFuelCostPerLightYear();
        int combinedFuelCost = 0;

        float fleetToEntryGateDist = Misc.getDistanceLY(playerFleet, entryGate);
        combinedFuelCost += fleetToEntryGateDist * fuelPerLY;
        
        float gateTravelDist = Misc.getDistanceLY(entryGate, exitGate);
        combinedFuelCost += (int)Math.ceil((double)(gateTravelDist * fuelPerLY * Misc.GATE_FUEL_COST_MULT));
        
        float exitToUltimateTargetDist = Misc.getDistanceLY(exitGate, ultimateTarget);
        combinedFuelCost += exitToUltimateTargetDist * fuelPerLY;

        return combinedFuelCost;
    }

    // unused
    public static float getCombinedDistLY(
        CampaignFleetAPI playerFleet,
        SectorEntityToken entryGate,
        SectorEntityToken exitGate,
        SectorEntityToken ultimateTarget
    ) {
        float fleetToEntryGateDist = Misc.getDistanceLY(playerFleet, entryGate);
        float exitToUltimateTargetDist = Misc.getDistanceLY(exitGate, ultimateTarget);
        
        return fleetToEntryGateDist + exitToUltimateTargetDist;
    }

    public static int getFuelCostToUltimateTarget(CampaignFleetAPI playerFleet, SectorEntityToken ultimateTarget) {
        return (int) (playerFleet.getLogistics().getFuelCostPerLightYear() * Misc.getDistanceLY(playerFleet, ultimateTarget));
    }

    // unused
    public static boolean isPlayerFleetBetweenGates(
        CampaignFleetAPI playerFleet,
        SectorEntityToken entryGate,
        SectorEntityToken exitGate
    ) {
        Vector2f playerLoc = playerFleet.getLocationInHyperspace();
        Vector2f entryLoc = entryGate.getLocationInHyperspace();
        Vector2f exitLoc = exitGate.getLocationInHyperspace();
    
        Vector2f ap = Vector2f.sub(playerLoc, entryLoc, null);
        Vector2f ab = Vector2f.sub(exitLoc, entryLoc, null);
        float abLenSq = ab.lengthSquared();
    
        if (abLenSq == 0f) return false;
    
        float t = Vector2f.dot(ap, ab) / abLenSq;
    
        if (t < 0f || t > 1f) return false;
    
        Vector2f proj = new Vector2f(
            entryLoc.x + ab.x * t,
            entryLoc.y + ab.y * t
        );
    
        float dist = Vector2f.sub(playerLoc, proj, null).length();
        return dist < 50f;
    }
    
    // unused
    public static boolean isGateInLocation(LocationAPI loc) {
        if (!(loc instanceof StarSystemAPI)) return false;

        List<CustomCampaignEntityAPI> gates = loc.getCustomEntitiesWithTag(Tags.GATE);
        if (gates.size() == 0) return false;
        
        for (CustomCampaignEntityAPI gate : gates) if (GateEntityPlugin.isScanned(gate)) return true;
        return false;
    }

    /** Returns null if  exit gate is nearest gate to player or player is nearer to ultimate target*/
    public static CustomCampaignEntityAPI getNearestGateToPlayerOutsideLocation(SectorEntityToken exitGate, SectorEntityToken ultimateTarget) {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (ultimateTarget == null || exitGate == null || Misc.getDistanceLY(playerFleet, ultimateTarget) < Misc.getDistanceLY(playerFleet, exitGate)) return null;

        Vector2f hyperSpaceLoc = playerFleet.getLocationInHyperspace();
        
        StarSystemAPI targetSystem = null;
        CustomCampaignEntityAPI targetGate = null;

        float bestDistSq = Float.MAX_VALUE;
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            List<CustomCampaignEntityAPI> gates = system.getCustomEntitiesWithTag(Tags.GATE);

            if (gates.size() > 0) {
                Vector2f starLoc = system.getHyperspaceAnchor().getLocationInHyperspace();
                float dx = starLoc.x - hyperSpaceLoc.x;
                float dy = starLoc.y - hyperSpaceLoc.y;
                float distSq = dx*dx + dy*dy;
                
                if (distSq < bestDistSq) {
                    CustomCampaignEntityAPI target = null;
                    for (CustomCampaignEntityAPI gate : gates) {
                        if (GateEntityPlugin.isScanned(gate)) {
                            target = gate;
                            break;
                        }
                    }
                    if (target == null) continue;

                    targetGate = target;
                    targetSystem = system;
                    bestDistSq = distSq;
                }
            }
        }

        if (targetSystem == null
            || targetSystem ==  playerFleet.getContainingLocation()
            || targetGate == exitGate) {
            return null;
        }
        return getNearestGateInLocation(targetSystem, getClosestJumpPoint(targetSystem, targetGate).getLocation());
    }

    /**Returns null if nearest gate is in player location or player fleet is closer to ultimate target */
    public static CustomCampaignEntityAPI getNearestGate(SectorEntityToken ultimateTarget) {
        Vector2f hyperSpaceLoc = ultimateTarget.getLocationInHyperspace();
        
        StarSystemAPI targetSystem = null;
        CustomCampaignEntityAPI targetGate = null;
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        float bestDistSq = Float.MAX_VALUE;
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            List<CustomCampaignEntityAPI> gates = system.getCustomEntitiesWithTag(Tags.GATE);

            if (gates.size() > 0) {
                Vector2f starLoc = system.getHyperspaceAnchor().getLocationInHyperspace();
                float dx = starLoc.x - hyperSpaceLoc.x;
                float dy = starLoc.y - hyperSpaceLoc.y;
                float distSq = dx*dx + dy*dy;
                
                if (distSq < bestDistSq) {
                    CustomCampaignEntityAPI target = null;
                    for (CustomCampaignEntityAPI gate : gates) {
                        if (GateEntityPlugin.isScanned(gate)) {
                            target = gate;
                            break;
                        }
                    }
                    if (target == null || playerFleetIsCloser(playerFleet, target, ultimateTarget)) continue;

                    targetGate = target;
                    targetSystem = system;
                    bestDistSq = distSq;
                }
            }
        }
        
        if (targetSystem == null
            || targetSystem ==  playerFleet.getContainingLocation()) {
            // || playerFleetIsCloser(playerFleet, targetGate, ultimateTarget)) {
            return null;
        }
        return getNearestGateInLocation(targetSystem, getClosestJumpPoint(targetSystem, targetGate).getLocation());
    }

    public static JumpPointAPI getClosestJumpPoint(StarSystemAPI loc, CustomCampaignEntityAPI gate) {
        JumpPointAPI target = null;
        List<SectorEntityToken> jps = loc.getJumpPoints();

        Vector2f gateLoc = gate.getLocation();
        float bestDistSq = Float.MAX_VALUE;

        for (SectorEntityToken jp : jps) {
            if (jp instanceof JumpPointAPI && !jp.isInHyperspace()) {
                Vector2f jpLoc = jp.getLocation();
                
                float dx = jpLoc.x - gateLoc.x;
                float dy = jpLoc.y - gateLoc.y;
                float distSq = dx*dx + dy*dy;
                
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    target = (JumpPointAPI) jp;
                }
            }
        }

        return target;
    }

    public static CustomCampaignEntityAPI getNearestGateInLocation(LocationAPI loc, Vector2f targetLoc) {
        List<CustomCampaignEntityAPI> gates = loc.getCustomEntitiesWithTag(Tags.GATE);
        int size = gates.size();
        
        if (size == 0) {
            return null;

        } else if (size == 1) {
            CustomCampaignEntityAPI gate = gates.get(0);
            if (GateEntityPlugin.isScanned(gate)) return gate;
            return null;
        }

        CustomCampaignEntityAPI closest = null;
        float bestDistSq = Float.MAX_VALUE;
        
        for (CustomCampaignEntityAPI gate : gates) {
            if (GateEntityPlugin.isScanned(gate)) {
                Vector2f g = gate.getLocation();

                float dx = g.x - targetLoc.x;
                float dy = g.y - targetLoc.y;
                float distSq = dx*dx + dy*dy;
            
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    closest = gate;
                }
            }
        }
        
        return closest;
    }
}
