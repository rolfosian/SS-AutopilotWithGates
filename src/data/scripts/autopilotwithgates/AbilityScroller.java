package data.scripts.autopilotwithgates;

import java.util.List;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;

import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;

import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;

import com.fs.starfarer.campaign.CampaignEngine;
import com.fs.starfarer.campaign.CampaignUIPersistentData.AbilitySlots;

import data.scripts.autopilotwithgates.util.UiUtil;

public class AbilityScroller {
    private final CustomPanelAPI scrollPanel;
    private final UIPanelAPI abilityPanel;
    private final LabelAPI abilityBarIdxLabel;

    public AbilitySlotters ourAbilitySlots;
    public AbilitySlots oldAbilitySlots;

    public void remove() {
        try {
            this.abilityPanel.removeComponent(scrollPanel);
        } catch (Throwable ignored) {
            return;
        }
    }

    public AbilityScroller(UIPanelAPI abilityPanel) {
        PositionAPI abilityPanelPos = abilityPanel.getPosition();
        
        float leftBound = abilityPanelPos.getCenterX() - abilityPanelPos.getWidth() / 2;
        float rightBound = abilityPanelPos.getCenterX() + abilityPanelPos.getWidth() / 2;
        float topBound = abilityPanelPos.getCenterY() - abilityPanelPos.getHeight() / 2;
        float bottomBound = abilityPanelPos.getCenterY() + abilityPanelPos.getHeight() / 2;

        final ButtonAPI[] prevBtn = {null};
        final ButtonAPI[] nextBtn = {null};

        for (UIComponentAPI child : UiUtil.utils.getChildrenNonCopy(abilityPanel)) {
            if (child instanceof ButtonAPI btn) {
                String text = btn.getText();
                if (text != null) {
                    text = text.toLowerCase();
                    if (text.equals("prev")) prevBtn[0] = btn;
                    else if (text.equals("next")) nextBtn[0] = btn;
                }
            }
        }

        this.abilityPanel = abilityPanel;
        this.scrollPanel = Global.getSettings().createCustom(0f, 0f, new BaseCustomUIPanelPlugin() {

            private ButtonAPI prevButton = prevBtn[0];
            private ButtonAPI nextButton = nextBtn[0];

            private boolean isInBounds(float mouseX, float mouseY) {
                return mouseX >= leftBound && mouseX <= rightBound &&
                       mouseY >= topBound && mouseY <= bottomBound;
            }

            @Override
            public void processInput(List<InputEventAPI> events) {
                if (Global.getCurrentState() != GameState.CAMPAIGN) return;

                for (InputEventAPI event : events) {
                    if (!event.isConsumed() && event.isMouseScrollEvent() && isInBounds(event.getX(), event.getY())) {
                        if (event.getEventValue() > 0) {
                            UiUtil.utils.actionPerformed(abilityPanel, null, prevButton);
                        } else {
                            UiUtil.utils.actionPerformed(abilityPanel, null, nextButton);
                        }
                        event.consume();
                    }
                }
            }
        });

        this.abilityBarIdxLabel = Global.getSettings().createLabel("0/5", Fonts.ORBITRON_12);
        this.abilityBarIdxLabel.setColor(Misc.getButtonTextColor());
        this.abilityBarIdxLabel.setText(String.valueOf(CampaignEngine.getInstance().getUIData().getAbilitySlots().getCurrBarIndex() + 1) + "/5");

        this.abilityPanel.addComponent(this.scrollPanel).inTL(0,0);
        this.abilityPanel.addComponent((UIComponentAPI)this.abilityBarIdxLabel).rightOfMid((UIComponentAPI)nextBtn[0], 6f).setYAlignOffset(-2f);

        this.oldAbilitySlots = CampaignEngine.getInstance().getUIData().getAbilitySlots();
        this.ourAbilitySlots = new AbilitySlotters();

        UiUtil.setAbilitySlots(this.ourAbilitySlots, this.oldAbilitySlots.getSlots());
        this.ourAbilitySlots.setCurrBarIndex(this.oldAbilitySlots.getCurrBarIndex());
        this.ourAbilitySlots.setLocked(this.oldAbilitySlots.isLocked());

        CampaignEngine.getInstance().getUIData().setAbilitySlots(this.ourAbilitySlots);
    }

    public CustomPanelAPI getScrollPanel() {
        return this.scrollPanel;
    }

    public void setOldAbilitySlots(AbilitySlots slots) {
        this.oldAbilitySlots = slots;
    }

    public AbilitySlots getOldAbilitySlots() {
        return this.oldAbilitySlots;
    }

    public void setOurAbilitySlots(AbilitySlotters slots) {
        this.ourAbilitySlots = slots;
    }

    public AbilitySlots getOurAbilitySlots() {
        return this.ourAbilitySlots;
    }

    public class AbilitySlotters extends AbilitySlots {
        @Override
        public void setCurrBarIndex(int arg0) {
            super.setCurrBarIndex(arg0);
            abilityBarIdxLabel.setText(String.valueOf(arg0 + 1) + "/5");
        }
    }
}
