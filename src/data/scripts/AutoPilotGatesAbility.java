package data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.abilities.BaseToggleAbility;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

public class AutoPilotGatesAbility extends BaseToggleAbility {
    @Override
    protected void activateImpl() {
        AutopilotWithGatesPlugin.listener.on();
        Global.getSector().getPersistentData().put("$autopilotWithGatesAbility", true);
    }

    @Override
    protected void applyEffect(float arg0, float arg1) {

    }

    @Override
    protected void cleanupImpl() {

    }

    @Override
    protected void deactivateImpl() {
        AutopilotWithGatesPlugin.listener.off();
        Global.getSector().getPersistentData().put("$autopilotWithGatesAbility", false);
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean arg1) {
        Color gray = Misc.getGrayColor();
        String status = " (off)";
        if (this.isActive()) status = " (on)";
        
        if (!Global.CODEX_TOOLTIP_MODE) {
            LabelAPI title = tooltip.addTitle(this.spec.getName() + status);
            title.highlightLast(status);
            title.setHighlightColor(gray);
         } else {
            tooltip.addSpacer(-10.0F);
         }

         float pad = 10.0f;

         tooltip.addPara("Automatically sets the autopilot course target to the nearest gate to the fleet and links to the gate nearest to the ultimate autopilot course target", pad);
    }

    @Override
    public boolean isTooltipExpandable() {
        return false;
    }
}
