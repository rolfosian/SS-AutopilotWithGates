package data.scripts.autopilotwithgates.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.campaign.CampaignUIPersistentData.AbilitySlots;

import data.scripts.autopilotwithgates.AutoPilotGatesAbility;
import data.scripts.autopilotwithgates.AutopilotWithGatesPlugin;
import data.scripts.autopilotwithgates.AbilityScroller.AbilitySlotters;
import data.scripts.autopilotwithgates.util.UiUtil;

public class RemoveAbility implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (Global.getCurrentState() != GameState.CAMPAIGN) {
            Console.showMessage("This command is only applicable to the campaign.");
            return CommandResult.WRONG_CONTEXT;
        }

        if (AutopilotWithGatesPlugin.abilityScroller != null && AutopilotWithGatesPlugin.abilityScroller.getOldAbilitySlots() instanceof AbilitySlotters) {
            AbilitySlots ourAbilitySlots = AutopilotWithGatesPlugin.abilityScroller.getOurAbilitySlots();
            AbilitySlots newAbilitySlots = new AbilitySlots();

            newAbilitySlots.setLocked(ourAbilitySlots.isLocked());
            newAbilitySlots.setCurrBarIndex(ourAbilitySlots.getCurrBarIndex());
            UiUtil.setAbilitySlots(newAbilitySlots, ourAbilitySlots.getSlots());
            AutopilotWithGatesPlugin.abilityScroller.setOldAbilitySlots(newAbilitySlots);
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        if (playerFleet.hasAbility("AutoPilotWithGates")) {
            ((AutoPilotGatesAbility)playerFleet.getAbility("AutoPilotWithGates")).deactivate();

            playerFleet.removeAbility("AutoPilotWithGates");
            Global.getSector().getCharacterData().removeAbility("AutoPilotWithGates");

            Console.showMessage("AutoPilotWithGates ability removed. You may now disable the mod and load this save without issues.");
            Global.getSector().getCampaignUI().cmdSaveAndExit();
        }

        return CommandResult.SUCCESS;
    }
    
}
