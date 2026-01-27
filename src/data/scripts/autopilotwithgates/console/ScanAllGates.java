package data.scripts.autopilotwithgates.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;
import org.lazywizard.console.commands.AddSpecial;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.missions.GateCMD;

public class ScanAllGates implements BaseCommand {

    @Override
    public CommandResult runCommand(String arg0, CommandContext arg1) {
        if (Global.getCurrentState() != GameState.CAMPAIGN) {
            Console.showMessage("This command is only applicable to the campaign.");
            return CommandResult.WRONG_CONTEXT;
        }

        scanAllGates(arg1);
        return CommandResult.SUCCESS;
    }

    private void scanAllGates(CommandContext ctx) {
        if (!GateEntityPlugin.canUseGates()) {
            new AddSpecial().runCommand("janus", ctx);
        }

        Global.getSector().getMemoryWithoutUpdate().set("$gatesActive", true);
        Global.getSector().getMemoryWithoutUpdate().set("$canScanGates", true);
        Global.getSector().getMemoryWithoutUpdate().set("$playerCanUseGates", true);

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (CustomCampaignEntityAPI gate : system.getCustomEntitiesWithTag(Tags.GATE)) {
                if (!GateEntityPlugin.isScanned(gate)) {
                    GateCMD.notifyScanned(gate);
                    gate.getMemoryWithoutUpdate().set("$gateScanned", true);
                } 
            }
        }
    }
    
}
