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
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;

import data.scripts.autopilotwithgates.util.GateAutoPilotRuleMemory;
import data.scripts.autopilotwithgates.util.GateFinder;
import data.scripts.autopilotwithgates.util.UiUtil;

import static data.scripts.autopilotwithgates.AutopilotWithGatesPlugin.systemBifrostData;
import static data.scripts.autopilotwithgates.AutopilotWithGatesPlugin.systemGateData;

import lunalib.lunaSettings.LunaSettings;
import com.fs.starfarer.api.impl.campaign.aotd_entities.BiFrostGateEntity;

public class AutoPilotListenerWithBifrosts extends AutoPilotListener {
    private boolean areGatesBifrosts = false;
    private boolean preferBifrostsInSystemsWithBoth = false;
    
    public AutoPilotListenerWithBifrosts(boolean abilityActive) {
        super(abilityActive);

        if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
            this.preferBifrostsInSystemsWithBoth = LunaSettings.getBoolean("autopilot_with_gates", "preferBifrostsInSystemsWithBoth");

        } else {
            this.preferBifrostsInSystemsWithBoth = Global.getSettings().getBoolean("gateAutopilot_preferBifrostsInSystemsWithBoth");
        }
    }

    @Override
    protected void reset() {
        this.areGatesBifrosts = false;
        super.reset();
    }

    @Override
    protected void setArrowColor(CampaignFleetAPI playerFleet) {
        if (!this.areGatesBifrosts) {
            this.gateArrowColor = UiUtil.getFleetArrow(playerFleet).getColor();
            super.setArrowColor(playerFleet);
            return;
        }

        this.gateArrowColor = Color.cyan;
        this.arrowColor = DARK_RED;
    }

    @Override
    protected void findNewEntryGate(CampaignUIAPI campaignUI, LocationAPI playerLoc, CampaignFleetAPI playerFleet) {
        GateData entryNormGate, exitNormGate;
        Pair<GateData, GateData> bifrosts;
        
        if (playerLoc.isHyperspace()) {
            exitNormGate = GateFinder.getNearestGate(systemGateData, this.currentUltimateTarget);
            entryNormGate = GateFinder.getNearestGateToPlayerOutsideLocation(systemGateData, exitNormGate.gate, this.currentUltimateTarget);
        } else {
            exitNormGate = GateFinder.getNearestGate(systemGateData, this.currentUltimateTarget);
            entryNormGate = GateFinder.getNearestGateInLocation(playerLoc, playerFleet.getLocation());
        }
        bifrosts = this.findBifrosts(playerLoc, playerFleet, this.currentUltimateTarget);

        boolean validGates = exitNormGate != null && entryNormGate != null;
        boolean validBifrosts = bifrosts != null;

        boolean useBifrosts;
        if (validGates && validBifrosts) {
            useBifrosts = shouldUseBifrosts(bifrosts, entryNormGate, exitNormGate, playerFleet, this.currentUltimateTarget);
        } else if (validBifrosts) {
            useBifrosts = true;
        } else if (validGates) {
            useBifrosts = false;
        } else {
            return;
        }

        GateData bestEntry, bestExit;
        if (useBifrosts) {
            bestEntry = bifrosts.one;
            bestExit = bifrosts.two;
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
        boolean wasJustActivated = this.wasJustActivated;
        boolean wasJustGotCloserThanGate = this.wasJustGotCloserThanGate;

        super.findGates(campaignUI, playerLoc, playerFleet, ultimateTarget);
        Pair<GateData, GateData> bifrosts = findBifrosts(playerLoc, playerFleet, ultimateTarget);

        if (bifrosts != null) {
            if (this.entryGate == null || shouldUseBifrosts(bifrosts, this.entryGate, this.exitGate, playerFleet, ultimateTarget)) {
                this.areGatesBifrosts = true;
                this.entryGate = bifrosts.one;
                this.exitGate = bifrosts.two;

                boolean followMouse = campaignUI.isPlayerFleetFollowingMouse();
                boolean isFollowingDirectCommand = campaignUI.isFollowingDirectCommand();
                SectorEntityToken interactionTarget = playerFleet.getInteractionTarget();

                this.layInCourseFor(this.entryGate.gate);

                if (wasJustActivated || wasJustGotCloserThanGate) this.handleMouseStatus(followMouse, isFollowingDirectCommand, interactionTarget, campaignUI);

                this.wasJustActivated = false;
                this.wasJustGotCloserThanGate = false;
                return;
            }
        }
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

            if (this.autoJump) {
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

    private Pair<GateData, GateData> findBifrosts(
        LocationAPI playerLoc,
        SectorEntityToken playerFleet,
        SectorEntityToken ultimateTarget
    ) {
        GateData entry, exit;

        if (playerLoc instanceof StarSystemAPI) {
            entry = GateFinder.getNearestBifrostInLocation(playerLoc, playerFleet.getLocation());

            if (entry != null) {
                exit = GateFinder.getNearestGate(systemBifrostData, ultimateTarget);

                if (exit != null) return new Pair<>(entry, exit);
                else entry = null;
            }
        }

        exit = GateFinder.getNearestGate(systemBifrostData, ultimateTarget);
        entry = exit != null ? GateFinder.getNearestGateToPlayerOutsideLocation(systemBifrostData, exit.gate, this.currentUltimateTarget) : null;

        return entry != null && exit != null ? new Pair<>(entry, exit) : null;
    }

    private boolean shouldUseBifrosts(
        Pair<GateData, GateData> bifrosts,
        GateData normEntryGate,
        GateData normExitGate,
        SectorEntityToken playerFleet,
        SectorEntityToken ultimateTarget
    ) {
        LocationAPI playerContainingLoc = playerFleet.getContainingLocation();
        LocationAPI ultimateTargetContainingLoc = ultimateTarget.getContainingLocation();

        if (playerContainingLoc == bifrosts.one.gate.getContainingLocation() && playerContainingLoc == normEntryGate.gate.getContainingLocation()) {
            if (ultimateTargetContainingLoc == bifrosts.two.gate.getContainingLocation() && ultimateTargetContainingLoc == normExitGate.gate.getContainingLocation()) {
                if (this.preferBifrostsInSystemsWithBoth) return true;
                // calc dist in systems
                Vector2f playerLoc = playerFleet.getLocation();
                float distBifrost = distanceBetween(bifrosts.one.gate.getLocation(), playerLoc) + distanceBetween(bifrosts.two.gate.getLocation(), ultimateTarget.getLocation());
                float distNorm = distanceBetween(normEntryGate.gate.getLocation(), playerLoc) + distanceBetween(normExitGate.gate.getLocation(), ultimateTarget.getLocation());

                return distBifrost <= distNorm;
            }
        }

        if (bifrosts.one.gate.getContainingLocation() == normEntryGate.gate.getContainingLocation()) {
            if (ultimateTargetContainingLoc == bifrosts.two.gate.getContainingLocation() && ultimateTargetContainingLoc == normExitGate.gate.getContainingLocation()) {
                if (this.preferBifrostsInSystemsWithBoth) return true;
                
                Vector2f ultimateTargetLoc = ultimateTarget.getLocation();

                float distBifrost = bifrosts.one.closestEntryDistSq + distanceBetween(bifrosts.two.gate.getLocation(), ultimateTargetLoc);
                float distNorm = normEntryGate.closestEntryDistSq + distanceBetween(normExitGate.gate.getLocation(), ultimateTargetLoc);

                return distBifrost <= distNorm;
            }
        }

        return isBifrostsLessDistanceLY(bifrosts, normEntryGate, normExitGate, playerFleet, ultimateTarget);
    }

    private boolean isBifrostsLessDistanceLY(
        Pair<GateData, GateData> bifrosts,
        GateData normEntryGate,
        GateData normExitGate,
        SectorEntityToken playerFleet,
        SectorEntityToken ultimateTarget
    ) {
        return Misc.getDistanceLY(playerFleet, bifrosts.one.gate) + Misc.getDistanceLY(bifrosts.two.gate, ultimateTarget)
            < Misc.getDistanceLY(playerFleet, normEntryGate.gate) + Misc.getDistanceLY(normExitGate.gate, ultimateTarget);
    }
}
