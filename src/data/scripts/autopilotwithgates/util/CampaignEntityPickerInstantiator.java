package data.scripts.autopilotwithgates.util;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

import com.fs.starfarer.api.campaign.CampaignEntityPickerListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.ui.newui.CampaignEntityPickerDialog;

public class CampaignEntityPickerInstantiator {
    private static MethodHandle campaignEntityPickerDialogCtor;
    private static MethodHandle dialogShowMethodHandle;

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
                MethodType.methodType(
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

            dialogShowMethodHandle = lookup.findVirtual(
                CampaignEntityPickerDialog.class.getSuperclass(),
                "show",
                MethodType.methodType(void.class, float.class, float.class)
            );

            Class<?> dialogDismissedParamClass = CampaignEntityPickerDialog.class.getSuperclass().getSuperclass();

            MethodType actualSamMethodType = MethodType.methodType(void.class, dialogDismissedParamClass, int.class);
            MethodHandle implementationMethodHandle = lookup.findVirtual(DialogDismissedListenerProxy.class, "dialogDismissed", MethodType.methodType(void.class, Object.class, int.class));
            MethodType factoryType = MethodType.methodType(dialogDismissedInterface, DialogDismissedListenerProxy.class);
        
            dialogDismissedCallSite = LambdaMetafactory.metafactory(
                lookup,
                "dialogDismissed",
                factoryType,
                actualSamMethodType,
                implementationMethodHandle,
                actualSamMethodType
            );

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
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

            dialogShowMethodHandle.invoke(entityPickerDialog, 0.3f, 0.2f);
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
}
