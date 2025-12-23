package data.scripts.autopilotwithgates;

import java.awt.Color;
import java.util.*;

import org.apache.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import com.fs.graphics.LayeredRenderable;

import com.fs.starfarer.campaign.BaseLocation;
import com.fs.starfarer.campaign.CampaignEngine;
import com.fs.starfarer.campaign.comms.v2.EventsPanel;
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

import com.fs.starfarer.api.util.Misc;

import data.scripts.autopilotwithgates.util.GateAutoPilotRuleMemory;
import data.scripts.autopilotwithgates.util.GateFinder;
import data.scripts.autopilotwithgates.util.UiUtil;
import data.scripts.autopilotwithgates.util.TreeTraverser;
import data.scripts.autopilotwithgates.util.TreeTraverser.TreeNode;
import data.scripts.autopilotwithgates.util.UiUtil.UtilInterface;

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
    private static final UtilInterface utils = UiUtil.utils;

    private static final EnumSet<CampaignEngineLayers> layers = EnumSet.of(CampaignEngineLayers.FLEETS);
    private static final Color DARK_RED = new Color(139, 0, 0);
    private static final Color DARK_GREEN = new Color(0, 139, 0);
    public static final SpriteAPI arrow = Global.getSettings().getSprite("graphics/warroom/ship_arrow.png");
    public static final SpriteAPI gateCircle = Global.getSettings().getSprite("graphics/icons/gate0.png");

    private final AutoPilotListener self = this;
    private AutoPilotGatesAbility ability;
    private final boolean autoJump;
    // private IntervalUtil interval = new IntervalUtil(0.1f, 0.1f);

    private final List<Object> messageDisplayList;

    private SectorEntityToken currentUltimateTarget;
    private CustomCampaignEntityAPI entryGate;
    private CustomCampaignEntityAPI exitGate;

    private boolean postGateJump = false;
    private boolean abilityActive = false;
    private boolean wasJustActivated = false;
    private boolean wasJustGotCloserThanGate = false;

    private boolean noExitJumpPoints = true;
    private boolean renderingArrow = false;
    private BaseLocation arrowRenderingLoc;
    private Color arrowColor = DARK_RED;

    private final CustomPanelAPI mapTabMapArrowPanel = Global.getSettings().createCustom(0f, 0f, new MapArrowRenderer() {
        @Override
        protected UIPanelAPI getMap() {
            return self.mapTabMap;
        }
    });
    private final CustomPanelAPI intelTabMapArrowPanel = Global.getSettings().createCustom(0f, 0f, new MapArrowRenderer() {
        @Override
        protected UIPanelAPI getMap() {
            return self.intelTabMap;
        }
    });

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

        if (Global.getSector().getPlayerFleet().getContainingLocation() instanceof StarSystemAPI system && system.getEntities(JumpPointAPI.class).size() == 0) this.noExitJumpPoints = true;
        else this.noExitJumpPoints = false;

        this.messageDisplayList = UiUtil.getMessageDisplayList(Global.getSector().getCampaignUI());
    }

    @Override
    public void advance(float arg0) {
        if (!this.abilityActive) return;

        CampaignUIAPI campaignUI = Global.getSector().getCampaignUI();
        if (utils.getCourseWidget(campaignUI) == null) return;

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
        
        if (ultimateTarget == this.entryGate) {
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

                for (TreeNode node : new TreeTraverser(interactionDialog).getNodes()) {
                    for (UIComponentAPI child : node.getChildren()) {
                        if (child.getClass() == UiUtil.mapClass && !(child == this.mapTabMap || child == this.intelTabMap) && !dialogMaps.containsKey(child)) {
                            UIPanelAPI mape = (UIPanelAPI) child;

                            CustomPanelAPI arrowPanel = Global.getSettings().createCustom(0f, 0f, new MapArrowRenderer() {
                                @Override
                                protected UIPanelAPI getMap() {
                                    return mape;
                                }
                            });
                            
                            mape.addComponent(arrowPanel);
                            this.dialogMaps.put(mape, arrowPanel);
                            dialogMapsIsEmpty = false;
                        }
                    }
                }

            } else if (!dialogMapsIsEmpty) {
                this.dialogMaps.clear();
            }
            
            if (CoreUITabId.MAP == currentCoreTabId) {
                if (interactionDialog != null && this.mapTabMap != null) {
                    this.mapTab = utils.coreGetCurrentTab(UiUtil.getCore(campaignUI, interactionDialog));
                    UIPanelAPI mape = utils.mapTabGetMap(this.mapTab);

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
                    this.mapTab = utils.coreGetCurrentTab(UiUtil.getCore(campaignUI, interactionDialog));

                    this.mapTabMap = utils.mapTabGetMap(this.mapTab);
                    this.mapTabMap.addComponent(this.mapTabMapArrowPanel);
                }

            } else if (CoreUITabId.INTEL == currentCoreTabId) {
                if (interactionDialog != null) {

                    if (this.intelTab != null) {
                        this.intelTab = utils.coreGetCurrentTab(UiUtil.getCore(campaignUI, interactionDialog));
                        UIPanelAPI mape = getMapFromIntelTab(this.intelTab);
                        
                        this.intelTabMap.removeComponent(this.intelTabMapArrowPanel);
                        this.intelTabMap = mape;
                        this.intelTabMap.addComponent(this.intelTabMapArrowPanel);

                    } else {
                        UIPanelAPI intTab = utils.coreGetCurrentTab(UiUtil.getCore(campaignUI, interactionDialog));
                        UIPanelAPI intMap = getMapFromIntelTab(intTab);

                        if (!this.dialogMaps.containsKey(intMap)) {
                            this.intelTab = intTab;
                            this.intelTabMap = intMap;
                            this.intelTabMap.addComponent(this.intelTabMapArrowPanel);
                        }
                    }
                }

                if (this.mapTabMap != null) {
                    this.mapTabMap.removeComponent(this.mapTabMapArrowPanel);
                    this.mapTabMap = null;
                    this.mapTab = null;
                }

                if (this.intelTabMap == null && dialogMapsIsEmpty) {
                    this.intelTab = utils.coreGetCurrentTab(UiUtil.getCore(campaignUI, interactionDialog));
                    this.intelTabMap = getMapFromIntelTab(this.intelTab);
                    this.intelTabMap.addComponent(this.intelTabMapArrowPanel);

                } else if (this.intelTabMap != null) {
                    UIPanelAPI intTab = utils.coreGetCurrentTab(UiUtil.getCore(campaignUI, interactionDialog));
                    UIPanelAPI intMap = getMapFromIntelTab(this.intelTab);

                    if (intMap != this.intelTabMap || intTab != this.intelTab) {
                        this.intelTab = intTab;

                        this.intelTabMap.removeComponent(this.intelTabMapArrowPanel);
                        this.intelTabMap = intMap;
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
            CustomCampaignEntityAPI newEntryGate = GateFinder.getNearestGateToPlayerOutsideLocation(this.exitGate, this.currentUltimateTarget);

            if (newEntryGate != null) {
                if (this.entryGate != newEntryGate) {
                    this.entryGate = newEntryGate;

                    boolean followMouse = campaignUI.isPlayerFleetFollowingMouse();
                    this.layInCourseFor(newEntryGate);
                    
                    if (followMouse) UiUtil.setFollowMouseTrue(campaignUI);
                }
                return;

            } else {
                boolean followMouse = campaignUI.isPlayerFleetFollowingMouse();
                this.layInCourseFor(this.currentUltimateTarget);
                
                if (followMouse) UiUtil.setFollowMouseTrue(campaignUI);

                this.entryGate = null;
                this.exitGate = null;
                this.currentUltimateTarget = null;
                this.wasJustGotCloserThanGate = true;
                return;
            }
        }

        this.currentUltimateTarget = ultimateTarget;

        if (playerLoc instanceof StarSystemAPI) {
            this.entryGate = GateFinder.getNearestGateInLocation(playerLoc, playerFleet.getLocation());

            if (this.entryGate != null) {
                this.exitGate = GateFinder.getNearestGate(ultimateTarget);

                if (this.exitGate != null) {
                    boolean followMouse = campaignUI.isPlayerFleetFollowingMouse();
                    this.layInCourseFor(this.entryGate);

                    if (this.wasJustActivated && followMouse) UiUtil.setFollowMouseTrue(campaignUI);

                    this.wasJustActivated = false;
                    this.wasJustGotCloserThanGate = false;

                } else {
                    this.entryGate = null;
                }
                return;
            }
        }
        this.exitGate = GateFinder.getNearestGate(ultimateTarget);
        this.entryGate = GateFinder.getNearestGateToPlayerOutsideLocation(this.exitGate, this.currentUltimateTarget);

        if (this.entryGate != null && this.exitGate != null) {
            boolean followMouse = campaignUI.isPlayerFleetFollowingMouse();
            this.layInCourseFor(this.entryGate);

            if ((this.wasJustActivated || this.wasJustGotCloserThanGate) && followMouse) UiUtil.setFollowMouseTrue(campaignUI);

            this.wasJustGotCloserThanGate = false;
            this.wasJustActivated = false;
            return;
        }

        this.wasJustActivated = false;
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

                                self.layInCourseFor(ultimateTarget);

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
                            self.layInCourseFor(ultimateTarget);

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
                dialog.getOptionPanel().addOptionTooltipAppender("gateAutoPilotRule", new OptionTooltipCreator() {
                    @Override
                    public void createTooltip(TooltipMakerAPI arg0, boolean arg1) {
                        arg0.addPara("Not enough fuel to make the jump. Requires %s fuel. You have %s fuel available.",
                            0f,
                            new Color[] {Misc.getHighlightColor(), Misc.getNegativeHighlightColor()},
                            String.valueOf(cost), String.valueOf(available)
                        );
                    }
                });
                dialog.getOptionPanel().setEnabled("gateAutoPilotRule", false);
                return;
            }
        }
    }

    @Override
    public void reportFleetJumped(CampaignFleetAPI fleet, SectorEntityToken from, JumpDestination to) {
        if (!fleet.isPlayerFleet()) return;
        if (to.getDestination().getContainingLocation() instanceof StarSystemAPI system && system.getEntities(JumpPointAPI.class).size() == 0) this.noExitJumpPoints = true;
        else this.noExitJumpPoints = false;
    }

    private void layInCourseFor(SectorEntityToken target) {
        int messageDisplayListSize = this.messageDisplayList.size();
        Global.getSector().layInCourseFor(target);
        if (this.messageDisplayList.size() > messageDisplayListSize) this.messageDisplayList.remove(this.messageDisplayList.size()-1);
    }

    public void on() {
        this.abilityActive = true;
        this.wasJustActivated = true;
    }

    public void off() {
        this.abilityActive = false;
        
        SectorEntityToken temp = this.currentUltimateTarget;
        this.currentUltimateTarget = null;

        boolean followMouse = Global.getSector().getCampaignUI().isPlayerFleetFollowingMouse();
        this.layInCourseFor(temp);

        if (temp != null && followMouse) UiUtil.setFollowMouseTrue(Global.getSector().getCampaignUI());
        
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

        this.wasJustGotCloserThanGate = false;
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

    public boolean isNoExitJumpPoints() {
        return this.noExitJumpPoints;
    }

    public BaseLocation getArrowRenderingLoc() {
        return this.arrowRenderingLoc;
    }

    public Color getArrowColor() {
        return this.arrowColor;
    }

    private void renderCourseArrow() {
        CampaignFleet playerFleet = (CampaignFleet) Global.getSector().getPlayerFleet();
        float alphaMult = Global.getSector().getViewport().getAlphaMult();
        alphaMult *= playerFleet.getSensorFader().getBrightness();

        if (!(alphaMult <= 0.0F)) {
            GL11.glPushMatrix();

            Vector2f fleetLoc = playerFleet.getLocation();
            GL11.glTranslatef(fleetLoc.x, fleetLoc.y, 0.0f);
            alphaMult *= playerFleet.getSensorContactFaderBrightness();

            if (alphaMult <= 0.0f) {
                GL11.glPopMatrix();
                return;
            }

            Object courseWidget = utils.getCourseWidget(Global.getSector().getCampaignUI());

            SectorEntityToken nextStep = utils.getNextStep(courseWidget, this.currentUltimateTarget);
            
            if (nextStep == null) {
                GL11.glPopMatrix();
                return;
            }
            alphaMult *= utils.getInner(courseWidget).getBrightness();

            float arrowSize = 10.0F;
            float zoomFactor = Global.getSector().getCampaignUI().getZoomFactor();
            arrowSize *= zoomFactor;

            arrow.setSize(arrowSize, arrowSize);
            arrow.setColor(arrowColor);
            arrow.setAlphaMult(alphaMult);

            float angle = angleBetween(Global.getSector().getPlayerFleet().getLocation(), nextStep.getLocation());
            arrow.setAngle(angle - 90.0F);

            float cosAngle = (float)Math.cos(Math.toRadians((double)angle));
            float sinAngle = (float)Math.sin(Math.toRadians((double)angle));

            float arrowSpacing = 3.0F;
            float numArrows = 15.0F;

            float totalArrowPathLength = (arrowSize + arrowSpacing) * numArrows;
            float remainingDistance = Math.max(0.0F, distanceBetween(fleetLoc, nextStep.getLocation()) - totalArrowPathLength - 50.0F);

            float fadeFactor;
            if (totalArrowPathLength > remainingDistance) {
                fadeFactor = remainingDistance / totalArrowPathLength;
                alphaMult *= fadeFactor;
            }

            float fadeStartDistance = 0.1F;
            float fadeEndDistance = 0.25F;

            for(float arrowIndex = 0.0F; arrowIndex < numArrows; ++arrowIndex) {
                float phase;
                for(phase = utils.getPhase(courseWidget) + arrowIndex * (1.0F / numArrows); phase > 1.0F; --phase) {
                }

                float arrowAlpha = 1.0F;
                if (phase < fadeStartDistance) {
                    arrowAlpha = phase / fadeStartDistance;
                } else if (phase > 1.0F - fadeEndDistance) {
                    arrowAlpha = (1.0F - phase) / fadeEndDistance;
                }

                float distanceFromFleet = playerFleet.getSelectionSize() + 5.0F + arrowSize + phase * totalArrowPathLength;
                float arrowX = distanceFromFleet * cosAngle;
                float arrowY = distanceFromFleet * sinAngle;

                arrow.setAlphaMult(alphaMult * arrowAlpha);
                arrow.renderAtCenter(arrowX, arrowY);
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
        if (this.noExitJumpPoints) return;
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

    private UIPanelAPI mapTab;
    private UIPanelAPI mapTabMap;

    private UIPanelAPI intelTab;
    private UIPanelAPI intelTabMap;

    private abstract class MapArrowRenderer extends BaseCustomUIPanelPlugin {
        private float mapArrowPulseValue;

        protected abstract UIPanelAPI getMap();

        @Override
        public void advance(float deltaTime) {
            if (self.entryGate == null || Global.getSector().getCampaignUI().getCurrentCourseTarget() == null) return;
            UIPanelAPI mape = this.getMap();

            if (!utils.isRadarMode(mape)) {
                Object courseWidget = utils.getCourseWidget(Global.getSector().getCampaignUI());
                SectorEntityToken nextStep = utils.getNextStep(courseWidget, self.currentUltimateTarget);
                if (nextStep == null) return;

                CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

                Vector2f playerLocation = playerFleet.getLocation();
                Vector2f targetLocation = nextStep.getLocation();

                BaseLocation mapLoc = utils.mapGetLocation(mape);
                LocationAPI campaignMapLocation = CampaignEngine.getInstance().getUIData().getCampaignMapLocation();

                if (mapLoc != nextStep.getContainingLocation()) {
                    if (mapLoc == null || (!mapLoc.isHyperspace() ||
                        (self.intelTab == null && campaignMapLocation != null && !campaignMapLocation.isHyperspace() && Global.getSector().getCampaignUI().getCurrentInteractionDialog() == null))) {
                        return;
                    }

                    nextStep = self.currentUltimateTarget;

                    playerLocation = playerFleet.getLocationInHyperspace();
                    targetLocation = nextStep.getLocationInHyperspace();

                } else if (self.noExitJumpPoints && (self.intelTabMap != null || (campaignMapLocation != null && campaignMapLocation.isHyperspace()))) {
                    playerLocation = playerFleet.getLocationInHyperspace();
                    targetLocation = nextStep.getLocationInHyperspace();
                }

                float distance = distanceBetween(playerLocation, targetLocation);
                if (distance < 1000.0F) {
                    distance = 1000.0F;
                }

                for(this.mapArrowPulseValue += deltaTime * 0.1F * 10000.0F / distance; this.mapArrowPulseValue > 1.0F; --this.mapArrowPulseValue) {
                }
            }
        }

        @Override
        public void render(float alphaMult) {
            if (self.entryGate == null || Global.getSector().getCampaignUI().getCurrentCourseTarget() == null) return;
            UIPanelAPI mape = this.getMap();

            if (!utils.isRadarMode(mape)) {
                Object courseWidget = utils.getCourseWidget(Global.getSector().getCampaignUI());
                SectorEntityToken nextStep = utils.getNextStep(courseWidget, self.currentUltimateTarget);
                
                if (nextStep.isInHyperspace() && nextStep instanceof JumpPointAPI) {
                    JumpPointAPI jumpPoint = (JumpPointAPI)nextStep;
                    if (!jumpPoint.getDestinations().isEmpty()) {
                        SectorEntityToken destination = ((JumpPointAPI.JumpDestination)jumpPoint.getDestinations().get(0)).getDestination();
                        if (destination != null && destination.getStarSystem() != null) {
                            nextStep = destination.getStarSystem().getHyperspaceAnchor();
                        }
                    }
                }

                CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

                Vector2f playerLocation = playerFleet.getLocation();
                Vector2f targetLocation = nextStep.getLocation();
                
                LocationAPI campaignMapLocation = CampaignEngine.getInstance().getUIData().getCampaignMapLocation();
                BaseLocation mapLoc = utils.mapGetLocation(mape);

                boolean shouldRenderRegularCourse = true;
                boolean campaignMapLocationNonNull = campaignMapLocation != null;

                if (mapLoc != nextStep.getContainingLocation()) {
                    if (mapLoc == null || (!mapLoc.isHyperspace() ||
                        (self.intelTab == null && campaignMapLocationNonNull && !campaignMapLocation.isHyperspace() && Global.getSector().getCampaignUI().getCurrentInteractionDialog() == null))) {
                        return;
                    }

                    nextStep = self.currentUltimateTarget;

                    playerLocation = playerFleet.getLocationInHyperspace();
                    targetLocation = nextStep.getLocationInHyperspace();

                } else if (self.noExitJumpPoints && (self.intelTabMap != null || (campaignMapLocationNonNull && campaignMapLocation.isHyperspace()))) {
                    playerLocation = playerFleet.getLocationInHyperspace();
                    targetLocation = nextStep.getLocationInHyperspace();
                    shouldRenderRegularCourse = false;
                }

                float distance = distanceBetween(playerLocation, targetLocation);
                if (!(distance < 1000.0F)) {
                    float arrowSize = 10.0F;
                    float arrowSpacing = 3.0F;

                    float zoomLevel = utils.getZoomLevel(utils.getZoomTracker(mape));
                    if (zoomLevel < 0.75F) {
                        zoomLevel = 0.75F;
                    }

                    arrowSize /= zoomLevel;
                    arrowSpacing /= zoomLevel;

                    if (arrowSize < 7.0F) {
                        arrowSize = 7.0F;
                        arrowSpacing = 2.1F;
                    }

                    float factor = utils.getFactor(mape);

                    float scaledStartX = playerLocation.x * factor;
                    float scaledStartY = playerLocation.y * factor;

                    float scaledEndX = targetLocation.x * factor;
                    float scaledEndY = targetLocation.y * factor;

                    alphaMult *= 0.5F;

                    PositionAPI mapPos = mape.getPosition();

                    GL11.glPushMatrix();
                    GL11.glTranslatef((int) mapPos.getCenterX(), (int) mapPos.getCenterY(), 0.0f);
                    
                    if (shouldRenderRegularCourse)
                    renderCourseArrowOnMap(
                        scaledStartX,
                        scaledStartY,
                        scaledEndX,
                        scaledEndY,
                        this.mapArrowPulseValue,
                        arrowSize,
                        arrowSpacing,
                        self.arrowColor,
                        alphaMult,
                        arrow
                    );

                    if ((campaignMapLocationNonNull && campaignMapLocation.isHyperspace())
                        || mapLoc.isHyperspace()) {
                        playerLocation = self.entryGate.getLocationInHyperspace();
                        targetLocation = self.exitGate.getLocationInHyperspace();

                        Color gateArrowColor = Misc.getBasePlayerColor();

                        distance = distanceBetween(playerLocation, targetLocation);
                        if (!(distance < 1000.0F)) {
                            arrowSize = 10.0F;
                            arrowSpacing = 3.0F;
                            
                            arrowSize /= zoomLevel;
                            arrowSpacing /= zoomLevel;

                            if (arrowSize < 7.0F) {
                                arrowSize = 7.0F;
                                arrowSpacing = 2.1F;
                            }
                            
                            scaledStartX = playerLocation.x * factor;
                            scaledStartY = playerLocation.y * factor;
                            
                            scaledEndX = targetLocation.x * factor;
                            scaledEndY = targetLocation.y * factor;
        
                            renderCourseArrowOnMap(
                                scaledStartX,
                                scaledStartY,
                                scaledEndX,
                                scaledEndY,
                                this.mapArrowPulseValue,
                                arrowSize,
                                arrowSpacing,
                                gateArrowColor,
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
                        
                        playerLocation = targetLocation;
                        targetLocation = nextStep.getLocationInHyperspace();

                        distance = distanceBetween(playerLocation, targetLocation);
                        if (!(distance < 1000.0F)) {
                            arrowSize = 10.0F;
                            arrowSpacing = 3.0F;
                                
                            arrowSize /= zoomLevel;
                            arrowSpacing /= zoomLevel;

                            if (arrowSize < 7.0F) {
                                arrowSize = 7.0F;
                                arrowSpacing = 2.1F;
                            }
                            
                            scaledStartX = playerLocation.x * factor;
                            scaledStartY = playerLocation.y * factor;

                            scaledEndX = targetLocation.x * factor;
                            scaledEndY = targetLocation.y * factor;
                            
                            renderCourseArrowOnMap(
                                scaledStartX,
                                scaledStartY,
                                scaledEndX,
                                scaledEndY,
                                this.mapArrowPulseValue,
                                arrowSize,
                                arrowSpacing,
                                gateArrowColor,
                                alphaMult,
                                arrow
                            );
                        }
                    }
                    GL11.glPopMatrix();
                }
            }
        }

        private void renderCourseArrowOnMap(float startX, float startY, float endX, float endY, float pulseValue, float arrowSize, float arrowSpacing, Color arrowColor, float alphaMult, SpriteAPI arrow) {
            arrow.setSize(arrowSize, arrowSize);
            arrow.setColor(arrowColor);
            arrow.setAlphaMult(alphaMult);
    
            Vector2f startPos = new Vector2f(startX, startY);
            Vector2f endPos = new Vector2f(endX, endY);
    
            float angle = angleBetween(startPos, endPos);
            arrow.setAngle(angle - 90.0F);
    
            float cosAngle = (float)Math.cos(Math.toRadians((double)angle));
            float sinAngle = (float)Math.sin(Math.toRadians((double)angle));
    
            float distance = distanceBetween(startPos, endPos);
    
            float numArrows = (float)((int)(distance / (arrowSize + arrowSpacing)));
    
            float fadeStartDistance = arrowSize * 4.0F;
            float fadeEndDistance = fadeStartDistance;
            
            for(float arrowIndex = 0.0F; arrowIndex < numArrows; ++arrowIndex) {
                float phase;
                for(phase = pulseValue + arrowIndex * (1.0F / numArrows); phase > 1.0F; --phase) {
                }
        
                float distanceAlongPath = 5.0F + arrowSize + phase * distance;
                
                float arrowX = startX + distanceAlongPath * cosAngle;
                float arrowY = startY + distanceAlongPath * sinAngle;
    
                float arrowAlpha = 1.0F;
                float currentDistance = arrowSize + phase * distance;
    
                if (currentDistance < fadeStartDistance) {
                    arrowAlpha = currentDistance / fadeStartDistance;
                } else if (currentDistance > distance - fadeEndDistance) {
                    arrowAlpha = 1.0F - (currentDistance - (distance - fadeEndDistance)) / fadeEndDistance;
                    if (arrowAlpha < 0.0F) {
                        arrowAlpha = 0.0F;
                    }
                }
        
                arrow.setAlphaMult(alphaMult * arrowAlpha);
                arrow.renderAtCenter(arrowX, arrowY);
            }
        }
    }

    private UIPanelAPI getMapFromIntelTab(Object intelTab) {
        EventsPanel eventsPanel = utils.getEventsPanel(intelTab);
        Object outerMap = utils.eventsPanelGetMap(eventsPanel);
        return utils.mapTabGetMap(outerMap);
    }

    private static float distanceBetween(Vector2f pos1, Vector2f pos2) {
        return (float)Math.sqrt((double)((pos1.x - pos2.x) * (pos1.x - pos2.x) + (pos1.y - pos2.y) * (pos1.y - pos2.y)));
    }
    
    private static float angleBetween(Vector2f pos1, Vector2f pos2) {
        return UiUtil.fastAtan2(pos2.y - pos1.y, pos2.x - pos1.x) * 57.295784F;
    }
}
