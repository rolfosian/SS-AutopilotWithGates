package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;

public class GateAutoPilotRule extends BaseCommandPlugin {
    @Override
    public boolean execute(String arg0, InteractionDialogAPI dialog, List<Token> arg2, Map<String, MemoryAPI> arg3) {
        Map<String, MemoryAPI> memMap = dialog.getPlugin().getMemoryMap();
        if (memMap == null) {
            dialog.dismiss();
            return true;
        }
        MemoryAPI mem = memMap.get("$gateAutoPilotRule");
        if (mem == null) {
            dialog.dismiss();
            return true;
        }

        ((Runnable) mem.get("jump")).run();
        
        memMap.remove("$gateAutoPilotRule");
        return true;
    }
}
