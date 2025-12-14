package data.scripts.autopilotwithgates.util;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
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
    
    /** Returns null if exit gate is nearest gate to player or player is nearer to ultimate target*/
    public static CustomCampaignEntityAPI getNearestGateToPlayerOutsideLocation(SectorEntityToken exitGate, SectorEntityToken ultimateTarget) {
        if (ultimateTarget == null || exitGate == null) return null;
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        Vector2f targetHyperspaceLoc = playerFleet.getLocationInHyperspace();
        
        SystemGateData targetSystemGateData = null;
        StarSystemAPI targetSystem = null;
        CustomCampaignEntityAPI targetGate = null;

        float bestDistSq = Float.MAX_VALUE;

        synchronized(AutopilotWithGatesPlugin.systemGateData) {
            for (SystemGateData systemGateData : AutopilotWithGatesPlugin.systemGateData) {
                float distSq = getDistSq(systemGateData.systemLoc, targetHyperspaceLoc);
                
                if (distSq < bestDistSq) {
                    targetSystemGateData = systemGateData;
                    targetSystem = systemGateData.system;
                    targetGate = systemGateData.gates[0];
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

        if (targetSystemGateData.gates.length == 1) return targetGate;

        List<SectorEntityToken> jumpPoints = new ArrayList<>();
        for (SectorEntityToken jumpPoint : targetSystem.getJumpPoints()) {
            if (jumpPoint instanceof JumpPointAPI && jumpPoint.getContainingLocation() == targetSystem) {
                jumpPoints.add(jumpPoint);
            }
        }
        List<PlanetAPI> planets = targetSystem.getPlanets();

        boolean hasJumpPoints = jumpPoints.size() > 0;
        boolean hasPlanets = planets.size() > 0;

        if (hasJumpPoints && hasPlanets) {
            CustomCampaignEntityAPI target = null;
            bestDistSq = Float.MAX_VALUE;
        
            for (CustomCampaignEntityAPI gate : targetSystemGateData.gates) {
                Vector2f gateLoc = gate.getLocation();
        
                SectorEntityToken closestJumpPoint = getClosestJumpPoint(jumpPoints, gateLoc);
                PlanetAPI closestPlanetGravityWell = getClosestPlanetGravityWell(planets, gateLoc);
        
                Vector2f jpLoc = closestJumpPoint.getLocation();
                Vector2f planetLoc = closestPlanetGravityWell != null ? closestPlanetGravityWell.getLocation() : null;
        
                Vector2f referenceLoc = jpLoc;
        
                if (planetLoc != null) {
                    float jpDistSq = getDistSq(gateLoc, jpLoc);
                    float planetDistSq = getDistSq(gateLoc, planetLoc);
        
                    if (planetDistSq < jpDistSq) {
                        referenceLoc = planetLoc;
                    }
                }

                float distSq = getDistSq(gateLoc, referenceLoc);
        
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    target = gate;
                }
            }
            return target;

        } else if (hasJumpPoints) {
            CustomCampaignEntityAPI target = null;
            bestDistSq = Float.MAX_VALUE;
            
            for (CustomCampaignEntityAPI gate : targetSystemGateData.gates) {
                Vector2f gateLoc = gate.getLocation();
                float distSq = getDistSq(gateLoc, getClosestJumpPoint(jumpPoints, gateLoc).getLocation());

                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    target = gate;
                }
            }
            return target;

        } else if (hasPlanets) {
            CustomCampaignEntityAPI target = null;
            bestDistSq = Float.MAX_VALUE;
            
            for (CustomCampaignEntityAPI gate : targetSystemGateData.gates) {
                Vector2f gateLoc = gate.getLocation();
                PlanetAPI closestPlanetGravityWell = getClosestPlanetGravityWell(planets, gateLoc);
                if (closestPlanetGravityWell == null) continue;

                float distSq = getDistSq(gate.getLocation(), closestPlanetGravityWell.getLocation());

                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    target = gate;
                }
            }
            if (target != null) return target;
        }

        return getNearestGateInLocation(targetSystem, targetSystem.getCenter().getLocation());
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
            if (!GateEntityPlugin.isScanned(gate)) continue;
            
            float distSq = getDistSq(gate.getLocation(), targetLoc);
        
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                closest = gate;
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

        return getClosestGateToTarget(targetSystemGateData.system, targetSystemGateData.gates, ultimateTarget);
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

    public static CustomCampaignEntityAPI getClosestGateToTarget(StarSystemAPI system, CustomCampaignEntityAPI[] gates, SectorEntityToken ultimateTarget) {
        if (gates.length == 1) return gates[0];

        CustomCampaignEntityAPI target = null;
        float bestDistSq = Float.MAX_VALUE;

        if (ultimateTarget.getContainingLocation() == system) {
            Vector2f ultimateTargetLoc = ultimateTarget.getLocation();

            for (CustomCampaignEntityAPI gate : gates) {
                Vector2f gateLoc = gate.getLocation();
                float distSq = getDistSq(ultimateTargetLoc, gateLoc);
                
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
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
                float distSq = getClosestJumpPointDist(jumpPoints, gate.getLocation());
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    target = gate;
                }
            }
            return target;
        }

        return gates[0];
    }

    public static float getDistSq(Vector2f loc1, Vector2f loc2) {
        float dx = loc1.x - loc2.x;
        float dy = loc1.y - loc2.y;
        return dx*dx + dy*dy;
    }
}
