package data.scripts.autopilotwithgates;

import java.awt.Color;

import static org.lwjgl.input.Keyboard.getKeyName;

import com.fs.starfarer.api.Global;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

public class AutoPilotWithGatesSettings {
    public static boolean ABILITY_SCROLL;
    public static boolean AUTOJUMP;
    public static boolean PREFER_BIFROSTS_IN_SYSTEMS_WITH_BOTH;

    public static int BLACKLIST_DIALOG_HOTKEY;
    public static String BLACKLIST_DIALOG_HOTKEY_NAME;

    public static final Color DARK_RED = new Color(139, 0, 0);
    public static final Color DARK_GREEN = new Color(0, 139, 0);
    public static final Color[] TOOLTIP_PARA_COLORS = {DARK_GREEN, DARK_RED};

    public static void init() {}
    static {
        if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
            ABILITY_SCROLL = LunaSettings.getBoolean("autopilot_with_gates", "abilityScroll");
            AUTOJUMP = LunaSettings.getBoolean("autopilot_with_gates", "autoJump");
            PREFER_BIFROSTS_IN_SYSTEMS_WITH_BOTH = LunaSettings.getBoolean("autopilot_with_gates", "preferBifrostsInSystemsWithBoth");
            BLACKLIST_DIALOG_HOTKEY = LunaSettings.getInt("autopilot_with_gates", "blacklistDialogHotkey");

            LunaSettings.addSettingsListener(new LunaSettingsListener() {
                @Override
                public void settingsChanged(String arg0) {
                    if (arg0.equals("autopilot_with_gates")) {
                        ABILITY_SCROLL = LunaSettings.getBoolean("autopilot_with_gates", "abilityScroll");
                        AUTOJUMP = LunaSettings.getBoolean("autopilot_with_gates", "autoJump");
                        PREFER_BIFROSTS_IN_SYSTEMS_WITH_BOTH = LunaSettings.getBoolean("autopilot_with_gates", "preferBifrostsInSystemsWithBoth");
                        BLACKLIST_DIALOG_HOTKEY = LunaSettings.getInt("autopilot_with_gates", "blacklistDialogHotkey");
                        BLACKLIST_DIALOG_HOTKEY_NAME = getKeyName(BLACKLIST_DIALOG_HOTKEY);
                    }
                }
            });

        } else {
            ABILITY_SCROLL = Global.getSettings().getBoolean("gateAutopilot_abilityScroll");
            AUTOJUMP = Global.getSettings().getBoolean("gateAutopilot_autoJump");
            PREFER_BIFROSTS_IN_SYSTEMS_WITH_BOTH = Global.getSettings().getBoolean("gateAutopilot_preferBifrostsInSystemsWithBoth");
            BLACKLIST_DIALOG_HOTKEY = Global.getSettings().getInt("gateAutopilot_blacklistDialogHotkey");
        }

        BLACKLIST_DIALOG_HOTKEY_NAME = getKeyName(BLACKLIST_DIALOG_HOTKEY);
    }
}
