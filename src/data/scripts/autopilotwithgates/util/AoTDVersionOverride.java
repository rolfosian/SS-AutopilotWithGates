package data.scripts.autopilotwithgates.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;

import data.scripts.autopilotwithgates.org.objectweb.asm.*;

// backwards compatibility
public class AoTDVersionOverride implements Opcodes {
    public static interface CanUseBifrostsDelegate {
        public boolean canUseBifrosts();
    }

    public static final CanUseBifrostsDelegate delegate;

    public static void init() {}
    static {
        ModSpecAPI modSpec = Global.getSettings().getModManager().getModSpec("aotd_vok");
        if (modSpec != null && Integer.parseInt(modSpec.getVersionInfo().getMajor()) < 5) {
            // delegate = new CanUseBifrostsDelegate() {
            //     @Override
            //     public boolean canUseBifrosts() {
            //         return data.kaysaar.aotd.vok.campaign.econ.globalproduction.models.GPManager.getInstance().getMegastructure("aotd_bifrost") != null;
            //     }
            // };

            String className = "data.scripts.autopilotwithgates.util.CanUseBifrostsVersionOverride";
            String internalName = className.replace('.', '/');

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

            cw.visit(
                V17,
                ACC_PUBLIC,
                internalName,
                null,
                "java/lang/Object",
                new String[]{
                    Type.getInternalName(CanUseBifrostsDelegate.class)
                }
            );

            {
                MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();

                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);

                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }

            {
                MethodVisitor mv = cw.visitMethod(
                    ACC_PUBLIC,
                    "canUseBifrosts",
                    "()Z",
                    null,
                    null
                );

                mv.visitCode();

                mv.visitMethodInsn(
                    INVOKESTATIC,
                    "data/kaysaar/aotd/vok/campaign/econ/globalproduction/models/GPManager",
                    "getInstance",
                    "()Ldata/kaysaar/aotd/vok/campaign/econ/globalproduction/models/GPManager;",
                    false
                );

                mv.visitLdcInsn("aotd_bifrost");
                mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "data/kaysaar/aotd/vok/campaign/econ/globalproduction/models/GPManager",
                    "getMegastructure",
                    "(Ljava/lang/String;)Ldata/kaysaar/aotd/vok/campaign/econ/globalproduction/models/megastructures/GPBaseMegastructure;",
                    false
                );

                Label isNull = new Label();

                mv.visitJumpInsn(IFNULL, isNull);

                mv.visitInsn(ICONST_1);
                mv.visitInsn(IRETURN);

                mv.visitLabel(isNull);
                mv.visitInsn(ICONST_0);
                mv.visitInsn(IRETURN);

                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }

            cw.visitEnd();

            delegate = (CanUseBifrostsDelegate) Refl.instantiateClass(new ClassLoader(AoTDVersionOverride.class.getClassLoader()) {
                public Class<?> define(byte[] classBytes, String name) {
                    return defineClass(name, classBytes, 0, classBytes.length);
                }
            }.define(cw.toByteArray(), className).getConstructors()[0]);

        } else {
            delegate = new CanUseBifrostsDelegate() {
                @Override
                public boolean canUseBifrosts() {
                    return com.fs.starfarer.api.impl.campaign.aotd_entities.BiFrostGateEntity.canUseBifrostGates();
                }
            };
        }
    }
}
