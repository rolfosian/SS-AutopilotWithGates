package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import org.lwjgl.input.Keyboard;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.abilities.TransponderAbility;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
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

        if (mem.get("JUMP_CONFIRM_TURN_TRANSPONDER_ON") != null) {
            AbilityPlugin t = Global.getSector().getPlayerFleet().getAbility(Abilities.TRANSPONDER);
            if (t != null && !t.isActive()) {
                t.activate();
            }
            ((Runnable) mem.get("jump")).run();
            memMap.remove("$gateAutoPilotRule");
            return true;
        }

        SectorEntityToken target = (SectorEntityToken) mem.get("dest");
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (target != null && target.getContainingLocation() != null && 
                !target.getContainingLocation().isHyperspace() && !player.isTransponderOn()) {
            List<FactionAPI> wouldBecomeHostile = TransponderAbility.getFactionsThatWouldBecomeHostile(player);
            boolean wouldMindTOff = false;
            boolean isPopulated = false;
            for (MarketAPI market : Global.getSector().getEconomy().getMarkets(target.getContainingLocation())) {
                if (market.isHidden()) continue;
                if (market.getFaction().isPlayerFaction()) continue;
                
                isPopulated = true;
                if (!market.getFaction().isHostileTo(Factions.PLAYER) && 
                        !market.isFreePort() &&
                        !market.getFaction().getCustomBoolean(Factions.CUSTOM_ALLOWS_TRANSPONDER_OFF_TRADE)) {
                    wouldMindTOff = true;
                }
            }
            
            if (isPopulated) {
                TextPanelAPI textPanel = dialog.getTextPanel();
                OptionPanelAPI options = dialog.getOptionPanel();
                if (wouldMindTOff) {
                    textPanel.addPara("Your transponder is off, and patrols " +
                            "in the " + 
                            target.getContainingLocation().getNameWithLowercaseType() + 
                            " are likely to give you trouble over the fact, if you're spotted.");
                } else {
                    textPanel.addPara("Your transponder is off, but any patrols in the " + 
                            target.getContainingLocation().getNameWithLowercaseType() + 
                            " are unlikely to raise the issue.");
                }
                
                if (!wouldBecomeHostile.isEmpty()) {
                    String str = "Turning the transponder on now would reveal your hostile actions to";
                    boolean first = true;
                    boolean last = false;
                    for (FactionAPI faction : wouldBecomeHostile) {
                        last = wouldBecomeHostile.indexOf(faction) == wouldBecomeHostile.size() - 1;
                        if (first || !last) {
                            str += " " + faction.getDisplayNameWithArticle() + ",";
                        } else {
                            str += " and " + faction.getDisplayNameWithArticle() + ",";
                        }
                    }
                    str = str.substring(0, str.length() - 1) + ".";
                    textPanel.addPara(str, Misc.getNegativeHighlightColor());
                }
                
                options.clearOptions();
                
                options.addOption("Turn the transponder on and then travel through the gate", "gateAutoPilotRule", null);
                options.addOption("Travel through the gate, keeping the transponder off", "gateAutoPilotTransponderRule", null);
                
                options.addOption("Abort", "rbid_failsafe_leave", null);
                options.setShortcut("rbid_failsafe_leave", Keyboard.KEY_ESCAPE, false, false, false, true);

                mem.set("JUMP_CONFIRM_TURN_TRANSPONDER_ON", new Object()); // lol

                return true;
            }
        }

        ((Runnable) mem.get("jump")).run();
        
        memMap.remove("$gateAutoPilotRule");
        return true;
    }
}
