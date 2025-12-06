package data.scripts;

// import java.awt.Color;
// import java.util.*;

import org.apache.log4j.Logger;
// import org.lwjgl.opengl.GL11;
// import org.lwjgl.util.vector.Vector2f;

// import com.fs.graphics.LayeredRenderable;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
// import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
// import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.missions.GateCMD;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
// import com.fs.starfarer.campaign.BaseLocation;
// import com.fs.starfarer.campaign.CampaignEngine;
// import com.fs.starfarer.campaign.fleet.CampaignFleet;
// import com.fs.starfarer.api.campaign.CampaignEngineLayers;
// import com.fs.starfarer.combat.CombatViewport;

// the commented out shit isnt worth the hassle

public class AutoPilotListener extends BaseCampaignEventListener implements EveryFrameScript { //, LayeredRenderable<CampaignEngineLayers, CombatViewport> {
    private static final Logger logger = Logger.getLogger(AutoPilotListener.class);
    public static void print(Object... args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            sb.append(args[i] instanceof String ? (String) args[i] : String.valueOf(args[i]));
            if (i < args.length - 1) sb.append(' ');
        }
        logger.info(sb.toString());
    }
    private final AutoPilotListener self = this;
    
    private IntervalUtil interval = new IntervalUtil(0.1f, 0.1f);

    private SectorEntityToken currentUltimateTarget;
    private CustomCampaignEntityAPI entryGate;
    private CustomCampaignEntityAPI exitGate;

    private boolean lookingAtMap = false;
    private boolean postGateJump = false;
    private boolean abilityActive = false;

    private final boolean autoJump;
    private final boolean mapOverride;
    private final AutoPilotGatesAbility ability;

    // private final EnumSet<CampaignEngineLayers> layers = EnumSet.of(CampaignEngineLayers.FLEETS);
    // private final SpriteAPI arrow = Global.getSettings().getSprite("graphics/warroom/ship_arrow.png");
    // private final Color arrowColor = new Color(139, 0, 0);
    // private boolean renderingArrow = false;
    // private BaseLocation arrowRenderingLoc;

    public AutoPilotListener(boolean abilityActive) {
        super(false);
        this.abilityActive = abilityActive;
        this.ability = (AutoPilotGatesAbility) Global.getSector().getPlayerFleet().getAbility("AutoPilotWithGates");
        
        if (Global.getSettings().getModManager().isModEnabled("LunaLib")) {
            this.autoJump = Global.getSettings().getBoolean("gateAutopilot_autoJump");
            this.mapOverride = Global.getSettings().getBoolean("gateAutopilot_mapOverride");

        } else {
            this.autoJump = Global.getSettings().getBoolean("gateAutopilot_autoJump");
            this.mapOverride = Global.getSettings().getBoolean("gateAutopilot_mapOverride");
        }
    }

    @Override
    public void advance(float arg0) {
        if (!this.abilityActive) return;
        this.interval.advance(arg0);
        if (!this.interval.intervalElapsed()) return;

        CampaignUIAPI campaignUI = Global.getSector().getCampaignUI();

        SectorEntityToken ultimateTarget = campaignUI.getUltimateCourseTarget();
        if (ultimateTarget == null) {
            this.currentUltimateTarget = null;
            this.entryGate = null;
            this.exitGate = null;
            return;
        }
        boolean ultimateTargetIsEntryGate = ultimateTarget == this.entryGate;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        LocationAPI playerLoc = playerFleet.getContainingLocation();
        // if (this.arrowRenderingLoc != null && playerLoc != this.arrowRenderingLoc) {
        //     removeArrowRenderer();
        // }

        // if (this.entryGate != null && !this.renderingArrow) {
        //     addArrowRenderer(playerLoc);

        // } else if (this.entryGate == null && this.renderingArrow) {
        //     removeArrowRenderer();

        // } else if (this.entryGate != null && this.renderingArrow && playerLoc != this.arrowRenderingLoc) {
        //     removeArrowRenderer();
        //     addArrowRenderer(playerLoc);
        // }

        if (this.mapOverride) {
            CoreUITabId coreTab = campaignUI.getCurrentCoreTab();
            boolean coreTabIsMap = coreTab == CoreUITabId.MAP;
    
            if (ultimateTargetIsEntryGate
                && coreTabIsMap
                && !this.lookingAtMap
                && ultimateTarget != this.currentUltimateTarget
            ) {
                Global.getSector().layInCourseFor(this.currentUltimateTarget);
                this.lookingAtMap = true;
                return;
    
            } else if (this.entryGate != null 
                && !coreTabIsMap
                && !ultimateTargetIsEntryGate
                && !this.postGateJump
                && this.lookingAtMap
            ) {
                this.lookingAtMap = false;

                if (this.currentUltimateTarget != ultimateTarget) {
                    this.currentUltimateTarget = ultimateTarget;
                    this.exitGate = GateFinder.getNearestGate(ultimateTarget);

                    if (this.exitGate == null || this.entryGate == this.exitGate) {
                        this.entryGate = null;
                        this.exitGate = null;
                        this.currentUltimateTarget = null;
                    }
                }
                return;
            
            } else if (this.lookingAtMap) { 
                return;
            }
        }
        
        if (ultimateTargetIsEntryGate) {
            if (!playerFleet.isInHyperspace()) return;
            CustomCampaignEntityAPI newEntryGate = GateFinder.getNearestGateToPlayerOutsideLocation(this.exitGate);

            if (newEntryGate != null) {
                if (this.entryGate != newEntryGate) {
                    this.entryGate = newEntryGate;
                    Global.getSector().layInCourseFor(this.entryGate);
                }
            } else {
                Global.getSector().layInCourseFor(this.currentUltimateTarget);
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
                        // removeArrowRenderer();

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
                    "Travel through the gate to " + this.exitGate.getContainingLocation().getName(),
                    "gateAutoPilotRule"
                );
                dialog.getOptionPanel().setTooltip(
                    "gateAutoPilotRule", 
                    "Travel through the gate to get to ultimate autopilot course target " + this.currentUltimateTarget.getName() + " in "
                    + this.currentUltimateTarget.getContainingLocation().getName()
                );
                return;

            } else {
                dialog.getOptionPanel().addOption(
                    "Travel through the gate to " + this.exitGate.getContainingLocation().getName(),
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
        this.lookingAtMap = false;
        this.abilityActive = false;

        SectorEntityToken temp = this.currentUltimateTarget;
        this.currentUltimateTarget = null;
        Global.getSector().layInCourseFor(temp);
        // removeArrowRenderer();

        this.entryGate = null;
        this.exitGate = null;
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

    // private void renderCourseArrow() {
    //     // outer method render
    //     CampaignFleet playerFleet = (CampaignFleet) Global.getSector().getPlayerFleet();
    //     float var1 = Global.getSector().getViewport().getAlphaMult();
    //     var1 *= playerFleet.getSensorFader().getBrightness();

    //     if (!(var1 <= 0.0F)) {
    //         GL11.glPushMatrix();
    //         // GL11.glEnable(GL11.GL_TEXTURE_2D);
    //         // GL11.glEnable(GL11.GL_BLEND);
    //         // GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

    //         Vector2f fleetLoc = playerFleet.getLocation();
    //         GL11.glTranslatef(fleetLoc.x, fleetLoc.y, 0.0f);
    //         var1 *= playerFleet.getSensorContactFaderBrightness();

    //         if (var1 <= 0.0f) {
    //             GL11.glPopMatrix();
    //             return;
    //         }

    //         // float arrowSize = 10.0f;

    //         // arrow.setSize(arrowSize, arrowSize);
    //         // arrow.setColor(new Color(255, 0, 0));
    //         // arrow.setAlphaMult(var1 * 0.5f);
            
    //         // float facing = playerFleet.getFacing();
    //         // arrow.setAngle(facing - 90.0f);
    //         // float cos = (float)Math.cos(Math.toRadians((double)facing));
    //         // float sin = (float)Math.sin(Math.toRadians((double)facing));
            
    //         // float selectionSize = playerFleet.getSelectionSize() + 5.0f;
    //         // float x = selectionSize * cos;
    //         // float y = selectionSize * sin;
    //         // arrow.renderAtCenter(x, y);

    //         // renderCourseArrow inner method
    //         SectorEntityToken var4 = this.entryGate == Global.getSector().getCampaignUI().getUltimateCourseTarget() ?
    //         CampaignEngine.getInstance().getCampaignUI().getCourseWidget().getNextStep(this.currentUltimateTarget) : 
    //         CampaignEngine.getInstance().getCampaignUI().getCourseWidget().getNextStep(this.entryGate); // override
            
    //         if (var4 == null) {
    //             GL11.glPopMatrix();
    //             return;
    //         }
    //         var1 *= CampaignEngine.getInstance().getCampaignUI().getCourseWidget().getInner().getBrightness();

    //         float var5 = 10.0F;
    //         float var6 = Global.getSector().getCampaignUI().getZoomFactor();
    //         var5 *= var6;

    //         arrow.setSize(var5, var5);
    //         arrow.setColor(arrowColor);
    //         arrow.setAlphaMult(var1);
    //         float var7 = angleBetween(Global.getSector().getPlayerFleet().getLocation(), var4.getLocation());
    //         arrow.setAngle(var7 - 90.0F);
    //         float var8 = (float)Math.cos(Math.toRadians((double)var7));
    //         float var9 = (float)Math.sin(Math.toRadians((double)var7));
    //         float var10 = 3.0F;
    //         float var11 = 15.0F;
    //         float var12 = (var5 + var10) * var11;
    //         float var13 = Math.max(0.0F, distanceBetween(playerFleet.getLocation(), var4.getLocation()) - var12 - 50.0F);
    //         float var14;
    //         if (var12 > var13) {
    //             var14 = var13 / var12;
    //             var1 *= var14;
    //         }

    //         var14 = 0.1F;
    //         float var15 = 0.25F;

    //         for(float var16 = 0.0F; var16 < var11; ++var16) {
    //             float var17;
    //             for(var17 = CampaignEngine.getInstance().getCampaignUI().getCourseWidget().getPhase() + var16 * (1.0F / var11); var17 > 1.0F; --var17) {
    //             }

    //             float var18 = 1.0F;
    //             if (var17 < var14) {
    //                 var18 = var17 / var14;
    //             } else if (var17 > 1.0F - var15) {
    //                 var18 = (1.0F - var17) / var15;
    //             }

    //             float var19 = playerFleet.getSelectionSize() + 5.0F + var5 + var17 * var12;
    //             float var20 = var19 * var8;
    //             float var21 = var19 * var9;
    //             arrow.setAlphaMult(var1 * var18);
    //             arrow.renderAtCenter(var20, var21);
    //         }
    //         GL11.glPopMatrix();
    //     }
    // }

    // @Override
    // public EnumSet<CampaignEngineLayers> getActiveLayers() {
        // return this.layers;
        // return null;
    // }

    // @Override
    // public void render(CampaignEngineLayers arg0, CombatViewport arg1) {
        // renderCourseArrow();
    // }

    // private void addArrowRenderer(LocationAPI playerLoc) {
    //     this.renderingArrow = true;
    //     this.arrowRenderingLoc = (BaseLocation) playerLoc;
    //     this.arrowRenderingLoc.addObject(this);
    // }

    // private void removeArrowRenderer() {
    //     this.renderingArrow = false;
    //     if (this.arrowRenderingLoc != null) {
    //         this.arrowRenderingLoc.removeObject(this);
    //         this.arrowRenderingLoc = null;
    //     }
    // }

    // private static float distanceBetween(Vector2f var0, Vector2f var1) {
    //     return (float)Math.sqrt((double)((var0.x - var1.x) * (var0.x - var1.x) + (var0.y - var1.y) * (var0.y - var1.y)));
    // }

    // private static final int TABLE_SIZE = (int)Math.sqrt(1048576.0);
    // private static final float INV_TABLE_RANGE;
    // private static final float[] ATAN2_LOOKUP;
    
    // static {
    //     INV_TABLE_RANGE = 1.0F / (float)(TABLE_SIZE - 1);
    //     ATAN2_LOOKUP = new float[1048576];
    
    //     for (int var0 = 0; var0 < TABLE_SIZE; ++var0) {
    //         for (int var1 = 0; var1 < TABLE_SIZE; ++var1) {
    //             float var2 = (float)var0 / (float)TABLE_SIZE;
    //             float var3 = (float)var1 / (float)TABLE_SIZE;
    //             ATAN2_LOOKUP[var1 * TABLE_SIZE + var0] = (float)Math.atan2((double)var3, (double)var2);
    //         }
    //     }
    // }
    
    // private static float angleBetween(Vector2f var0, Vector2f var1) {
    //     return fastAtan2(var1.y - var0.y, var1.x - var0.x) * 57.295784F;
    // }
    
    // private static final float fastAtan2(float var0, float var1) {
    //     float var2;
    //     float var3;
    
    //     if (var1 < 0.0F) {
    //         if (var0 < 0.0F) {
    //             var1 = -var1;
    //             var0 = -var0;
    //             var3 = 1.0F;
    //         } else {
    //             var1 = -var1;
    //             var3 = -1.0F;
    //         }
    //         var2 = -3.1415927F;
    //     } else {
    //         if (var0 < 0.0F) {
    //             var0 = -var0;
    //             var3 = -1.0F;
    //         } else {
    //             var3 = 1.0F;
    //         }
    //         var2 = 0.0F;
    //     }
    
    //     float var4 = 1.0F / ((var1 < var0 ? var0 : var1) * INV_TABLE_RANGE);
    //     int var5 = (int)(var1 * var4);
    //     int var6 = (int)(var0 * var4);
    //     int var7 = var6 * TABLE_SIZE + var5;
    
    //     return var7 >= 0 && var7 < ATAN2_LOOKUP.length
    //             ? (ATAN2_LOOKUP[var7] + var2) * var3
    //             : 0.0F;
    // }
}
