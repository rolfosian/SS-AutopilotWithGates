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
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator.StarSystemType;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
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
    private LabelAPI blacklistLabel = null;
    private boolean isPaused;
    private float hiddenSystemsScrollY = 0f;
    private float hiddenGatesScrollY = 0f;

    public AutoPilotGatesAbility() {
        super();
        isShowingEntityPicker = false;
    }

    public static void setShowingEntityPicker(boolean bool) {
        isShowingEntityPicker = bool;
    }

    private void showBlacklistDialog(boolean isAdding) {
        CampaignUIAPI campaignUI = Global.getSector().getCampaignUI();
        if (utils.campaignUIGetDialogType(campaignUI) != null) return;
        utils.campaignUISetDialogType(campaignUI, UiUtil.DIALOG_TYPE_MENU_ENUM);
        UIPanelAPI screenPanel = utils.campaignUIGetScreenPanel(campaignUI);

        setUpDialog(
            isAdding,
            showConfirmDialog(
                screenPanel,
                "graphics/illustrations/dead_gate.jpg",
                "",
                660f,
                100f,
                isAdding ? new DialogDismissedListener() {
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
                        Global.getSector().setPaused(isPaused);
                        utils.campaignUISetDialogType(campaignUI, null);
                        isShowingEntityPicker = false;
                    }
                },
                0,
                3,
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

    private void setUpDialog(boolean isAdding, Object[] dialogComponents, UIPanelAPI screenPanel) {
        List<SectorEntityToken> gates = new ArrayList<>();
        LinkedHashMap<StarSystemAPI, List<SectorEntityToken>> hiddenSystemsToGates = new LinkedHashMap<>();
        refGates(isAdding, gates, hiddenSystemsToGates);
        hiddenSystemsToGates = sortAlphabetically(hiddenSystemsToGates);

        UIPanelAPI innerPanel = (UIPanelAPI) dialogComponents[1];
        ButtonAPI[] buttons = (ButtonAPI[]) dialogComponents[2];
        buttons[0].setShortcut(Keyboard.KEY_G, false);

        if (!gates.isEmpty()) {
            utils.buttonSetListener(buttons[1], new ActionListener() {
                @Override
                public void actionPerformed(Object inputEvent, Object uiElement) {
                    UIPanelAPI entityPickerDialog = showCampaignEntityPicker(
                        screenPanel,
                        entityPickerDialogDismissed,
                        "Choose a Gate",
                        "",
                        "Confirm",
                        Global.getSector().getPlayerFaction(),
                        gates,
                        new BlacklistCampaignEntityPickerListener(isAdding)
                    );
                    if (listener.getEntryGate() != null) {
                        listener.getMaps().add(UiUtil.getMapFromCampaignPickerDialog(entityPickerDialog));
                    }
                    isShowingEntityPicker = true;
                }
            }.getProxy());
        } else {
            buttons[1].setEnabled(false);
            buttons[1].setClickable(false);
        }

        HiddenSystemsTableDialog tablePicker = !hiddenSystemsToGates.isEmpty() ?
            new HiddenSystemsTableDialog(screenPanel, isAdding, hiddenSystemsToGates) : null;

        if (tablePicker != null) {
            utils.buttonSetListener(buttons[2], new ActionListener() {
                @Override
                public void actionPerformed(Object inputEvent, Object uiElement) {
                    tablePicker.show();
                }
            }.getProxy());
        } else {
            buttons[2].setEnabled(false);
            buttons[2].setClickable(false);
        }

        blacklistLabel = Global.getSettings().createLabel(isAdding ? "Choose a gate to add to the blacklist" : "Choose a gate to remove from the blacklist", Fonts.INSIGNIA_VERY_LARGE);
        blacklistLabel.setAlignment(Alignment.MID);
        blacklistLabel.setHighlight("add", "remove");
        blacklistLabel.setHighlightColors(new Color[] {Misc.getHighlightColor(), Misc.getHighlightColor()});
        innerPanel.addComponent((UIComponentAPI)blacklistLabel).inTMid(10f);
    }

    private void refGates(boolean isAdding, List<SectorEntityToken> gates, LinkedHashMap<StarSystemAPI, List<SectorEntityToken>> hiddenSystemsToGates) {
        List<SectorEntityToken> jumPpointsInHyper = Global.getSector().getHyperspace().getJumpPoints();

        if (!isAdding) {
            for (String id : listener.getBlacklist()) {
                SectorEntityToken entity = Global.getSector().getEntityById(id);
                StarSystemAPI system = (StarSystemAPI) entity.getContainingLocation();

                if (!isShowOnHyperspaceMap(system, jumPpointsInHyper)) hiddenSystemsToGates.computeIfAbsent(system, sys -> new ArrayList<>()).add(entity);
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
                    if (!isShowOnHyperspaceMap(system,jumPpointsInHyper)) hiddenSystemsToGates.put(system, foundGates);
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
        private final boolean isAdding;

        public BlacklistCampaignEntityPickerListener(boolean isAdding) {
            super();
            this.isAdding = isAdding;

            Global.getSector().addTransientScript(new BaseEveryFrameScript(true) {
                IntervalUtil interval = new IntervalUtil(0.2f, 0.2f);
                @Override
                public void advance(float arg0) {
                    interval.advance(arg0);

                    if (interval.intervalElapsed()) {
                        String text = isAdding ? "Choose a gate to add to the blacklist" : "Choose a gate to remove from the blacklist";
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

            String labelString = this.isAdding ? 
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

            String text = this.isAdding ? "Choose a gate to add to the blacklist" : "Choose a gate to remove from the blacklist";
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
            return this.isAdding ?
                "Add " + entity.getName() + " in " + entity.getContainingLocation().getNameWithTypeShort() + " to Autopilot's Blacklist?"
                : "Remove " + entity.getName() + " in " + entity.getContainingLocation().getNameWithTypeShort() + " from Autopilot's Blacklist?";
        }
    }

    private class HiddenSystemsTableDialog {
        private final UIPanelAPI screenPanel;
        private final boolean isAdding;
        private final Map<StarSystemAPI, List<SectorEntityToken>> systemsToGates;

        private boolean wasPicked = false;
        private float panelWidth;
        private float panelHeight;

        private UIPanelAPI dialog;
        private UIPanelAPI innerPanel;
        private UITable systemsTable;
        private CustomPanelAPI currSelected;

        private Runnable reselectGate = null;
        private Runnable reselectSystem = null;
        private Runnable reselect = null;

        private final Map<LocationAPI, CustomPanelAPI> systemsToTablePanels = new HashMap<>();
        private final Map<LocationAPI, UITable> systemsToTables = new HashMap<>();
        private final List<Object> systemRows = new ArrayList<>();

        public HiddenSystemsTableDialog(UIPanelAPI screenPanel, boolean isAdding, Map<StarSystemAPI, List<SectorEntityToken>> systemsToGates) {
            this.screenPanel = screenPanel;
            this.isAdding = isAdding;
            this.systemsToGates = systemsToGates;
        }

        public void show() {
            float dialogHeight = Math.min(35f * systemsToGates.size() + 300f, 400f);

            Object[] dialogComponents = showConfirmDialog(
                screenPanel,
                "graphics/illustrations/dead_gate.jpg",
                "",
                560f,
                dialogHeight,
                createDialogDismissedListener(),
                0,
                0,
                "Confirm"
            );

            this.dialog = (UIPanelAPI) dialogComponents[0];
            this.innerPanel = (UIPanelAPI) dialogComponents[1];
            ((ButtonAPI[]) dialogComponents[2])[0].setShortcut(Keyboard.KEY_G, false);

            this.panelWidth = this.innerPanel.getPosition().getWidth() / 2f;
            this.panelHeight = this.innerPanel.getPosition().getHeight() - 10f;

            buildSystemsTable(buildGatesTables());
        }

        private DialogDismissedListener createDialogDismissedListener() {
            return new DialogDismissedListener() {
                @Override
                public void dialogDismissed(Object dialog, int yesOrNo) {
                    if (yesOrNo != 0 || !wasPicked || pickedGate == null) {
                        pickedGate = null;
                        resetBlacklistLabel();
                        return;
                    }
                    applyGateSelection();
                }
            };
        }

        private void resetBlacklistLabel() {
            String text = isAdding ? "Choose a gate to add to the blacklist" : "Choose a gate to remove from the blacklist";
            blacklistLabel.setText(text);
            blacklistLabel.autoSizeToWidth(blacklistLabel.computeTextWidth(text));
            blacklistLabel.setHighlight("add", "remove");
            blacklistLabel.setHighlightColors(new Color[] {Misc.getHighlightColor(), Misc.getHighlightColor()});
        }

        private void applyGateSelection() {
            LocationAPI location = pickedGate.getContainingLocation();
            String locationName = location.getNameWithTypeShort();
            
            String labelString = isAdding 
                ? "Blacklist " + pickedGate.getName() + " in " + locationName + "?"
                : "Remove " + pickedGate.getName() + " in " + locationName + " from blacklist?";

            blacklistLabel.setText(labelString);
            blacklistLabel.autoSizeToWidth(blacklistLabel.computeTextWidth(labelString));

            StarSystemAPI system = (StarSystemAPI) location;
            PlanetAPI star = system.getStar();
            
            Color highlightColor = star != null ? star.getSpec().getIconColor() : system.getLightColor();
            blacklistLabel.setHighlightColor(highlightColor);
            blacklistLabel.setHighlight(locationName);
        }

        private StarSystemAPI buildGatesTables() {
            StarSystemAPI reselect = null;

            Color baseColor = Global.getSettings().getBasePlayerColor();
            Color darkColor = Global.getSettings().getDarkPlayerColor();

            float gatesTableWidth = panelWidth - 10f;
            float gatesTableHeight = panelHeight - 45f;

            for (Map.Entry<StarSystemAPI, List<SectorEntityToken>> entry : systemsToGates.entrySet()) {
                boolean doReselect = false;
                StarSystemAPI system = entry.getKey();
                List<SectorEntityToken> gates = entry.getValue();
                sortAlphabetically(gates);

                CustomPanelAPI panel = Global.getSettings().createCustom(gatesTableWidth + 5f, gatesTableHeight, null);
                TooltipMakerAPI tooltip = panel.createUIElement(gatesTableWidth + 5f, gatesTableHeight, true);
                
                UITable table = (UITable) tooltip.beginTable(
                    baseColor, darkColor, Misc.getHighlightedOptionColor(),
                    30f, true, false, 
                    new Object[]{"Gates", gatesTableWidth}
                );
                systemsToTables.put(entry.getKey(), table);
                
                for (SectorEntityToken gate : gates) {
                    if (gate == pickedGate) {
                        this.reselectGate = createGateRow(tooltip, table, gate, baseColor, true);
                        doReselect = true;
                    } else {
                        createGateRow(tooltip, table, gate, baseColor, false);
                    } 
                }

                tooltip.addTable("", 0, 0f);
                ((UIPanelAPI) table).getPosition().setXAlignOffset(0f).setYAlignOffset(-5f);
                table.setItemsSelectable(true);
                
                panel.addUIElement(tooltip).inTL(0f, 0f);
                systemsToTablePanels.put(system, panel);

                if (doReselect) reselect = system;
            }
            return reselect;
        }

        private Runnable createGateRow(TooltipMakerAPI tooltip, UITable table, SectorEntityToken gate, Color baseColor, boolean doReslect) {
            String gateName = gate.getName();

            Object row = tooltip.addRowWithGlow(baseColor, gateName);
            utils.uiTableRowSetData(row, new Object[] {tooltip, table});

            ButtonAPI rowButton = utils.uiTableRowGetButton(row);
            Object oldListener = utils.buttonGetListener(rowButton);

            utils.buttonSetListener(rowButton, new ActionListener() {
                @Override
                public void actionPerformed(Object inputEvent, Object uiElement) {
                    Object selected = utils.uiTableGetSelected(table);
                    if (selected != null && selected != row) {
                        Object[] params = (Object[]) UiUtil.uiTableRowParamsHandle.get(selected);
                        UiUtil.setRowColorAndText(selected, new Object[] {baseColor, params[1]});
                        utils.uiTableSelect((UITable) ((Object[])utils.uiTableRowGetData(selected))[1], null, null); 
                    }

                    UiUtil.setRowColorAndText(row, new Object[] {TEXT_HIGHLIGHT_COLOR, gateName});
                    utils.uiTableSelect(table, row, null);
                    utils.actionPerformed(oldListener, inputEvent, uiElement);

                    if (!"reselect".equals(inputEvent))
                        hiddenGatesScrollY = tooltip.getExternalScroller().getYOffset();
                    
                    pickedGate = gate;
                    wasPicked = true;
                }
            }.getProxy());

            tooltip.addTooltipToAddedRow(new EntityRowTooltipCreator(gate), TooltipLocation.RIGHT);

            return doReslect ? () -> {
                utils.actionPerformed(utils.buttonGetListener(rowButton), "reselect", rowButton);
                tooltip.getExternalScroller().setYOffset(hiddenGatesScrollY);
                utils.uiPanelFakeAdvance(table, 1f);
            } : null;
        }

        private void buildSystemsTable(StarSystemAPI toReselect) {
            Color baseColor = Global.getSettings().getBasePlayerColor();
            Color darkColor = Global.getSettings().getDarkPlayerColor();
            float systemsTableWidth = panelWidth - 5f;

            CustomPanelAPI panel = Global.getSettings().createCustom(
                systemsTableWidth + 5f, 
                panelHeight, 
                createSystemPanelPlugin(baseColor)
            );

            TooltipMakerAPI tooltip = panel.createUIElement(systemsTableWidth + 5f, panelHeight, true);
            systemsTable = (UITable) tooltip.beginTable(
                baseColor, darkColor, Misc.getHighlightedOptionColor(),
                30f, true, false, 
                new Object[]{"Systems", systemsTableWidth}
            );

            for (StarSystemAPI system : systemsToGates.keySet()) {
                if (system == toReselect) this.reselectSystem = createSystemRow(tooltip, system, baseColor, true);
                else createSystemRow(tooltip, system, baseColor, false);
            }

            tooltip.addTable("", 0, 0f);
            ((UIPanelAPI) systemsTable).getPosition().setXAlignOffset(0f).setYAlignOffset(-5f);
            systemsTable.setItemsSelectable(true);
            panel.addUIElement(tooltip).inTL(0f, 0f);
            innerPanel.addComponent(panel).inTL(5f, 5f);

            if (this.reselectSystem != null) {
                CustomPanelAPI interceptor;
                dialog.addComponent(interceptor = Global.getSettings().createCustom(0f,0f, new BaseCustomUIPanelPlugin() {
                    @Override
                    public void processInput(List<InputEventAPI> events) {
                        for (InputEventAPI e : events) e.consume();
                    }
                }));

                Runnable reselectSys = this.reselectSystem;
                Runnable reselectGato = this.reselectGate;

                this.reselectSystem = null;
                this.reselectGate = null;

                this.reselect = () -> {
                    reselectSys.run();

                    Global.getSector().addTransientScript(new BaseEveryFrameScript(true) {
                        @Override
                        public void advance(float arg0) {
                            reselectGato.run();
                            dialog.removeComponent(interceptor);
                            Global.getSector().removeTransientScript(this);
                        }
                    });
                };
            }

            Global.getSector().addTransientScript(new BaseEveryFrameScript(true) {
                private final IntervalUtil interval = new IntervalUtil(0.2f, 0.2f);
                @Override
                public void advance(float amount) {
                    interval.advance(amount);

                    if (reselect != null) {
                        reselect.run();
                        reselect = null;
                    }

                    if (interval.intervalElapsed()) {
                        resetBlacklistLabel();
                        Global.getSector().removeTransientScript(this);
                    }
                    
                }
            });
        }

        private BaseCustomUIPanelPlugin createSystemPanelPlugin(Color baseColor) {
            return new BaseCustomUIPanelPlugin() {
                @Override
                public void processInput(List<InputEventAPI> events) {
                    for (InputEventAPI event : events) {
                        if (event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
                            handleEscapePress(event, baseColor);
                            break;
                        }
                    }
                }
            };
        }

        private void handleEscapePress(InputEventAPI event, Color baseColor) {
            Object selected = utils.uiTableGetSelected(systemsTable);
            if (selected == null) return;

            UITable selectedGatesTable = systemsToTables.get(utils.uiTableRowGetData(selected));
            if (selectedGatesTable != null) {
                Object selectedGate = utils.uiTableGetSelected(selectedGatesTable);
                if (selectedGate != null) {
                    Object[] params = (Object[]) UiUtil.uiTableRowParamsHandle.get(selectedGate);
                    UiUtil.setRowColorAndText(selectedGate, new Object[] {baseColor, params[1]});
                    utils.uiTableSelect(selectedGatesTable, null, null);
                    utils.uiPanelFakeAdvance(selectedGatesTable, 1f);
                }
            }

            utils.uiTableSelect(systemsTable, null, null);
            List<UIComponentAPI> children = utils.getChildrenNonCopy(innerPanel);
            innerPanel.removeComponent(children.get(children.size() - 1));
            
            wasPicked = false;
            pickedGate = null;
            event.consume();
        }

        private Runnable createSystemRow(TooltipMakerAPI tooltip, StarSystemAPI system, Color baseColor, boolean doReselect) {
            Object row = tooltip.addRowWithGlow(system.getStar() != null ? system.getStar().getSpec().getIconColor() : system.getLightColor(), system.getNameWithTypeShort());
            utils.uiTableRowSetData(row, system);
            tooltip.addTooltipToAddedRow(new SystemRowTooltipCreator(system), TooltipLocation.RIGHT);
            systemRows.add(row);

            ButtonAPI rowButton = utils.uiTableRowGetButton(row);
            Object oldListener = utils.buttonGetListener(rowButton);

            utils.buttonSetListener(rowButton, new ActionListener() {
                @Override
                public void actionPerformed(Object inputEvent, Object uiElement) {
                    Object selected = utils.uiTableGetSelected(systemsTable);
                    if (selected != null) {
                        if (row == selected) {
                            utils.actionPerformed(oldListener, inputEvent, uiElement);
                            return;
                        }

                        UITable selectedGatesTable = systemsToTables.get(utils.uiTableRowGetData(selected));
                        if (selectedGatesTable != null) {
                            Object selectedGate = utils.uiTableGetSelected(selectedGatesTable);
                            if (selectedGate != null) {
                                Object[] params = (Object[]) UiUtil.uiTableRowParamsHandle.get(selectedGate);
                                UiUtil.setRowColorAndText(selectedGate, new Object[] {baseColor, params[1]});
                                utils.uiTableSelect(selectedGatesTable, null, null);
                                utils.uiPanelFakeAdvance(selectedGatesTable, 1f);

                                ScrollPanelAPI scroller = ((TooltipMakerAPI) ((Object[]) utils.uiTableRowGetData(selectedGate))[0]).getExternalScroller();
                                if (scroller != null) scroller.setYOffset(0f);
                            }
                        }
                    }

                    if (!"reselect".equals(inputEvent))
                        hiddenSystemsScrollY = tooltip.getExternalScroller().getYOffset();

                    if (currSelected != null) innerPanel.removeComponent(currSelected);
                    currSelected = systemsToTablePanels.get(system);

                    utils.uiTableSelect(systemsTable, row, null);
                    utils.actionPerformed(oldListener, inputEvent, uiElement);
                    wasPicked = false;
                    pickedGate = null;

                    innerPanel.addComponent(currSelected).inTL(panelWidth + 4f, 5f);
                }
            }.getProxy());

            return doReselect ? () -> {
                utils.actionPerformed(utils.buttonGetListener(rowButton), "reselect", rowButton);
                tooltip.getExternalScroller().setYOffset(hiddenSystemsScrollY);
                utils.uiPanelFakeAdvance(systemsTable, 1f);
            } : null;
        }
    }

    private class BlacklistMessagePlugin extends BaseIntelPlugin {
        private final boolean isAdding;
        private String gateName;
        private String locName;

        public BlacklistMessagePlugin(boolean isAdding, SectorEntityToken pickedGate) {
            this.isAdding = isAdding;
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

            if (this.isAdding) info.addPara(gateName + " in " + locName + " added to the autopilot's gate blacklist.", 0f);
            else info.addPara(gateName + " in " + locName + " removed from the autopilot's gate blacklist.", 0f);
        }
    }

    public static boolean isShowOnHyperspaceMap(StarSystemAPI system, List<SectorEntityToken> jumpPointsInHyper) {
        if (system.getType() == StarSystemType.DEEP_SPACE) return false;

        for (SectorEntityToken e : jumpPointsInHyper) {
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

    public static void sortAlphabetically(List<SectorEntityToken> entities) {
        if (entities.size() <= 1) return;

        entities.sort(
            Comparator.comparing(
                SectorEntityToken::getName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            )
        );
    }
}
