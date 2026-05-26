package data.scripts.autopilotwithgates.util;

import java.util.LinkedList;
import java.util.List;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import org.apache.log4j.Logger;

import com.fs.graphics.Sprite;
import com.fs.graphics.util.Fader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.BaseLocation;
import com.fs.starfarer.campaign.CampaignState;
import com.fs.starfarer.campaign.CampaignUIPersistentData.AbilitySlot;
import com.fs.starfarer.campaign.CampaignUIPersistentData.AbilitySlots;
import com.fs.starfarer.campaign.comms.v2.EventsPanel;
import com.fs.starfarer.campaign.fleet.CampaignFleet;
import com.fs.starfarer.ui.newui.CampaignEntityPickerDialog;

import data.scripts.autopilotwithgates.org.objectweb.asm.*;

public class UiUtil implements Opcodes {
    private static final Logger logger = Logger.getLogger(UiUtil.class);
    public static void print(Object... args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            sb.append(args[i] instanceof String ? (String) args[i] : String.valueOf(args[i]));
            if (i < args.length - 1) sb.append(' ');
        }
        logger.info(sb.toString());
    }

    public static interface UtilInterface {
        public Object interactionDialogGetCore(Object interactionDialog);
        public Object campaignUIgetCore(Object campaignUI);
        public UIPanelAPI coreGetCurrentTab(Object core);

        public UIPanelAPI campaignUIGetScreenPanel(Object campaignUI);
        public Object campaignUIGetDialogType(Object campaignUI);
        public void campaignUISetDialogType(Object campaignUI, Object dialogTypeEnum);
        public UIPanelAPI confirmDialogGetInnerPanel(Object confirmDialog);

        public EventsPanel getEventsPanel(Object intelTab);
        public ButtonAPI intelTabGetPlanetsButton(Object intelTab);
        public UIPanelAPI intelTabGetPlanetsPanel(Object intelTab);

        public UIPanelAPI eventsPanelGetMap(EventsPanel eventsPanel);
        public UIPanelAPI mapTabGetMap(Object mapTab);

        public BaseLocation mapGetLocation(UIPanelAPI map);
        public UIPanelAPI mapGetMapTab(UIPanelAPI map);
        public boolean isRadarMode(UIPanelAPI map);
        public Object getZoomTracker(UIPanelAPI map);
        public float getFactor(UIPanelAPI map);

        public float getMaxZoomFactor(Object zoomTracker);
        public float getZoomLevel(Object zoomTracker);

        public Object getMessageDisplay(Object campaignUI);
        public Object getCourseWidget(Object campaignUI);
        public SectorEntityToken getNextStep(Object courseWidget, SectorEntityToken target);
        public Fader getInner(Object courseWidget);
        public float getPhase(Object courseWidget);

        public void actionPerformed(Object listener, Object inputEvent, Object uiElement);

        public void buttonSetListener(Object button, Object listener);
        public Object buttonGetListener(Object button);
        
        public Object uiComponentGetTooltip(Object uiComponent);
        public void uiComponentShowTooltip(Object uiComponent, Object tooltip);
        public void uiComponentHideTooltip(Object uiComponent, Object tooltip);

        public List<UIComponentAPI> getChildrenNonCopy(UIPanelAPI uiPanel);
        public List<UIComponentAPI> getChildrenNonCopy(UIComponentAPI parent); // custom method with instanceof check uiPanelClass else return null

    }

    // With this we can implement the above interface and generate a class at runtime to call obfuscated class methods platform agnostically without reflection overhead
    private static Class<?>[] implementUtilInterface(Class<?> coreClass, Class<?> abilityPanelClass, Class<?> actionListenerInterface) {
        String coreClassInternalName = Type.getInternalName(coreClass);

        Class<?> interactionDialogClass = Refl.getFieldType(Refl.getFieldByName("encounterDialog", CampaignState.class));

        Class<?> courseWidgetClass = Refl.getReturnType(Refl.getMethod("getCourseWidget", CampaignState.class));
        String courseWidgetInternalName = Type.getInternalName(courseWidgetClass);

        Class<?> messageDisplayClass = Refl.getFieldType(Refl.getFieldByName("messageDisplay", CampaignState.class));

        Class<?> mapTabClass = Refl.getReturnType(Refl.getMethod("getMap", EventsPanel.class));
        String mapTabInternalName = Type.getInternalName(mapTabClass);

        Class<?> mapClass = Refl.getReturnType(Refl.getMethod("getMap", mapTabClass));
        String mapClassInternalName = Type.getInternalName(mapClass);

        Class<?> uiPanelClass = mapClass.getSuperclass();
        Class<?> uiComponentClass = uiPanelClass.getSuperclass();
        Class<?> toolTipClass = Refl.getReturnType(Refl.getMethod("getTooltip", uiComponentClass));
        String uiPanelInternalName = Type.getInternalName(uiPanelClass);
        String uiComponentInternalName = Type.getInternalName(uiComponentClass);
        String confirmDialogInternalName = Type.getInternalName(CampaignEntityPickerDialog.class.getSuperclass());

        Class<?> buttonClass = Refl.getFieldType(Refl.getFieldByInterface(ButtonAPI.class, EventsPanel.class));

        String buttonClassInternalName = Type.getInternalName(buttonClass);
        String actionListenerInterfaceDesc = Type.getDescriptor(actionListenerInterface);
        String actionListenerInterfaceInternalName = Type.getInternalName(actionListenerInterface);

        Class<?> intelTabClass = Refl.getReturnType(Refl.getMethod("getIntelTab", EventsPanel.class));
        String intelTabInternalName = Type.getInternalName(intelTabClass);
        Class<?> intelTabPlanetsPanelClass = Refl.getReturnType(Refl.getMethod("getPlanetsPanel", intelTabClass));

        Class<?> zoomTrackerClass = Refl.getReturnType(Refl.getMethod("getZoomTracker", mapClass));
        String[] zoomTrackerMethodNames = getZoomTrackerMethodNames(zoomTrackerClass);
        
        String superName = Type.getType(Object.class).getInternalName();
        String interfaceName = Type.getType(UtilInterface.class).getInternalName();

        String coreClassDesc = Type.getDescriptor(coreClass);
        String uiPanelDesc = Type.getDescriptor(uiPanelClass);
        String mapTabDesc = Type.getDescriptor(mapTabClass);
        String sectorEntityTokenDesc = Type.getDescriptor(SectorEntityToken.class);
        String uiPanelAPIDesc = Type.getDescriptor(UIPanelAPI.class);
        String buttonAPIDesc = Type.getDescriptor(ButtonAPI.class);
        String buttonClassDesc = Type.getDescriptor(buttonClass);
        String eventsPanelDesc = Type.getDescriptor(EventsPanel.class);
        String baseLocationDesc = Type.getDescriptor(BaseLocation.class);
        String tooltipDesc = Type.getDescriptor(toolTipClass);
        String messageDisplayDesc = Type.getDescriptor(messageDisplayClass);

        String campaignStateInternalName = Type.getInternalName(CampaignState.class);

        Class<?> campaignStateDialogTypeEnumClass = Refl.getMethodParamTypes(Refl.getMethod("setDialogType", CampaignState.class))[0];
        String campaignStateDialogTypeInternalName = Type.getInternalName(campaignStateDialogTypeEnumClass);
        String campaignStateDialogTypeDesc = Type.getDescriptor(campaignStateDialogTypeEnumClass);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        // public class UtilInterface extends Object implements this crap
        cw.visit(
            V17,
            ACC_PUBLIC,
            "data/scripts/autopilotwithgates/util/UtilInterface",
            null,
            superName,
            new String[] {interfaceName}
        );

        MethodVisitor ctor = cw.visitMethod(
            ACC_PUBLIC,
            "<init>",
            "()V",
            null,
            null
        );
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false);
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        // public Object interactionDialogGetCore(Object interactionDialog) {
        //     return ((interactionDialogClass)interactionDialog).getCoreUI();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "interactionDialogGetCore",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null,
                null
            );
            mv.visitCode();
            String interactionDialogInternalName = Type.getInternalName(interactionDialogClass);

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, interactionDialogInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                interactionDialogInternalName,
                "getCoreUI",
                "()" + coreClassDesc,
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public Object campaignUIgetCore(Object campaignUI) {
        //     return ((CampaignState)campaignUI).getCore();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "campaignUIgetCore",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, campaignStateInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                campaignStateInternalName,
                "getCore",
                "()" + coreClassDesc,
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public UIPanelAPI coreGetCurrentTab(Object core) {
        //     return ((coreClass)core).getCurrentTab();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "coreGetCurrentTab",
                "(Ljava/lang/Object;)" + uiPanelAPIDesc,
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, coreClassInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                coreClassInternalName,
                "getCurrentTab",
                "()" + uiPanelDesc,
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public UIPanelAPI intelTabGetPlanetsPanel(Object intelTab) {
        //     return ((intelTabClass)intelTab).getPlanetsPanel();
        // }
        {   
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "intelTabGetPlanetsPanel",
                "(Ljava/lang/Object;)" + uiPanelAPIDesc,
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, intelTabInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                intelTabInternalName,
                "getPlanetsPanel",
                "()" + Type.getDescriptor(intelTabPlanetsPanelClass),
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public ButtonAPI intelTabGetPlanetsButton(Object intelTab) {
        //     return ((intelTabClass)intelTab).getPlanetsButton();
        // }
        {   
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "intelTabGetPlanetsButton",
                "(Ljava/lang/Object;)" + buttonAPIDesc,
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, intelTabInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                intelTabInternalName,
                "getPlanetsButton",
                "()" + buttonClassDesc,
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public EventsPanel getEventsPanel(Object intelTab) {
        //     return ((intelTabClass)intelTab).getEventsPanel();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getEventsPanel",
                "(Ljava/lang/Object;)" + eventsPanelDesc,
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(intelTabClass));

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(intelTabClass),
                "getEventsPanel",
                "()" + eventsPanelDesc,
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public UIPanelAPI eventsPanelGetMap(EventsPanel eventsPanel) {
        //     return ((EventsPanel)eventsPanel).getMap();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "eventsPanelGetMap",
                "(" + eventsPanelDesc + ")" + uiPanelAPIDesc,
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(EventsPanel.class),
                "getMap",
                "()" + mapTabDesc,
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public UIPanelAPI mapTabGetMap(Object mapTab) {
        //     return ((mapTabClass)mapTab).getMap();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "mapTabGetMap",
                "(Ljava/lang/Object;)" + uiPanelAPIDesc,
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, mapTabInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                mapTabInternalName,
                "getMap",
                "()" + Type.getDescriptor(mapClass),
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public BaseLocation mapGetLocation(UIPanelAPI map) {
        //     return ((mapClass)map).getLocation();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "mapGetLocation",
                "(" + uiPanelAPIDesc + ")" + baseLocationDesc,
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);

            mv.visitTypeInsn(CHECKCAST, mapClassInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                mapClassInternalName,
                "getLocation",
                "()" + baseLocationDesc,
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public boolean isRadarMode(UIPanelAPI map) {
        //     return ((mapClass)map).isRadarMode();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "isRadarMode",
                "(" + uiPanelAPIDesc + ")Z",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, mapClassInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                mapClassInternalName,
                "isRadarMode",
                "()Z",
                false
            );

            mv.visitInsn(IRETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public Object getZoomTracker(UIPanelAPI map) {
        //     return ((mapClass)map).getZoomTracker();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getZoomTracker",
                "(" + uiPanelAPIDesc + ")Ljava/lang/Object;",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, mapClassInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                mapClassInternalName,
                "getZoomTracker",
                "()" + Type.getDescriptor(zoomTrackerClass),
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public float getFactor(UIPanelAPI map) {
        //     return ((mapClass)map).getFactor();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getFactor",
                "(" + uiPanelAPIDesc + ")F",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, mapClassInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                mapClassInternalName,
                "getFactor",
                "()F",
                false
            );

            mv.visitInsn(FRETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public float getZoomLevel(Object zoomTracker) {
        //     return ((zoomTrackerClass)zoomTracker).zoomLevelMethodName();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getZoomLevel",
                "(Ljava/lang/Object;)F",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(zoomTrackerClass));

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(zoomTrackerClass),
                zoomTrackerMethodNames[0],
                "()F",
                false
            );

            mv.visitInsn(FRETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public float getMaxZoomFactor(Object zoomTracker) {
        //     return ((zoomTrackerClass)zoomTracker).getMaxZoomFactorMethodName();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getMaxZoomFactor",
                "(Ljava/lang/Object;)F",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(zoomTrackerClass));

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(zoomTrackerClass),
                zoomTrackerMethodNames[1],
                "()F",
                false
            );

            mv.visitInsn(FRETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public UIPanelAPI confirmDialogGetInnerPanel(Object confirmDialog) {
        //     return ((ConfirmDialogClass)confirmDialog).getInnerPanel();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "confirmDialogGetInnerPanel",
                "(Ljava/lang/Object;)" + uiPanelAPIDesc,
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, confirmDialogInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                confirmDialogInternalName,
                "getInnerPanel",
                "()" + uiPanelDesc,
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public UIPanelAPI campaignUIGetScreenPanel(Object campaignUI) {
        //     return ((CampaignState)campaignUI).getScreenPanel();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "campaignUIGetScreenPanel",
                "(Ljava/lang/Object;)" + uiPanelAPIDesc,
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, campaignStateInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                campaignStateInternalName,
                "getScreenPanel",
                "()" + uiPanelDesc,
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public Object campaignUIGetDialogType(Object campaignUI) {
        //     return ((CampaignState)campaignUI).getDialogType();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "campaignUIGetDialogType",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, campaignStateInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                campaignStateInternalName,
                "getDialogType",
                "()" + campaignStateDialogTypeDesc,
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public void campaignUISetDialogType(Object campaignUI, Object dialogType) {
        //    ((CampaignState)campaignUI).setDialogType(dialogType);
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "campaignUISetDialogType",
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, campaignStateInternalName);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitTypeInsn(CHECKCAST, campaignStateDialogTypeInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                campaignStateInternalName,
                "setDialogType",
                "(" + campaignStateDialogTypeDesc + ")V",
                false
            );

            mv.visitInsn(RETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public Object getMessageDisplay(Object campaignUI) {
        //     return ((CampaignState)campaignUI).getMessageDisplay();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getMessageDisplay",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, campaignStateInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                campaignStateInternalName,
                "getMessageDisplay",
                "()" + messageDisplayDesc,
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public Object getCourseWidget(Object campaignUI) {
        //     return ((CampaignState)campaignUI).getCourseWidget();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getCourseWidget",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, campaignStateInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                campaignStateInternalName,
                "getCourseWidget",
                "()" + Type.getDescriptor(courseWidgetClass),
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public SectorEntityToken getNextStep(Object courseWidget, SectorEntityToken target) {
        //     return ((courseWidgetClass)courseWidget).getNextStep(target);
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getNextStep",
                "(" +
                    "Ljava/lang/Object;" +
                    sectorEntityTokenDesc +
                ")" +
                sectorEntityTokenDesc,
                null,
                null
            );
            mv.visitCode();
        
            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, courseWidgetInternalName);
        
            mv.visitVarInsn(ALOAD, 2);
        
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                courseWidgetInternalName,
                "getNextStep",
                "(" + sectorEntityTokenDesc + ")" + sectorEntityTokenDesc,
                false
            );
        
            mv.visitInsn(ARETURN);
        
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public Fader getInner(Object courseWidget) {
        //     return ((courseWidgetClass)courseWidget).getFader();
        // }
        {
            String faderDesc = Type.getDescriptor(Fader.class);
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getInner",
                "(Ljava/lang/Object;)" + faderDesc,
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, courseWidgetInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                courseWidgetInternalName,
                "getInner",
                "()" + faderDesc,
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public float getPhase(Object courseWidget) {
        //     return ((courseWidgetClass)courseWidget).getPhase();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getPhase",
                "(Ljava/lang/Object;)F",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, courseWidgetInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                courseWidgetInternalName,
                "getPhase",
                "()F",
                false
            );

            mv.visitInsn(FRETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public void actionPerformed(Object listener, Object inputEvent, Object uiElement) {
        //     ((actionListenerInterface)listener).actionPerformed(inputEvent, uiElement);
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "actionPerformed",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, actionListenerInterfaceInternalName);

            mv.visitVarInsn(ALOAD, 2);
            mv.visitVarInsn(ALOAD, 3);

            mv.visitMethodInsn(
                INVOKEINTERFACE,
                actionListenerInterfaceInternalName,
                "actionPerformed",
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                true // interface method
            );

            mv.visitInsn(RETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public void buttonSetListener(Object button, Object listener) {
        //     ((buttonClass)button).setListener(listener);
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "buttonSetListener",
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, buttonClassInternalName);

            mv.visitVarInsn(ALOAD, 2);
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                buttonClassInternalName,
                "setListener",
                "(" + actionListenerInterfaceDesc + ")V",
                false
            );

            mv.visitInsn(RETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public Object buttonGetListener(Object button) {
        //     ((buttonClass)button).getListener();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "buttonGetListener",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, buttonClassInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                buttonClassInternalName,
                "getListener",
                "()" + actionListenerInterfaceDesc,
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public Object uiComponentGetTooltip(Object uiComponent) {
        //     ((uiComponentClass)uiComponent).getTooltip();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "uiComponentGetTooltip",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, uiComponentInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                uiComponentInternalName,
                "getTooltip",
                "()" + tooltipDesc,
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public Object uiComponentShowTooltip(Object uiComponent, Object tooltip) {
        //     ((uiComponentClass)uiComponent).showTooltip();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "uiComponentShowTooltip",
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, uiComponentInternalName);

            mv.visitVarInsn(ALOAD, 2);
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                uiComponentInternalName,
                "showTooltip",
                "(Ljava/lang/Object;)V",
                false
            );

            mv.visitInsn(RETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public Object uiComponentHideTooltip(Object button, Object tooltip) {
        //     ((uiComponentClass)uiComponent).hideTooltip();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "uiComponentHideTooltip",
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, uiComponentInternalName);

            mv.visitVarInsn(ALOAD, 2);
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                uiComponentInternalName,
                "hideTooltip",
                "(Ljava/lang/Object;)V",
                false
            );

            mv.visitInsn(RETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public List<UIComponentAPI> getChildrenNonCopy(UIComponentAPI parent) {
        //     if (parent instanceof uiPanelClass) {
        //         return ((uiPanelClass)parent).getChildrenNonCopy();
        //     }
        //     return null;
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getChildrenNonCopy",
                "(" + Type.getDescriptor(UIComponentAPI.class) + ")Ljava/util/List;",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            
            mv.visitTypeInsn(INSTANCEOF, uiPanelInternalName);
            
            Label ifInstanceOf = new Label();
            mv.visitJumpInsn(IFNE, ifInstanceOf);
            
            mv.visitInsn(ACONST_NULL);
            mv.visitInsn(ARETURN);
            
            mv.visitLabel(ifInstanceOf);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, uiPanelInternalName);
            
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                uiPanelInternalName,
                "getChildrenNonCopy",
                "()Ljava/util/List;",
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // public List<UIComponentAPI> getChildrenNonCopy(Object uiPanel) {
        //     return ((uiPanelClass)uiPanel).getChildrenNonCopy();
        // }
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getChildrenNonCopy",
                "(" + uiPanelAPIDesc + ")Ljava/util/List;",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, uiPanelInternalName);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                uiPanelInternalName,
                "getChildrenNonCopy",
                "()Ljava/util/List;",
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        
        cw.visitEnd();
        return new Class<?>[] {
            new ClassLoader(UiUtil.class.getClassLoader()) {
                public Class<?> define(byte[] classBytes, String name) {
                    return defineClass(name, classBytes, 0, classBytes.length);
                }
            }.define(cw.toByteArray(), "data.scripts.autopilotwithgates.util.UtilInterface"),
            mapClass,
            uiPanelClass,
            uiComponentClass,
            messageDisplayClass,
            intelTabPlanetsPanelClass,
            mapTabClass,
            campaignStateDialogTypeEnumClass
        };
    }

    public static final UtilInterface utils;
    public static final Class<?> mapClass;
    public static final Class<?> uiPanelClass;
    public static final Class<?> uiComponentClass;

    private static final VarHandle followMouseVarHandle;
    private static final VarHandle messageDisplayListVarHandle;
    private static final VarHandle coreUIAbilityPanelVarHandle;
    private static final VarHandle abilitySlotsVarHandle;
    private static final VarHandle intelTabPlanetsPanelMapHandle;
    private static final VarHandle campaignEntityPickerDialogMapHandle;
    private static final VarHandle campaignFleetArrowHandle;

    public static final Object DIALOG_TYPE_MENU_ENUM;

    // public static final VarHandle isOnAbilityBarHandle;
    public static final VarHandle abilityPanelPrevButtonHandle;
    public static final VarHandle abilityPanelNextButtonHandle;

    private static final CallSite actionListenerCallSite;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        try {
            Class<?> coreClass = Refl.getReturnType(Refl.getMethod("getCore", CampaignState.class));
            String abilityPanelFieldName = getAbilityPanelFieldName(coreClass);

            Class<?> abilityPanelClass = Refl.getFieldType(Refl.getFieldByName(abilityPanelFieldName, coreClass));
            Class<?> actionListenerInterface = abilityPanelClass.getInterfaces()[0];
            // Class<?> abilityTooltipClass = getAbilityTooltipClass(abilityPanelClass);

            // VarHandle handle = null;
            // for (Object field : abilityTooltipClass.getDeclaredFields()) {
            //     if (Refl.getFieldType(field) == boolean.class) {
            //         handle = MethodHandles.privateLookupIn(abilityTooltipClass, MethodHandles.lookup()).findVarHandle(
            //             abilityTooltipClass,
            //             Refl.getFieldName(field),
            //             boolean.class
            //         );
            //     }
            // }
            // isOnAbilityBarHandle = handle;
            
            Class<?>[] result = implementUtilInterface(coreClass, abilityPanelClass, actionListenerInterface);
            utils = (UtilInterface) Refl.instantiateClass(result[0].getConstructors()[0]);

            mapClass = result[1];
            uiPanelClass = result[2];
            uiComponentClass = result[3];

            Class<?> messageDisplayClass = result[4];
            messageDisplayListVarHandle = MethodHandles.privateLookupIn(messageDisplayClass, lookup).findVarHandle(
                messageDisplayClass,
                Refl.getFieldName(Refl.getFieldByType(LinkedList.class, messageDisplayClass)),
                LinkedList.class
            );

            Class<?> intelTabPlanetsPanelClass = result[5];
            Class<?> mapTabClass = result[6];
            intelTabPlanetsPanelMapHandle = MethodHandles.privateLookupIn(intelTabPlanetsPanelClass, lookup).findVarHandle(
                intelTabPlanetsPanelClass,
                Refl.getFieldName(Refl.getFieldByType(mapTabClass, intelTabPlanetsPanelClass)),
                mapTabClass
            );

            Object targetEnum = null;
            for (Object e : result[7].getEnumConstants()) {
                if (String.valueOf(e).equals("MENU")) {
                    targetEnum = e;
                    break;
                }
            }
            DIALOG_TYPE_MENU_ENUM = targetEnum;

            campaignFleetArrowHandle = MethodHandles.privateLookupIn(CampaignFleet.class, lookup).findVarHandle(
                CampaignFleet.class,
                "arrow",
                Sprite.class
            );

            campaignEntityPickerDialogMapHandle = MethodHandles.privateLookupIn(CampaignEntityPickerDialog.class, lookup).findVarHandle(
                CampaignEntityPickerDialog.class,
                "map",
                mapTabClass
            );

            coreUIAbilityPanelVarHandle = MethodHandles.privateLookupIn(coreClass, lookup).findVarHandle(
                coreClass,
                abilityPanelFieldName,
                abilityPanelClass
            );

            followMouseVarHandle = MethodHandles.privateLookupIn(CampaignState.class, lookup).findVarHandle(
                CampaignState.class,
                "followMouse",
                boolean.class
            );

            abilitySlotsVarHandle = MethodHandles.privateLookupIn(AbilitySlots.class, lookup).findVarHandle(
                AbilitySlots.class,
                "slots",
                AbilitySlot[][].class
            );

            String[] buttonFieldNames = getPrevNextButtonFieldNames(abilityPanelClass);
            MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(abilityPanelClass, lookup);

            String prevButtonName = buttonFieldNames[7];
            String nextButtonName = buttonFieldNames[8];
            VarHandle prevHandle = null;
            VarHandle nextHandle = null;
            for (Object field : abilityPanelClass.getDeclaredFields()) {
                String name = Refl.getFieldName(field);

                if (name.equals(prevButtonName)) {
                    prevHandle = privateLookup.findVarHandle(
                        abilityPanelClass,
                        name,
                        Refl.getFieldType(field)
                    );
                } else if (name.equals(nextButtonName)) {
                    nextHandle = privateLookup.findVarHandle(
                        abilityPanelClass,
                        name,
                        Refl.getFieldType(field)
                    );
                }
            }
            abilityPanelPrevButtonHandle = prevHandle;
            abilityPanelNextButtonHandle = nextHandle;
            
            MethodType factoryType = MethodType.methodType(actionListenerInterface, ActionListenerProxy.class);
            MethodType actualSamMethodType = MethodType.methodType(void.class, Object.class, Object.class);
            MethodType implSignature = MethodType.methodType(void.class, Object.class, Object.class);
            MethodHandle implementationMethodHandle = lookup.findVirtual(ActionListenerProxy.class, "actionPerformed", implSignature);

            actionListenerCallSite = LambdaMetafactory.metafactory(
                lookup,
                "actionPerformed",
                factoryType,
                actualSamMethodType,
                implementationMethodHandle,
                actualSamMethodType
            );

            CampaignEntityPickerInstantiator.init();

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getCore(Object campaignUI, Object interactionDialog) {
        return interactionDialog == null ? utils.campaignUIgetCore(campaignUI) : utils.interactionDialogGetCore(interactionDialog);
    }

    public static UIPanelAPI getAbilityPanel(Object coreUI) {
        return (UIPanelAPI) coreUIAbilityPanelVarHandle.get(coreUI);
    }

    public static List<Object> getMessageDisplayList(CampaignUIAPI campaignUI) {
        return (List<Object>) messageDisplayListVarHandle.get(utils.getMessageDisplay(campaignUI));
    }

    public static UIPanelAPI getMapFromIntelTab(Object intelTab) {
        EventsPanel eventsPanel = utils.getEventsPanel(intelTab);
        Object outerMap = utils.eventsPanelGetMap(eventsPanel);
        return utils.mapTabGetMap(outerMap);
    }

    public static UIPanelAPI getMapFromCampaignPickerDialog(Object campaignPickerDialog) {
        return utils.mapTabGetMap(campaignEntityPickerDialogMapHandle.get(campaignPickerDialog));
    }

    public static UIPanelAPI getIntelTabPlanetsPanelMap(UIPanelAPI planetsPanel) {
        Object mapParent = intelTabPlanetsPanelMapHandle.get(planetsPanel);
        return mapParent == null ? null : utils.mapTabGetMap(mapParent);
    }

    public static void setFollowMouseTrue(CampaignUIAPI campaignUI) {
        ((CampaignState)campaignUI).followEntity(null, true);
        Global.getSector().getPlayerFleet().setInteractionTarget(null);

        followMouseVarHandle.set(campaignUI, true);
    }

    public static void setFleetArrow(CampaignFleetAPI fleet, Sprite arrow) {
        campaignFleetArrowHandle.set(fleet, arrow);
    }

    public static Sprite getFleetArrow(CampaignFleetAPI fleet) {
        return (Sprite) campaignFleetArrowHandle.get(fleet);
    }

    public static void followEntity(CampaignUIAPI campaignUI, SectorEntityToken entity) {
        ((CampaignState)campaignUI).followEntity(entity, true);
    }

    public static void setAbilitySlots(AbilitySlots abilitySlots, AbilitySlot[][] slots) {
        abilitySlotsVarHandle.set(abilitySlots, slots);
    }

    public static boolean isInBounds(PositionAPI pos, float mouseX, float mouseY) {
        float leftBound = pos.getCenterX() - pos.getWidth() / 2;
        float rightBound = pos.getCenterX() + pos.getWidth() / 2;
        float topBound = pos.getCenterY() - pos.getHeight() / 2;
        float bottomBound = pos.getCenterY() + pos.getHeight() / 2;

        return mouseX >= leftBound && mouseX <= rightBound &&
               mouseY >= topBound && mouseY <= bottomBound;
    }

    public static final BaseIntelPlugin unlockedMessagePlugin = new BaseIntelPlugin() {
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
            info.addPara("Autopilot With Gates ability unlocked", 0f);
        }
    };

    public static final BaseIntelPlugin disabledMessagePlugin = new BaseIntelPlugin() {
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
            info.addPara("Autopilot gate jump was aborted. Autopilot With Gates ability disabled.", 0f);
        }
    };

    public static void setButtonHook(ButtonAPI button, Runnable runBefore, Runnable runAfter) {
        Object oldListener = utils.buttonGetListener(button);
        utils.buttonSetListener(button, new ActionListener() {
            @Override
            public void actionPerformed(Object inputEvent, Object uiElement) {
                runBefore.run();
                utils.actionPerformed(oldListener, inputEvent, uiElement);
                runAfter.run();
            }
        }.getProxy());
    }

    public static abstract class ActionListener {
        private final Object listener;
    
        public ActionListener() {
            try {
                listener = actionListenerCallSite.getTarget().invoke(new ActionListenerProxy(this));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    
        public abstract void actionPerformed(Object inputEvent, Object uiElement);
        
        public final Object getProxy() {
            return listener;
        }
    }

    @FunctionalInterface
    private static interface DummyActionListenerInterface {
        public void actionPerformed(Object arg0, Object arg1);
    }

    private static class ActionListenerProxy implements DummyActionListenerInterface {
        private final ActionListener proxyTriggerClassInstance;

        public ActionListenerProxy(ActionListener proxyTriggerClassInstance) {
            this.proxyTriggerClassInstance = proxyTriggerClassInstance;
        }

        @Override
        public void actionPerformed(Object arg0, Object arg1) {
            proxyTriggerClassInstance.actionPerformed(arg0, arg1);
        }
    }

    private static int computeBufferSize(Object inputStream, Object inputStreamAvailableMethod) {
        try {
            int expectedLength = (int) Refl.invokeMethodDirectly(inputStreamAvailableMethod, inputStream);

            if (expectedLength < 256) {
              return 4096;
            }
            return Math.min(expectedLength, 1024 * 1024);

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
      }

    private static byte[] readStream(Object inputStream) {
        try {
            Class<?> baosClass = Class.forName("java.io.ByteArrayOutputStream", false, Class.class.getClassLoader());
            Class<?> inputStreamClass = Class.forName("java.io.InputStream", false, Class.class.getClassLoader());

            Object baosCtor = Refl.getConstructor(baosClass, new Class<?>[0]);
            Object baosWriteMethod = Refl.getMethodExplicit("write", baosClass, new Class<?>[]{byte[].class, int.class, int.class});
            Object outputStreamFlushMethod = Refl.getMethod("flush", baosClass.getSuperclass());
            Object baosToByteArrayMethod = Refl.getMethod("toByteArray", baosClass);
    
            Object inputStreamAvailableMethod = Refl.getMethod("available", inputStreamClass);
            Object inputStreamReadMethod = Refl.getMethodExplicit("read", inputStreamClass, new Class<?>[] {byte[].class, int.class, int.class});

            int bufferSize = computeBufferSize(inputStream, inputStreamAvailableMethod);
            Object outputStream = Refl.instantiateClass(baosCtor);

            byte[] data = new byte[bufferSize];
            int bytesRead;
            int readCount = 0;

            while ((bytesRead = (int) Refl.invokeMethodDirectly(inputStreamReadMethod, inputStream, data, 0, bufferSize)) != -1) {
                Refl.invokeMethodDirectly(baosWriteMethod, outputStream, data, 0, bytesRead);
                readCount++;
            }
            
            Refl.invokeMethodDirectly(outputStreamFlushMethod, outputStream);
            if (readCount == 1) {
                return data;
            }
            return (byte[]) Refl.invokeMethodDirectly(baosToByteArrayMethod, outputStream);

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static String getAbilityPanelFieldName(Class<?> coreClass) {
        Object inputStream = Refl.getMethodAndInvokeDirectly(
            "getResourceAsStream",
            coreClass.getClassLoader(),
            coreClass.getCanonicalName().replace(".", "/") + ".class"
        );

        ClassReader cr = new ClassReader(readStream(inputStream));
        final String[] foundName = {null};

        cr.accept(new ClassVisitor(ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
    
                if (!desc.equals("()V") || !name.equals("showAbilityBar") || foundName[0] != null) return null;
    
                return new MethodVisitor(ASM9) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fld, String fldDesc) {
                        if (opcode == GETFIELD && foundName[0] == null) foundName[0] = fld;
                    }
                };
            }
        }, 0);

        return foundName[0];
    }

    private static String[] getPrevNextButtonFieldNames(Class<?> abilityPanelClass) {
        Object inputStream = Refl.getMethodAndInvokeDirectly(
            "getResourceAsStream",
            abilityPanelClass.getClassLoader(),
            abilityPanelClass.getCanonicalName().replace(".", "/") + ".class"
        );

        ClassReader cr = new ClassReader(readStream(inputStream));
        final String[] fields = new String[9];

        cr.accept(new ClassVisitor(ASM9) {
            int getFields = 0;

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                if (!name.equals("actionPerformed")) return null;
    
                return new MethodVisitor(ASM9) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fld, String fldDesc) {
                        if (opcode == GETFIELD) {
                            fields[getFields++] = fld;
                        }
                    }
                };
            }
        }, 0);

        return fields;
    }

    private static String[] getZoomTrackerMethodNames(Class<?> zoomTrackerClass) {
        Object inputStream = Refl.getMethodAndInvokeDirectly(
            "getResourceAsStream",
            zoomTrackerClass.getClassLoader(),
            zoomTrackerClass.getCanonicalName().replace(".", "/") + ".class"
        );
        byte[] stream = readStream(inputStream);
        final String[] foundNames = {null, null};

        ClassReader cr = new ClassReader(stream);
        final String[] maxZoomFactorFieldName = {null};

        cr.accept(new ClassVisitor(ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                if (!desc.equals("()F")) return null;

                return new MethodVisitor(ASM9) {
                    int fieldGets = 0;
                    int fcmps = 0;
                    int fReturns = 0;

                    String lastFieldName;
                    String secondCompareField;
    
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fld, String fldDesc) {
                        if (opcode == GETFIELD && fldDesc.equals("F")) {
                            fieldGets++;
                            lastFieldName = fld;
                        }
                    }
    
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == FCMPG || opcode == FCMPL) {
                            fcmps++;
                            if (fcmps == 2) {
                                secondCompareField = lastFieldName;
                            }
                        }
                        if (opcode == FRETURN) {
                            fReturns++;
                        }
                    }
    
                    @Override
                    public void visitEnd() {
                        if (fieldGets >= 3 && fcmps >= 2 && fReturns == 1) {
                            foundNames[0] = name;
                            maxZoomFactorFieldName[0] = secondCompareField;
                        }
                    }
                };
            }
        }, 0);

        cr.accept(new ClassVisitor(ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                if (!desc.equals("()F") || access != ACC_PUBLIC) return null;

                return new MethodVisitor(ASM9) {
                    int fieldGets = 0;
                    int fReturns = 0;
                    int visitFieldInsns = 0;
                    int visitMethodInsns = 0;

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        visitMethodInsns++;
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fld, String fldDesc) {
                        visitFieldInsns++;
                        if (opcode == GETFIELD && fldDesc.equals("F") && fld.equals(maxZoomFactorFieldName[0])) {
                            fieldGets++;
                        }
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == FRETURN) {
                            fReturns++;
                        }
                    }

                    @Override
                    public void visitEnd() {
                        if (fieldGets == 1 && fReturns == 1 && visitFieldInsns == 1 & visitMethodInsns == 0) {
                            foundNames[1] = name;
                        }
                    }
                };
            }
        }, 0);

        return foundNames;
    }

    private static final int TABLE_SIZE = (int)Math.sqrt(1048576.0);
    private static final float INV_TABLE_RANGE;
    private static final float[] ATAN2_LOOKUP;
    
    static {
        INV_TABLE_RANGE = 1.0F / (float)(TABLE_SIZE - 1);
        ATAN2_LOOKUP = new float[1048576];
    
        for (int xIndex = 0; xIndex < TABLE_SIZE; ++xIndex) {
            for (int yIndex = 0; yIndex < TABLE_SIZE; ++yIndex) {
                float normalizedX = (float)xIndex / (float)TABLE_SIZE;
                float normalizedY = (float)yIndex / (float)TABLE_SIZE;
                ATAN2_LOOKUP[yIndex * TABLE_SIZE + xIndex] = (float)Math.atan2((double)normalizedY, (double)normalizedX);
            }
        }
    }

    public static final float atan2(float y, float x) {
        float angleOffset;
        float signMultiplier;
    
        if (x < 0.0F) {
            if (y < 0.0F) {
                x = -x;
                y = -y;
                signMultiplier = 1.0F;
            } else {
                x = -x;
                signMultiplier = -1.0F;
            }
            angleOffset = -3.1415927F;
        } else {
            if (y < 0.0F) {
                y = -y;
                signMultiplier = -1.0F;
            } else {
                signMultiplier = 1.0F;
            }
            angleOffset = 0.0F;
        }
    
        float scaleFactor = 1.0F / ((x < y ? y : x) * INV_TABLE_RANGE);
        int xLookupIndex = (int)(x * scaleFactor);
        int yLookupIndex = (int)(y * scaleFactor);
        int lookupIndex = yLookupIndex * TABLE_SIZE + xLookupIndex;
    
        return lookupIndex >= 0 && lookupIndex < ATAN2_LOOKUP.length
                ? (ATAN2_LOOKUP[lookupIndex] + angleOffset) * signMultiplier
                : 0.0F;
    }

    public static void init() {}
}