package data.scripts.autopilotwithgates.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.apache.log4j.Logger;

import com.fs.graphics.util.Fader;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.campaign.BaseLocation;
import com.fs.starfarer.campaign.CampaignEngine;

import data.scripts.autopilotwithgates.org.objectweb.asm.*;

public class UiUtil {
    private static final Logger logger = Logger.getLogger(UiUtil.class);
    public static void print(Object... args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            sb.append(args[i] instanceof String ? (String) args[i] : String.valueOf(args[i]));
            if (i < args.length - 1) sb.append(' ');
        }
        logger.info(sb.toString());
    }

    public static MethodHandle getMapHandle;
    public static MethodHandle getLocationMapHandle;
    public static MethodHandle isRadarModeHandle;
    public static MethodHandle getZoomTrackerHandle;
    public static MethodHandle getFactorHandle;
    public static MethodHandle zoomTrackerFloatGetterHandle;

    public static void refMapHandles(Object mapTab) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            Class<?> mapTabClass = mapTab.getClass();
            Class<?> mapClass = Refl.getReturnType(Refl.getMethod("getMap", mapTabClass));
            Class<?> zoomTrackerClass = Refl.getReturnType(Refl.getMethod("getZoomTracker", mapClass));

            getMapHandle = lookup.findVirtual(mapTabClass, "getMap", MethodType.methodType(mapClass));
            isRadarModeHandle = lookup.findVirtual(mapClass, "isRadarMode", MethodType.methodType(boolean.class));
            getZoomTrackerHandle = lookup.findVirtual(mapClass, "getZoomTracker", MethodType.methodType(zoomTrackerClass));
            getFactorHandle = lookup.findVirtual(mapClass, "getFactor", MethodType.methodType(float.class));
            getLocationMapHandle = lookup.findVirtual(mapClass, "getLocation", MethodType.methodType(BaseLocation.class));

            String methodName = UiUtil.getZoomTrackerFloatGetterName(zoomTrackerClass);
            zoomTrackerFloatGetterHandle = lookup.findVirtual(zoomTrackerClass, methodName, MethodType.methodType(float.class));

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static final MethodHandle campaignEngineGetCoreHandle;
    public static final MethodHandle coreGetCurrentTabHandle;

    public static final MethodHandle campaignUIGetCourseWidgetHandle;
    public static final MethodHandle courseWidgetGetNextStepHandle;
    public static final MethodHandle courseWidgetGetInnerHandle;
    public static final MethodHandle courseWidgetGetPhaseHandle;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        try {
            Class<?> campaignUIClass = null;
            for (Class<?> cls : CampaignEngine.class.getDeclaredClasses()) {
                if (cls.isInterface()) {
                    campaignUIClass = cls;
                    break;
                }
            }

            Class<?> coreClass = Refl.getReturnType(Refl.getMethod("getCore", campaignUIClass));
            Class<?> courseWidgetClass = Refl.getReturnType(Refl.getMethod("getCourseWidget", campaignUIClass));

            campaignEngineGetCoreHandle = lookup.findVirtual(campaignUIClass, "getCore", MethodType.methodType(coreClass));
            coreGetCurrentTabHandle = lookup.findVirtual(coreClass, "getCurrentTab", MethodType.methodType(Refl.getReturnType(Refl.getMethod("getCurrentTab", coreClass))));
            campaignUIGetCourseWidgetHandle = lookup.findVirtual(campaignUIClass, "getCourseWidget", MethodType.methodType(courseWidgetClass));

            courseWidgetGetNextStepHandle = lookup.findVirtual(courseWidgetClass, "getNextStep", MethodType.methodType(SectorEntityToken.class, SectorEntityToken.class));
            courseWidgetGetInnerHandle = lookup.findVirtual(courseWidgetClass, "getInner", MethodType.methodType(Fader.class));
            courseWidgetGetPhaseHandle = lookup.findVirtual(courseWidgetClass, "getPhase", MethodType.methodType(float.class));

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static UIPanelAPI coreGetCurrentTab(Object core) {
        try {
            return (UIPanelAPI) coreGetCurrentTabHandle.invoke(core);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getCore(Object campaignUI) {
        try {
            return (Object) campaignEngineGetCoreHandle.invoke(campaignUI);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getCourseWidget(Object campaignUI) {
        try {
            return (Object) campaignUIGetCourseWidgetHandle.invoke(campaignUI);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static SectorEntityToken getNextStep(Object courseWidget, SectorEntityToken target) {
        try {
            return (SectorEntityToken) courseWidgetGetNextStepHandle.invoke(courseWidget, target);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Fader courseWidgetGetInner(Object courseWidget) {
        try {
            return (Fader) courseWidgetGetInnerHandle.invoke(courseWidget);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static float courseWidgetGetPhase(Object courseWidget) {
        try {
            return (float) courseWidgetGetPhaseHandle.invoke(courseWidget);
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

    public static void init() {}
}