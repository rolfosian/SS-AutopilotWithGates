package data.scripts.autopilotwithgates;

import java.awt.Color;
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

import data.scripts.autopilotwithgates.util.CampaignEntityPickerInstantiator.DialogDismissedListener;
import data.scripts.autopilotwithgates.util.UiUtil;
import static data.scripts.autopilotwithgates.util.UiUtil.utils;

import static data.scripts.autopilotwithgates.AutopilotWithGatesPlugin.listener;
import static data.scripts.autopilotwithgates.AutopilotWithGatesPlugin.isBifrostUsable;
import static data.scripts.autopilotwithgates.util.CampaignEntityPickerInstantiator.showCampaignEntityPicker;

public class AutoPilotGatesAbility extends BaseToggleAbility {
    private static boolean isShowingEntityPicker = false;

    private static final Object dialogDismissed = new DialogDismissedListener() {
        @Override
        public void dialogDismissed(Object dialog, int yesOrNo) {
            isShowingEntityPicker = false;
        }
    }.getProxy();

    public AutoPilotGatesAbility() {
        super();
        isShowingEntityPicker = false;
    }

    protected void setShowingEntityPicker(boolean bool) {
        isShowingEntityPicker = bool;
    }

    public static void showAddToBlacklistDialog() {
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

        CampaignUIAPI campaignUI = Global.getSector().getCampaignUI();

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

        isShowingEntityPicker = true;

    }

    public static void showRemoveFromBlacklistDialog() {
        List<SectorEntityToken> gates = new ArrayList<>();

        for (String id : listener.getBlacklist()) 
            gates.add(Global.getSector().getEntityById(id));

        CampaignUIAPI campaignUI = Global.getSector().getCampaignUI();
            
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
        BaseCustomUIPanelPlugin plugin = null;
        
        if (!Global.CODEX_TOOLTIP_MODE) {
            LabelAPI title = tooltip.addTitle(this.spec.getName() + status);
            title.highlightLast(status);
            title.setHighlightColor(gray);

            if (Global.getCurrentState() == GameState.CAMPAIGN) {
                plugin = new BaseCustomUIPanelPlugin() {
                    @Override
                    public void processInput(List<InputEventAPI> events) {
                        for (InputEventAPI event : events) {
                            if (event.isConsumed() || isShowingEntityPicker) continue;

                            if (event.isKeyDownEvent()) {
                                if (event.getEventValue() == Keyboard.KEY_RETURN) {
                                    event.consume();
                                    if (event.isShiftDown()) {
                                        showRemoveFromBlacklistDialog();
                                    } else {
                                        showAddToBlacklistDialog();
                                    }
                                }
                            }
                        }
                    }
                };
                tooltip.addCustom(Global.getSettings().createCustom(0f, 0f, plugin), 0f);
            }
        } else {
            tooltip.addSpacer(-10.0F);
        }

        float pad = 10.0f;

        tooltip.addPara("Automatically sets the autopilot course target to the nearest gate to the fleet and links to the gate nearest to the ultimate autopilot course target.\n\nIf the non-gate route costs less fuel than the gate route, then the default course arrow will be green. Otherwise, it will be red.", pad);
        if (plugin != null) {
            tooltip.addParaWithMarkup("Press {{%s}} to add gates to the blacklist for the autopilot to ignore.", 5f, "[ENTER]");
            tooltip.addParaWithMarkup("Press {{%s}} to remove gates from the blacklist.", 2f, "[SHIFT + ENTER]");
        } 
    }

    @Override
    public boolean isTooltipExpandable() {
        return false;
    }
}
