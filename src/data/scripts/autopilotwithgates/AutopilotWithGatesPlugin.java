package data.scripts.autopilotwithgates;

import java.util.*;

import org.lwjgl.opengl.Display;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.IntervalUtil;

import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.NascentGravityWellAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.UIPanelAPI;

import com.fs.starfarer.campaign.BaseLocation;
import com.fs.starfarer.campaign.CampaignEngine;
import com.fs.starfarer.campaign.CampaignUIPersistentData.AbilitySlots;

import data.scripts.autopilotwithgates.util.AoTDVersionOverride;
import data.scripts.autopilotwithgates.util.GateFinder;
import data.scripts.autopilotwithgates.util.Refl;
import data.scripts.autopilotwithgates.util.UiUtil;

// import data.kaysaar.aotd.vok.campaign.econ.globalproduction.models.GPManager;

import lunalib.lunaSettings.LunaSettings;

public class AutopilotWithGatesPlugin extends BaseModPlugin {
    public static AutoPilotListener listener;
    private static AutopilotWithGatesPlugin instance;

    private Thread systemGateIteratorThread;
    private static volatile boolean iteratorRunning = true;
    public static final Object systemGateIteratorLock = new Object();
    public static Map<LocationAPI, SystemGateData> systemsToGates = new HashMap<>();
    public static Map<LocationAPI, SystemGateData> systemsToBifrosts = new HashMap<>();
    public static List<SystemGateData> systemGateData = new ArrayList<>();
    public static List<SystemGateData> systemBifrostData = new ArrayList<>();

    public static AbilityScroller abilityScroller;

    public static boolean aotdEnabled;

    public static AutopilotWithGatesPlugin getInstance() {
        return instance;
    }

    @Override
    public void onApplicationLoad() {
        Refl.init();
        UiUtil.init();
        if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
            GateFinder.LY_DIST_TOLERANCE = Global.getSettings().getFloat("gateAutopilot_LY_DIST_TOLERANCE");
        } else {
            GateFinder.LY_DIST_TOLERANCE = Global.getSettings().getFloat("gateAutopilot_LY_DIST_TOLERANCE");
        }
        aotdEnabled = Global.getSettings().getModManager().isModEnabled("aotd_vok");
        instance = this;
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

        if (systemGateIteratorThread != null) {
            iteratorRunning = false;
            while (systemGateIteratorThread.isAlive()) {
                systemGateIteratorThread.interrupt();
            }
            systemGateData = null;
        }

        if (listener != null) {
            if (!listener.getMaps().isEmpty()) listener.getMaps().clear();
            listener.removeArrowRenderer();
        }

        listener = aotdEnabled ? new AutoPilotListenerWithBifrosts(abilityActive) : new AutoPilotListener(abilityActive);
        sector.addTransientListener(listener);
        sector.addTransientScript(listener);

        if (GateEntityPlugin.canUseGates() || canUseBifrosts()) {
            CampaignFleetAPI playerFleet = sector.getPlayerFleet();

            if (!playerFleet.hasAbility("AutoPilotWithGates")) {
                sector.getCharacterData().addAbility("AutoPilotWithGates");
                playerFleet.addAbility("AutoPilotWithGates");

                listener.setAbility((AutoPilotGatesAbility) Global.getSector().getPlayerFleet().getAbility("AutoPilotWithGates"));
                listener.getAbility().setShowingEntityPicker(false);

                sector.getCampaignUI().addMessage(UiUtil.unlockedMessagePlugin, MessageClickAction.NOTHING);

            } else if (listener.getAbility() == null) {
                AutoPilotGatesAbility ability = (AutoPilotGatesAbility) Global.getSector().getPlayerFleet().getAbility("AutoPilotWithGates");
                ability.setShowingEntityPicker(false);
                listener.setAbility(ability);
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

                    if (GateEntityPlugin.canUseGates() || canUseBifrosts()) {
                        Global.getSector().getCharacterData().addAbility("AutoPilotWithGates");
                        Global.getSector().getPlayerFleet().addAbility("AutoPilotWithGates");

                        listener.setAbility((AutoPilotGatesAbility) Global.getSector().getPlayerFleet().getAbility("AutoPilotWithGates"));
                        listener.getAbility().setShowingEntityPicker(false);

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

        if (abilityScroller != null) {
            abilityScroller.remove();
            abilityScroller = null;
        }

        if (abilityScroll) {
            Global.getSector().addTransientScript(new EveryFrameScript() {
                private boolean isDone = false;
                private int f = 0;
    
                @Override
                public void advance(float arg0) {
                    if (++f < 2) return;
                    
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

    private void layInCourseFor(SectorEntityToken target) {
        List<Object> messageDisplayList = listener.getMessageDisplayList();

        int messageDisplayListSize = messageDisplayList.size();
        Global.getSector().layInCourseFor(target);
        if (messageDisplayList.size() > messageDisplayListSize) messageDisplayList.remove(messageDisplayList.size()-1);
    }

    private BaseLocation arrowRenderingLoc;
    private boolean followMouse = false;
    private boolean followingDirectCommand = false;
    private SectorEntityToken interactionTarget = null;
    @Override
    public void beforeGameSave() {
        this.followMouse = Global.getSector().getCampaignUI().isPlayerFleetFollowingMouse();
        this.followingDirectCommand = Global.getSector().getCampaignUI().isFollowingDirectCommand();
        this.interactionTarget = Global.getSector().getPlayerFleet().getInteractionTarget();

        SectorEntityToken ult = listener.getCurrentUltimateTarget();
        if (ult != null) this.layInCourseFor(ult);

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
        SectorEntityToken entry = listener.getEntryGate() != null ? listener.getEntryGate().gate : null;
        if (entry != null) {
            this.layInCourseFor(entry);
        }

        if (this.followMouse) {
            UiUtil.setFollowMouseTrue(Global.getSector().getCampaignUI());
        } else if (this.followingDirectCommand) {
            if (this.interactionTarget != null) UiUtil.followEntity(Global.getSector().getCampaignUI(), this.interactionTarget);
            else UiUtil.setFollowMouseTrue(Global.getSector().getCampaignUI());
        }

        this.followMouse = false;
        this.followingDirectCommand = false;
        this.interactionTarget = null;

        if (this.arrowRenderingLoc != null) {
            listener.addArrowRenderer(this.arrowRenderingLoc);
            this.arrowRenderingLoc = null;
        }

        if (abilityScroller != null)  {
            CampaignEngine.getInstance().getUIData().setAbilitySlots(abilityScroller.getOurAbilitySlots());
        }
    }

    protected void registerGateIterator() {
        if (systemGateIteratorThread != null) {
            iteratorRunning = false;
            while (systemGateIteratorThread.isAlive()) {
                systemGateIteratorThread.interrupt();
            }
            systemsToGates = new HashMap<>();
            systemGateData = new ArrayList<>();

            systemsToBifrosts = new HashMap<>();
            systemBifrostData = new ArrayList<>();
        }

        iteratorRunning = true;

        systemGateIteratorThread = new Thread(
            Thread.currentThread().getThreadGroup(),
            aotdEnabled ? () -> {
                while (iteratorRunning) {
                    try {
                        if (!Display.isActive() || Global.getCurrentState() != GameState.CAMPAIGN) {
                            try {
                                Thread.sleep(10);
                                continue;
                            } catch (InterruptedException ignore) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }

                        refreshSystemGateAndBifrostData();
    
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignore) {
                            Thread.currentThread().interrupt();
                            break;
                        }

                    } catch (Throwable e) {
                        try {
                            Thread.sleep(1);
                            continue;
                        } catch (InterruptedException ignore) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } : () -> {
                while (iteratorRunning) {
                    try {
                        if (!Display.isActive() || Global.getCurrentState() != GameState.CAMPAIGN) {
                            try {
                                Thread.sleep(10);
                                continue;
                            } catch (InterruptedException ignore) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }

                        refreshSystemGateData();
    
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignore) {
                            Thread.currentThread().interrupt();
                            break;
                        }

                    } catch (Throwable e) {
                        try {
                            Thread.sleep(1);
                            continue;
                        } catch (InterruptedException ignore) {
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

    private static void refreshSystemGateData() {
        List<NascentGravityWellAPI> wells = snapshot(Global.getSector().getHyperspace().getGravityWells());
        List<SectorEntityToken> jumpPoints = snapshot(Global.getSector().getHyperspace().getJumpPoints());
        List<SystemGateData> newSystemGateData = new ArrayList<>();
        Map<LocationAPI, SystemGateData> newSystemsToGates = new HashMap<>();

        for (StarSystemAPI system : snapshot(Global.getSector().getStarSystems())) {
            List<CustomCampaignEntityAPI> gates = snapshot(system.getCustomEntitiesWithTag(Tags.GATE));

            if (gates.size() > 0) {
                List<CustomCampaignEntityAPI> gatos = new ArrayList<>();
                for (CustomCampaignEntityAPI gate : gates) {
                    if (isScanned(gate)) gatos.add(gate);
                }
                if (gatos.size() > 0) {
                    SystemGateData data = new SystemGateData(system, gatos, isNoEntry(system, wells, jumpPoints));
                    newSystemGateData.add(data);
                    newSystemsToGates.put(system, data);
                }
            }
        }

        synchronized(systemGateIteratorLock) {
            systemsToGates.clear();
            systemsToGates.putAll(newSystemsToGates);
            systemGateData.clear();
            systemGateData.addAll(newSystemGateData);
        }
    }

    private static void refreshSystemGateAndBifrostData() {
        List<NascentGravityWellAPI> wells = snapshot(Global.getSector().getHyperspace().getGravityWells());
        List<SectorEntityToken> jumpPoints = snapshot(Global.getSector().getHyperspace().getJumpPoints());

        Map<LocationAPI, SystemGateData> newSystemsToGates = new HashMap<>();
        List<SystemGateData> newSystemGateData = new ArrayList<>();

        Map<LocationAPI, SystemGateData> newSystemsToBifrosts = new HashMap<>();
        List<SystemGateData> newSystemBifrostData = new ArrayList<>();

        for (StarSystemAPI system : snapshot(Global.getSector().getStarSystems())) {
            List<CustomCampaignEntityAPI> gates = snapshot(system.getCustomEntitiesWithTag(Tags.GATE));

            if (gates.size() > 0) {
                List<CustomCampaignEntityAPI> gatos = new ArrayList<>();
                for (CustomCampaignEntityAPI gate : gates) {
                    if (isScanned(gate)) gatos.add(gate);
                }
                if (gatos.size() > 0) {
                    SystemGateData data = new SystemGateData(system, gatos, isNoEntry(system, wells, jumpPoints));
                    newSystemGateData.add(data);
                    newSystemsToGates.put(system, data);
                }
            }

            List<CustomCampaignEntityAPI> bifrosts = snapshot(system.getCustomEntitiesWithTag("bifrost"));

            if (bifrosts.size() > 0) {
                List<CustomCampaignEntityAPI> bifrostos = new ArrayList<>();
                for (CustomCampaignEntityAPI bifrost : bifrosts) {
                    if (isBifrostUsable(bifrost)) bifrostos.add(bifrost);
                }

                if (bifrostos.size() > 0) {
                    SystemGateData data = new SystemGateData(system, bifrostos, isNoEntry(system, wells, jumpPoints));
                    newSystemBifrostData.add(data);
                    newSystemsToBifrosts.put(system, data);
                } 
            }
        }

        synchronized(systemGateIteratorLock) {
            systemsToBifrosts.clear();
            systemsToBifrosts.putAll(newSystemsToBifrosts);
            systemBifrostData.clear();
            systemBifrostData.addAll(newSystemBifrostData);

            systemsToGates.clear();
            systemsToGates.putAll(newSystemsToGates);
            systemGateData.clear();
            systemGateData.addAll(newSystemGateData);
        }
    }

    private static <T> List<T> snapshot(List<T> list) {
        if (list == null) return Collections.emptyList();
    
        for (int i = 0; i < 5; i++) {
            try {
                return new ArrayList<>(list);
            } catch (ConcurrentModificationException ignored) {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    
        return Collections.emptyList();
    }

    public static boolean isBifrostUsable(CustomCampaignEntityAPI bifrost) {
        for (int i = 0; i < 5; i++) {
            try {
                if (listener.isBlacklisted(bifrost)) return false;

                MemoryAPI mem = bifrost.getMemory();
                if (mem == null) return false;

                return mem.is("$used", false) && !bifrost.hasTag("fading_out_and_expiring");

            } catch (ConcurrentModificationException ignored) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return false;
    }

    private static boolean canUseBifrosts() {
        return aotdEnabled && AoTDVersionOverride.delegate.canUseBifrosts();
    }

    private static boolean isScanned(CustomCampaignEntityAPI gate) {
        for (int i = 0; i < 5; i++) {
            try {
                return listener.isBlacklisted(gate) ? false : GateEntityPlugin.isScanned(gate);
            } catch (ConcurrentModificationException ignored) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return isScanned(gate);
    }

    private static boolean isNoEntry(StarSystemAPI system, List<NascentGravityWellAPI> wellsInHyper, List<SectorEntityToken> jumpPointsInHyper) {
        for (NascentGravityWellAPI well : wellsInHyper) {
            if (well.getTarget().getContainingLocation() == system) return false;
        }
        for (SectorEntityToken token : jumpPointsInHyper) {
            if (token instanceof JumpPointAPI jp && jp.getDestinationStarSystem() == system) return false;
        }
        return true;
    }
}
