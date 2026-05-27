package data.scripts.autopilotwithgates;

import java.awt.Color;
import java.util.*;

import org.apache.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import com.fs.graphics.LayeredRenderable;

import com.fs.starfarer.campaign.BaseLocation;
import com.fs.starfarer.campaign.CampaignEngine;
import com.fs.starfarer.campaign.fleet.CampaignFleet;
import com.fs.starfarer.combat.CombatViewport;
import com.fs.starfarer.ui.newui.CampaignEntityPickerDialog;
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
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.missions.GateCMD;

import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;

import com.fs.starfarer.api.util.Misc;

import data.scripts.autopilotwithgates.util.GateAutoPilotRuleMemory;
import data.scripts.autopilotwithgates.util.GateFinder;
import data.scripts.autopilotwithgates.util.TreeTraverser;
import data.scripts.autopilotwithgates.util.TreeTraverser.TreeNode;
import data.scripts.autopilotwithgates.util.UiUtil;

import static data.scripts.autopilotwithgates.AutoPilotWithGatesSettings.*;
import static data.scripts.autopilotwithgates.util.UiUtil.utils;
import static data.scripts.autopilotwithgates.AutopilotWithGatesPlugin.systemGateData;

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

    protected static final EnumSet<CampaignEngineLayers> layers = EnumSet.of(CampaignEngineLayers.FLEETS);
    
    protected static final SpriteAPI arrow = Global.getSettings().getSprite("graphics/warroom/ship_arrow.png");
    protected static final SpriteAPI gateCircle = Global.getSettings().getSprite("graphics/icons/gate0.png");

    protected final AutoPilotListener self = this;
    protected AutoPilotGatesAbility ability;

    private final Set<String> blacklist;
    private final List<Object> messageDisplayList;

    protected SectorEntityToken currentUltimateTarget;
    protected GateData entryGate;
    protected GateData exitGate;

    protected boolean postGateJump = false;
    protected boolean abilityActive = false;
    protected boolean wasJustActivated = true;
    protected boolean wasJustGotCloserThanGate = false;

    protected boolean noExitJumpPoints = true;
    protected boolean renderingArrow = false;
    protected BaseLocation arrowRenderingLoc;

    protected Color arrowColor = DARK_RED;
    protected Color gateArrowColor = Misc.getBasePlayerColor();
    protected Color lastLegArrowColor = Misc.getBasePlayerColor();

    protected final Maps maps = new Maps();

    @SuppressWarnings("unchecked")
    protected AutoPilotListener(boolean abilityActive) {
        super(false);
        this.abilityActive = abilityActive;

        this.blacklist = (Set<String>) Global.getSector().getPersistentData().computeIfAbsent("$apwgBlacklist", key -> new HashSet<>());

        if (Global.getSector().getPlayerFleet().getContainingLocation() instanceof StarSystemAPI system && system.getEntities(JumpPointAPI.class).size() == 0) this.noExitJumpPoints = true;
        else this.noExitJumpPoints = false;

        this.messageDisplayList = UiUtil.getMessageDisplayList(Global.getSector().getCampaignUI());
    }

    protected void reset() {
        if (!this.maps.isEmpty()) maps.clear();
        if (this.renderingArrow) removeArrowRenderer();

        this.currentUltimateTarget = null;
        this.entryGate = null;
        this.exitGate = null;
    }

    protected void resetAfterBlacklist(CampaignUIAPI campaignUI) {
        if (this.entryGate != null) {
            this.entryGate = null;
            this.exitGate = null;

            boolean followMouse = campaignUI.isPlayerFleetFollowingMouse();
            boolean isFollowingDirectCommand = campaignUI.isFollowingDirectCommand();
            SectorEntityToken interactionTarget = Global.getSector().getPlayerFleet().getInteractionTarget();

            this.layInCourseFor(this.currentUltimateTarget);
            this.currentUltimateTarget = null;
            
            this.wasJustActivated = true;
            this.handleMouseStatus(followMouse, isFollowingDirectCommand, interactionTarget, campaignUI);
        }
    }

    @Override
    public void advance(float dt) {
        if (!this.abilityActive) return;

        CampaignUIAPI campaignUI = Global.getSector().getCampaignUI();
        if (utils.getCourseWidget(campaignUI) == null) return;

        SectorEntityToken ultimateTarget = campaignUI.getUltimateCourseTarget();
        if (ultimateTarget == null) {
            this.reset();
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
            if (!this.maps.isEmpty()) this.maps.clear();
        }
        
        if (this.entryGate != null && ultimateTarget == this.entryGate.gate) {
            setArrowColor(playerFleet);

            Object core = null;
            CoreUITabId currentCoreTabId = campaignUI.getCurrentCoreTab();
            InteractionDialogAPI interactionDialog = campaignUI.getCurrentInteractionDialog();
            boolean mapsPresent = false;

            // there is potential for maps with course arrow functionality in interaction dialog tree (courier missions etc)
            if (interactionDialog != null) {
                core = utils.interactionDialogGetCore(interactionDialog);

                for (TreeNode node : new TreeTraverser((UIPanelAPI)interactionDialog).getNodes()) {
                    for (UIComponentAPI child : node.getChildren()) {
                        if (child.getClass() == UiUtil.mapClass) {
                            mapsPresent = true;
                            if (!this.maps.containsKey(child))
                                this.maps.add((UIPanelAPI) child);
                            
                        } else if (child.getClass() == CampaignEntityPickerDialog.class) {
                            mapsPresent = true;
                            UIPanelAPI map = UiUtil.getMapFromCampaignPickerDialog(child);
                            if (!this.maps.containsKey(map))
                                this.maps.add(map);
                        }
                    }
                }
            } else {
                core = utils.campaignUIgetCore(campaignUI);
            }

            if (CoreUITabId.MAP == currentCoreTabId) {
                mapsPresent = true;
                UIPanelAPI map = utils.mapTabGetMap(utils.coreGetCurrentTab(core));
                if (!this.maps.containsKey(map)) this.maps.add(map);

            } else if (CoreUITabId.INTEL == currentCoreTabId) {
                mapsPresent = true;
                UIPanelAPI intelTab = utils.coreGetCurrentTab(core);
                UIPanelAPI map = UiUtil.getMapFromIntelTab(intelTab);

                if (!this.maps.containsKey(map)) this.maps.add(map);
                
                ButtonAPI planetsButton = utils.intelTabGetPlanetsButton(intelTab);
                if (planetsButton != null && planetsButton.isHighlighted()) {
                    UIPanelAPI planetsPanel = utils.intelTabGetPlanetsPanel(intelTab);
                    UIPanelAPI planetsMap = UiUtil.getIntelTabPlanetsPanelMap(planetsPanel);

                    if (planetsMap != null && !this.maps.containsKey(planetsMap)) this.maps.add(planetsMap);
                }
            }

            if (!AutoPilotGatesAbility.isShowingEntityPicker() && !mapsPresent && !this.maps.isEmpty()) this.maps.clear();
            this.findNewEntryGate(campaignUI, playerLoc, playerFleet);
            return;
        }
        this.findGates(campaignUI, playerLoc, playerFleet, ultimateTarget);
    }

    protected void handleMouseStatus(boolean followMouse, boolean isFollowingDirectCommand, SectorEntityToken interactionTarget, CampaignUIAPI campaignUI) {
        if (followMouse) {
            UiUtil.setFollowMouseTrue(Global.getSector().getCampaignUI());
        } else if (isFollowingDirectCommand) {
            if (interactionTarget != null) UiUtil.followEntity(campaignUI, interactionTarget);
            else UiUtil.setFollowMouseTrue(Global.getSector().getCampaignUI());
        }
    }

    protected void findNewEntryGate(CampaignUIAPI campaignUI, LocationAPI playerLoc, CampaignFleetAPI playerFleet) {
        if (!playerFleet.isInHyperspace()) return;

        GateData newEntryGate = GateFinder.getNearestGateToPlayerOutsideLocation(systemGateData, this.exitGate.gate, this.currentUltimateTarget);
        if (newEntryGate != null) {
            if (this.entryGate.gate != newEntryGate.gate) {
                this.entryGate = newEntryGate;

                boolean followMouse = campaignUI.isPlayerFleetFollowingMouse();
                boolean isFollowingDirectCommand = campaignUI.isFollowingDirectCommand();
                SectorEntityToken interactionTarget = playerFleet.getInteractionTarget();

                this.layInCourseFor(newEntryGate.gate);
                this.handleMouseStatus(followMouse, isFollowingDirectCommand, interactionTarget, campaignUI);
            }

        } else {
            boolean followMouse = campaignUI.isPlayerFleetFollowingMouse();
            boolean isFollowingDirectCommand = campaignUI.isFollowingDirectCommand();
            SectorEntityToken interactionTarget = playerFleet.getInteractionTarget();

            this.layInCourseFor(this.currentUltimateTarget);
            this.handleMouseStatus(followMouse, isFollowingDirectCommand, interactionTarget, campaignUI);

            this.entryGate = null;
            this.exitGate = null;
            this.currentUltimateTarget = null;
            this.wasJustGotCloserThanGate = true;
        }
    }

    protected void findGates(CampaignUIAPI campaignUI, LocationAPI playerLoc, CampaignFleetAPI playerFleet, SectorEntityToken ultimateTarget) {
        this.currentUltimateTarget = ultimateTarget;

        if (!playerLoc.isHyperspace()) {
            this.entryGate = GateFinder.getNearestGateInLocation(playerLoc, playerFleet.getLocation());

            if (this.entryGate != null) {
                this.exitGate = GateFinder.getNearestGate(systemGateData, ultimateTarget);

                if (this.exitGate != null) {
                    boolean isFollowingDirectCommand = campaignUI.isFollowingDirectCommand();
                    boolean followMouse = campaignUI.isPlayerFleetFollowingMouse();
                    SectorEntityToken interactionTarget = playerFleet.getInteractionTarget();

                    this.layInCourseFor(this.entryGate.gate);

                    if (this.wasJustActivated) {
                        this.handleMouseStatus(followMouse, isFollowingDirectCommand, interactionTarget, campaignUI);
                    }

                    this.wasJustActivated = false;
                    this.wasJustGotCloserThanGate = false;

                } else {
                    this.entryGate = null;
                }
                return;
            }
        }
        this.exitGate = GateFinder.getNearestGate(systemGateData, ultimateTarget);
        this.entryGate = this.exitGate != null ? GateFinder.getNearestGateToPlayerOutsideLocation(systemGateData, this.exitGate.gate, this.currentUltimateTarget) : null;

        if (this.entryGate != null && this.exitGate != null) {
            boolean followMouse = campaignUI.isPlayerFleetFollowingMouse();
            boolean isFollowingDirectCommand = campaignUI.isFollowingDirectCommand();
            SectorEntityToken interactionTarget = playerFleet.getInteractionTarget();

            this.layInCourseFor(this.entryGate.gate);

            if (this.wasJustActivated || this.wasJustGotCloserThanGate) {
                this.handleMouseStatus(followMouse, isFollowingDirectCommand, interactionTarget, campaignUI);
            }

            this.wasJustGotCloserThanGate = false;
            this.wasJustActivated = false;
            return;
        }

        this.wasJustActivated = false;
        this.entryGate = null;
        this.exitGate = null;
        return;
    }

    protected boolean isGateFuelCostMore(CampaignFleetAPI playerFleet) {
        return GateFinder.getCombinedFuelCost(playerFleet, this.entryGate.gate, this.exitGate.gate, this.currentUltimateTarget)
                > GateFinder.getFuelCostToUltimateTarget(playerFleet, this.currentUltimateTarget);
    }

    protected void setArrowColor(CampaignFleetAPI playerFleet) {
        this.arrowColor = isGateFuelCostMore(playerFleet) ? DARK_GREEN : DARK_RED;
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

        if (interactionTarget != null && this.entryGate != null && interactionTarget == this.entryGate.gate) {
            int cost = GateCMD.computeFuelCost(this.exitGate.gate);
            int available = (int) Global.getSector().getPlayerFleet().getCargo().getFuel();

            if (cost <= available) {
                CustomCampaignEntityAPI entry = this.entryGate.gate;
                CustomCampaignEntityAPI exit = this.exitGate.gate;
                SectorEntityToken ultimateTarget = this.currentUltimateTarget;

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
                        ((GateEntityPlugin) exit.getCustomPlugin()).showBeingUsed(distLY);
                        ((GateEntityPlugin) entry.getCustomPlugin()).showBeingUsed(distLY);
                            
                        ListenerUtil.reportFleetTransitingGate(playerFleet, interactionTarget, exit);

                        // AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
                        Global.getSector().addTransientScript(new EveryFrameScript() {
                            private boolean isDone = false;

                            @Override
                            public void advance(float arg0) {
                                if (Global.getSector().getPlayerFleet().getContainingLocation() != exit.getContainingLocation()) return;

                                self.reportFleetJumped(Global.getSector().getPlayerFleet(), entry, dest);
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
                            Global.getSector().getCampaignUI().addMessage(UiUtil.disabledMessagePlugin, MessageClickAction.NOTHING);
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

                if (AUTOJUMP) {
                    jump.run();
                    return;
                }

                MemoryAPI mem = new GateAutoPilotRuleMemory();
                mem.set("jump", jump);
                mem.set("dest", exit);
                dialog.getPlugin().getMemoryMap().put("$gateAutoPilotRule", mem);

                dialog.getOptionPanel().addOption(
                    "Travel through the Gate to " + exit.getContainingLocation().getName(),
                    "gateAutoPilotRule"
                );
                dialog.getOptionPanel().addOptionTooltipAppender("gateAutoPilotRule", new OptionTooltipCreator() {
                    @Override
                    public void createTooltip(TooltipMakerAPI arg0, boolean arg1) {
                        arg0.addParaWithMarkup("Travel through the Gate to get to ultimate autopilot course target " + ultimateTarget.getName() + " in "
                            + ultimateTarget.getContainingLocation().getName() + " at the cost of {{%s}} fuel.",
                            0f,
                            String.valueOf(cost)
                        );
                    }
                });
                return;

            } else {
                CustomCampaignEntityAPI exit = this.exitGate.gate;
                dialog.getOptionPanel().addOption(
                    "Travel through the Gate to " + exit.getContainingLocation().getName(),
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

    protected void layInCourseFor(SectorEntityToken target) {
        int messageDisplayListSize = this.messageDisplayList.size();
        Global.getSector().layInCourseFor(target);
        if (this.messageDisplayList.size() > messageDisplayListSize) this.messageDisplayList.remove(this.messageDisplayList.size()-1);
    }

    protected List<Object> getMessageDisplayList() {
        return this.messageDisplayList;
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

        if (!this.maps.isEmpty()) this.maps.clear();

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

    public GateData getEntryGate() {
        return this.entryGate;
    }

    public GateData getExitGate() {
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

        if (alphaMult > 0.0F) {
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

            for (float arrowIndex = 0.0F; arrowIndex < numArrows; ++arrowIndex) {
                float phase;
                for (phase = utils.getPhase(courseWidget) + arrowIndex * (1.0F / numArrows); phase > 1.0F; --phase);

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

    public Maps getMaps() {
        return this.maps;
    }

    public Set<String> getBlacklist() {
        return this.blacklist;
    }

    public boolean isBlacklisted(SectorEntityToken gate) {
        return this.blacklist.contains(gate.getId());
    }

    private class MapArrowRenderer extends BaseCustomUIPanelPlugin {
        private final UIPanelAPI map;
        private SectorEntityToken nextStep;

        private float regularCourseArrowOffset;
        private float gateCourseCircleOffset;
        private float gateCourseLastLegOffset;

        public MapArrowRenderer(UIPanelAPI map) {
            super();
            this.map = map;
        }

        @Override
        public void advance(float deltaTime) {
            if (this.nextStep == null || self.entryGate == null || Global.getSector().getCampaignUI().getCurrentCourseTarget() == null) return;

            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

            Vector2f playerLocation = playerFleet.getLocation();
            Vector2f targetLocation = this.nextStep.getLocation();

            BaseLocation mapLoc = utils.mapGetLocation(this.map);
            LocationAPI campaignMapLoc = CampaignEngine.getInstance().getUIData().getCampaignMapLocation();
            boolean isHyperSpace = (mapLoc != null && mapLoc.isHyperspace()) || (campaignMapLoc != null && campaignMapLoc.isHyperspace());

            if (mapLoc != this.nextStep.getContainingLocation()) {
                this.nextStep = self.currentUltimateTarget;

                playerLocation = playerFleet.getLocationInHyperspace();
                targetLocation = this.nextStep.getLocationInHyperspace();

            } else if (isHyperSpace) {
                playerLocation = playerFleet.getLocationInHyperspace();
                targetLocation = this.nextStep.getLocationInHyperspace();
            }

            if (isHyperSpace) {
                advanceGateCircleOffset(deltaTime);
                advanceGateCourseLastLegOffset(deltaTime);
            }

            float distance = distanceBetween(playerLocation, targetLocation);
            if (distance < 1000.0F) distance = 1000.0F;

            for (this.regularCourseArrowOffset += deltaTime * 0.1F * 10000.0F / distance; this.regularCourseArrowOffset > 1.0F; --this.regularCourseArrowOffset);
        }

        private void advanceGateCircleOffset(float deltaTime) {
            float distance = distanceBetween(self.entryGate.gate.getLocationInHyperspace(), self.exitGate.gate.getLocationInHyperspace());
            if (distance < 1000.0F) distance = 1000.0F;
            for (this.gateCourseCircleOffset += deltaTime * 0.1F * 10000.0F / distance; this.gateCourseCircleOffset > 1.0F; --this.gateCourseCircleOffset);
        }

        private void advanceGateCourseLastLegOffset(float deltaTime) {
            float distance = distanceBetween(self.exitGate.gate.getLocationInHyperspace(), self.currentUltimateTarget.getLocationInHyperspace());
            if (distance < 1000.0F) distance = 1000.0F;
            for (this.gateCourseLastLegOffset += deltaTime * 0.1F * 10000.0F / distance; this.gateCourseLastLegOffset > 1.0F; --this.gateCourseLastLegOffset);
        }

        @Override // i wish the hyperspace and system maps used distinct classes but here we are
        public void render(float alphaMult) {
            if (self.entryGate == null || Global.getSector().getCampaignUI().getCurrentCourseTarget() == null) return;

            this.nextStep = utils.getNextStep(utils.getCourseWidget(Global.getSector().getCampaignUI()), self.currentUltimateTarget);
            if (this.nextStep == null) return;

            Object zoomTracker = utils.getZoomTracker(this.map);
            float maxZoomFactor = utils.getMaxZoomFactor(zoomTracker);

            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
            LocationAPI playerFleetContainingLoc = playerFleet.getContainingLocation();
            
            boolean isHyperSpace = maxZoomFactor > 3.0f;
            boolean shouldRenderRegularCourse = true;

            Vector2f playerLocation = playerFleet.getLocation();
            Vector2f targetLocation = this.nextStep.getLocation();

            boolean isStar = false;
            if (isHyperSpace && self.currentUltimateTarget.isInHyperspace() && self.currentUltimateTarget instanceof JumpPointAPI jumpPoint && !jumpPoint.getDestinations().isEmpty()) {
                SectorEntityToken destination = jumpPoint.getDestinations().get(0).getDestination();
                if (destination != null && destination.getStarSystem() != null) {
                    if (destination.isStar()) {
                        isStar = true;

                        playerLocation = playerFleet.getLocationInHyperspace();
                        this.nextStep = destination.getStarSystem().getStar();
                        targetLocation = this.nextStep.getLocationInHyperspace();
                    }
                }
                
            }
            
            if (!isStar && this.nextStep.isInHyperspace() && this.nextStep instanceof JumpPointAPI jumpPoint && !jumpPoint.getDestinations().isEmpty()) {
                SectorEntityToken destination = jumpPoint.getDestinations().get(0).getDestination();
                if (destination != null && destination.getStarSystem() != null) {
                    this.nextStep = destination.getStarSystem().getHyperspaceAnchor();
                    targetLocation = this.nextStep.getLocationInHyperspace();
                }
            }

            BaseLocation mapLoc = utils.mapGetLocation(this.map);

            if (!isStar && mapLoc != this.nextStep.getContainingLocation()) {
                if (!isHyperSpace || mapLoc == null) return;
                if (self.noExitJumpPoints) shouldRenderRegularCourse = false;

                playerLocation = playerFleet.getLocationInHyperspace();
                targetLocation = self.currentUltimateTarget.getLocationInHyperspace();

            } else if (self.noExitJumpPoints && isHyperSpace) {
                playerLocation = playerFleet.getLocationInHyperspace();
                
                targetLocation = self.currentUltimateTarget.getLocationInHyperspace();
                shouldRenderRegularCourse = false;

            } else if (!isHyperSpace && mapLoc != playerFleetContainingLoc) {
                shouldRenderRegularCourse = false;

            } else if (isHyperSpace && playerLocation != playerFleet.getLocationInHyperspace()) {
                playerLocation = playerFleet.getLocationInHyperspace();
                targetLocation = self.currentUltimateTarget.getLocationInHyperspace();
            }

            PositionAPI mapPos = this.map.getPosition();
            GL11.glPushMatrix();
            GL11.glTranslatef((int) mapPos.getCenterX(), (int) mapPos.getCenterY(), 0.0f);

            float arrowSize;
            float arrowSpacing;
            float scaledStartX;
            float scaledStartY;
            float scaledEndX;
            float scaledEndY;

            float factor = utils.getFactor(this.map);
            float zoomLevel = utils.getZoomLevel(zoomTracker);
            alphaMult *= 0.5F;

            if (shouldRenderRegularCourse && distanceBetween(playerLocation, targetLocation) >= 1000f) {
                arrowSize = 10.0F;
                arrowSpacing = 3.0F;

                if (zoomLevel < 0.75F) {
                    zoomLevel = 0.75F;
                }

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
                    this.regularCourseArrowOffset,
                    arrowSize,
                    arrowSpacing,
                    self.arrowColor,
                    alphaMult,
                    arrow
                );
            }

            if (isHyperSpace) {
                playerLocation = self.entryGate.gate.getLocationInHyperspace();
                targetLocation = self.exitGate.gate.getLocationInHyperspace();

                if (distanceBetween(playerLocation, targetLocation) >= 1000f) {
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
                        this.gateCourseCircleOffset,
                        arrowSize,
                        arrowSpacing,
                        self.gateArrowColor,
                        alphaMult,
                        gateCircle
                    );
                }

                if ((self.currentUltimateTarget.isInHyperspace()
                    && self.currentUltimateTarget instanceof JumpPointAPI jp
                    && jp.getDestinationStarSystem() == self.exitGate.gate.getContainingLocation())
                || self.currentUltimateTarget.getContainingLocation() == self.exitGate.gate.getContainingLocation()) {
                    GL11.glPopMatrix();
                    return;
                }
                
                playerLocation = targetLocation;
                if (self.currentUltimateTarget instanceof JumpPointAPI jp && jp.isInHyperspace() && jp.getDestinations().get(0).getDestination().isStar()) {
                    targetLocation = jp.getDestinationStarSystem().getStar().getLocationInHyperspace();
                } else {
                    targetLocation = self.currentUltimateTarget.getLocationInHyperspace();
                }

                if (distanceBetween(playerLocation, targetLocation) >= 1000f) {
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
                        this.gateCourseLastLegOffset,
                        arrowSize,
                        arrowSpacing,
                        self.lastLegArrowColor,
                        alphaMult,
                        arrow
                    );
                }
            }
            GL11.glPopMatrix();
        }

        private void renderCourseArrowOnMap(float startX, float startY, float endX, float endY, float offset, float arrowSize, float arrowSpacing, Color arrowColor, float alphaMult, SpriteAPI arrow) {
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
            
            for (float arrowIndex = 0.0F; arrowIndex < numArrows; ++arrowIndex) {
                float phase;
                for (phase = offset + arrowIndex * (1.0F / numArrows); phase > 1.0F; --phase);
        
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

    protected final class Maps extends HashMap<UIPanelAPI, CustomPanelAPI> {
        @Override
        public void clear() {
            for (Map.Entry<UIPanelAPI, CustomPanelAPI> entry : this.entrySet()) {
                entry.getKey().removeComponent(entry.getValue());
            }
            super.clear();
            return;
        }

        public void add(UIPanelAPI map) {
            CustomPanelAPI arrowRenderingPanel = Global.getSettings().createCustom(0f, 0f, new MapArrowRenderer(map));
            map.addComponent(arrowRenderingPanel);
            this.put(map, arrowRenderingPanel);
        }
    };

    public static float distanceBetween(Vector2f pos1, Vector2f pos2) {
        return (float)Math.sqrt((double)((pos1.x - pos2.x) * (pos1.x - pos2.x) + (pos1.y - pos2.y) * (pos1.y - pos2.y)));
    }
    
    private static float angleBetween(Vector2f pos1, Vector2f pos2) {
        return UiUtil.atan2(pos2.y - pos1.y, pos2.x - pos1.x) * 57.295784F;
    }
}
