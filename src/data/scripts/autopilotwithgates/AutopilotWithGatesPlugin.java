package data.scripts.autopilotwithgates;

import java.util.*;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.BaseLocation;

import data.scripts.autopilotwithgates.util.GateFinder;
import data.scripts.autopilotwithgates.util.Refl;
import data.scripts.autopilotwithgates.util.UiUtil;

import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;

public class AutopilotWithGatesPlugin extends BaseModPlugin {
    public static AutoPilotListener listener;
    // public static List<SystemGateData> systemGateData;

    private static final BaseIntelPlugin unlockedMessagePlugin = new BaseIntelPlugin() {
        @Override
        public String getIcon() {
            return "graphics/icons/missions/at_the_gates.png";
        }
        @Override
        public boolean isHidden() {
            return false;
        }
        @Override
        public void createIntelInfo(TooltipMakerAPI info, IntelInfoPlugin.ListInfoMode mode) {
            info.setParaFontColor(Misc.getBrightPlayerColor());
            info.addPara("Autopilot With Gates ability unlocked", 0f);
        }
    };

    @Override
    public void onApplicationLoad() {
        Refl.init();
        UiUtil.init();
        if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
            GateFinder.LY_DIST_TOLERANCE = Global.getSettings().getFloat("gateAutopilot_LY_DIST_TOLERANCE");
        } else {
            GateFinder.LY_DIST_TOLERANCE = Global.getSettings().getFloat("gateAutopilot_LY_DIST_TOLERANCE");
        }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        SectorAPI sector = Global.getSector();
        Map<String, Object> persistentData = sector.getPersistentData();
        Boolean abilityActive = (Boolean) persistentData.get("$autopilotWithGatesAbility");

        if (abilityActive == null) {
            persistentData.put("$autopilotWithGatesAbility", false);
            abilityActive = false;
        }

        // populateSystemGateData();

        listener = new AutoPilotListener(abilityActive);
        sector.addTransientListener(listener);
        sector.addTransientScript(listener);

        if (GateEntityPlugin.canUseGates()) {
            CampaignFleetAPI playerFleet = sector.getPlayerFleet();

            if (!playerFleet.hasAbility("AutoPilotWithGates")) {
                sector.getCharacterData().addAbility("AutoPilotWithGates");
                playerFleet.addAbility("AutoPilotWithGates");
                listener.setAbility((AutoPilotGatesAbility) Global.getSector().getPlayerFleet().getAbility("AutoPilotWithGates"));

                sector.getCampaignUI().addMessage(unlockedMessagePlugin, MessageClickAction.NOTHING);

            } else if (listener.getAbility() == null) {
                listener.setAbility((AutoPilotGatesAbility) Global.getSector().getPlayerFleet().getAbility("AutoPilotWithGates"));
            }

        } else {
            sector.getCharacterData().removeAbility("AutoPilotWithGates");
            sector.getPlayerFleet().removeAbility("AutoPilotWithGates");

            sector.addTransientScript(new EveryFrameScript() {
                private IntervalUtil interval = new IntervalUtil(0.5f, 0.5f);
                private boolean isDone = false;
                @Override
                public void advance(float arg0) {
                    interval.advance(arg0);
                    if (!interval.intervalElapsed()) return;

                    if (GateEntityPlugin.canUseGates()) {
                        Global.getSector().getCharacterData().addAbility("AutoPilotWithGates");
                        Global.getSector().getPlayerFleet().addAbility("AutoPilotWithGates");
                        listener.setAbility((AutoPilotGatesAbility) Global.getSector().getPlayerFleet().getAbility("AutoPilotWithGates"));

                        Global.getSector().getCampaignUI().addMessage(unlockedMessagePlugin, MessageClickAction.NOTHING);

                        isDone = true;
                        Global.getSector().removeTransientScript(this);
                    }
                }

                @Override
                public boolean isDone() {
                    return isDone;
                }

                @Override
                public boolean runWhilePaused() {
                    return true;
                }
            });
        }
    }

    private BaseLocation arrowRenderingLoc;
    @Override
    public void beforeGameSave() {
        SectorEntityToken ult = listener.getCurrentUltimateTarget();
        if (ult != null) Global.getSector().layInCourseFor(ult);

        this.arrowRenderingLoc = listener.getArrowRenderingLoc();
        if (this.arrowRenderingLoc != null) listener.removeArrowRenderer();
    }

    @Override
    public void afterGameSave() {
        SectorEntityToken entry = listener.getEntryGate();
        if (entry != null) Global.getSector().layInCourseFor(entry);

        if (this.arrowRenderingLoc != null) {
            listener.addArrowRenderer(this.arrowRenderingLoc);
            this.arrowRenderingLoc = null;
        }
    }

    // private void populateSystemGateData() {
    //     systemGateData = new ArrayList<>();

    //     for (StarSystemAPI system : Global.getSector().getStarSystems()) {
    //         List<CustomCampaignEntityAPI> gates = system.getCustomEntitiesWithTag(Tags.GATE);

    //         if (gates.size() > 0) {
    //             systemGateData.add(new SystemGateData(system, gates));
    //         }
    //     }
    // }
}
