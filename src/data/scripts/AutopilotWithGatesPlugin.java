package data.scripts;

import java.util.Map;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;

public class AutopilotWithGatesPlugin extends BaseModPlugin {
    public static AutoPilotListener listener;

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
        if (Global.getSettings().getModManager().isModEnabled("LunaLib")) {
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

        listener = new AutoPilotListener(abilityActive);
        sector.addTransientListener(listener);
        sector.addTransientScript(listener);

        if (GateEntityPlugin.canUseGates()) {
            CampaignFleetAPI playerFleet = sector.getPlayerFleet();

            if (!playerFleet.hasAbility("AutoPilotWithGates")) {
                sector.getCharacterData().addAbility("AutoPilotWithGates");
                playerFleet.addAbility("AutoPilotWithGates");
                sector.getCampaignUI().addMessage(unlockedMessagePlugin, MessageClickAction.NOTHING);
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

    @Override
    public void beforeGameSave() {
        SectorEntityToken ult = listener.getCurrentUltimateTarget();

        if (ult != null) {
            Global.getSector().layInCourseFor(ult);
        }
    }

    @Override
    public void afterGameSave() {
        SectorEntityToken entry = listener.getEntryGate();

        if (entry != null) {
            Global.getSector().layInCourseFor(entry);
        }
    }
}
