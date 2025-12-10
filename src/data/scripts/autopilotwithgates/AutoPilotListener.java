package data.scripts.autopilotwithgates;

import java.awt.Color;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import com.fs.graphics.LayeredRenderable;

import com.fs.starfarer.campaign.BaseLocation;
import com.fs.starfarer.campaign.CampaignEngine;
import com.fs.starfarer.campaign.fleet.CampaignFleet;
import com.fs.starfarer.combat.CombatViewport;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI.OptionTooltipCreator;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.missions.GateCMD;

import com.fs.starfarer.api.graphics.SpriteAPI;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;

// import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

import data.scripts.autopilotwithgates.util.GateAutoPilotRuleMemory;
import data.scripts.autopilotwithgates.util.GateFinder;
import data.scripts.autopilotwithgates.util.UiUtil;
import data.scripts.autopilotwithgates.util.TreeTraverser;
import data.scripts.autopilotwithgates.util.TreeTraverser.TreeNode;

import lunalib.lunaSettings.LunaSettings;

public class AutoPilotListener extends BaseCampaignEventListener implements EveryFrameScript, LayeredRenderable<CampaignEngineLayers, CombatViewport> {
    private static final Logger logger = Logger.getLogger(AutoPilotListener.class);
    public static void print(Object... args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            sb.append(args[i] instanceof String ? (String) args[i] : String.valueOf(args[i]));
            if (i < args.length - 1) sb.append(' ');
        }
        logger.info(sb.toString());
    }
    private static final EnumSet<CampaignEngineLayers> layers = EnumSet.of(CampaignEngineLayers.FLEETS);
    private static final Color DARK_RED = new Color(139, 0, 0);
    private static final Color DARK_GREEN = new Color(0, 139, 0);
    private static final SpriteAPI arrow = Global.getSettings().getSprite("graphics/warroom/ship_arrow.png");
    private static final SpriteAPI gateCircle = Global.getSettings().getSprite("graphics/icons/gate0.png");

    private final AutoPilotListener self = this;
    private AutoPilotGatesAbility ability;
    private final boolean autoJump;
    // private IntervalUtil interval = new IntervalUtil(0.1f, 0.1f);

    private SectorEntityToken currentUltimateTarget;
    private CustomCampaignEntityAPI entryGate;
    private CustomCampaignEntityAPI exitGate;

    private boolean postGateJump = false;
    private boolean abilityActive = false;

    private boolean renderingArrow = false;
    private BaseLocation arrowRenderingLoc;
    private Color arrowColor = DARK_RED;

    private Map<UIPanelAPI, CustomPanelAPI> dialogMaps = new HashMap<>() {
        @Override
        public void clear() {
            for (Map.Entry<UIPanelAPI, CustomPanelAPI> entry : this.entrySet()) {
                entry.getKey().removeComponent(entry.getValue());
            }
            super.clear();
        }
    };

    public AutoPilotListener(boolean abilityActive) {
        super(false);
        this.abilityActive = abilityActive;
        
        if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
            this.autoJump = LunaSettings.getBoolean("autopilot_with_gates", "autoJump");

        } else {
            this.autoJump = Global.getSettings().getBoolean("gateAutopilot_autoJump");
        }
    }

    @Override
    public void advance(float arg0) {
        if (!this.abilityActive) return;
        // this.interval.advance(arg0);
        // if (!this.interval.intervalElapsed()) return;

        CampaignUIAPI campaignUI = Global.getSector().getCampaignUI();
        if (UiUtil.getCourseWidget(campaignUI) == null) return;

        SectorEntityToken ultimateTarget = campaignUI.getUltimateCourseTarget();
        if (ultimateTarget == null) {
            if (this.mapTabMap != null) {
                this.mapTabMap.removeComponent(this.mapTabMapArrowPanel);
                this.mapTabMap = null;
                this.mapTab = null;
            }

            if (this.intelTabMap != null) {
                this.intelTabMap.removeComponent(this.intelTabMapArrowPanel);
                this.intelTabMap = null;
                this.intelTab = null;
            }

            if (!this.dialogMaps.isEmpty()) dialogMaps.clear();

            this.currentUltimateTarget = null;
            this.entryGate = null;
            this.exitGate = null;
            return;
        }
        boolean ultimateTargetIsEntryGate = ultimateTarget == this.entryGate;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        LocationAPI playerLoc = playerFleet.getContainingLocation();

        if (this.arrowRenderingLoc != null && playerLoc != this.arrowRenderingLoc) {
            removeArrowRenderer();
            addArrowRenderer(playerLoc);

        } else if (this.entryGate != null && !this.renderingArrow) {
            addArrowRenderer(playerLoc);

        } else if (this.entryGate == null) {
            if (this.renderingArrow) removeArrowRenderer();
            if (!this.dialogMaps.isEmpty()) this.dialogMaps.clear();

            if (this.mapTabMap != null) {
                this.mapTabMap.removeComponent(this.mapTabMapArrowPanel);
                this.mapTabMap = null;
                this.mapTab = null;
            }

            if (this.intelTabMap != null) {
                this.intelTabMap.removeComponent(this.intelTabMapArrowPanel);
                this.intelTabMap = null;
                this.intelTab = null;
            }
        }
        
        if (ultimateTargetIsEntryGate) {
            if (GateFinder.getCombinedFuelCost(playerFleet, this.entryGate, this.exitGate, this.currentUltimateTarget)
                > GateFinder.getFuelCostToUltimateTarget(playerFleet, this.currentUltimateTarget)) this.arrowColor = DARK_GREEN;
            else this.arrowColor = DARK_RED;

            CoreUITabId currentCoreTabId = campaignUI.getCurrentCoreTab();
            InteractionDialogAPI interactionDialog = campaignUI.getCurrentInteractionDialog();
            boolean dialogMapsIsEmpty = this.dialogMaps.isEmpty();

            // there is potential for maps with course arrow functionality in interaction dialog tree (courier missions etc)
            if (interactionDialog != null && interactionDialog.getInteractionTarget() != self.entryGate) {
                if (this.intelTabMap != null) {
                    this.intelTabMap.removeComponent(this.intelTabMapArrowPanel);
                    this.intelTabMap = null;
                    this.intelTab = null;

                } else if (this.mapTabMap != null) {
                    this.mapTabMap.removeComponent(this.mapTabMapArrowPanel);
                    this.mapTabMap = null;
                    this.mapTab = null;
                }

                TreeTraverser trav = new TreeTraverser(interactionDialog);
                for (TreeNode node : trav.getNodes()) {
                    for (UIComponentAPI child : node.getChildren()) {
                        if (child.getClass() == UiUtil.mapClass && !dialogMaps.containsKey(child)) {
                            UIPanelAPI mape = (UIPanelAPI) child;
                            CustomPanelAPI ephArrowPanel = Global.getSettings().createCustom(0f, 0f, new EphemeralMapArrowRenderer(mape));

                            mape.addComponent(ephArrowPanel);
                            this.dialogMaps.put(mape, ephArrowPanel);
                        }
                    }
                }

            } else if (!dialogMapsIsEmpty) {
                this.dialogMaps.clear();
            }
            
            if (CoreUITabId.MAP == currentCoreTabId) {
                if (interactionDialog != null && this.mapTabMap != null) {
                    this.mapTab = UiUtil.coreGetCurrentTab(UiUtil.getCore(campaignUI, interactionDialog));
                    UIPanelAPI mape = (UIPanelAPI) getMapFromMapTab(this.mapTab);

                    if (mape != this.mapTabMap) {
                        this.mapTabMap.removeComponent(this.mapTabMapArrowPanel);
                        this.mapTabMap = mape;
                        this.mapTabMap.addComponent(this.mapTabMapArrowPanel);
                    }
                }

                if (this.intelTabMap != null) {
                    this.intelTabMap.removeComponent(this.intelTabMapArrowPanel);
                    this.intelTabMap = null;
                    this.intelTab = null;
                }

                if (this.mapTabMap == null && dialogMapsIsEmpty) {
                    this.mapTab = UiUtil.coreGetCurrentTab(UiUtil.getCore(campaignUI, interactionDialog));

                    this.mapTabMap = (UIPanelAPI) getMapFromMapTab(this.mapTab);
                    this.mapTabMap.addComponent(this.mapTabMapArrowPanel);
                }

            } else if (CoreUITabId.INTEL == currentCoreTabId) {
                if (interactionDialog != null && this.intelTabMap != null) {
                    this.intelTab = UiUtil.coreGetCurrentTab(UiUtil.getCore(campaignUI, interactionDialog));
                    UIPanelAPI mape = (UIPanelAPI) getMapFromIntelTab(this.intelTab);

                    if (mape != this.intelTabMap) {
                        this.intelTabMap.removeComponent(this.intelTabMapArrowPanel);
                        this.intelTabMap = mape;
                        this.intelTabMap.addComponent(this.intelTabMapArrowPanel);
                    }
                }

                if (this.mapTabMap != null) {
                    this.mapTabMap.removeComponent(this.mapTabMapArrowPanel);
                    this.mapTabMap = null;
                    this.mapTab = null;
                }

                if (this.intelTabMap == null && dialogMapsIsEmpty) {
                    this.intelTab = UiUtil.coreGetCurrentTab(UiUtil.getCore(campaignUI, interactionDialog));

                    this.intelTabMap = (UIPanelAPI) getMapFromIntelTab(this.intelTab);
                    this.intelTabMap.addComponent(this.intelTabMapArrowPanel);

                } else if (this.intelTabMap != null) {
                    UIPanelAPI intTab = UiUtil.coreGetCurrentTab(UiUtil.getCore(campaignUI, interactionDialog));
                    if (intTab != this.intelTab) {
                        this.intelTab = intTab;

                        this.intelTabMap.removeComponent(this.intelTabMapArrowPanel);
                        this.intelTabMap = (UIPanelAPI) getMapFromIntelTab(this.intelTab);
                        this.intelTabMap.addComponent(this.intelTabMapArrowPanel);
                    }
                }

            } else {
                if (this.intelTabMap != null) {
                    this.intelTabMap.removeComponent(this.intelTabMapArrowPanel);
                    this.intelTabMap = null;
                    this.intelTab = null;
                }

                 if (this.mapTabMap != null) {
                    this.mapTabMap.removeComponent(this.mapTabMapArrowPanel);
                    this.mapTabMap = null;
                    this.mapTab = null;
                }
            }
            
            if (!playerFleet.isInHyperspace()) return;
            CustomCampaignEntityAPI newEntryGate = GateFinder.getNearestGateToPlayerOutsideLocation(this.exitGate);

            if (newEntryGate != null) {
                if (this.entryGate != newEntryGate) {
                    this.entryGate = newEntryGate;
                    Global.getSector().layInCourseFor(this.entryGate);
                }
                return;

            } else {
                Global.getSector().layInCourseFor(this.currentUltimateTarget);
                this.entryGate = null;
                this.exitGate = null;
                this.currentUltimateTarget = null;
                return;
            }
        }

        this.currentUltimateTarget = ultimateTarget;

        if (playerLoc instanceof StarSystemAPI) {
            this.entryGate = GateFinder.getNearestGateInLocation(playerLoc, playerFleet.getLocation());

            if (this.entryGate != null) {
                this.exitGate = GateFinder.getNearestGate(ultimateTarget);

                if (this.exitGate != null) {
                    Global.getSector().layInCourseFor(this.entryGate);

                } else {
                    this.entryGate = null;
                }
                return;
            }
        }
        this.exitGate = GateFinder.getNearestGate(ultimateTarget);
        this.entryGate = GateFinder.getNearestGateToPlayerOutsideLocation(this.exitGate);

        if (this.entryGate != null && this.exitGate != null) {
            Global.getSector().layInCourseFor(this.entryGate);
            return;
        }
        this.entryGate = null;
        this.exitGate = null;
        return;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {
        SectorEntityToken interactionTarget = dialog.getInteractionTarget();

        if (interactionTarget != null && interactionTarget == this.entryGate) {
            int cost = GateCMD.computeFuelCost(this.exitGate);
            int available = (int) Global.getSector().getPlayerFleet().getCargo().getFuel();

            if (cost <= available) {
                CustomCampaignEntityAPI entry = entryGate;
                CustomCampaignEntityAPI exit = exitGate;
                SectorEntityToken ultimateTarget = currentUltimateTarget;

                Runnable jump = new Runnable() {
                    @Override
                    public void run() {
                        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
                        playerFleet.getCargo().removeFuel(cost);
                        dialog.dismiss();
                        removeArrowRenderer();

                        Global.getSector().setPaused(false);
                        JumpDestination dest = new JumpDestination(exit, null);
                        Global.getSector().doHyperspaceTransition(playerFleet, interactionTarget, dest, 2f);
                        
                        float distLY = Misc.getDistanceLY(exit, entry);
                        
                        GateEntityPlugin plugin = (GateEntityPlugin) exit.getCustomPlugin();
                        plugin.showBeingUsed(distLY);
                        
                        plugin = (GateEntityPlugin) entry.getCustomPlugin();
                        plugin.showBeingUsed(distLY);
                        
                        ListenerUtil.reportFleetTransitingGate(playerFleet, interactionTarget, exit);

                        // AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
                        Global.getSector().addTransientScript(new EveryFrameScript() {
                            private boolean isDone = false;

                            @Override
                            public void advance(float arg0) {
                                if (Global.getSector().getPlayerFleet().getContainingLocation() != exit.getContainingLocation()) return;
                                Global.getSector().layInCourseFor(ultimateTarget);
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

                        self.postGateJump = true;
                        self.entryGate = null;
                        self.exitGate = null;

                        dialog.getPlugin().getMemoryMap().remove("$gateAutoPilotRule");
                    }
                };

                Global.getSector().addTransientScript(new EveryFrameScript() {
                    private boolean isDone = false;

                    @Override
                    public void advance(float arg0) {
                        if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() == null) {
                            if (self.postGateJump) {
                                self.postGateJump = false;
                                this.isDone = true;
                                Global.getSector().removeTransientScript(this);
                                return;
                            }

                            Global.getSector().getCampaignUI().clearLaidInCourse();
                            dialog.getPlugin().getMemoryMap().remove("$gateAutoPilotRule");

                            self.postGateJump = false;
                            self.abilityActive = false;
                            
                            self.ability.deactivate();
                            Global.getSector().layInCourseFor(ultimateTarget);

                            this.isDone = true;
                            Global.getSector().removeTransientScript(this);
                            return;
                        }
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

                if (this.autoJump) {
                    jump.run();
                    return;
                }

                MemoryAPI mem = new GateAutoPilotRuleMemory();
                mem.set("jump", jump);
                dialog.getPlugin().getMemoryMap().put("$gateAutoPilotRule", mem);

                dialog.getOptionPanel().addOption(
                    "Travel through the gate to " + exit.getContainingLocation().getName(),
                    "gateAutoPilotRule"
                );
                dialog.getOptionPanel().addOptionTooltipAppender("gateAutoPilotRule", new OptionTooltipCreator() {
                    @Override
                    public void createTooltip(TooltipMakerAPI arg0, boolean arg1) {
                        arg0.addParaWithMarkup("Travel through the gate to get to ultimate autopilot course target " + ultimateTarget.getName() + " in "
                            + ultimateTarget.getContainingLocation().getName() + " at the cost of {{%s}} fuel.",
                            0f,
                            String.valueOf(cost)
                        );
                    }
                });
                return;

            } else {
                CustomCampaignEntityAPI exit = this.exitGate;
                dialog.getOptionPanel().addOption(
                    "Travel through the gate to " + exit.getContainingLocation().getName(),
                    "gateAutoPilotRule"
                );
                dialog.getOptionPanel().setTooltip("gateAutoPilotRule", "Not enough fuel to make the jump.");
                dialog.getOptionPanel().setEnabled("gateAutoPilotRule", false);
                return;
            }
        }
    }

    public void on() {
        this.abilityActive = true;
    }

    public void off() {
        this.abilityActive = false;

        SectorEntityToken temp = this.currentUltimateTarget;
        this.currentUltimateTarget = null;
        Global.getSector().layInCourseFor(temp);
        removeArrowRenderer();

        if (this.intelTabMap != null) {
            this.intelTabMap.removeComponent(this.intelTabMapArrowPanel);
            this.intelTabMap = null;
            this.intelTab = null;
        }
        
        if (this.mapTabMap != null) {
            this.mapTabMap.removeComponent(this.mapTabMapArrowPanel);
            this.mapTabMap = null;
            this.mapTab = null;
        }

        if (!this.dialogMaps.isEmpty()) this.dialogMaps.clear();

        this.entryGate = null;
        this.exitGate = null;
    }

    public void setAbility(AutoPilotGatesAbility ability) {
        this.ability = ability;
    }

    public AutoPilotGatesAbility getAbility() {
        return this.ability;
    }

    public CustomCampaignEntityAPI getEntryGate() {
        return this.entryGate;
    }

    public CustomCampaignEntityAPI getExitGate() {
        return this.exitGate;
    }

    public SectorEntityToken getCurrentUltimateTarget() {
        return this.currentUltimateTarget;
    }

    public BaseLocation getArrowRenderingLoc() {
        return this.arrowRenderingLoc;
    }

    private void renderCourseArrow() {
        CampaignFleet playerFleet = (CampaignFleet) Global.getSector().getPlayerFleet();
        float var1 = Global.getSector().getViewport().getAlphaMult();
        var1 *= playerFleet.getSensorFader().getBrightness();

        if (!(var1 <= 0.0F)) {
            GL11.glPushMatrix();

            Vector2f fleetLoc = playerFleet.getLocation();
            GL11.glTranslatef(fleetLoc.x, fleetLoc.y, 0.0f);
            var1 *= playerFleet.getSensorContactFaderBrightness();

            if (var1 <= 0.0f) {
                GL11.glPopMatrix();
                return;
            }

            Object courseWidget = UiUtil.getCourseWidget(Global.getSector().getCampaignUI());

            SectorEntityToken var4 = UiUtil.getNextStep(courseWidget, this.currentUltimateTarget);
            
            if (var4 == null) {
                GL11.glPopMatrix();
                return;
            }
            var1 *= UiUtil.courseWidgetGetInner(courseWidget).getBrightness();

            float var5 = 10.0F;
            float var6 = Global.getSector().getCampaignUI().getZoomFactor();
            var5 *= var6;

            arrow.setSize(var5, var5);
            arrow.setColor(arrowColor);
            arrow.setAlphaMult(var1);
            float var7 = angleBetween(Global.getSector().getPlayerFleet().getLocation(), var4.getLocation());
            arrow.setAngle(var7 - 90.0F);
            float var8 = (float)Math.cos(Math.toRadians((double)var7));
            float var9 = (float)Math.sin(Math.toRadians((double)var7));
            float var10 = 3.0F;
            float var11 = 15.0F;
            float var12 = (var5 + var10) * var11;
            float var13 = Math.max(0.0F, distanceBetween(fleetLoc, var4.getLocation()) - var12 - 50.0F);
            float var14;
            if (var12 > var13) {
                var14 = var13 / var12;
                var1 *= var14;
            }

            var14 = 0.1F;
            float var15 = 0.25F;

            for(float var16 = 0.0F; var16 < var11; ++var16) {
                float var17;
                for(var17 = UiUtil.courseWidgetGetPhase(courseWidget) + var16 * (1.0F / var11); var17 > 1.0F; --var17) {
                }

                float var18 = 1.0F;
                if (var17 < var14) {
                    var18 = var17 / var14;
                } else if (var17 > 1.0F - var15) {
                    var18 = (1.0F - var17) / var15;
                }

                float var19 = playerFleet.getSelectionSize() + 5.0F + var5 + var17 * var12;
                float var20 = var19 * var8;
                float var21 = var19 * var9;
                arrow.setAlphaMult(var1 * var18);
                arrow.renderAtCenter(var20, var21);
            }
            GL11.glPopMatrix();
        }
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return layers;
    }

    @Override
    public void render(CampaignEngineLayers arg0, CombatViewport arg1) {
        renderCourseArrow();
    }

    public void addArrowRenderer(LocationAPI playerLoc) {
        this.renderingArrow = true;
        this.arrowRenderingLoc = (BaseLocation) playerLoc;
        this.arrowRenderingLoc.addObject(this);
    }

    public void removeArrowRenderer() {
        this.renderingArrow = false;
        if (this.arrowRenderingLoc != null) {
            this.arrowRenderingLoc.removeObject(this);
            this.arrowRenderingLoc = null;
        }
    }

    private static float distanceBetween(Vector2f var0, Vector2f var1) {
        return (float)Math.sqrt((double)((var0.x - var1.x) * (var0.x - var1.x) + (var0.y - var1.y) * (var0.y - var1.y)));
    }

    private static final int TABLE_SIZE = (int)Math.sqrt(1048576.0);
    private static final float INV_TABLE_RANGE;
    private static final float[] ATAN2_LOOKUP;
    
    static {
        INV_TABLE_RANGE = 1.0F / (float)(TABLE_SIZE - 1);
        ATAN2_LOOKUP = new float[1048576];
    
        for (int var0 = 0; var0 < TABLE_SIZE; ++var0) {
            for (int var1 = 0; var1 < TABLE_SIZE; ++var1) {
                float var2 = (float)var0 / (float)TABLE_SIZE;
                float var3 = (float)var1 / (float)TABLE_SIZE;
                ATAN2_LOOKUP[var1 * TABLE_SIZE + var0] = (float)Math.atan2((double)var3, (double)var2);
            }
        }
    }
    
    private static float angleBetween(Vector2f var0, Vector2f var1) {
        return fastAtan2(var1.y - var0.y, var1.x - var0.x) * 57.295784F;
    }
    
    private static final float fastAtan2(float var0, float var1) {
        float var2;
        float var3;
    
        if (var1 < 0.0F) {
            if (var0 < 0.0F) {
                var1 = -var1;
                var0 = -var0;
                var3 = 1.0F;
            } else {
                var1 = -var1;
                var3 = -1.0F;
            }
            var2 = -3.1415927F;
        } else {
            if (var0 < 0.0F) {
                var0 = -var0;
                var3 = -1.0F;
            } else {
                var3 = 1.0F;
            }
            var2 = 0.0F;
        }
    
        float var4 = 1.0F / ((var1 < var0 ? var0 : var1) * INV_TABLE_RANGE);
        int var5 = (int)(var1 * var4);
        int var6 = (int)(var0 * var4);
        int var7 = var6 * TABLE_SIZE + var5;
    
        return var7 >= 0 && var7 < ATAN2_LOOKUP.length
                ? (ATAN2_LOOKUP[var7] + var2) * var3
                : 0.0F;
    }

    private Object getMapFromIntelTab(Object intelTab) {
        try {
            Object eventsPanel = UiUtil.getEventsPanelHandle.invoke(intelTab);
            Object outerMap = UiUtil.eventsPanelGetMapHandle.invoke(eventsPanel);
            return UiUtil.mapTabGetMapHandle.invoke(outerMap);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private Object getMapFromMapTab(Object mapTab) {
        try {
            return UiUtil.mapTabGetMapHandle.invoke(mapTab);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private BaseLocation getLocation(Object map) {
        try {
            return (BaseLocation) UiUtil.getLocationMapHandle.invoke(map);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private UIPanelAPI mapTab;
    private UIPanelAPI mapTabMap;

    private UIPanelAPI intelTab;
    private UIPanelAPI intelTabMap;

    private class MapArrowRenderer extends BaseCustomUIPanelPlugin {
        private float mapArrowPulseValue;
        private final MapGetter mapGetter;

        public MapArrowRenderer(MapGetter mapGetter) {
            this.mapGetter = mapGetter;
        }

        @Override
        public void advance(float deltaTime) {
            if (self.entryGate == null) return;
            UIPanelAPI mape = this.mapGetter.get();

            try {
                if (!((boolean)UiUtil.isRadarModeHandle.invoke(mape))) {
                    Object courseWidget = UiUtil.getCourseWidget(Global.getSector().getCampaignUI());
                    SectorEntityToken var4 = UiUtil.getNextStep(courseWidget, self.currentUltimateTarget);
                    if (var4 == null) return;

                    Vector2f var5 = CampaignEngine.getInstance().getPlayerFleet().getLocation();
                    Vector2f var6 = var4.getLocation();

                    BaseLocation mapLoc = getLocation(mape);
                    if (mapLoc != var4.getContainingLocation()) {
                        LocationAPI campaignMapLocation = CampaignEngine.getInstance().getUIData().getCampaignMapLocation();
                        if (mapLoc == null || (!mapLoc.isHyperspace() ||
                            (self.intelTab == null && campaignMapLocation != null && !campaignMapLocation.isHyperspace()))) {
                           return;
                        }

                        // if (self.currentUltimateTarget.isInHyperspace() && !var4.isInHyperspace()) var4 = self.currentUltimateTarget;
                        var4 = self.currentUltimateTarget;

                        var5 = CampaignEngine.getInstance().getPlayerFleet().getLocationInHyperspace();
                        var6 = var4.getLocationInHyperspace();
                    }

                    float var7 = distanceBetween(var5, var6);
                    if (var7 < 1000.0F) {
                        var7 = 1000.0F;
                    }

                    for(this.mapArrowPulseValue += deltaTime * 0.1F * 10000.0F / var7; this.mapArrowPulseValue > 1.0F; --this.mapArrowPulseValue) {
                    }
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void render(float alphaMult) {
            if (self.entryGate == null) return;
            UIPanelAPI mape = this.mapGetter.get();

            try {
                if (!((boolean)UiUtil.isRadarModeHandle.invoke(mape))) {
                    Object courseWidget = UiUtil.getCourseWidget(Global.getSector().getCampaignUI());
                    SectorEntityToken var4 = UiUtil.getNextStep(courseWidget, self.currentUltimateTarget);
                    
                    if (var4.isInHyperspace() && var4 instanceof JumpPointAPI) {
                        JumpPointAPI var5 = (JumpPointAPI)var4;
                        if (!var5.getDestinations().isEmpty()) {
                            SectorEntityToken var6 = ((JumpPointAPI.JumpDestination)var5.getDestinations().get(0)).getDestination();
                            if (var6 != null && var6.getStarSystem() != null) {
                                var4 = var6.getStarSystem().getHyperspaceAnchor();
                            }
                        }
                    }

                    Vector2f var16 = CampaignEngine.getInstance().getPlayerFleet().getLocation();
                    Vector2f var17 = var4.getLocation();
                    
                    LocationAPI campaignMapLocation = CampaignEngine.getInstance().getUIData().getCampaignMapLocation();
                    BaseLocation mapLoc = getLocation(mape);
                    if (mapLoc != var4.getContainingLocation()) {
                        if (mapLoc == null || (!mapLoc.isHyperspace() ||
                            (self.intelTab == null && campaignMapLocation != null && !campaignMapLocation.isHyperspace()))) {
                            return;
                        }

                        var4 = self.currentUltimateTarget;
   
                        var16 = CampaignEngine.getInstance().getPlayerFleet().getLocationInHyperspace();
                        var17 = var4.getLocationInHyperspace();
                    }
  
                    float var7 = distanceBetween(var16, var17);
                    if (!(var7 < 1000.0F)) {
                        float var8 = 10.0F;
                        float var9 = 3.0F;
                        float var10 = (float) UiUtil.zoomTrackerFloatGetterHandle.invoke(UiUtil.getZoomTrackerHandle.invoke(mape));
                        if (var10 < 0.75F) {
                            var10 = 0.75F;
                        }
    
                        var8 /= var10;
                        var9 /= var10;
                        if (var8 < 7.0F) {
                            var8 = 7.0F;
                            var9 = 2.1F;
                        }

                        float factor = (float) UiUtil.getFactorHandle.invoke(mape);
                        float var12 = var16.x * factor;
                        float var13 = var16.y * factor;
                        float var14 = var17.x * factor;
                        float var15 = var17.y * factor;
                        alphaMult *= 0.5F;

                        PositionAPI mapPos = mape.getPosition();

                        GL11.glPushMatrix();
                        GL11.glTranslatef((int) mapPos.getCenterX(), (int) mapPos.getCenterY(), 0.0f);
                        renderCourseArrowOnMap(
                            var12,
                            var13,
                            var14,
                            var15,
                            this.mapArrowPulseValue,
                            var8,
                            var9,
                            self.arrowColor,
                            alphaMult,
                            arrow
                        );

                        if ((campaignMapLocation != null && campaignMapLocation.isHyperspace())
                            || mapLoc.isHyperspace()) {
                            var16 = self.entryGate.getLocationInHyperspace();
                            var17 = self.exitGate.getLocationInHyperspace();

                            Color var11 = Misc.getBasePlayerColor();

                            var7 = distanceBetween(var16, var17);
                            if (!(var7 < 1000.0F)) {
                                var8 = 10.0F;
                                var9 = 3.0F;
                                
                                var8 /= var10;
                                var9 /= var10;
                                if (var8 < 7.0F) {
                                    var8 = 7.0F;
                                    var9 = 2.1F;
                                }
                                
                                var12 = var16.x * factor;
                                var13 = var16.y * factor;
                                var14 = var17.x * factor;
                                var15 = var17.y * factor;
            
                                renderCourseArrowOnMap(
                                    var12,
                                    var13,
                                    var14,
                                    var15,
                                    this.mapArrowPulseValue,
                                    var8,
                                    var9,
                                    var11,
                                    alphaMult,
                                    gateCircle
                                );
                            }

                            if ((self.currentUltimateTarget.isInHyperspace() && self.currentUltimateTarget instanceof JumpPointAPI
                            && ((JumpPointAPI)self.currentUltimateTarget).getDestinationStarSystem() == self.exitGate.getContainingLocation())
                            || self.currentUltimateTarget.getContainingLocation() == self.exitGate.getContainingLocation()) {
                                GL11.glPopMatrix();
                                return;
                            }

                            var16 = self.exitGate.getLocationInHyperspace();
                            var17 = self.currentUltimateTarget.getLocationInHyperspace();

                            var7 = distanceBetween(var16, var17);
                            if (!(var7 < 1000.0F)) {
                                var8 = 10.0F;
                                var9 = 3.0F;
                                    
                                var8 /= var10;
                                var9 /= var10;
                                if (var8 < 7.0F) {
                                    var8 = 7.0F;
                                    var9 = 2.1F;
                                }
                                
                                var12 = var16.x * factor;
                                var13 = var16.y * factor;
                                var14 = var17.x * factor;
                                var15 = var17.y * factor;
                                
                                renderCourseArrowOnMap(
                                    var12,
                                    var13,
                                    var14,
                                    var15,
                                    this.mapArrowPulseValue,
                                    var8,
                                    var9,
                                    var11,
                                    alphaMult,
                                    arrow
                                );
                            }
                        }
                        GL11.glPopMatrix();
                    }
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }
    private final CustomPanelAPI mapTabMapArrowPanel = Global.getSettings().createCustom(0f, 0f, new MapArrowRenderer(
        new MapGetter() {
            @Override
            public UIPanelAPI get() {
                return self.mapTabMap;
            }
        }
    ));
    private final CustomPanelAPI intelTabMapArrowPanel = Global.getSettings().createCustom(0f, 0f, new MapArrowRenderer(
        new MapGetter() {
            @Override
            public UIPanelAPI get() {
                return self.intelTabMap;
            }
        }
    ));
    private interface MapGetter {
        public UIPanelAPI get();
    }

    private void renderCourseArrowOnMap(float var0, float var1, float var2, float var3, float var4, float var5, float var6, Color var7, float var8, SpriteAPI arrow) {
        arrow.setSize(var5, var5);
        arrow.setColor(var7);
        arrow.setAlphaMult(var8);
        Vector2f var9 = new Vector2f(var0, var1);
        Vector2f var10 = new Vector2f(var2, var3);
        float var11 = angleBetween(var9, var10);
        arrow.setAngle(var11 - 90.0F);
        float var12 = (float)Math.cos(Math.toRadians((double)var11));
        float var13 = (float)Math.sin(Math.toRadians((double)var11));
        float var14 = distanceBetween(var9, var10);
        float var15 = (float)((int)(var14 / (var5 + var6)));
        float var16 = var5 * 4.0F;
        float var17 = var16;
        
        for(float var18 = 0.0F; var18 < var15; ++var18) {
            float var19;
            for(var19 = var4 + var18 * (1.0F / var15); var19 > 1.0F; --var19) {
            }
    
            float var20 = 5.0F + var5 + var19 * var14;
            float var21 = var0 + var20 * var12;
            float var22 = var1 + var20 * var13;
            float var23 = 1.0F;
            float var24 = var5 + var19 * var14;
            if (var24 < var16) {
                var23 = var24 / var16;
            } else if (var24 > var14 - var17) {
                var23 = 1.0F - (var24 - (var14 - var17)) / var17;
                if (var23 < 0.0F) {
                    var23 = 0.0F;
                }
            }
    
            arrow.setAlphaMult(var8 * var23);
            arrow.renderAtCenter(var21, var22);
        }
    }

    private class EphemeralMapArrowRenderer extends BaseCustomUIPanelPlugin {
        private float mapArrowPulseValue;
        private final UIPanelAPI map;

        public EphemeralMapArrowRenderer(UIPanelAPI map) {
            this.map = map;
        }

        @Override
        public void advance(float deltaTime) {
            if (self.entryGate == null) return;

            try {
                if (!((boolean)UiUtil.isRadarModeHandle.invoke(this.map))) {
                    Object courseWidget = UiUtil.getCourseWidget(Global.getSector().getCampaignUI());
                    SectorEntityToken var4 = UiUtil.getNextStep(courseWidget, self.currentUltimateTarget);
                    if (var4 == null) return;

                    Vector2f var5 = CampaignEngine.getInstance().getPlayerFleet().getLocation();
                    Vector2f var6 = var4.getLocation();

                    BaseLocation mapLoc = getLocation(map);
                    if (mapLoc != var4.getContainingLocation()) {
                        if (mapLoc == null || !mapLoc.isHyperspace()) {
                           return;
                        }

                        // if (self.currentUltimateTarget.isInHyperspace() && !var4.isInHyperspace()) var4 = self.currentUltimateTarget;
                        var4 = self.currentUltimateTarget;

                        var5 = CampaignEngine.getInstance().getPlayerFleet().getLocationInHyperspace();
                        var6 = var4.getLocationInHyperspace();
                    }

                    float var7 = distanceBetween(var5, var6);
                    if (var7 < 1000.0F) {
                        var7 = 1000.0F;
                    }

                    for(this.mapArrowPulseValue += deltaTime * 0.1F * 10000.0F / var7; this.mapArrowPulseValue > 1.0F; --this.mapArrowPulseValue) {
                    }
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void render(float alphaMult) {
            if (self.entryGate == null) return;

            try {
                if (!((boolean)UiUtil.isRadarModeHandle.invoke(this.map))) {
                    Object courseWidget = UiUtil.getCourseWidget(Global.getSector().getCampaignUI());
                    SectorEntityToken var4 = UiUtil.getNextStep(courseWidget, self.currentUltimateTarget);
                    
                    if (var4.isInHyperspace() && var4 instanceof JumpPointAPI) {
                        JumpPointAPI var5 = (JumpPointAPI)var4;
                        if (!var5.getDestinations().isEmpty()) {
                            SectorEntityToken var6 = ((JumpPointAPI.JumpDestination)var5.getDestinations().get(0)).getDestination();
                            if (var6 != null && var6.getStarSystem() != null) {
                                var4 = var6.getStarSystem().getHyperspaceAnchor();
                            }
                        }
                    }

                    Vector2f var16 = CampaignEngine.getInstance().getPlayerFleet().getLocation();
                    Vector2f var17 = var4.getLocation();
                    
                    BaseLocation mapLoc = getLocation(map);
                    if (mapLoc != var4.getContainingLocation()) {
                        if (mapLoc == null || !mapLoc.isHyperspace()) {
                            return;
                        }

                        // if (self.currentUltimateTarget.isInHyperspace() && !var4.isInHyperspace()) var4 = self.currentUltimateTarget;
                        var4 = self.currentUltimateTarget;
   
                        var16 = CampaignEngine.getInstance().getPlayerFleet().getLocationInHyperspace();
                        var17 = var4.getLocationInHyperspace();
                     }
  
                    float var7 = distanceBetween(var16, var17);
                    if (!(var7 < 1000.0F)) {
                        float var8 = 10.0F;
                        float var9 = 3.0F;
                        float var10 = (float) UiUtil.zoomTrackerFloatGetterHandle.invoke(UiUtil.getZoomTrackerHandle.invoke(this.map));
                        if (var10 < 0.75F) {
                            var10 = 0.75F;
                        }
    
                        var8 /= var10;
                        var9 /= var10;
                        if (var8 < 7.0F) {
                            var8 = 7.0F;
                            var9 = 2.1F;
                        }


                        float factor = (float) UiUtil.getFactorHandle.invoke(this.map);
                        float var12 = var16.x * factor;
                        float var13 = var16.y * factor;
                        float var14 = var17.x * factor;
                        float var15 = var17.y * factor;
                        alphaMult *= 0.5F;

                        PositionAPI mapPos = this.map.getPosition();

                        GL11.glPushMatrix();
                        GL11.glTranslatef((int) mapPos.getCenterX(), (int) mapPos.getCenterY(), 0.0f);
                        renderCourseArrowOnMap(
                            var12,
                            var13,
                            var14,
                            var15,
                            this.mapArrowPulseValue,
                            var8,
                            var9,
                            self.arrowColor,
                            alphaMult,
                            arrow
                        );
                        
                        if (mapLoc.isHyperspace()) {
                            var16 = self.entryGate.getLocationInHyperspace();
                            var17 = self.exitGate.getLocationInHyperspace();

                            Color var11 = Misc.getBasePlayerColor();

                            var7 = distanceBetween(var16, var17);
                            if (!(var7 < 1000.0F)) {
                                var8 = 10.0F;
                                var9 = 3.0F;
                                    
                                var8 /= var10;
                                var9 /= var10;
                                if (var8 < 7.0F) {
                                    var8 = 7.0F;
                                    var9 = 2.1F;
                                }
                                
                                var12 = var16.x * factor;
                                var13 = var16.y * factor;
                                var14 = var17.x * factor;
                                var15 = var17.y * factor;
            
                                renderCourseArrowOnMap(
                                    var12,
                                    var13,
                                    var14,
                                    var15,
                                    this.mapArrowPulseValue,
                                    var8,
                                    var9,
                                    var11,
                                    alphaMult,
                                    gateCircle
                                );
                            }

                            if ((self.currentUltimateTarget.isInHyperspace() && self.currentUltimateTarget instanceof JumpPointAPI
                            && ((JumpPointAPI)self.currentUltimateTarget).getDestinationStarSystem() == self.exitGate.getContainingLocation())
                            || self.currentUltimateTarget.getContainingLocation() == self.exitGate.getContainingLocation()) {
                                GL11.glPopMatrix();
                                return;
                            }

                            var16 = self.exitGate.getLocationInHyperspace();
                            var17 = self.currentUltimateTarget.getLocationInHyperspace();

                            var7 = distanceBetween(var16, var17);
                            if (!(var7 < 1000.0F)) {
                                var8 = 10.0F;
                                var9 = 3.0F;
                                    
                                var8 /= var10;
                                var9 /= var10;
                                if (var8 < 7.0F) {
                                    var8 = 7.0F;
                                    var9 = 2.1F;
                                }
                                
                                var12 = var16.x * factor;
                                var13 = var16.y * factor;
                                var14 = var17.x * factor;
                                var15 = var17.y * factor;
                                
                                renderCourseArrowOnMap(
                                    var12,
                                    var13,
                                    var14,
                                    var15,
                                    this.mapArrowPulseValue,
                                    var8,
                                    var9,
                                    var11,
                                    alphaMult,
                                    arrow
                                );
                            }
                        }
                        GL11.glPopMatrix();
                    }
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }
}
