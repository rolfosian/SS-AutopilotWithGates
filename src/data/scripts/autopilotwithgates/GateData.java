package data.scripts.autopilotwithgates;

import java.util.List;

import com.fs.starfarer.api.campaign.*;
import org.lwjgl.util.vector.Vector2f;

import data.scripts.autopilotwithgates.util.GateFinder;

public class GateData {
    public final StarSystemAPI system;
    public final CustomCampaignEntityAPI gate;

    public SectorEntityToken closestExit;
    public SectorEntityToken closestEntry;

    public float closestExitDistSq;
    public float closestEntryDistSq;

    public GateData(StarSystemAPI system, List<SectorEntityToken> jumpPoints, List<PlanetAPI> planets, boolean hasJumpPoints, boolean hasPlanets, CustomCampaignEntityAPI gate) {
        this.system = system;
        this.gate = gate;
        Vector2f gateLoc = gate.getLocation();

        if (hasJumpPoints && hasPlanets) {
            PlanetAPI closestGravityWell = GateFinder.getClosestPlanetGravityWell(planets, gateLoc);

            if (closestGravityWell != null) {
                SectorEntityToken closestJumpPoint = GateFinder.getClosestJumpPoint(jumpPoints, gateLoc);

                float jpDistSq = GateFinder.getDistSq(gateLoc, closestJumpPoint.getLocation());
                float gwDistSq = GateFinder.getDistSq(gateLoc, closestGravityWell.getLocation());

                this.closestEntry = jpDistSq > gwDistSq ? closestGravityWell : closestJumpPoint;
                this.closestEntryDistSq = GateFinder.getDistSq(gateLoc, this.closestEntry.getLocation());

                this.closestExit = closestJumpPoint;
                this.closestExitDistSq = GateFinder.getDistSq(gateLoc, this.closestExit.getLocation());

            } else {
                this.closestEntry = GateFinder.getClosestJumpPoint(jumpPoints, gateLoc);
                float distSq =  GateFinder.getDistSq(gateLoc, this.closestEntry.getLocation());
                this.closestEntryDistSq = distSq;

                this.closestExit = this.closestEntry;
                this.closestExitDistSq = distSq;
            }

        } else if (hasJumpPoints) {
            SectorEntityToken closestJumpPoint = GateFinder.getClosestJumpPoint(jumpPoints, gateLoc);
            this.closestEntry = closestJumpPoint;
            this.closestExit = closestJumpPoint;

            float distSq =  GateFinder.getDistSq(gateLoc, this.closestEntry.getLocation());
            this.closestEntryDistSq = distSq;
            this.closestExitDistSq = distSq;

        } else if (hasPlanets) {
            PlanetAPI closestGravityWell = GateFinder.getClosestPlanetGravityWell(planets, gateLoc);
            this.closestEntry = closestGravityWell != null ? closestGravityWell : system.getCenter();
            this.closestExit = system.getCenter();

            this.closestEntryDistSq = GateFinder.getDistSq(gateLoc, this.closestEntry.getLocation());
            this.closestExitDistSq = Float.MAX_VALUE;

        } else {
            this.closestEntry = system.getCenter();
            this.closestExit = system.getCenter();
            this.closestEntryDistSq = GateFinder.getDistSq(gateLoc, this.closestEntry.getLocation());
            this.closestExitDistSq = Float.MAX_VALUE;
        }
    }
}
