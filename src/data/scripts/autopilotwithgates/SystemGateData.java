package data.scripts.autopilotwithgates;

import java.util.*;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

public class SystemGateData {
    public final StarSystemAPI system;
    public final Vector2f systemLoc;
    public final CustomCampaignEntityAPI[] gates;

    public SystemGateData(StarSystemAPI system, List<CustomCampaignEntityAPI> gates) {
        this.system = system;
        this.systemLoc = system.getLocation();
        this.gates = gates.toArray(new CustomCampaignEntityAPI[0]);
    }
}