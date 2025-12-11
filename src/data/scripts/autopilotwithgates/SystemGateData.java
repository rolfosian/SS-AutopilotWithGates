package data.scripts.autopilotwithgates;

import java.util.*;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

public class SystemGateData {
    public final StarSystemAPI system;
    public final Vector2f hyperSpaceAnchorLoc;
    public final List<CustomCampaignEntityAPI> gates;

    public SystemGateData(StarSystemAPI system, List<CustomCampaignEntityAPI> gates) {
        this.system = system;
        this.gates = gates;
        this.hyperSpaceAnchorLoc = system.getHyperspaceAnchor().getLocationInHyperspace();
    }
}
