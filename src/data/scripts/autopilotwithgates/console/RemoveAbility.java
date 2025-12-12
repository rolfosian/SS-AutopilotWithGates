package data.scripts.autopilotwithgates.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;

import data.scripts.autopilotwithgates.AutoPilotGatesAbility;

public class RemoveAbility implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (Global.getCurrentState() != GameState.CAMPAIGN) {
            Console.showMessage("This command is only applicable to the campaign.");
            return CommandResult.WRONG_CONTEXT;
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
