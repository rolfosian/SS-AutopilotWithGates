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

import data.scripts.autopilotwithgates.AutopilotWithGatesPlugin;
import data.scripts.autopilotwithgates.SystemGateData;

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

    /** Returns null if exit gate is nearest gate to player or player is nearer to ultimate target*/
    public static CustomCampaignEntityAPI getNearestGateToPlayerOutsideLocation(SectorEntityToken exitGate, SectorEntityToken ultimateTarget) {
        if (ultimateTarget == null || exitGate == null) return null;
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        Vector2f targetHyperspaceLoc = playerFleet.getLocationInHyperspace();
        
        SystemGateData targetSystemGateData = null;
        CustomCampaignEntityAPI targetGate = null;

        float bestDistSq = Float.MAX_VALUE;

        synchronized(AutopilotWithGatesPlugin.systemGateData) {
            for (SystemGateData systemGateData : AutopilotWithGatesPlugin.systemGateData) {
                float dx = systemGateData.systemLoc.x - targetHyperspaceLoc.x;
                float dy = systemGateData.systemLoc.y - targetHyperspaceLoc.y;
                float distSq = dx*dx + dy*dy;
                
                if (distSq < bestDistSq) {
                    CustomCampaignEntityAPI target = null;
                    for (CustomCampaignEntityAPI gate : systemGateData.gates) {
                        if (GateEntityPlugin.isScanned(gate)) {
                            target = gate;
                            break;
                        }
                    }
                    if (target == null) continue;
                    
                    targetSystemGateData = systemGateData;
                    targetGate = target;
                    bestDistSq = distSq;
                }
            }
        }

        if (targetSystemGateData == null
            || targetSystemGateData.system ==  playerFleet.getContainingLocation()
            || targetGate.getContainingLocation() == exitGate.getContainingLocation()
            || Misc.getDistanceLY(playerFleet, ultimateTarget) < (Misc.getDistanceLY(playerFleet, targetGate) - LY_DIST_TOLERANCE) + Misc.getDistanceLY(exitGate, ultimateTarget)) {
            return null;
        }

        List<JumpPointAPI> jumpPoints = new ArrayList<>();
        for (SectorEntityToken jumpPoint : targetSystemGateData.system.getJumpPoints()) {
            if (jumpPoint instanceof JumpPointAPI && jumpPoint.getContainingLocation() == targetSystemGateData.system) {
                jumpPoints.add((JumpPointAPI)jumpPoint);
            }
        }

        if (jumpPoints.size() > 0) {
            CustomCampaignEntityAPI target = null;
            bestDistSq = Float.MAX_VALUE;
            
            for (CustomCampaignEntityAPI gate : targetSystemGateData.gates) {
                if (!GateEntityPlugin.isScanned(gate)) continue;
                Vector2f gateLoc = gate.getLocation();
                JumpPointAPI closest = getClosestJumpPoint(jumpPoints, gate.getLocation());
                Vector2f jpLoc = closest.getLocation();

                float dx = gateLoc.x - jpLoc.x;
                float dy = gateLoc.y - jpLoc.y;
                float distSq = dx*dx + dy*dy;

                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    target = gate;
                }
            }
            return target;
        }

        return getNearestGateInLocation(targetSystemGateData.system, targetSystemGateData.system.getCenter().getLocation());
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

    /**Returns null if nearest gate is in player location or player fleet is closer to ultimate target */
    public static CustomCampaignEntityAPI getNearestGate(SectorEntityToken ultimateTarget) {
        Vector2f targetHyperspaceLoc = ultimateTarget.getLocationInHyperspace();
        
        SystemGateData targetSystemGateData = null;
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        float bestDistSq = Float.MAX_VALUE;

        synchronized(AutopilotWithGatesPlugin.systemGateData) {
            for (SystemGateData systemGateData : AutopilotWithGatesPlugin.systemGateData) {
                float dx = systemGateData.systemLoc.x - targetHyperspaceLoc.x;
                float dy = systemGateData.systemLoc.y - targetHyperspaceLoc.y;
                float distSq = dx*dx + dy*dy;
                
                if (distSq < bestDistSq) {
                    CustomCampaignEntityAPI target = null;
                    for (CustomCampaignEntityAPI gate : systemGateData.gates) {
                        if (GateEntityPlugin.isScanned(gate)) {
                            target = gate;
                            break;
                        }
                    }
                    if (target == null) continue;
                    
                    targetSystemGateData = systemGateData;
                    bestDistSq = distSq;
                }
            }
        }
        
        if (targetSystemGateData == null
            || targetSystemGateData.system == playerFleet.getContainingLocation()) {
            return null;
        }

        return getClosestGateToTarget(targetSystemGateData.system, targetSystemGateData.gates, ultimateTarget);
    }

    public static Vector2f getClosestJumpPointLoc(StarSystemAPI loc, CustomCampaignEntityAPI gate) {
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
        if (target != null) return target.getLocation();

        return loc.getCenter().getLocation();
    }

    public static float getClosestJumpPointDist(List<JumpPointAPI> jumpPoints, Vector2f gateLoc) {
        float bestDistSq = Float.MAX_VALUE;

        for (JumpPointAPI jp : jumpPoints) {
            Vector2f jpLoc = jp.getLocation();
            
            float dx = jpLoc.x - gateLoc.x;
            float dy = jpLoc.y - gateLoc.y;
            float distSq = dx*dx + dy*dy;
            
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
            }
        }

        return bestDistSq;
    }

    public static JumpPointAPI getClosestJumpPoint(List<JumpPointAPI> jumpPoints, Vector2f gateLoc) {
        float bestDistSq = Float.MAX_VALUE;
        JumpPointAPI target = null;

        for (JumpPointAPI jp : jumpPoints) {
            Vector2f jpLoc = jp.getLocation();
            
            float dx = jpLoc.x - gateLoc.x;
            float dy = jpLoc.y - gateLoc.y;
            float distSq = dx*dx + dy*dy;
            
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                target = jp;
            }
        }

        return target;
    }

    public static CustomCampaignEntityAPI getClosestGateToTarget(StarSystemAPI system, CustomCampaignEntityAPI[] gates, SectorEntityToken ultimateTarget) {
        if (gates.length == 1) {
            CustomCampaignEntityAPI gate = gates[0];
            if (GateEntityPlugin.isScanned(gate)) return gate;
            return null;
        }

        CustomCampaignEntityAPI target = null;
        float bestDistSq = Float.MAX_VALUE;

        if (ultimateTarget.getContainingLocation() == system) {
            Vector2f ultimateTargetLoc = ultimateTarget.getLocation();

            for (CustomCampaignEntityAPI gate : gates) {
                if (!GateEntityPlugin.isScanned(gate)) continue;    
                Vector2f gateLoc = gate.getLocation();

                float dx = ultimateTargetLoc.x - gateLoc.x;
                float dy = ultimateTargetLoc.y - gateLoc.y;
                float distSq = dx*dx + dy*dy;
                
                if (distSq < bestDistSq) {
                    target = gate;
                }
            }
            return target;
        }

        List<JumpPointAPI> jumpPoints = new ArrayList<>();
        for (SectorEntityToken jp : system.getJumpPoints()) {
            if (jp instanceof JumpPointAPI && jp.getContainingLocation() == system) {
                jumpPoints.add((JumpPointAPI)jp);
            }
        }

        if (jumpPoints.size() > 0) {
            for (CustomCampaignEntityAPI gate : gates) {
                if (!GateEntityPlugin.isScanned(gate)) continue;
    
                float distSq = getClosestJumpPointDist(jumpPoints, gate.getLocation());
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    target = gate;
                }
            }
            return target;
        }

        for (CustomCampaignEntityAPI gate : gates) {
            if (GateEntityPlugin.isScanned(gate)) return gate;
        }
        return target;
    }
}
