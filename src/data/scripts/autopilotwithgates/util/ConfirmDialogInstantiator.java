package data.scripts.autopilotwithgates.util;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import static java.lang.invoke.MethodType.methodType;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

import com.fs.graphics.Sprite;
import com.fs.graphics.TextureLoader;
import com.fs.graphics.util.Fader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignEntityPickerListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.campaign.CampaignState;
import com.fs.starfarer.campaign.command.AdminPickerDialog;
import com.fs.starfarer.ui.newui.CampaignEntityPickerDialog;

import data.scripts.autopilotwithgates.util.UiUtil.ActionListener;

import static data.scripts.autopilotwithgates.util.UiUtil.print;
import static data.scripts.autopilotwithgates.util.UiUtil.utils;

public class ConfirmDialogInstantiator {
    private static final MethodHandle campaignEntityPickerDialogCtor;
    private static final MethodHandle campaignEntityPickerDialogShowMethodHandle;

    private static final MethodHandle confirmDialogCtor;
    private static final MethodHandle confirmDialogGetButtonHandle;
    private static final MethodHandle confirmDialogShowHandle;
    private static final MethodHandle confirmDialogGetInnerPanelHandle;
    private static final MethodHandle confirmDialogSetRightClickOutsideCancelsHandle;

    private static CallSite dialogDismissedCallSite;

    public static void init() {}
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            Object ctor = CampaignEntityPickerDialog.class.getConstructors()[0];
            Class<?>[] paramTypes = Refl.getConstructorParamTypes(ctor);
            Class<?> uiPanelClass = paramTypes[5];
            Class<?> dialogDismissedInterface = paramTypes[6];

            campaignEntityPickerDialogCtor = lookup.findConstructor(
                CampaignEntityPickerDialog.class, 
                methodType(
                    void.class,
                    String.class,
                    String.class,
                    String.class,
                    FactionAPI.class,
                    List.class,
                    uiPanelClass,
                    dialogDismissedInterface,
                    CampaignEntityPickerListener.class
                )
            );

            campaignEntityPickerDialogShowMethodHandle = lookup.findVirtual(
                CampaignEntityPickerDialog.class.getSuperclass(),
                "show",
                methodType(void.class, float.class, float.class)
            );

            Class<?> confirmDialogClass = AdminPickerDialog.class.getSuperclass();
            Class<?> buttonClass = Refl.getReturnType(Refl.getMethod("getButton", confirmDialogClass));;

            confirmDialogCtor = lookup.findConstructor(
                confirmDialogClass,
                methodType(
                    void.class,
                    float.class,
                    float.class,
                    uiPanelClass,
                    dialogDismissedInterface,
                    String.class,
                    String[].class
                )
            );
            confirmDialogShowHandle = lookup.findVirtual(
                confirmDialogClass,
                "show",
                methodType(void.class, float.class, float.class)
            );
            confirmDialogGetInnerPanelHandle = lookup.findVirtual(
                confirmDialogClass,
                "getInnerPanel",
                methodType(uiPanelClass)
            );
            confirmDialogGetButtonHandle = lookup.findVirtual(
                confirmDialogClass,
                "getButton",
                methodType(buttonClass, int.class)
            );
            confirmDialogSetRightClickOutsideCancelsHandle = lookup.findVirtual(
                confirmDialogClass,
                "setRightClickOutsideCancels",
                methodType(void.class, boolean.class)
            );

            MethodType factoryType = methodType(dialogDismissedInterface, DialogDismissedListenerProxy.class);
            MethodType actualSamMethodType = methodType(
                void.class,
                CampaignEntityPickerDialog.class.getSuperclass().getSuperclass(),
                int.class
            );
        
            dialogDismissedCallSite = LambdaMetafactory.metafactory(
                lookup,
                "dialogDismissed",
                factoryType,
                actualSamMethodType,
                lookup.findVirtual(DialogDismissedListenerProxy.class, "dialogDismissed", methodType(void.class, Object.class, int.class)),
                actualSamMethodType
            );

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object[] createConfirmDialog(
        UIPanelAPI dialogParent,
        String title,
        float width,
        float height,
        DialogDismissedListener dialogDismissedListener,
        String... buttonTexts
    ) {
        try {
            Object dialog = confirmDialogCtor.invoke(
                width,
                height,
                dialogParent,
                dialogDismissedListener.getProxy(),
                title,
                buttonTexts
            );

            ButtonAPI[] buttons = new ButtonAPI[buttonTexts.length];
            for (int i = 0; i < buttons.length; i++)
                buttons[i] = (ButtonAPI) confirmDialogGetButtonHandle.invoke(dialog, i);

            return new Object[] {
                dialog,
                confirmDialogGetInnerPanelHandle.invoke(dialog), // UIPanelAPI assignable
                buttons
            };
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static Object[] showConfirmDialog(
        UIPanelAPI dialogParent,
        String title,
        float width,
        float height,
        DialogDismissedListener dialogDismissedListener,
        String... buttonTexts
    ) {
        return showConfirmDialog(dialogParent, title, width, height, dialogDismissedListener.getProxy(), buttonTexts);
    }

    public static Object[] showConfirmDialog(
        UIPanelAPI dialogParent,
        String title,
        float width,
        float height,
        Object dialogDismissedListener,
        String... buttonTexts
    ) {
        try {
            Object dialog = confirmDialogCtor.invoke(
                width,
                height,
                dialogParent,
                dialogDismissedListener,
                title,
                buttonTexts
            );
            confirmDialogShowHandle.invoke(dialog, 0.25f, 0.25f);

            ButtonAPI[] buttons = new ButtonAPI[buttonTexts.length];
            for (int i = 0; i < buttons.length; i++)
                buttons[i] = (ButtonAPI) confirmDialogGetButtonHandle.invoke(dialog, i);

            return new Object[] {
                dialog,
                confirmDialogGetInnerPanelHandle.invoke(dialog),
                buttons
            };

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object[] showConfirmDialog(
        UIPanelAPI dialogParent,
        String backgroundImage,
        String title,
        float width,
        float height,
        DialogDismissedListener dialogDismissedListener,
        int confirmButtonIndex,
        int cancelButtonIndex,
        String... buttonTexts
    ) {
        return showConfirmDialog(dialogParent, backgroundImage, true, title, width, height, dialogDismissedListener, confirmButtonIndex, cancelButtonIndex, buttonTexts);
    }

    public static Object[] showConfirmDialog(
        UIPanelAPI dialogParent,
        String backgroundImage,
        boolean rightClickOutsideCancels,
        String title,
        float width,
        float height,
        DialogDismissedListener dialogDismissedListener,
        int confirmButtonIndex,
        int cancelButtonIndex,
        String... buttonTexts
    ) {
        Object[] dialogComponents = createConfirmDialog(dialogParent, title, width, height, dialogDismissedListener, buttonTexts);
        ButtonAPI[] btns = (ButtonAPI[]) dialogComponents[2];

        ButtonAPI confirmButton = btns[confirmButtonIndex];
        ButtonAPI cancelButton = btns[cancelButtonIndex];

        UIPanelAPI confirmDialog = (UIPanelAPI) dialogComponents[0];
        LabelAPI label = utils.confirmDialogGetLabel(confirmDialog);
        UIPanelAPI innerPanel = utils.confirmDialogGetInnerPanel(confirmDialog);
        
        BackGroundImagePanelPlugin imagePanelPlugin = new BackGroundImagePanelPlugin(confirmDialog, rightClickOutsideCancels);
        imagePanelPlugin.addBackgroundImage(confirmButton, cancelButton, backgroundImage);

        innerPanel.bringComponentToTop((UIComponentAPI)label);
        for (ButtonAPI btn : btns) innerPanel.bringComponentToTop((UIComponentAPI)btn);

        try {
            confirmDialogSetRightClickOutsideCancelsHandle.invoke(confirmDialog, rightClickOutsideCancels);
            confirmDialogShowHandle.invoke(confirmDialog, 0.25f, 0.25f);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return dialogComponents;
    }

    public static UIPanelAPI showCampaignEntityPicker(
        UIPanelAPI dialogParent, // Choose between an InteractionDialog itself, CampaignState's getScreenPanel(), CoreUI's getScreenPanel(), or a core tab's getDialogParent() return value
        DialogDismissedListener dialogDismissedListener, // might as well be null with campaignEntityPickerListener's functionality tbh. If you want to use this then pass the return value of DialogDismissedListener.getProxy() 
        String title,
        String selectedText,
        String okText,
        FactionAPI factionForUiColors,
        List<SectorEntityToken> entities,
        CampaignEntityPickerListener campaignEntityPickerListener
    ) {
        return showCampaignEntityPicker(dialogParent, dialogDismissedListener.getProxy(), title, selectedText, okText, factionForUiColors, entities, campaignEntityPickerListener);
    }

    public static UIPanelAPI showCampaignEntityPicker(
        UIPanelAPI dialogParent, // Choose between an InteractionDialog itself, CampaignState's getScreenPanel(), CoreUI's getScreenPanel(), or a core tab's getDialogParent() return value
        Object dialogDismissedListener, // might as well be null with campaignEntityPickerListener's functionality tbh. If you want to use this then pass the return value of DialogDismissedListener.getProxy() 
        String title,
        String selectedText,
        String okText,
        FactionAPI factionForUiColors,
        List<SectorEntityToken> entities,
        CampaignEntityPickerListener campaignEntityPickerListener
    ) {
        try {
            UIPanelAPI entityPickerDialog = (UIPanelAPI) campaignEntityPickerDialogCtor.invoke(
                title,
                selectedText,
                okText,
                factionForUiColors,
                entities,
                dialogParent,
                dialogDismissedListener,
                campaignEntityPickerListener
            );

            campaignEntityPickerDialogShowMethodHandle.invoke(entityPickerDialog, 0.3f, 0.2f);
            return entityPickerDialog;

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static class DialogDismissedListenerProxy {
        private final DialogDismissedListener listener;

        public DialogDismissedListenerProxy(DialogDismissedListener listener) {
            this.listener = listener;
        }

        @SuppressWarnings("unused")
        // @Override
        public void dialogDismissed(Object arg0, int arg1) {
            this.listener.dialogDismissed(arg0, arg1);
        };
    }

    public static abstract class DialogDismissedListener {
        private final Object proxy;

        public DialogDismissedListener() {
            try {
                proxy = dialogDismissedCallSite.getTarget().invoke(new DialogDismissedListenerProxy(this));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        public abstract void dialogDismissed(Object dialog, int yesOrNo);

        public final Object getProxy() {
            return this.proxy;
        }
    }

    public static class BackGroundImagePanelPlugin extends BaseCustomUIPanelPlugin {
        private final boolean rightClickOutsideCancels;

        private UIPanelAPI dialog;
        private CustomPanelAPI imagePanel; 
        private TooltipMakerAPI tt;

        private ButtonAPI cancelButton;
        private ButtonAPI confirmButton;

        private float dialogLeftBound;
        private float dialogRightBound;
        private float dialogTopBound;
        private float dialogBottomBound;

        public BackGroundImagePanelPlugin(UIPanelAPI dialog, boolean rightClickOutsideCancels) {
            super();
            this.dialog = dialog;

            this.rightClickOutsideCancels = rightClickOutsideCancels;

            PositionAPI dialogPos = dialog.getPosition();
            this.dialogLeftBound = dialogPos.getCenterX() - dialogPos.getWidth() / 2;
            this.dialogRightBound = dialogPos.getCenterX() + dialogPos.getWidth() / 2;
            this.dialogTopBound = dialogPos.getCenterY() + dialogPos.getHeight() / 2;
            this.dialogBottomBound = dialogPos.getCenterY() - dialogPos.getHeight() / 2;
        }

        private boolean isOutsideDialogBounds(float mouseX, float mouseY) {
            return (mouseX < dialogLeftBound || mouseX > dialogRightBound || 
            mouseY < dialogBottomBound || mouseY > dialogTopBound);
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            for (InputEventAPI event : events) {
                if ((event.isKeyDownEvent() && Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) || (this.rightClickOutsideCancels && event.isRMBDownEvent() && isOutsideDialogBounds(event.getX(), event.getY()))) {
                    imagePanel.setOpacity(0f);
                    tt.setOpacity(0f);
                    imagePanel.removeComponent(tt);
                    dialog.removeComponent(imagePanel);
                    break;
                }
            }
        }

        @Override
        public void positionChanged(PositionAPI pos) {
            PositionAPI dialogPos = dialog.getPosition();
            this.dialogLeftBound = dialogPos.getCenterX() - dialogPos.getWidth() / 2;
            this.dialogRightBound = dialogPos.getCenterX() + dialogPos.getWidth() / 2;
            this.dialogTopBound = dialogPos.getCenterY() + dialogPos.getHeight() / 2;
            this.dialogBottomBound = dialogPos.getCenterY() - dialogPos.getHeight() / 2;
        }

        public void addBackgroundImage(ButtonAPI confirmButton, ButtonAPI cancelButton, String backgroundImagePath) {
            PositionAPI dialogPos = dialog.getPosition();
            
            this.confirmButton = confirmButton;
            this.cancelButton = cancelButton;

            this.imagePanel = Global.getSettings().createCustom(dialogPos.getWidth(), dialogPos.getHeight(), this);
            this.tt = imagePanel.createUIElement(dialogPos.getWidth(), dialogPos.getHeight(), false);
            
            this.confirmButton = confirmButton;
            this.cancelButton = confirmButton == cancelButton ? null : cancelButton;
            setConfirmDialogButtonInterceptorsForImage();
        
            tt.addImage(backgroundImagePath, dialogPos.getWidth()-5f, dialogPos.getHeight()-5f, 0f);
            imagePanel.addUIElement(tt);
        
            dialog.addComponent((UIComponentAPI)imagePanel).inMid();
            dialog.sendToBottom(imagePanel);
            dialog.sendToBottom(tt);
            return;
        }

        private void setConfirmDialogButtonInterceptorsForImage() {
            if (cancelButton != null) {
                Object oldListener = utils.buttonGetListener(cancelButton);

                utils.buttonSetListener(cancelButton, new ActionListener() {
                    public void actionPerformed(Object arg0, Object arg1) {
                        imagePanel.setOpacity(0f);
                        tt.setOpacity(0f);

                        imagePanel.removeComponent(tt);
                        dialog.removeComponent(imagePanel);

                        utils.actionPerformed(oldListener, arg0, arg1);
                    }
                }.getProxy());
            }
            if (confirmButton != null) {
                Object oldListener = utils.buttonGetListener(confirmButton);

                utils.buttonSetListener(confirmButton, new ActionListener() {
                    public void actionPerformed(Object arg0, Object arg1) {
                        imagePanel.setOpacity(0f);
                        tt.setOpacity(0f);

                        imagePanel.removeComponent(tt);
                        dialog.removeComponent(imagePanel);

                        utils.actionPerformed(oldListener, arg0, arg1);
                    }
                }.getProxy());
            }
        }
    }

    
    private static Object noiseTexture;
    private static final VarHandle noiseSeedHandle;
    private static final MethodHandle noiseCtor;
    private static final MethodHandle noiseFaderHandle;
    private static final MethodHandle beginFlickerNoiseHandle;
    private static final MethodHandle renderNoiseHandle;

    private static final VarHandle noiseRngHandle;
    private static final MethodHandle noiseRngAdvanceHandle;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            Class<?>[] paramTypes = null;
            Class<?> textureClass = Refl.getFieldType(Refl.getFieldByName("texture", Sprite.class));
            Class<?> noiseClass = Refl.getFieldType(Refl.getFieldByName("noise", CampaignState.class));
            Class<?> noiseRngClass = null;
            noiseCtor = lookup.findConstructor(noiseClass, methodType(void.class, textureClass, float.class, float.class));

            VarHandle varHandle = null;
            VarHandle varHandle2 = null;
            MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(noiseClass, lookup);

            for (Object field : noiseClass.getDeclaredFields()) {
                Class<?> type = Refl.getFieldType(field);
                if (type == long.class) {
                    varHandle = privateLookup.findVarHandle(
                        noiseClass,
                        Refl.getFieldName(field),
                        long.class
                    );
                } else if (type != float.class && type != Fader.class && type != textureClass && type != Color.class) {
                    noiseRngClass = type;
                    varHandle2 = privateLookup.findVarHandle(
                        noiseClass,
                        Refl.getFieldName(field),
                        type
                    );
                }
            }
            noiseSeedHandle = varHandle;
            noiseRngHandle = varHandle2;

            MethodHandle handle = null;
            for (Object method : noiseRngClass.getDeclaredMethods()) {
                if (Refl.getReturnType(method) == boolean.class) {
                    handle = lookup.findVirtual(noiseRngClass, Refl.getMethodName(method), methodType(boolean.class, float.class));
                    break;
                }
            }
            noiseRngAdvanceHandle = handle;

            MethodHandle handle1 = null;
            MethodHandle handle2 = null;
            MethodHandle handle3 = null;
            for (Object method : noiseClass.getDeclaredMethods()) {
                Class<?> returnType = Refl.getReturnType(method);

                if (returnType == void.class) {
                    paramTypes = Refl.getMethodParamTypes(method);

                    if (paramTypes.length == 2 && paramTypes[0] == float.class && paramTypes[1] == float.class) {
                        handle1 = lookup.findVirtual(noiseClass,
                            Refl.getMethodName(method),
                            methodType(void.class, float.class, float.class)
                        );

                    } else if (paramTypes.length == 3 && paramTypes[0] == float.class && paramTypes[1] == float.class && paramTypes[2] == float.class) {
                        handle2 = lookup.findVirtual(noiseClass,
                            Refl.getMethodName(method),
                            methodType(void.class, float.class, float.class, float.class)
                        );

                    }
                } else if (returnType == Fader.class) {
                    handle3 = lookup.findVirtual(noiseClass,
                        Refl.getMethodName(method),
                        methodType(Fader.class)
                    );
                }
            }
            beginFlickerNoiseHandle = handle1;
            renderNoiseHandle = handle2;
            noiseFaderHandle = handle3;


        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void refNoiseTex() {
        try {
            Class<?> textureClass = Refl.getFieldType(Refl.getFieldByName("texture", Sprite.class));
            Global.getSettings().loadTexture("graphics/fx/noise.png");
            SpriteAPI spriteApi = Global.getSettings().getSprite("graphics/fx/noise.png");

            Sprite sprite = null;
            for (Object field : spriteApi.getClass().getDeclaredFields()) {
                if (Refl.getFieldType(field) == Sprite.class) {
                    sprite = (Sprite) MethodHandles.privateLookupIn(spriteApi.getClass(), MethodHandles.lookup()).findVarHandle(
                        spriteApi.getClass(),
                        Refl.getFieldName(field),
                        Sprite.class
                    ).get(spriteApi);
                    break;
                }
            }

            noiseTexture = MethodHandles.privateLookupIn(Sprite.class, MethodHandles.lookup()).findVarHandle(
                Sprite.class,
                "texture",
                textureClass
            ).get(sprite);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object createNoiseRenderer(float width, float height) {
        try {
            Object noise = noiseCtor.invoke(noiseTexture, width, height);
            Fader fader = (Fader) noiseFaderHandle.invoke(noise);
            fader.setDurationOut(0.5f);
            fader.fadeOut();
            // fader.setDuration(0.2f, 0.2f);
            // fader.fadeIn();
            return noise;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getNoiseRng(Object noise) {
        return noiseRngHandle.get(noise);
    }

    public static boolean advanceNoiseRng(Object rng, float amount) {
        try {
            return (boolean) noiseRngAdvanceHandle.invoke(rng, amount);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void setNoiseSeed(Object noise, long seed) {
        noiseSeedHandle.set(noise, seed);
    }

    public static Fader getNoiseFader(Object noise) {
        try {
            return (Fader) noiseFaderHandle.invoke(noise);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void beginFlickerNoise(Object noise) {
        try {
            beginFlickerNoiseHandle.invoke(noise, 0.0f, 0.5f);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void renderNoise(Object noise, float x, float y, float intensity) {
        try {
            renderNoiseHandle.invoke(noise, x, y, intensity);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
