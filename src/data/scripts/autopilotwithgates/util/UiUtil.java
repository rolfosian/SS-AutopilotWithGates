package data.scripts.autopilotwithgates.util;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.graphics.util.Fader;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.campaign.BaseLocation;
import com.fs.starfarer.campaign.CampaignState;
import com.fs.starfarer.campaign.comms.v2.EventsPanel;

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
        public Object campaignUIgetCore(Object campaignEngine);
        public UIPanelAPI coreGetCurrentTab(Object core);

        public EventsPanel getEventsPanel(Object intelTab);
        public Object eventsPanelGetMap(EventsPanel eventsPanel);
        public UIPanelAPI mapTabGetMap(Object mapTab);

        public BaseLocation mapGetLocation(UIPanelAPI map);
        public boolean isRadarMode(UIPanelAPI map);
        public Object getZoomTracker(UIPanelAPI map);
        public float getFactor(UIPanelAPI map);
        public float getZoomLevel(Object zoomTracker);

        public Object getCourseWidget(Object campaignUI);
        public SectorEntityToken getNextStep(Object courseWidget, SectorEntityToken target);
        public Fader getInner(Object courseWidget);
        public float getPhase(Object courseWidget);
        
        public List<UIComponentAPI> getChildrenNonCopy(Object uiPanel);
    }

    private static Class<?>[] implementUtilInterface() throws Exception {
        Class<?> interactionDialogClass = Refl.getFieldType(Refl.getFieldByName("encounterDialog", CampaignState.class));

        Class<?> coreClass = Refl.getReturnType(Refl.getMethod("getCore", CampaignState.class));
        Class<?> courseWidgetClass = Refl.getReturnType(Refl.getMethod("getCourseWidget", CampaignState.class));

        Class<?> mapTabClass = Refl.getReturnType(Refl.getMethod("getMap", EventsPanel.class));

        Class<?> mapClass = Refl.getReturnType(Refl.getMethod("getMap", mapTabClass));
        Class<?> uiPanelClass = mapClass.getSuperclass();

        Class<?> intelTabClass = Refl.getReturnType(Refl.getMethod("getIntelTab", EventsPanel.class));
        Class<?> zoomTrackerClass = Refl.getReturnType(Refl.getMethod("getZoomTracker", mapClass));
        
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String superName = Type.getType(Object.class).getInternalName();
        String interfaceName = Type.getType(UtilInterface.class).getInternalName();

        String uiPanelAPIDescriptor = Type.getDescriptor(UIPanelAPI.class);

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

        // interactionDialogGetCore(Object)
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "interactionDialogGetCore",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(interactionDialogClass));

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(interactionDialogClass),
                "getCoreUI",
                "()" + Type.getDescriptor(coreClass),
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // campaignUIgetCore(Object)
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
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(CampaignState.class));

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(CampaignState.class),
                "getCore",
                "()" + Type.getDescriptor(coreClass),
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // coreGetCurrentTab(Object)
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "coreGetCurrentTab",
                "(Ljava/lang/Object;)" + uiPanelAPIDescriptor,
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(coreClass));

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(coreClass),
                "getCurrentTab",
                "()" + Type.getDescriptor(uiPanelClass),
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // getEventsPanel(Object intelTab)
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getEventsPanel",
                "(Ljava/lang/Object;)" + Type.getDescriptor(EventsPanel.class),
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
                "()" + Type.getDescriptor(EventsPanel.class),
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // eventsPanelGetMap(EventsPanel)
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "eventsPanelGetMap",
                "(" + Type.getDescriptor(EventsPanel.class) + ")Ljava/lang/Object;",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(EventsPanel.class),
                "getMap",
                "()" + Type.getDescriptor(mapTabClass),
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // mapTabGetMap(Object)
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "mapTabGetMap",
                "(Ljava/lang/Object;)" + uiPanelAPIDescriptor,
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(mapTabClass));

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(mapTabClass),
                "getMap",
                "()" + Type.getDescriptor(mapClass),
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // mapGetLocation(UIPanelAPI)
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "mapGetLocation",
                "(" + uiPanelAPIDescriptor + ")" + Type.getDescriptor(BaseLocation.class),
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);

            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(mapClass));

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(mapClass),
                "getLocation",
                "()" + Type.getDescriptor(BaseLocation.class),
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // isRadarMode(UIPanelAPI)
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "isRadarMode",
                "(" + uiPanelAPIDescriptor + ")Z",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(mapClass));

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(mapClass),
                "isRadarMode",
                "()Z",
                false
            );

            mv.visitInsn(IRETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // getZoomTracker(UIPanelAPI)
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getZoomTracker",
                "(" + uiPanelAPIDescriptor + ")Ljava/lang/Object;",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(mapClass));

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(mapClass),
                "getZoomTracker",
                "()" + Type.getDescriptor(zoomTrackerClass),
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // getFactor(UIPanelAPI)
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getFactor",
                "(" + uiPanelAPIDescriptor + ")F",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(mapClass));

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(mapClass),
                "getFactor",
                "()F",
                false
            );

            mv.visitInsn(FRETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // getZoomLevel(Object zoomTracker)
        {
            String zoomLevelMethodName = getZoomTrackerFloatGetterName(zoomTrackerClass);

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
                zoomLevelMethodName,
                "()F",
                false
            );

            mv.visitInsn(FRETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // getCourseWidget(Object)
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
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(CampaignState.class));

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(CampaignState.class),
                "getCourseWidget",
                "()" + Type.getDescriptor(courseWidgetClass),
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // getNextStep(Object courseWidget)
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getNextStep",
                "(" +
                    "Ljava/lang/Object;" +
                    Type.getDescriptor(SectorEntityToken.class) +
                ")" +
                Type.getDescriptor(SectorEntityToken.class),
                null,
                null
            );
            mv.visitCode();
        
            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(courseWidgetClass));
        
            mv.visitVarInsn(ALOAD, 2);
        
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(courseWidgetClass),
                "getNextStep",
                "(" + Type.getDescriptor(SectorEntityToken.class) + ")" + Type.getDescriptor(SectorEntityToken.class),
                false
            );
        
            mv.visitInsn(ARETURN);
        
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // getInner(Object courseWidget)
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getInner",
                "(Ljava/lang/Object;)" + Type.getDescriptor(Fader.class),
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(courseWidgetClass));

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(courseWidgetClass),
                "getInner",
                "()" + Type.getDescriptor(Fader.class),
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // getPhase(Object courseWidget)
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
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(courseWidgetClass));

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(courseWidgetClass),
                "getPhase",
                "()F",
                false
            );

            mv.visitInsn(FRETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // getChildrenNonCopy(Object uiPanel)
        {
            MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getChildrenNonCopy",
                "(Ljava/lang/Object;)Ljava/util/List;",
                null,
                null
            );
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(uiPanelClass));

            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(uiPanelClass),
                "getChildrenNonCopy",
                "()Ljava/util/List;",
                false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        cw.visitEnd();

        byte[] classBytes = cw.toByteArray();
        String classBinaryName = "data/scripts/autopilotwithgates/util/UtilInterface".replace('/', '.');

        return new Class<?>[] {(Class<?>) Refl.getMethodDeclaredAndInvokeDirectly("define", new ClassLoader(UiUtil.class.getClassLoader()) {
            Class<?> define(byte[] b) {
                return defineClass(classBinaryName, b, 0, b.length);
            }
        },
        classBytes),
        mapClass,
        uiPanelClass};
    }

    public static final Class<?> mapClass;
    public static final Class<?> uiPanelClass;
    public static final UtilInterface utils;

    static {
        try {
            Class<?>[] result = implementUtilInterface();

            utils = (UtilInterface) Refl.instantiateClass(result[0].getConstructors()[0]);
            mapClass = result[1];
            uiPanelClass = result[2];

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getCore(Object campaignUI, Object interactionDialog) {
        try {
            return interactionDialog == null ? utils.campaignUIgetCore(campaignUI) : utils.interactionDialogGetCore(interactionDialog);
        } catch (Throwable e) {
            throw new RuntimeException(e);
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

            Object baosCtor = Refl.getConstructor(baosClass, new Class<?>[0]);
            Object baosWriteMethod = Refl.getMethodExplicit("write", baosClass, new Class<?>[]{byte[].class, int.class, int.class});
            Object baosFlushMethod = Refl.getMethod("flush", baosClass);
            Object baosToByteArrayMethod = Refl.getMethod("toByteArray", baosClass);
    
            Class<?> inputStreamClass = inputStream.getClass();
            Object inputStreamAvailableMethod = Refl.getMethod("available", inputStreamClass);
            Object inputStreamReadMethod = Refl.getMethod("read", inputStreamClass);

            int bufferSize = computeBufferSize(inputStream, inputStreamAvailableMethod);
            Object outputStream = Refl.instantiateClass(baosCtor);

            byte[] data = new byte[bufferSize];
            int bytesRead;
            int readCount = 0;

            while ((bytesRead = (int) Refl.invokeMethodDirectly(inputStreamReadMethod, inputStream, data, 0, bufferSize)) != -1) {
                Refl.invokeMethodDirectly(baosWriteMethod, outputStream, data, 0, bytesRead);
                readCount++;
            }
            
            Refl.invokeMethodDirectly(baosFlushMethod, outputStream);
            if (readCount == 1) {
                return data;
            }
            return (byte[]) Refl.invokeMethodDirectly(baosToByteArrayMethod, outputStream);

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static String getZoomTrackerFloatGetterName(Class<?> cls) throws Exception {
        Object inputStream = Refl.getMethodAndInvokeDirectly(
            "getResourceAsStream",
            cls.getClassLoader(),
            cls.getCanonicalName().replace(".", "/") + ".class"
        );

        ClassReader cr = new ClassReader(readStream(inputStream));
        final String[] foundName = {null};

        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
    
                if (!desc.equals("()F")) return null;
    
                return new MethodVisitor(Opcodes.ASM9) {
    
                    int fieldGets = 0;
                    int fcmps = 0;
                    int fReturns = 0;
    
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fld, String fldDesc) {
                        if (opcode == Opcodes.GETFIELD && fldDesc.equals("F")) fieldGets++;
                    }
    
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.FCMPG || opcode == Opcodes.FCMPL) fcmps++;
                        if (opcode == Opcodes.FRETURN) fReturns++;
                    }
    
                    @Override
                    public void visitEnd() {
                        if (fieldGets >= 3 && fcmps >= 2 && fReturns == 1) {
                            foundName[0] = name;
                        }
                    }
                };
            }
        }, 0);

        return foundName[0];
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

    public static final float fastAtan2(float y, float x) {
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