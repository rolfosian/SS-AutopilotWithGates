package data.scripts.autopilotwithgates.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;

import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import data.scripts.autopilotwithgates.AutoPilotListener;

public class AddGateAtPlayerLoc implements BaseCommand {

    @Override
    public CommandResult runCommand(String arg0, CommandContext arg1) {

        if (Global.getCurrentState() != GameState.CAMPAIGN) {
            Console.showMessage("This command is only applicable to the campaign.");
            return CommandResult.WRONG_CONTEXT;
        }
        
        LocationAPI loc = Global.getSector().getPlayerFleet().getContainingLocation();
        if (!(loc instanceof StarSystemAPI)) {
            Console.showMessage("This command can only be used in a system");
            return CommandResult.WRONG_CONTEXT;
        }

        if (arg0 == null || arg0.isBlank() || arg0.isEmpty()) {
            Console.showMessage("Please provide a name for the gate");
            return CommandResult.WRONG_CONTEXT;
        }

        addGateAtPlayerLocation(arg0, (StarSystemAPI)loc);
        return CommandResult.SUCCESS;
    }

    private static void addGateAtPlayerLocation(String name, StarSystemAPI system) {
        Vector2f playerLoc = Global.getSector().getPlayerFleet().getLocation();

        CustomCampaignEntityAPI gate = system.addCustomEntity(
            null,
            name,
            "inactive_gate",
            "neutral"
        );

        if (system.getStar() != null) {
            Vector2f starLoc = system.getStar().getLocation();

            float dx = playerLoc.x - starLoc.x;
            float dy = playerLoc.y - starLoc.y;
            float angle = (float) Math.toDegrees(Math.atan2(dy, dx));

            gate.setCircularOrbit(system.getStar(), angle, AutoPilotListener.distanceBetween(playerLoc, system.getStar().getLocation()), 200f);
            return;
        }

        gate.setLocation(playerLoc.x, playerLoc.y);
    }
}
