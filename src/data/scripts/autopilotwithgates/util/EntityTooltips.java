package data.scripts.autopilotwithgates.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import static java.lang.invoke.MethodType.methodType;

import java.awt.Color;

import com.fs.starfarer.ui.impl.StandardTooltipV2Expandable;
import com.fs.starfarer.ui.impl.StarSystemTooltipFactory;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.campaign.BaseLocation;
import com.fs.starfarer.campaign.CampaignPlanet;
import com.fs.starfarer.campaign.StarSystem;

import static data.scripts.autopilotwithgates.util.UiUtil.utils;
import static data.scripts.autopilotwithgates.util.UiUtil.print;

public class EntityTooltips {
    private static final MethodHandle createStarSystemTooltipHandle;
    
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            Object[] methods = StarSystemTooltipFactory.class.getDeclaredMethods();
            Object method = methods[0];

            createStarSystemTooltipHandle = lookup.findStatic(
                StarSystemTooltipFactory.class,
                Refl.getMethodName(method),
                methodType(StandardTooltipV2Expandable.class, StarSystemAPI.class)
            );

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static StandardTooltipV2Expandable createStarSystemTooltip(StarSystemAPI system) {
        try {
            return (StandardTooltipV2Expandable) createStarSystemTooltipHandle.invoke(system);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static class SystemRowTooltipCreator implements TooltipMakerAPI.TooltipCreator {
        private final UIPanelAPI tt;
        private final float width;
        private final float height;
        
        public SystemRowTooltipCreator(StarSystemAPI system) {
            StandardTooltipV2Expandable preTt = createStarSystemTooltip(system);

            if (system.getStar() == null) {
                ((StarSystem)system).setStar(new CampaignPlanet(null, null, "star_white", 0) {
                    @Override
                    public BaseLocation getContainingLocation() {
                        return (StarSystem) system;
                    }
                });
                ((StandardTooltipV2Expandable)preTt).createImpl(true);
                ((StarSystem)system).setStar(null);

            } else {
                ((StandardTooltipV2Expandable)preTt).createImpl(true);
            }

            this.width = preTt.getWidthSoFar();
            this.height = preTt.getHeightSoFar();

            this.tt = utils.getContents(preTt);
            utils.getParent(this.tt).removeComponent(this.tt);
        }

        @Override
        public boolean isTooltipExpandable(Object var1) {
            return false;
        }

        @Override
        public float getTooltipWidth(Object var1) {
            return this.width;
        }

        @Override
        public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object var3) {
            tooltip.addCustom(this.tt, 0f);
            this.tt.getPosition().setXAlignOffset(0f);
            tooltip.setHeightSoFar(this.height);
        }
    }

    public static class EntityRowTooltipCreator implements TooltipMakerAPI.TooltipCreator {
        private static final float minWidth = Global.getSettings().createLabel("Active", Fonts.DEFAULT_SMALL).computeTextWidth("Active");

        private final CustomCampaignEntityPlugin plugin;
        private final boolean customTooltip;

        private final String text;
        private final float width;
        private final Color color;
        
        public EntityRowTooltipCreator(SectorEntityToken entity) {
            this.plugin = entity.getCustomPlugin();
            this.customTooltip = plugin != null && plugin.hasCustomMapTooltip();

            String defaultName = entity.getCustomEntitySpec().getDefaultName();
            String name = entity.getName();
            if (name == null) name = "";

            String text;

            if (defaultName != null && !name.equals(defaultName) && !defaultName.toLowerCase().endsWith(defaultName.toLowerCase())) {
                text = name + " (" + defaultName + ")";
            } else {
                text = name;
            }

            this.text = text;
            this.color = entity.getFaction().isPlayerFaction() ? entity.getFaction().getBaseUIColor() : Global.getSector().getFaction("neutral").getBaseUIColor();
            this.width = Math.max(minWidth, Global.getSettings().createLabel(text, Fonts.DEFAULT_SMALL).computeTextWidth(text));
        }

        @Override
        public boolean isTooltipExpandable(Object var1) {
            return false;
        }

        @Override
        public float getTooltipWidth(Object var1) {
            return this.width + 5f;
        }

        @Override
        public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object var3) {
            if (this.customTooltip) {
                this.plugin.createMapTooltip(tooltip, expanded);
                return;
            }
            tooltip.addPara(this.text, this.color, 0f);
        }
    }
}
