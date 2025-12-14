package data.scripts.autopilotwithgates;

import java.util.*;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
// import com.fs.starfarer.api.campaign.JumpPointAPI;
// import com.fs.starfarer.api.campaign.PlanetAPI;
// import com.fs.starfarer.api.campaign.SectorEntityToken;

public class SystemGateData {
    public final StarSystemAPI system;
    public final Vector2f systemLoc;
    public final CustomCampaignEntityAPI[] gates;
    // public final GateData[] gateData;

    public SystemGateData(StarSystemAPI system, List<CustomCampaignEntityAPI> gates) {
        this.system = system;
        this.systemLoc = system.getLocation();
        this.gates = gates.toArray(new CustomCampaignEntityAPI[0]);
        // this.gateData = new GateData[gates.size()];

        // List<SectorEntityToken> jumpPoints = new ArrayList<>();
        // for (SectorEntityToken jumpPoint : system.getJumpPoints()) {
        //     if (jumpPoint instanceof JumpPointAPI && jumpPoint.getContainingLocation() == system) {
        //         jumpPoints.add(jumpPoint);
        //     }
        // }
        // List<PlanetAPI> planets = system.getPlanets();

        // boolean hasJumpPoints = jumpPoints.size() > 0;
        // boolean hasPlanets = planets.size() > 0;

        // for (int i = 0; i < this.gates.length; i++) {
        //     this.gateData[i] = new GateData(system, jumpPoints, planets, hasJumpPoints, hasPlanets, gates.get(i));
        // }
    }
}