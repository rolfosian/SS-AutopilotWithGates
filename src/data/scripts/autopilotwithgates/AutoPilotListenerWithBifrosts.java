package data.scripts.autopilotwithgates;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI.OptionTooltipCreator;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

import com.fs.starfarer.api.util.Misc;

import data.scripts.autopilotwithgates.util.BaseEveryFrameScript;
import data.scripts.autopilotwithgates.util.GateAutoPilotRuleMemory;
import data.scripts.autopilotwithgates.util.GateFinder;
import data.scripts.autopilotwithgates.util.UiUtil;

import static data.scripts.autopilotwithgates.AutoPilotWithGatesSettings.*;

import static data.scripts.autopilotwithgates.AutopilotWithGatesPlugin.systemBifrostData;
import static data.scripts.autopilotwithgates.AutopilotWithGatesPlugin.systemGateData;

import com.fs.starfarer.api.impl.campaign.aotd_entities.BiFrostGateEntity;

public class AutoPilotListenerWithBifrosts extends AutoPilotListener {
    private boolean areGatesBifrosts = false;
    
    public AutoPilotListenerWithBifrosts(boolean abilityActive) {
        super(abilityActive);
    }

    @Override
    protected void reset() {
        this.areGatesBifrosts = false;
        super.reset();
    }

    @Override
    protected void resetAfterBlacklist(CampaignUIAPI campaignUI) {
        this.areGatesBifrosts = false;
        super.resetAfterBlacklist(campaignUI);
    }

    @Override
    protected void setArrowColor(CampaignFleetAPI playerFleet) {
        if (!this.areGatesBifrosts) {
            this.gateArrowColor = lastLegArrowColor;
            super.setArrowColor(playerFleet);
            return;
        }

        this.gateArrowColor = Color.cyan;
        this.arrowColor = DARK_RED;
    }

    @Override
    protected void findNewEntryGate(CampaignUIAPI campaignUI, LocationAPI playerLoc, CampaignFleetAPI playerFleet) {
        GateData entryNormGate = null, exitNormGate, entryBifrost = null, exitBifrost;

        boolean validGates = false;
        boolean validBifrosts = false;

        exitNormGate = GateFinder.getNearestGate(systemGateData, this.currentUltimateTarget);
        exitBifrost = GateFinder.getNearestGate(systemBifrostData, this.currentUltimateTarget);

        if (playerLoc.isHyperspace()) {
            if (exitNormGate != null) {
                entryNormGate = GateFinder.getNearestGateToPlayerOutsideLocation(systemGateData, exitNormGate.gate, this.currentUltimateTarget);
                validGates = entryNormGate != null;
            }
            if (exitBifrost != null) {
                entryBifrost = GateFinder.getNearestGateToPlayerOutsideLocation(systemBifrostData, exitBifrost.gate, this.currentUltimateTarget);
                validBifrosts = entryBifrost != null;
            }
        } else {
            if (exitBifrost != null) {
                entryBifrost = GateFinder.getNearestBifrostInLocation(playerLoc, playerFleet.getLocation());
                if (entryBifrost == null) entryBifrost = GateFinder.getNearestGateToPlayerOutsideLocation(systemBifrostData, exitBifrost.gate, this.currentUltimateTarget);
                validBifrosts = entryBifrost != null;
            }
            if (exitNormGate != null) {
                entryNormGate = GateFinder.getNearestGateInLocation(playerLoc, playerFleet.getLocation());
                if (entryNormGate == null) entryNormGate = GateFinder.getNearestGateToPlayerOutsideLocation(systemGateData, exitNormGate.gate, this.currentUltimateTarget);
                validGates = entryNormGate != null;
            }
        }

        boolean useBifrosts;
        if (validGates && validBifrosts) {
            useBifrosts = shouldUseBifrosts(entryBifrost, exitBifrost, entryNormGate, exitNormGate, playerFleet, this.currentUltimateTarget);
        } else if (validBifrosts) {
            useBifrosts = true;
        } else if (validGates) {
            useBifrosts = false;
        } else {
            boolean followMouse = campaignUI.isPlayerFleetFollowingMouse();
            boolean isFollowingDirectCommand = campaignUI.isFollowingDirectCommand();
            SectorEntityToken interactionTarget = playerFleet.getInteractionTarget();

            this.layInCourseFor(this.currentUltimateTarget);
            this.handleMouseStatus(followMouse, isFollowingDirectCommand, interactionTarget, campaignUI);

            this.areGatesBifrosts = false;
            this.entryGate = null;
            this.exitGate = null;
            this.currentUltimateTarget = null;
            this.wasJustGotCloserThanGate = true;
            return;
        }

        GateData bestEntry, bestExit;
        if (useBifrosts) {
            bestEntry = entryBifrost;
            bestExit = exitBifrost;
        } else {
            bestEntry = entryNormGate;
            bestExit = exitNormGate;
        }

        if (!(this.entryGate.gate == bestEntry.gate && this.exitGate.gate == bestExit.gate)) {
            boolean followMouse = campaignUI.isPlayerFleetFollowingMouse();
            boolean isFollowingDirectCommand = campaignUI.isFollowingDirectCommand();
            SectorEntityToken interactionTarget = playerFleet.getInteractionTarget();

            this.layInCourseFor(bestEntry.gate);
            this.handleMouseStatus(followMouse, isFollowingDirectCommand, interactionTarget, campaignUI);

            this.entryGate = bestEntry;
            this.exitGate = bestExit;
            this.areGatesBifrosts = useBifrosts;
        }
    }

    @Override
    protected void findGates(CampaignUIAPI campaignUI, LocationAPI playerLoc, CampaignFleetAPI playerFleet, SectorEntityToken ultimateTarget) {
        this.currentUltimateTarget = ultimateTarget;
        GateData entryNormGate = null;
        GateData exitNormGate = null;
        GateData entryBifrost = null;
        GateData exitBifrost = null;

        boolean validGates = false;
        boolean validBifrosts = false;

        if (!playerLoc.isHyperspace()) {
            entryNormGate = GateFinder.getNearestGateInLocation(playerLoc, playerFleet.getLocation());

            if (entryNormGate != null) {
                exitNormGate = GateFinder.getNearestGate(systemGateData, ultimateTarget);
                validGates = exitNormGate != null;
            }

            entryBifrost = GateFinder.getNearestBifrostInLocation(playerLoc, playerFleet.getLocation());

            if (entryBifrost != null) {
                exitBifrost = GateFinder.getNearestGate(systemBifrostData, ultimateTarget);
                validBifrosts = exitBifrost != null;
            }

            if (!validGates && !validBifrosts) {
                if (entryBifrost != null && entryNormGate != null) {
                    return;
                }
            } else {
                applyBestRoute(
                    campaignUI,
                    playerFleet,
                    ultimateTarget,
                    entryNormGate,
                    exitNormGate,
                    entryBifrost,
                    exitBifrost,
                    validGates,
                    validBifrosts
                );
                return;
            }
        }

        exitBifrost = GateFinder.getNearestGate(systemBifrostData, ultimateTarget);
        if (exitBifrost != null) {
            if (entryBifrost == null) entryBifrost = GateFinder.getNearestGateToPlayerOutsideLocation(systemBifrostData, exitBifrost.gate, ultimateTarget);
            validBifrosts = entryBifrost != null;
        }

        exitNormGate = GateFinder.getNearestGate(systemGateData, ultimateTarget);

        if (exitNormGate != null) {
            if (entryNormGate == null) entryNormGate = GateFinder.getNearestGateToPlayerOutsideLocation(systemGateData, exitNormGate.gate, ultimateTarget);
            validGates = entryNormGate != null;
        }
        

        if (!validGates && !validBifrosts) {
            this.wasJustActivated = false;
            this.entryGate = null;
            this.exitGate = null;
            return;
        }

        applyBestRoute(
            campaignUI,
            playerFleet,
            ultimateTarget,
            entryNormGate,
            exitNormGate,
            entryBifrost,
            exitBifrost,
            validGates,
            validBifrosts
        );
    }

    private void applyBestRoute(
            CampaignUIAPI campaignUI,
            CampaignFleetAPI playerFleet,
            SectorEntityToken ultimateTarget,
            GateData entryNormGate,
            GateData exitNormGate,
            GateData entryBifrost,
            GateData exitBifrost,
            boolean validGates,
            boolean validBifrosts
    ) {
        boolean useBifrosts;

        if (validGates && validBifrosts) {
            useBifrosts = shouldUseBifrosts(
                    entryBifrost,
                    exitBifrost,
                    entryNormGate,
                    exitNormGate,
                    playerFleet,
                    ultimateTarget
            );
        } else {
            useBifrosts = validBifrosts;
        }

        this.areGatesBifrosts = useBifrosts;

        if (useBifrosts) {
            this.entryGate = entryBifrost;
            this.exitGate = exitBifrost;
        } else {
            this.entryGate = entryNormGate;
            this.exitGate = exitNormGate;
        }

        boolean followMouse = campaignUI.isPlayerFleetFollowingMouse();
        boolean isFollowingDirectCommand = campaignUI.isFollowingDirectCommand();
        SectorEntityToken interactionTarget = playerFleet.getInteractionTarget();

        this.layInCourseFor(this.entryGate.gate);

        if (this.wasJustActivated || this.wasJustGotCloserThanGate)
            this.handleMouseStatus(followMouse, isFollowingDirectCommand, interactionTarget, campaignUI);
        
        this.wasJustActivated = false;
        this.wasJustGotCloserThanGate = false;
    }

    @Override
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {
        if (!this.areGatesBifrosts) {
            super.reportShownInteractionDialog(dialog);
            return;
        }

        SectorEntityToken interactionTarget = dialog.getInteractionTarget();

        if (interactionTarget != null && this.entryGate != null && interactionTarget == this.entryGate.gate) {
            CustomCampaignEntityAPI entry = this.entryGate.gate;
            CustomCampaignEntityAPI exit = this.exitGate.gate;
            SectorEntityToken ultimateTarget = this.currentUltimateTarget;

            Runnable jump = new Runnable() {
                @Override
                public void run() {
                    CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
                    dialog.dismiss();
                    removeArrowRenderer();

                    entry.getMemoryWithoutUpdate().set("$used", true);
                    exit.getMemoryWithoutUpdate().set("$used", true);
                    entry.getMemoryWithoutUpdate().set("$cooldown", 30f);
                    exit.getMemoryWithoutUpdate().set("$cooldown", 30f);

                    Global.getSector().setPaused(false);
                    JumpDestination dest = new JumpDestination(exit, null);
                    Global.getSector().doHyperspaceTransition(playerFleet, interactionTarget, dest, 2f);
                    
                    float distLY = Misc.getDistanceLY(exit, entry);
                    ((BiFrostGateEntity) exit.getCustomPlugin()).showBeingUsed(distLY);
                    ((BiFrostGateEntity) entry.getCustomPlugin()).showBeingUsed(distLY);
                    
                    ListenerUtil.reportFleetTransitingGate(playerFleet, interactionTarget, exit);

                    // AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
                    Global.getSector().addTransientScript(new BaseEveryFrameScript(true) {
                        @Override
                        public void advance(float arg0) {
                            if (Global.getSector().getPlayerFleet().getContainingLocation() != exit.getContainingLocation()) return;

                            self.reportFleetJumped(Global.getSector().getPlayerFleet(), entry, dest);
                            self.layInCourseFor(ultimateTarget);

                            this.isDone = true;
                            Global.getSector().removeTransientScript(this);
                        }
                    });

                    self.postGateJump = true;
                    self.entryGate = null;
                    self.exitGate = null;

                    dialog.getPlugin().getMemoryMap().remove("$gateAutoPilotRule");
                }
            };

            Global.getSector().addTransientScript(new BaseEveryFrameScript(true) {
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
                "Travel through the Bifrost Gate to " + exit.getContainingLocation().getName(),
                "gateAutoPilotRule"
            );
            dialog.getOptionPanel().addOptionTooltipAppender("gateAutoPilotRule", new OptionTooltipCreator() {
                @Override
                public void createTooltip(TooltipMakerAPI arg0, boolean arg1) {
                    arg0.addParaWithMarkup("Travel through the Bifrost Gate to get to ultimate autopilot course target " + ultimateTarget.getName() + " in "
                        + ultimateTarget.getContainingLocation().getName() + " at the cost of {{%s}} fuel.",
                        0f,
                        String.valueOf(0)
                    );
                }
            });
            return;
        }
    }

    @Override
    public void off() {
        super.off();
        this.areGatesBifrosts = false;
    }

    private boolean shouldUseBifrosts(
        GateData entryBifrost,
        GateData exitBifrost,
        GateData normEntryGate,
        GateData normExitGate,
        SectorEntityToken playerFleet,
        SectorEntityToken ultimateTarget
    ) {
        LocationAPI playerContainingLoc = playerFleet.getContainingLocation();
        LocationAPI ultimateTargetContainingLoc = ultimateTarget.getContainingLocation();

        if (playerContainingLoc == entryBifrost.gate.getContainingLocation() && playerContainingLoc == normEntryGate.gate.getContainingLocation()) {
            if (ultimateTargetContainingLoc == exitBifrost.gate.getContainingLocation() && ultimateTargetContainingLoc == normExitGate.gate.getContainingLocation()) {
                if (PREFER_BIFROSTS_IN_SYSTEMS_WITH_BOTH) return true;
                // calc dist in systems
                Vector2f playerLoc = playerFleet.getLocation();
                float distBifrost = GateFinder.getDistSq(entryBifrost.gate.getLocation(), playerLoc) + GateFinder.getDistSq(exitBifrost.gate.getLocation(), ultimateTarget.getLocation());
                float distNorm = GateFinder.getDistSq(normEntryGate.gate.getLocation(), playerLoc) + GateFinder.getDistSq(normExitGate.gate.getLocation(), ultimateTarget.getLocation());

                return distBifrost <= distNorm;
            }
        }

        if (entryBifrost.gate.getContainingLocation() == normEntryGate.gate.getContainingLocation()) {
            if (ultimateTargetContainingLoc == exitBifrost.gate.getContainingLocation() && ultimateTargetContainingLoc == normExitGate.gate.getContainingLocation()) {
                if (PREFER_BIFROSTS_IN_SYSTEMS_WITH_BOTH) return true;
                
                Vector2f ultimateTargetLoc = ultimateTarget.getLocation();

                float distBifrost = entryBifrost.closestEntryDistSq + GateFinder.getDistSq(exitBifrost.gate.getLocation(), ultimateTargetLoc);
                float distNorm = normEntryGate.closestEntryDistSq + GateFinder.getDistSq(normExitGate.gate.getLocation(), ultimateTargetLoc);

                return distBifrost <= distNorm;
            }
        }

        return isBifrostsLessDistanceLY(entryBifrost, exitBifrost, normEntryGate, normExitGate, playerFleet, ultimateTarget);
    }

    private boolean isBifrostsLessDistanceLY(
        GateData entryBifrost,
        GateData exitBifrost,
        GateData normEntryGate,
        GateData normExitGate,
        SectorEntityToken playerFleet,
        SectorEntityToken ultimateTarget
    ) {
        return Misc.getDistanceLY(playerFleet, entryBifrost.gate) + Misc.getDistanceLY(exitBifrost.gate, ultimateTarget)
            < Misc.getDistanceLY(playerFleet, normEntryGate.gate) + Misc.getDistanceLY(normExitGate.gate, ultimateTarget);
    }
}
