package data.scripts.autopilotwithgates.util;

import java.util.*;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import data.scripts.autopilotwithgates.GateData;
import data.scripts.autopilotwithgates.SystemGateData;

import static data.scripts.autopilotwithgates.AutopilotWithGatesPlugin.systemGateIteratorLock;
import static data.scripts.autopilotwithgates.AutopilotWithGatesPlugin.systemsToGates;
import static data.scripts.autopilotwithgates.AutopilotWithGatesPlugin.systemsToBifrosts;

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

    public static int getCombinedFuelCostExclGate (
        CampaignFleetAPI playerFleet,
        SectorEntityToken entryGate,
        SectorEntityToken exitGate,
        SectorEntityToken ultimateTarget
    ) {
        float fuelPerLY = playerFleet.getLogistics().getFuelCostPerLightYear();
        int combinedFuelCost = 0;

        float fleetToEntryGateDist = Misc.getDistanceLY(playerFleet, entryGate);
        combinedFuelCost += fleetToEntryGateDist * fuelPerLY;
        
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
    
    /** Returns null if exit gate is nearest gate to player or player is nearer to ultimate target*/
    public static GateData getNearestGateToPlayerOutsideLocation(List<SystemGateData> systemGateDatas, SectorEntityToken exitGate, SectorEntityToken ultimateTarget) {
        if (ultimateTarget == null || exitGate == null) return null;
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        Vector2f targetHyperspaceLoc = playerFleet.getLocationInHyperspace();
        
        SystemGateData targetSystemGateData = null;
        GateData targetGate = null;

        float bestDistSq = Float.MAX_VALUE;

        synchronized(systemGateIteratorLock) {
            for (SystemGateData systemGateData : systemGateDatas) {
                if (systemGateData.systemHasNoEntry) continue;

                float distSq = getDistSq(systemGateData.systemLoc, targetHyperspaceLoc);
                
                if (distSq < bestDistSq) {
                    targetSystemGateData = systemGateData;
                    targetGate = systemGateData.gateDatas[0];
                    bestDistSq = distSq;
                }
            }
        }

        if (targetSystemGateData == null
            || targetSystemGateData.system == playerFleet.getContainingLocation()
            || targetGate.gate.getContainingLocation() == exitGate.getContainingLocation()
            || Misc.getDistanceLY(playerFleet, ultimateTarget) < (Misc.getDistanceLY(playerFleet, targetGate.gate) - LY_DIST_TOLERANCE) + Misc.getDistanceLY(exitGate, ultimateTarget)) {
            return null;
        }

        if (targetSystemGateData.gates.length == 1) return targetGate;

        GateData target = null;
        bestDistSq = Float.MAX_VALUE;
    
        for (GateData gateData : targetSystemGateData.gateDatas) {
            if (gateData.closestEntryDistSq < bestDistSq) {
                bestDistSq = gateData.closestEntryDistSq;
                target = gateData;
            }
        }
        return target;
    }

    public static GateData getNearestGateInLocation(LocationAPI loc, Vector2f targetLoc) {
        SystemGateData data = null;
        synchronized(systemGateIteratorLock) {
            data = systemsToGates.get(loc);
        }

        if (data == null) return null;
        if (data.gateDatas.length == 1) return data.gateDatas[0];

        GateData closest = null;
        float bestDistSq = Float.MAX_VALUE;
        
        for (GateData gate : data.gateDatas) {
            float distSq = getDistSq(gate.gate.getLocation(), targetLoc);
        
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                closest = gate;
            }
        }
        return closest;
    }

    public static GateData getNearestBifrostInLocation(LocationAPI loc, Vector2f targetLoc) {
        SystemGateData data = null;
        synchronized(systemGateIteratorLock) {
            data = systemsToBifrosts.get(loc);
        }

        if (data == null) return null;
        if (data.gateDatas.length == 1) return data.gateDatas[0];

        GateData closest = null;
        float bestDistSq = Float.MAX_VALUE;
        
        for (GateData gate : data.gateDatas) {
            float distSq = getDistSq(gate.gate.getLocation(), targetLoc);
        
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                closest = gate;
            }
        }
        return closest;
    }

    /**Returns null if nearest gate is in player location or player fleet is closer to ultimate target */
    public static GateData getNearestGate(List<SystemGateData> systemGateDatas, SectorEntityToken ultimateTarget) {
        Vector2f targetHyperspaceLoc = ultimateTarget.getLocationInHyperspace();
        
        SystemGateData targetSystemGateData = null;
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        float bestDistSq = Float.MAX_VALUE;

        synchronized(systemGateIteratorLock) {
            for (SystemGateData systemGateData : systemGateDatas) {
                float distSq = getDistSq(systemGateData.systemLoc, targetHyperspaceLoc);
                
                if (distSq < bestDistSq) {
                    targetSystemGateData = systemGateData;
                    bestDistSq = distSq;
                }
            }
        }
        
        if (targetSystemGateData == null
            || targetSystemGateData.system == playerFleet.getContainingLocation()) {
            return null;
        }

        return getClosestGateToTarget(targetSystemGateData.system, targetSystemGateData.gateDatas, ultimateTarget);
    }

    public static float getClosestJumpPointDist(List<JumpPointAPI> jumpPoints, Vector2f gateLoc) {
        float bestDistSq = Float.MAX_VALUE;

        for (JumpPointAPI jp : jumpPoints) {
            float distSq = getDistSq(jp.getLocation(), gateLoc);
            
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
            }
        }

        return bestDistSq;
    }

    public static SectorEntityToken getClosestJumpPoint(List<SectorEntityToken> jumpPoints, Vector2f gateLoc) {
        float bestDistSq = Float.MAX_VALUE;
        SectorEntityToken target = null;

        for (SectorEntityToken jp : jumpPoints) {
            float distSq = getDistSq(jp.getLocation(), gateLoc);
            
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                target = jp;
            }
        }

        return target;
    }

    public static PlanetAPI getClosestPlanetGravityWell(List<PlanetAPI> planets, Vector2f gateLoc) {
        float bestDistSq = Float.MAX_VALUE;
        PlanetAPI target = null;

        for (PlanetAPI planet : planets) {
            if (planet.isStar() || planet.isBlackHole() || planet.isGasGiant()) {
                float distSq = getDistSq(planet.getLocation(), gateLoc);
                
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    target = planet;
                }
            }
        }
        return target;
    }

    public static GateData getClosestGateToTarget(StarSystemAPI system, GateData[] gates, SectorEntityToken ultimateTarget) {
        if (gates.length == 1) return gates[0];

        GateData target = null;
        float bestDistSq = Float.MAX_VALUE;

        if (ultimateTarget.getContainingLocation() == system) {
            Vector2f ultimateTargetLoc = ultimateTarget.getLocation();

            for (GateData gate : gates) {
                Vector2f gateLoc = gate.gate.getLocation();
                float distSq = getDistSq(ultimateTargetLoc, gateLoc);
                
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    target = gate;
                }
            }
            return target;
        }

        for (GateData gate : gates) {
            float distSq = gate.closestExitDistSq;
            
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                target = gate;
            }
        }
        return target;
    }

    public static float getDistSq(Vector2f loc1, Vector2f loc2) {
        float dx = loc1.x - loc2.x;
        float dy = loc1.y - loc2.y;
        return dx*dx + dy*dy;
    }
}
