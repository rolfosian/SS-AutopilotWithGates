package data.scripts.autopilotwithgates;

import java.util.*;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;

public class SystemGateData {
    public final StarSystemAPI system;
    public final Vector2f systemLoc;
    public final CustomCampaignEntityAPI[] gates;
    public final GateData[] gateDatas;

    public final boolean systemHasNoEntry;
    public final boolean hasJumpPoints;
    public final boolean hasPlanets;

    public SystemGateData(StarSystemAPI system, List<CustomCampaignEntityAPI> gates, boolean systemHasNoEntry) {
        this.system = system;
        this.systemLoc = system.getLocation();
        this.gates = gates.toArray(new CustomCampaignEntityAPI[0]);
        this.systemHasNoEntry = systemHasNoEntry;
        this.gateDatas = new GateData[gates.size()];

        if (this.gates.length == 1) {
            List<SectorEntityToken> jumpPoints = system.getJumpPoints();
            List<PlanetAPI> planets = system.getPlanets();
            this.hasJumpPoints = jumpPoints.size() > 0;
            this.hasPlanets = planets.size() > 0;

            this.gateDatas[0] = new GateData(system, jumpPoints, planets, this.hasJumpPoints,  this.hasPlanets, this.gates[0]);
            return;
        }

        List<SectorEntityToken> jumpPoints = system.getJumpPoints();
        List<PlanetAPI> planets = system.getPlanets();
        this.hasJumpPoints = jumpPoints.size() > 0;
        this.hasPlanets = planets.size() > 0;

        for (int i = 0; i < this.gates.length; i++) {
            this.gateDatas[i] = new GateData(system, jumpPoints, planets, this.hasJumpPoints,  this.hasPlanets, gates.get(i));
        }
    }
}