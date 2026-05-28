package data.scripts.autopilotwithgates;

import java.awt.Color;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;

import org.lwjgl.input.Keyboard;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;

import com.fs.starfarer.api.campaign.BaseCampaignEntityPickerListener;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;

import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.abilities.BaseToggleAbility;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;

import com.fs.starfarer.api.input.InputEventAPI;

import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;

import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.ui.impl.StandardTooltipV2Expandable;

import data.scripts.autopilotwithgates.util.CampaignEntityPickerInstantiator.DialogDismissedListener;
import data.scripts.autopilotwithgates.util.UiUtil;

import static data.scripts.autopilotwithgates.util.UiUtil.utils;

import static data.scripts.autopilotwithgates.AutoPilotWithGatesSettings.*;
import static data.scripts.autopilotwithgates.AutopilotWithGatesPlugin.listener;
import static data.scripts.autopilotwithgates.AutopilotWithGatesPlugin.isBifrostUsable;
import static data.scripts.autopilotwithgates.util.CampaignEntityPickerInstantiator.showCampaignEntityPicker;

public class AutoPilotGatesAbility extends BaseToggleAbility {
    private static final VarHandle paraFontHandle;
    static {
        try {
            paraFontHandle = MethodHandles.privateLookupIn(StandardTooltipV2Expandable.class, MethodHandles.lookup()).findVarHandle(
                StandardTooltipV2Expandable.class,
                "paraFont",
                String.class
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isShowingEntityPicker = false;

    public static String tooltipStringDesc = Global.getSettings().getString("gateAutopilot_tooltipStringDesc");
    public static String tooltipStringDescGreen = Global.getSettings().getString("gateAutopilot_tooltipStringDescGreen");
    public static String tooltipStringDescRed = Global.getSettings().getString("gateAutopilot_tooltipStringDescRed");

    public static String tooltipStringBLAdd = Global.getSettings().getString("gateAutopilot_tooltipStringBLAdd");
    public static String tooltipStringBLRemove = Global.getSettings().getString("gateAutopilot_tooltipStringBLRemove");
    public static String shiftKeyName = Global.getSettings().getString("gateAutopilot_shiftKeyName");

    private static boolean isPaused;
    private static final Object dialogDismissed = new DialogDismissedListener() {
        @Override
        public void dialogDismissed(Object dialog, int yesOrNo) {
            isShowingEntityPicker = false;
            Global.getSector().setPaused(isPaused);
            utils.campaignUISetDialogType(Global.getSector().getCampaignUI(), null);
        }
    }.getProxy();

    private static final BaseCustomUIPanelPlugin keyCapturePlugin = new BaseCustomUIPanelPlugin() {
        @Override
        public void processInput(List<InputEventAPI> events) {
            for (InputEventAPI event : events) {
                if (event.isConsumed() || isShowingEntityPicker) continue;

                if (event.isKeyDownEvent()) {
                    if (event.getEventValue() == BLACKLIST_DIALOG_HOTKEY) {
                        event.consume();
                        if (event.isShiftDown()) {
                            showRemoveFromBlacklistDialog();
                        } else {
                            showAddToBlacklistDialog();
                        }
                        break;
                    }
                }
            }
        }
    };

    public AutoPilotGatesAbility() {
        super();
        isShowingEntityPicker = false;
    }

    public static void setShowingEntityPicker(boolean bool) {
        isShowingEntityPicker = bool;
    }

    public static void showAddToBlacklistDialog() {
        CampaignUIAPI campaignUI = Global.getSector().getCampaignUI();
        if (utils.campaignUIGetDialogType(campaignUI) != null) return;
        utils.campaignUISetDialogType(campaignUI, UiUtil.DIALOG_TYPE_MENU_ENUM);

        List<SectorEntityToken> gates = new ArrayList<>();

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (CustomCampaignEntityAPI entity : system.getCustomEntities()) {
                if (entity.hasTag(Tags.GATE) && GateEntityPlugin.isScanned(entity) && !listener.isBlacklisted(entity)) {
                    gates.add(entity);

                } else if (entity.hasTag("bifrost") && isBifrostUsable(entity) && !listener.isBlacklisted(entity)) {
                    gates.add(entity);
                }
            }
        }

        UIPanelAPI entityPickerDialog = showCampaignEntityPicker(
            utils.campaignUIGetScreenPanel(campaignUI),
            dialogDismissed,
            "Choose a Gate to add to Blacklist",
            "",
            "Confirm",
            Global.getSector().getPlayerFaction(),
            gates,
            new BaseCampaignEntityPickerListener() {
                @Override
                public void pickedEntity(SectorEntityToken entity) {
                    listener.getBlacklist().add(entity.getId());
                    AutopilotWithGatesPlugin.getInstance().registerGateIterator();
                    listener.resetAfterBlacklist(campaignUI);
                    isShowingEntityPicker = false;

                    campaignUI.addMessage(new BaseIntelPlugin() {
                        @Override
                        public String getIcon() {
                            return "graphics/icons/missions/at_the_gates.png";
                        }
                        @Override
                        public boolean isHidden() {
                            return false;
                        }
                        @Override
                        public void createIntelInfo(TooltipMakerAPI info, IntelInfoPlugin.ListInfoMode mode) {
                            info.setParaFontColor(Misc.getBrightPlayerColor());
                            info.addPara(entity.getName() + " in " + entity.getContainingLocation().getNameWithTypeShort() + " added to the autopilot's gate blacklist.", 0f);
                        }
                    }, MessageClickAction.NOTHING);
                }

                @Override
                public void cancelledEntityPicking() {
                    isShowingEntityPicker = false;
                }

                @Override
                public boolean canConfirmSelection(SectorEntityToken entity) {
                    return true;
                }

                @Override
                public String getMenuItemNameOverrideFor(SectorEntityToken entity) {
                    return entity.getName();
                }

                public String getSelectedTextOverrideFor(SectorEntityToken entity) {
                    return "Add " + entity.getName() + " in " + entity.getContainingLocation().getNameWithTypeShort() + " to Autopilot's Blacklist?";
                }
            }
        );

        if (listener.getEntryGate() != null) {
            listener.getMaps().add(UiUtil.getMapFromCampaignPickerDialog(entityPickerDialog));
        }

        isPaused = Global.getSector().isPaused();

        Global.getSector().setPaused(true);

        isShowingEntityPicker = true;
    }

    public static void showRemoveFromBlacklistDialog() {
        CampaignUIAPI campaignUI = Global.getSector().getCampaignUI();
        if (utils.campaignUIGetDialogType(campaignUI) != null) return;
        utils.campaignUISetDialogType(campaignUI, UiUtil.DIALOG_TYPE_MENU_ENUM);

        List<SectorEntityToken> gates = new ArrayList<>();

        for (String id : listener.getBlacklist()) 
            gates.add(Global.getSector().getEntityById(id));

        UIPanelAPI entityPickerDialog = showCampaignEntityPicker(
            utils.campaignUIGetScreenPanel(campaignUI),
            dialogDismissed,
            "Choose a Gate to remove From Blacklist",
            "",
            "Confirm",
            Global.getSector().getPlayerFaction(),
            gates,
            new BaseCampaignEntityPickerListener() {
                @Override
                public void pickedEntity(SectorEntityToken entity) {
                    listener.getBlacklist().remove(entity.getId());
                    AutopilotWithGatesPlugin.getInstance().registerGateIterator();
                    listener.resetAfterBlacklist(campaignUI);
                    isShowingEntityPicker = false;

                    campaignUI.addMessage(new BaseIntelPlugin() {
                        @Override
                        public String getIcon() {
                            return "graphics/icons/missions/at_the_gates.png";
                        }
                        @Override
                        public boolean isHidden() {
                            return false;
                        }
                        @Override
                        public void createIntelInfo(TooltipMakerAPI info, IntelInfoPlugin.ListInfoMode mode) {
                            info.setParaFontColor(Misc.getBrightPlayerColor());
                            info.addPara(entity.getName() + " in " + entity.getContainingLocation().getNameWithTypeShort() + " removed from the autopilot's gate blacklist.", 0f);
                        }
                    }, MessageClickAction.NOTHING);
                }

                @Override
                public void cancelledEntityPicking() {
                    isShowingEntityPicker = false;
                }

                @Override
                public boolean canConfirmSelection(SectorEntityToken entity) {
                    return true;
                }

                @Override
                public String getMenuItemNameOverrideFor(SectorEntityToken entity) {
                    return entity.getName();
                }

                public String getSelectedTextOverrideFor(SectorEntityToken entity) {
                    return "Remove " + entity.getName() + " in " + entity.getContainingLocation().getNameWithTypeShort() + " from Autopilot's Blacklist?";
                }
            }
        );

        if (listener.getEntryGate() != null) {
            listener.getMaps().add(UiUtil.getMapFromCampaignPickerDialog(entityPickerDialog));
        }

        isPaused = Global.getSector().isPaused();

        Global.getSector().setPaused(true);

        isShowingEntityPicker = true;
    }

    protected static boolean isShowingEntityPicker() {
        return isShowingEntityPicker;
    }

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
        String status = this.isActive() ? " (on)" : " (off)";
        boolean useKeyCapture = false;
        
        if (!Global.CODEX_TOOLTIP_MODE) {
            LabelAPI title = tooltip.addTitle(this.spec.getName() + status);
            title.highlightLast(status);
            title.setHighlightColor(gray);

            if (Global.getCurrentState() == GameState.CAMPAIGN)
                useKeyCapture = true;
            
        } else {
            tooltip.addSpacer(-10.0F);
        }

        tooltip.addPara(tooltipStringDesc, 10f, TOOLTIP_PARA_COLORS, tooltipStringDescGreen, tooltipStringDescRed);
        if (useKeyCapture) {
            tooltip.addCustom(Global.getSettings().createCustom(0f, 0f, keyCapturePlugin), 0f);
            
            String paraFont = (String) paraFontHandle.get(tooltip);
            tooltip.setParaFont("graphics/fonts/orbitron12condensed.fnt");

            tooltip.addParaWithMarkup(tooltipStringBLAdd, 5f, BLACKLIST_DIALOG_HOTKEY_NAME).setColor(gray);
            tooltip.addParaWithMarkup(tooltipStringBLRemove, 2f, shiftKeyName + " + " + BLACKLIST_DIALOG_HOTKEY_NAME).setColor(gray);
            tooltip.setParaFont(paraFont);
        }
    }

    @Override
    public boolean isTooltipExpandable() {
        return false;
    }
}
