package data.scripts.autopilotwithgates;

import java.util.*;

import org.lwjgl.opengl.Display;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;

import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.IntervalUtil;

import com.fs.starfarer.campaign.BaseLocation;
import com.fs.starfarer.campaign.CampaignEngine;
import com.fs.starfarer.campaign.CampaignUIPersistentData.AbilitySlots;

import data.scripts.autopilotwithgates.util.GateFinder;
import data.scripts.autopilotwithgates.util.Refl;
import data.scripts.autopilotwithgates.util.UiUtil;


import lunalib.lunaSettings.LunaSettings;

public class AutopilotWithGatesPlugin extends BaseModPlugin {
    public static AutoPilotListener listener;

    private Thread systemGateIteratorThread;
    private static volatile boolean iteratorRunning = true;
    public static List<SystemGateData> systemGateData;

    public static AbilityScroller abilityScroller;

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

        // for (StarSystemAPI system : Global.getSector().getStarSystems()) {
        //     List<CustomCampaignEntityAPI> gates = system.getCustomEntitiesWithTag(Tags.GATE);
        //     for (CustomCampaignEntityAPI gate : gates) {
        //         if (!GateEntityPlugin.isScanned(gate)) {
        //             GateCMD.notifyScanned(gate);
        //             gate.getMemoryWithoutUpdate().set("$gateScanned", true);
        //         } 
        //     }
        // }

        if (systemGateIteratorThread != null) {
            iteratorRunning = false;
            while (systemGateIteratorThread.isAlive()) {
                systemGateIteratorThread.interrupt();
            }
            systemGateData = null;
        }

        if (listener != null && !listener.getMaps().isEmpty()) listener.getMaps().clear();

        listener = new AutoPilotListener(abilityActive);
        sector.addTransientListener(listener);
        sector.addTransientScript(listener);

        if (GateEntityPlugin.canUseGates()) {
            CampaignFleetAPI playerFleet = sector.getPlayerFleet();

            if (!playerFleet.hasAbility("AutoPilotWithGates")) {
                sector.getCharacterData().addAbility("AutoPilotWithGates");
                playerFleet.addAbility("AutoPilotWithGates");
                listener.setAbility((AutoPilotGatesAbility) Global.getSector().getPlayerFleet().getAbility("AutoPilotWithGates"));

                sector.getCampaignUI().addMessage(UiUtil.unlockedMessagePlugin, MessageClickAction.NOTHING);

            } else if (listener.getAbility() == null) {
                listener.setAbility((AutoPilotGatesAbility) Global.getSector().getPlayerFleet().getAbility("AutoPilotWithGates"));
            }

            registerGateIterator();

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

                        registerGateIterator();

                        Global.getSector().getCampaignUI().addMessage(UiUtil.unlockedMessagePlugin, MessageClickAction.NOTHING);

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

        boolean abilityScroll;
        if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
            abilityScroll = LunaSettings.getBoolean("autopilot_with_gates", "abilityScroll");
        } else {
            abilityScroll = Global.getSettings().getBoolean("gateAutopilot_abilityScroll");
        }

        if (abilityScroller != null) abilityScroller.remove();

        if (abilityScroll) {
            Global.getSector().addTransientScript(new EveryFrameScript() {
                private boolean isDone = false;
                private int f = 0;
    
                @Override
                public void advance(float arg0) {
                    if (f++ < 11) return;
                    
                    Object core = UiUtil.getCore(sector.getCampaignUI(), sector.getCampaignUI().getCurrentInteractionDialog());
                    if (core == null) return;

                    UIPanelAPI abilityPanel = UiUtil.getAbilityPanel(core);
                    if (abilityPanel == null) return;

                    abilityScroller = new AbilityScroller(abilityPanel);
    
                    this.isDone = true;
                    Global.getSector().removeTransientScript(this); 
                }
    
                @Override
                public boolean isDone() {
                    return this.isDone;
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

        if (abilityScroller != null) {
            AbilitySlots oldAbilitySlots = abilityScroller.getOldAbilitySlots();
            AbilitySlots ourAbilitySlots = abilityScroller.getOurAbilitySlots();
            
            oldAbilitySlots.setCurrBarIndex(ourAbilitySlots.getCurrBarIndex());
            oldAbilitySlots.setLocked(ourAbilitySlots.isLocked());

            CampaignEngine.getInstance().getUIData().setAbilitySlots(oldAbilitySlots);
        } 
    }

    @Override
    public void afterGameSave() {
        SectorEntityToken entry = listener.getEntryGate();
        if (entry != null) Global.getSector().layInCourseFor(entry);

        if (this.arrowRenderingLoc != null) {
            listener.addArrowRenderer(this.arrowRenderingLoc);
            this.arrowRenderingLoc = null;
        }

        if (abilityScroller != null)  {
            CampaignEngine.getInstance().getUIData().setAbilitySlots(abilityScroller.getOurAbilitySlots());
        }
    }

    private void registerGateIterator() {
        if (systemGateIteratorThread != null) {
            iteratorRunning = false;
            while (systemGateIteratorThread.isAlive()) {
                systemGateIteratorThread.interrupt();
            }
            systemGateData = null;
        }

        systemGateData = new ArrayList<>();
        iteratorRunning = true;

        systemGateIteratorThread = new Thread(
            Thread.currentThread().getThreadGroup(),
            new Runnable() {
                @Override
                public void run() {
                    while (iteratorRunning) {
                        if (!Display.isActive() || Global.getCurrentState() != GameState.CAMPAIGN) {
                            try {
                                Thread.sleep(10);
                                continue;
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        List<SystemGateData> newSystemGateData = new ArrayList<>();
            
                        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                            List<CustomCampaignEntityAPI> gates = system.getCustomEntitiesWithTag(Tags.GATE);
                
                            if (gates.size() > 0) {
                                List<CustomCampaignEntityAPI> gatos = new ArrayList<>();
                                for (CustomCampaignEntityAPI gate : gates) {
                                    if (GateEntityPlugin.isScanned(gate)) gatos.add(gate);
                                }
                                if (gatos.size() > 0) newSystemGateData.add(new SystemGateData(system, gatos));
                            } 
                        }
        
                        synchronized(systemGateData) {
                            systemGateData.clear();
                            systemGateData.addAll(newSystemGateData);
                        }

                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            },
            "AutopilotWithGatesIterator"
        );

        systemGateIteratorThread.start();
    }
}
