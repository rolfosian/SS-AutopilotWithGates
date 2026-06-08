package data.scripts.autopilotwithgates;

import java.awt.Color;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.stream.Collectors;

import org.lwjgl.input.Keyboard;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;

import com.fs.starfarer.api.campaign.BaseCampaignEntityPickerListener;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;

import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.abilities.BaseToggleAbility;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.ui.UITable;
import com.fs.starfarer.ui.impl.StandardTooltipV2Expandable;

import data.scripts.autopilotwithgates.util.ConfirmDialogInstantiator.DialogDismissedListener;
import data.scripts.autopilotwithgates.util.BaseEveryFrameScript;
import data.scripts.autopilotwithgates.util.UiUtil;
import data.scripts.autopilotwithgates.util.UiUtil.ActionListener;

import data.scripts.autopilotwithgates.util.EntityTooltips.EntityRowTooltipCreator;
import data.scripts.autopilotwithgates.util.EntityTooltips.SystemRowTooltipCreator;

import static data.scripts.autopilotwithgates.util.UiUtil.utils;

import static data.scripts.autopilotwithgates.AutoPilotWithGatesSettings.*;
import static data.scripts.autopilotwithgates.AutopilotWithGatesPlugin.listener;
import static data.scripts.autopilotwithgates.AutopilotWithGatesPlugin.isBifrostUsable;
import static data.scripts.autopilotwithgates.util.ConfirmDialogInstantiator.*;

import static data.scripts.autopilotwithgates.AutoPilotListener.print;

/**
 * This is a singleton. Do NOT instantiate more than one of these; can't do private constructors as the game needs access
 */
public class AutoPilotGatesAbility extends BaseToggleAbility {
    private static final Color TEXT_HIGHLIGHT_COLOR = Misc.getHighlightColor();
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

    private static final Object entityPickerDialogDismissed = new DialogDismissedListener() {
        @Override
        public void dialogDismissed(Object dialog, int yesOrNo) {
            isShowingEntityPicker = false;
        }
    }.getProxy();

    private SectorEntityToken pickedGate = null;
    private ButtonAPI[] blacklistDialogButtons = null;
    private LabelAPI blacklistLabel = null;
    private boolean isPaused;

    public AutoPilotGatesAbility() {
        super();
        isShowingEntityPicker = false;
    }

    public static void setShowingEntityPicker(boolean bool) {
        isShowingEntityPicker = bool;
    }

    private void showBlacklistDialog(boolean add) {
        CampaignUIAPI campaignUI = Global.getSector().getCampaignUI();
        if (utils.campaignUIGetDialogType(campaignUI) != null) return;
        utils.campaignUISetDialogType(campaignUI, UiUtil.DIALOG_TYPE_MENU_ENUM);
        UIPanelAPI screenPanel = utils.campaignUIGetScreenPanel(campaignUI);

        setUpDialog(
            add,
            showConfirmDialog(
                screenPanel,
                "graphics/illustrations/dead_gate.jpg",
                "",
                660f,
                100f,
                add ? new DialogDismissedListener() {
                    @Override
                    public void dialogDismissed(Object dialog, int button) {
                        if (button == 0 && pickedGate != null) {
                            listener.getBlacklist().add(pickedGate.getId());
                            AutopilotWithGatesPlugin.getInstance().registerGateIterator();
                            listener.resetAfterBlacklist(campaignUI);
                            campaignUI.addMessage(new BlacklistMessagePlugin(true, pickedGate), MessageClickAction.NOTHING);
                        }

                        pickedGate = null;
                        blacklistLabel = null;
                        for (int i = 0; i < 4; i++) blacklistDialogButtons[i] = null;
                        Global.getSector().setPaused(isPaused);
                        utils.campaignUISetDialogType(campaignUI, null);
                        isShowingEntityPicker = false;
                    } 
                } : new DialogDismissedListener() {
                    @Override
                    public void dialogDismissed(Object dialog, int button) {
                        if (button == 0 && pickedGate != null) {
                            listener.getBlacklist().remove(pickedGate.getId());
                            AutopilotWithGatesPlugin.getInstance().registerGateIterator();
                            listener.resetAfterBlacklist(campaignUI);
                            campaignUI.addMessage(new BlacklistMessagePlugin(false, pickedGate), MessageClickAction.NOTHING);
                        }

                        pickedGate = null;
                        blacklistLabel = null;
                        for (int i = 0; i < 4; i++) blacklistDialogButtons[i] = null;
                        Global.getSector().setPaused(isPaused);
                        utils.campaignUISetDialogType(campaignUI, null);
                        isShowingEntityPicker = false;
                    }
                },
                "Confirm",
                "Pick from map",
                "Hidden Systems",
                "Cancel"
            ),
            screenPanel
        );

        isPaused = Global.getSector().isPaused();
        Global.getSector().setPaused(true);
    }

    private void setUpDialog(boolean add, Object[] dialogComponents, UIPanelAPI screenPanel) {
        List<SectorEntityToken> gates = new ArrayList<>();
        LinkedHashMap<StarSystemAPI, List<SectorEntityToken>> hiddenSystemsToGates = new LinkedHashMap<>();
        refGates(add, gates, hiddenSystemsToGates);

        Runnable showMapPicker = !gates.isEmpty() ? () -> {
            UIPanelAPI entityPickerDialog = showCampaignEntityPicker(
                screenPanel,
                entityPickerDialogDismissed,
                "Choose a Gate",
                "",
                "Confirm",
                Global.getSector().getPlayerFaction(),
                gates,
                new BlacklistCampaignEntityPickerListener(add)
            );
            if (listener.getEntryGate() != null) {
                listener.getMaps().add(UiUtil.getMapFromCampaignPickerDialog(entityPickerDialog));
            }
            isShowingEntityPicker = true;
        } : null;

        Runnable showTablePicker = !hiddenSystemsToGates.isEmpty() ? () -> {
            showHiddenSystemsTableDialog(screenPanel, add, hiddenSystemsToGates);
        } : null;

        UIPanelAPI innerPanel = (UIPanelAPI) dialogComponents[1];
        blacklistDialogButtons = (ButtonAPI[]) dialogComponents[2];
        blacklistDialogButtons[0].setShortcut(Keyboard.KEY_G, false);

        if (showMapPicker == null) {
            blacklistDialogButtons[1].setEnabled(false);
            blacklistDialogButtons[1].setClickable(false);
        } else {
            utils.buttonSetListener(blacklistDialogButtons[1], new ActionListener() {
                @Override
                public void actionPerformed(Object inputEvent, Object uiElement) {
                    showMapPicker.run();
                }
            }.getProxy());
        }

        if (showTablePicker == null) {
            blacklistDialogButtons[2].setEnabled(false);
            blacklistDialogButtons[2].setClickable(false);
        } else {
            utils.buttonSetListener(blacklistDialogButtons[2], new ActionListener() {
                @Override
                public void actionPerformed(Object inputEvent, Object uiElement) {
                    showTablePicker.run();
                }
            }.getProxy());
        }

        blacklistLabel = Global.getSettings().createLabel(add ? "Choose a gate to add to the blacklist" : "Choose a gate to remove from the blacklist", Fonts.INSIGNIA_VERY_LARGE);
        blacklistLabel.setAlignment(Alignment.MID);
        blacklistLabel.setHighlight("add", "remove");
        blacklistLabel.setHighlightColors(new Color[] {Misc.getHighlightColor(), Misc.getHighlightColor()});
        innerPanel.addComponent((UIComponentAPI)blacklistLabel).inTMid(10f);
    }

    private void showHiddenSystemsTableDialog(UIPanelAPI screenPanel, boolean add, Map<StarSystemAPI, List<SectorEntityToken>> hiddenSystemsToGates) {
        final boolean[] wasPicked = new boolean[1];
        wasPicked[0] = false;

        float dialogHeight = Math.min(35f * hiddenSystemsToGates.size() + 300f, 400f);

        Object[] dialogComponents = showConfirmDialog(
            screenPanel,
            "graphics/illustrations/dead_gate.jpg",
            "",
            560f,
            dialogHeight,
            new DialogDismissedListener() {
                @Override
                public void dialogDismissed(Object dialog, int yesOrNo) {
                    if (yesOrNo != 0 || !wasPicked[0] || pickedGate == null) {
                        pickedGate = null;

                        String text = add ? "Choose a gate to add to the blacklist" : "Choose a gate to remove from the blacklist";
                        blacklistLabel.setText(text);
                        blacklistLabel.autoSizeToWidth(blacklistLabel.computeTextWidth(text));
                        blacklistLabel.setHighlight("add", "remove");
                        blacklistLabel.setHighlightColors(new Color[] {Misc.getHighlightColor(), Misc.getHighlightColor()});
                        return;
                    }

                    String labelString = add ? 
                        "Blacklist " + pickedGate.getName() + " in " + pickedGate.getContainingLocation().getNameWithTypeShort() + "?"
                        : "Remove " + pickedGate.getName() + " in " + pickedGate.getContainingLocation().getNameWithTypeShort() + " from blacklist?";


                    blacklistLabel.setText(labelString);
                    blacklistLabel.autoSizeToWidth(blacklistLabel.computeTextWidth(labelString));

                    StarSystemAPI system = (StarSystemAPI) pickedGate.getContainingLocation();
                    PlanetAPI star = system.getStar();
                    
                    blacklistLabel.setHighlightColor(star != null ? star.getSpec().getIconColor() : system.getLightColor());
                    blacklistLabel.setHighlight(pickedGate.getContainingLocation().getNameWithTypeShort());
                }
                
            },
            "Confirm"
        );
        UIPanelAPI innerPanel = (UIPanelAPI) dialogComponents[1];
        ((ButtonAPI[]) dialogComponents[2])[0].setShortcut(Keyboard.KEY_G, false);

        float width = innerPanel.getPosition().getWidth() / 2, height = innerPanel.getPosition().getHeight() - 10f;

        Color c1 = Global.getSettings().getBasePlayerColor();
        Color c2 = Global.getSettings().getDarkPlayerColor();

        Map<LocationAPI, CustomPanelAPI> systemsToTablePanels = new HashMap<>();
        Map<LocationAPI, UITable> systemsToTables = new HashMap<>();

        float gatesTableWidth = width - 10f;
        float gatesTableHeight = height - 45f;

        for (Map.Entry<StarSystemAPI, List<SectorEntityToken>> entry : hiddenSystemsToGates.entrySet()) {
            CustomPanelAPI panel = Global.getSettings().createCustom(gatesTableWidth + 5f, gatesTableHeight, null);
            TooltipMakerAPI tt = panel.createUIElement(gatesTableWidth + 5f, gatesTableHeight, true);
            UITable table = (UITable) tt.beginTable(
                c1,
                c2,
                Misc.getHighlightedOptionColor(),
                30f,
                true,
                false, 
                new Object[]{"Gates", gatesTableWidth}
            );
            systemsToTables.put(entry.getKey(), table);

            for (SectorEntityToken gate : sortAlphabetically(entry.getValue())) {
                Object row = tt.addRowWithGlow(
                    c1,
                    gate.getName()
                );
                utils.uiTableRowSetData(row, table);

                String rowName = gate.getName();

                ButtonAPI rowButton = utils.uiTableRowGetButton(row);
                Object oldListener = utils.buttonGetListener(rowButton);

                utils.buttonSetListener(rowButton, new ActionListener() {
                    @Override
                    public void actionPerformed(Object arg0, Object arg1) {
                        Object selected = utils.uiTableGetSelected(table);
                        if (selected != null && selected != row) {
                            UiUtil.setRowColorAndText(selected, new Object[] {c1, ((Object[])UiUtil.uiTableRowParamsHandle.get(selected))[1]});
                            utils.uiTableSelect((UITable)utils.uiTableRowGetData(selected), null, null); 
                        }

                        UiUtil.setRowColorAndText(row, new Object[] {TEXT_HIGHLIGHT_COLOR, rowName});
                        utils.uiTableSelect(table, row, null);
                        utils.actionPerformed(oldListener, arg0, arg1);
                        pickedGate = gate;
                        wasPicked[0] = true;
                    }
                }.getProxy());
                tt.addTooltipToAddedRow(new EntityRowTooltipCreator(gate), TooltipLocation.RIGHT);
            }
            tt.addTable("", 0, 0f);
            ((UIPanelAPI)table).getPosition().setXAlignOffset(0f).setYAlignOffset(-5f);
            table.setItemsSelectable(true);
            
            panel.addUIElement(tt).inTL(0f,0f);
            systemsToTablePanels.put(entry.getKey(), panel);
        }

        UITable[] table = new UITable[1];
        float systemsTableWidth = width - 5f;
        CustomPanelAPI panel = Global.getSettings().createCustom(systemsTableWidth + 5f, height, new BaseCustomUIPanelPlugin() {
            @Override
            public void processInput(List<InputEventAPI> events) {
                for (InputEventAPI event : events) {
                    if (event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
                        Object selected = utils.uiTableGetSelected(table[0]);
                        if (selected != null) {
                            UITable selectedGatesTable = systemsToTables.get(utils.uiTableRowGetData(selected));
                            if (selectedGatesTable != null) {
                                Object selectedGate = utils.uiTableGetSelected(selectedGatesTable);
                                if (selectedGate != null) {
                                    UiUtil.setRowColorAndText(selectedGate, new Object[] {c1, ((Object[])UiUtil.uiTableRowParamsHandle.get(selectedGate))[1]});
                                    utils.uiTableSelect(selectedGatesTable, null, null);
                                    selectedGatesTable.advance(1f);
                                    selectedGatesTable.render(0f);
                                }
                            }
                            utils.uiTableSelect(table[0], null, null);
                            List<UIComponentAPI> children = utils.getChildrenNonCopy(innerPanel);
                            innerPanel.removeComponent(children.get(children.size()-1));
                            
                            wasPicked[0] = false;
                            pickedGate = null;

                            event.consume();
                            break;
                        }
                    }
                }
            }
        });
        TooltipMakerAPI tt = panel.createUIElement(systemsTableWidth + 5f, height, true);
        table[0] = (UITable) tt.beginTable(
            c1,
            c2,
            Misc.getHighlightedOptionColor(),
            30f,
            true,
            false, 
            new Object[]{"Systems", systemsTableWidth}
        );

        List<Object> rows = new ArrayList<>();
        for (StarSystemAPI system : hiddenSystemsToGates.keySet()) {
            String rowName = system.getNameWithTypeShort();
            Object row = tt.addRowWithGlow(
                c1,
                rowName
            );
            utils.uiTableRowSetData(row, system);
            tt.addTooltipToAddedRow(new SystemRowTooltipCreator(system), TooltipLocation.RIGHT);
            rows.add(row);

            ButtonAPI rowButton = utils.uiTableRowGetButton(row);
            Object oldListener = utils.buttonGetListener(rowButton);

            utils.buttonSetListener(rowButton, new ActionListener() {
                @Override
                public void actionPerformed(Object inputEvent, Object uiElement) {
                    Object selected = utils.uiTableGetSelected(table[0]);
                    if (selected != null && row == selected) {
                        utils.actionPerformed(oldListener, inputEvent, uiElement);
                        return;
                    }

                    List<UIComponentAPI> children = utils.getChildrenNonCopy(innerPanel);
                    for (CustomPanelAPI panel : systemsToTablePanels.values()) { // this sucks i hate it
                        if (children.contains(panel)) {
                            innerPanel.removeComponent(panel);
                            break;
                        }
                    }

                    utils.uiTableSelect(table[0], row, null);
                    utils.actionPerformed(oldListener, inputEvent, uiElement);
                    wasPicked[0] = false;
                    pickedGate = null;

                    innerPanel.addComponent(systemsToTablePanels.get(system)).inTL(width + 4f, 5f);
                }
            }.getProxy());
        }
        
        Global.getSector().addTransientScript(new BaseEveryFrameScript(true) {
            IntervalUtil interval = new IntervalUtil(0.2f, 0.2f);
            boolean b = false;
            @Override
            public void advance(float arg0) {
                interval.advance(arg0);
                
                if (!b) {
                    for (Object row : rows) {
                        StarSystemAPI system = (StarSystemAPI) utils.uiTableRowGetData(row);
                        UiUtil.setRowColorAndText(row, new Object[] {system.getStar() != null ? system.getStar().getSpec().getIconColor() :  system.getLightColor(), system.getNameWithTypeShort()});
                    }
                    b = true;
                }

                if (interval.intervalElapsed()) {
                    String text = add ? "Choose a gate to add to the blacklist" : "Choose a gate to remove from the blacklist";
                    blacklistLabel.setText(text);
                    blacklistLabel.autoSizeToWidth(blacklistLabel.computeTextWidth(text));
                    blacklistLabel.setHighlight("add", "remove");
                    blacklistLabel.setHighlightColors(new Color[] {Misc.getHighlightColor(), Misc.getHighlightColor()});
                    Global.getSector().removeTransientScript(this);
                }
            }
        });

        tt.addTable("", 0, 0f);
        ((UIPanelAPI)table[0]).getPosition().setXAlignOffset(0f).setYAlignOffset(-5f);
        // ((UIPanelAPI)table).getPosition().setYAlignOffset(-5f);
        table[0].setItemsSelectable(true);
        panel.addUIElement(tt).inTL(0f,0f);
        innerPanel.addComponent(panel).inTL(5f, 5f);
    }

    private void refGates(boolean add, List<SectorEntityToken> gates, LinkedHashMap<StarSystemAPI, List<SectorEntityToken>> hiddenSystemsToGates) {
        if (!add) {
            for (String id : listener.getBlacklist()) {
                SectorEntityToken entity = Global.getSector().getEntityById(id);
                StarSystemAPI system = (StarSystemAPI) entity.getContainingLocation();

                if (!isShowOnHyperspaceMap(system)) hiddenSystemsToGates.computeIfAbsent(system, sys -> new ArrayList<>()).add(entity);
                else gates.add(entity);
            }
        } else {
            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                List<SectorEntityToken> foundGates = new ArrayList<>();

                for (CustomCampaignEntityAPI entity : system.getCustomEntities()) {
                    if (entity.hasTag(Tags.GATE) && GateEntityPlugin.isScanned(entity) && !listener.isBlacklisted(entity)) {
                        foundGates.add(entity);

                    } else if (entity.hasTag("bifrost") && isBifrostUsable(entity)) {
                        foundGates.add(entity);
                    }
                }

                if (foundGates.size() > 0) {
                    if (!isShowOnHyperspaceMap(system)) hiddenSystemsToGates.put(system, foundGates);
                    else gates.addAll(foundGates);
                }
            }
        }
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

            useKeyCapture = Global.getCurrentState() == GameState.CAMPAIGN;
        } else {
            tooltip.addSpacer(-10.0F);
        }

        tooltip.addPara(tooltipStringDesc, 10f, TOOLTIP_PARA_COLORS, tooltipStringDescGreen, tooltipStringDescRed);
        if (useKeyCapture) {
            tooltip.addCustom(Global.getSettings().createCustom(0f, 0f, new BaseCustomUIPanelPlugin() {
                @Override
                public void processInput(List<InputEventAPI> events) {
                    for (InputEventAPI event : events) {
                        if (event.isConsumed() || isShowingEntityPicker) continue;

                        if (event.isKeyDownEvent()) {
                            if (event.getEventValue() == BLACKLIST_DIALOG_HOTKEY) {
                                showBlacklistDialog(!event.isShiftDown());
                                event.consume();
                                break;
                            }
                        }
                    }
                }
            }), 0f);
            
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

    private class BlacklistCampaignEntityPickerListener extends BaseCampaignEntityPickerListener {
        private final boolean add;

        public BlacklistCampaignEntityPickerListener(boolean add) {
            super();
            this.add = add;

            Global.getSector().addTransientScript(new BaseEveryFrameScript(true) {
                IntervalUtil interval = new IntervalUtil(0.2f, 0.2f);
                @Override
                public void advance(float arg0) {
                    interval.advance(arg0);

                    if (interval.intervalElapsed()) {
                        String text = add ? "Choose a gate to add to the blacklist" : "Choose a gate to remove from the blacklist";
                        blacklistLabel.setText(text);
                        blacklistLabel.autoSizeToWidth(blacklistLabel.computeTextWidth(text));
                        blacklistLabel.setHighlight("add", "remove");
                        blacklistLabel.setHighlightColors(new Color[] {Misc.getHighlightColor(), Misc.getHighlightColor()});
                        Global.getSector().removeTransientScript(this);
                    }
                }
            });
        }

        @Override
        public void pickedEntity(SectorEntityToken entity) {
            pickedGate = entity;

            String labelString = this.add ? 
                "Blacklist " + entity.getName() + " in " + entity.getContainingLocation().getNameWithTypeShort() + "?"
                : "Remove " + entity.getName() + " in " + entity.getContainingLocation().getNameWithTypeShort() + " from blacklist?";


            blacklistLabel.setText(labelString);
            blacklistLabel.autoSizeToWidth(blacklistLabel.computeTextWidth(labelString));

            StarSystemAPI system = (StarSystemAPI) entity.getContainingLocation();
            PlanetAPI star = system.getStar();
            
            blacklistLabel.setHighlightColor(star != null ? star.getSpec().getIconColor() : system.getLightColor());
            blacklistLabel.setHighlight(entity.getContainingLocation().getNameWithTypeShort());
            
            isShowingEntityPicker = false;
        }

        @Override
        public void cancelledEntityPicking() {
            isShowingEntityPicker = false;

            String text = add ? "Choose a gate to add to the blacklist" : "Choose a gate to remove from the blacklist";
            blacklistLabel.setText(text);
            blacklistLabel.autoSizeToWidth(blacklistLabel.computeTextWidth(text));
            blacklistLabel.setHighlight("add", "remove");
            blacklistLabel.setHighlightColors(new Color[] {Misc.getHighlightColor()});

            pickedGate = null;
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
            return this.add ?
                "Add " + entity.getName() + " in " + entity.getContainingLocation().getNameWithTypeShort() + " to Autopilot's Blacklist?"
                : "Remove " + entity.getName() + " in " + entity.getContainingLocation().getNameWithTypeShort() + " from Autopilot's Blacklist?";
        }
    }

    private class BlacklistMessagePlugin extends BaseIntelPlugin {
        private final boolean add;
        private String gateName;
        private String locName;

        public BlacklistMessagePlugin(boolean add, SectorEntityToken pickedGate) {
            this.add = add;
            this.gateName = pickedGate.getName();
            this.locName = pickedGate.getContainingLocation().getNameWithTypeShort();
        }

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

            if (add) info.addPara(gateName + " in " + locName + " added to the autopilot's gate blacklist.", 0f);
            else info.addPara(gateName + " in " + locName + " removed from the autopilot's gate blacklist.", 0f);
        }
    }

    public static boolean isShowOnHyperspaceMap(StarSystemAPI system) {
        for (SectorEntityToken e : Global.getSector().getHyperspace().getJumpPoints()) {
            JumpPointAPI jp = (JumpPointAPI) e;

            if (jp.getDestinationStarSystem() == system) {
                if (system.isNebula() && system.getStar() != null && system.getStar().getSpec().isNebulaCenter()) {
                    return true;
                }
                if ((jp.isStarAnchor() && !jp.hasTag(Tags.STAR_HIDDEN_ON_MAP))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static LinkedHashMap<StarSystemAPI, List<SectorEntityToken>> sortAlphabetically(LinkedHashMap<StarSystemAPI, List<SectorEntityToken>> map) {
        return map.size() <= 1 ? map : map.entrySet()
        .stream()
        .sorted(Comparator.comparing(
            entry -> entry.getKey().getName(), 
            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
        ))
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            Map.Entry::getValue,
            (a, b) -> a,
            LinkedHashMap::new
        ));
    }

    public static List<SectorEntityToken> sortAlphabetically(List<SectorEntityToken> entities) {
        return entities.size() <= 1 ? entities : entities.stream()
            .sorted(Comparator.comparing(
                SectorEntityToken::getName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            ))
            .toList();
    }
}
