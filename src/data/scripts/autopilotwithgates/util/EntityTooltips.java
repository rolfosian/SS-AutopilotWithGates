package data.scripts.autopilotwithgates.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import static java.lang.invoke.MethodType.methodType;

import java.awt.Color;
import java.util.*;


import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.AsteroidAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityPlugin;
import com.fs.starfarer.api.campaign.CustomEntitySpecAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetStubAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.NascentGravityWellAPI;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.ParticleControllerAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RingBandAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpawnPointPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.ui.impl.StandardTooltipV2Expandable;
import com.fs.starfarer.ui.impl.StarSystemTooltipFactory;

import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.ColorShifterAPI;
import com.fs.starfarer.campaign.BaseLocation;
import com.fs.starfarer.campaign.CampaignPlanet;
import com.fs.starfarer.campaign.StarSystem;

import static data.scripts.autopilotwithgates.util.UiUtil.utils;
import static data.scripts.autopilotwithgates.util.UiUtil.print;

@SuppressWarnings({"rawtypes", "deprecation", "unused"}) 
public class EntityTooltips {
    private static final MethodHandle createStarSystemTooltipHandle;
    
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            Object[] methods = StarSystemTooltipFactory.class.getDeclaredMethods();
            Object createStarSystemTooltipMethod = null;
            
            for (Object method : methods) {
                Class<?>[] paramTypes = Refl.getMethodParamTypes(method);
                Class<?> returnType = Refl.getReturnType(method);

                if (paramTypes.length == 1 && returnType == StandardTooltipV2Expandable.class) {
                    createStarSystemTooltipMethod = method;
                    break;
                } 
            }

            createStarSystemTooltipHandle = lookup.findStatic(
                StarSystemTooltipFactory.class,
                Refl.getMethodName(createStarSystemTooltipMethod),
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
                preTt.createImpl(true);
                ((StarSystem)system).setStar(null);

                this.tt = utils.getContents(preTt);
                utils.getParent(this.tt).removeComponent(this.tt);

                LabelAPI title = (LabelAPI) utils.getChildrenNonCopy(this.tt).get(0);
                title.setHighlightColor(system.getLightColor());
                title.setHighlight(title.getText());

            } else {
                preTt.createImpl(true);

                this.tt = utils.getContents(preTt);
                utils.getParent(this.tt).removeComponent(this.tt);
            }

            this.width = preTt.getWidthSoFar();
            this.height = preTt.getHeightSoFar();
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

    // lol lmao
    public static class DummyEntity implements SectorEntityToken {
        private SectorEntityToken center;
        private FactionAPI faction;

        public DummyEntity() {
            this(null);
        }

        public DummyEntity(SectorEntityToken center) {
            this.center = center;
            this.faction = Global.getSector().getFaction("neutral");
        }

        public DummyEntity(SectorEntityToken center, FactionAPI faction) {
            this.center = center;
            this.faction = faction;
        }

        public void setCenter(SectorEntityToken center) {
            this.center = center;
        }

        @Override
        public Vector2f getLocation() {
            return center.getLocationInHyperspace();
        }

        @Override
        public Vector2f getLocationInHyperspace() {
            return center.getLocationInHyperspace();
        }

        @Override
        public LocationAPI getContainingLocation() {
            return Global.getSector().getHyperspace();
        }

        @Override
        public MarketAPI getMarket() {
            return null;
        }

        @Override
        public boolean hasTag(String arg0) {
            return false;
        }

        @Override
        public MemoryAPI getMemory() {
            return memory;
        }

        @Override
        public MemoryAPI getMemoryWithoutUpdate() {
            return memory;
        }
        
        @Override
        public FactionAPI getFaction() {
            return faction;
        }

        @Override public void addAbility(String arg0) {}
        @Override public void addDropRandom(DropData arg0) {}
        @Override public void addDropRandom(String arg0, int arg1, int arg2) {}
        @Override public void addDropValue(DropData arg0) {}
        @Override public void addDropValue(String arg0, int arg1) {}
        @Override public void addFloatingText(String arg0, Color arg1, float arg2) {}
        @Override public void addFloatingText(String arg0, Color arg1, float arg2, boolean arg3) {}
        @Override public void addScript(EveryFrameScript arg0) {}
        @Override public void addTag(String arg0) {}
        @Override public void advance(float arg0) {}
        @Override public void autoUpdateHyperLocationBasedOnInSystemEntityAtRadius(SectorEntityToken arg0, float arg1) {}
        @Override public void clearAbilities() {}
        @Override public void clearFloatingText() {}
        @Override public void clearTags() {}
        @Override public void fadeInIndicator() {}
        @Override public void fadeOutIndicator() {}
        @Override public void forceOutIndicator() {}
        @Override public void forceSensorContactFaderBrightness(float arg0) {}
        @Override public void forceSensorFaderBrightness(float arg0) {}
        @Override public void forceSensorFaderOut() {}
        @Override public Map<String, AbilityPlugin> getAbilities() {return null;}
        @Override public AbilityPlugin getAbility(String arg0) {return null;}
        @Override public PersonAPI getActivePerson() {return null;}
        @Override public Boolean getAlwaysUseSensorFaderBrightness() {return false;}
        @Override public String getAutogenJumpPointNameInHyper() {return null;}
        @Override public float getBaseSensorRangeToDetect(float arg0) {return 0f;}
        @Override public CargoAPI getCargo() {return null;}
        @Override public float getCircularOrbitAngle() {return 0f;}
        @Override public float getCircularOrbitPeriod() {return 0f;}
        @Override public float getCircularOrbitRadius() {return 0f;}
        @Override public Constellation getConstellation() {return null;}
        @Override public Map<String, Object> getCustomData() {return null;}
        @Override public String getCustomDescriptionId() {return null;}
        @Override public CustomEntitySpecAPI getCustomEntitySpec() {return null;}
        @Override public String getCustomEntityType() {return null;}
        @Override public InteractionDialogImageVisual getCustomInteractionDialogImageVisual() {return null;}
        @Override public CustomCampaignEntityPlugin getCustomPlugin() {return null;}
        @Override public StatBonus getDetectedRangeMod() {return null;}
        @Override public Float getDetectionRangeDetailsOverrideMult() {return 0f;}
        @Override public Float getDiscoveryXP() {return 0f;}
        @Override public List<DropData> getDropRandom() {return null;}
        @Override public List<DropData> getDropValue() {return null;}
        @Override public float getExtendedDetectedAtRange() {return 0f;}
        @Override public float getFacing() {return 0f;}
        @Override public String getFullName() {return center.getFullName();}
        @Override public String getId() {return null;}
        @Override public Color getIndicatorColor() {return null;}
        @Override public Color getLightColor() {return null;}
        @Override public SectorEntityToken getLightSource() {return null;}
        @Override public float getMaxSensorRangeToDetect(SectorEntityToken arg0) {return 0f;}
        @Override public String getName() {return center.getName();}
        @Override public OrbitAPI getOrbit() {return null;}
        @Override public SectorEntityToken getOrbitFocus() { return null; }
        @Override public float getRadius() {return 0f;}
        @Override public Float getSalvageXP() {return 0f;}
        @Override public List<EveryFrameScript> getScripts() {return null;}
        @Override public float getSensorContactFaderBrightness() {return 0f;}
        @Override public float getSensorFaderBrightness() {return 0f;}
        @Override public float getSensorProfile() {return 0f;}
        @Override public StatBonus getSensorRangeMod() {return null;}
        @Override public float getSensorStrength() { return 0f; }
        @Override public StarSystemAPI getStarSystem() { return center.getStarSystem(); }
        @Override public Collection<String> getTags() {return null;}
        @Override public Vector2f getVelocity() {return null;}
        @Override public VisibilityLevel getVisibilityLevelOfPlayerFleet() {return null;}
        @Override public VisibilityLevel getVisibilityLevelTo(SectorEntityToken arg0) {return null;}
        @Override public VisibilityLevel getVisibilityLevelToPlayerFleet() {return null;}
        @Override public boolean hasAbility(String arg0) {return false;}
        @Override public boolean hasDiscoveryXP() {return false;}
        @Override public boolean hasSalvageXP() {return false;}
        @Override public boolean hasScriptOfClass(Class arg0) {return false;}
        @Override public boolean hasSensorProfile() {return false;}
        @Override public boolean hasSensorStrength() {return false;}
        @Override public boolean isAlive() {return false;}
        @Override public boolean isDiscoverable() {return false;}
        @Override public boolean isExpired() {return false;}
        @Override public boolean isFreeTransfer() {return false;}
        @Override public boolean isInCurrentLocation() {return false;}
        @Override public boolean isInHyperspace() {return true;}
        @Override public boolean isInOrNearSystem(StarSystemAPI arg0) {return false;}
        @Override public boolean isPlayerFleet() {return false;}
        @Override public boolean isSkipForJumpPointAutoGen() {return false;}
        @Override public boolean isStar() {return false;}
        @Override public boolean isSystemCenter() {return true;}
        @Override public boolean isTransponderOn() {return false;}
        @Override public boolean isVisibleToPlayerFleet() {return true;}
        @Override public boolean isVisibleToSensorsOf(SectorEntityToken arg0) {return false;}
        @Override public void removeAbility(String arg0) {}
        @Override public void removeScript(EveryFrameScript arg0) {}
        @Override public void removeScriptsOfClass(Class arg0) {}
        @Override public void removeTag(String arg0) {}
        @Override public void setActivePerson(PersonAPI arg0) {}
        @Override public void setAlwaysUseSensorFaderBrightness(Boolean arg0) {}
        @Override public void setAutogenJumpPointNameInHyper(String arg0) {}
        @Override public void setCircularOrbit(SectorEntityToken arg0, float arg1, float arg2, float arg3) {}
        @Override public void setCircularOrbitAngle(float arg0) {}
        @Override public void setCircularOrbitPointingDown(SectorEntityToken arg0, float arg1, float arg2, float arg3) {}
        @Override public void setCircularOrbitWithSpin (SectorEntityToken arg0, float arg1, float arg2, float arg3, float arg4, float arg5) {}
        @Override public void setContainingLocation(LocationAPI arg0) {}
        @Override public void setCustomDescriptionId(String arg0) {}
        @Override public void setCustomInteractionDialogImageVisual(InteractionDialogImageVisual arg0) {}
        @Override public void setDetectionRangeDetailsOverrideMult(Float arg0) {}
        @Override public void setDiscoverable(Boolean arg0) {}
        @Override public void setDiscoveryXP(Float arg0) {}
        @Override public void setExpired(boolean arg0) {}
        @Override public void setExtendedDetectedAtRange(Float arg0) {}
        @Override public void setFacing(float arg0) {}
        @Override public void setFaction(String arg0) {}
        @Override public void setFixedLocation(float arg0, float arg1) {}
        @Override public void setFreeTransfer(boolean arg0) {}
        @Override public void setId(String arg0) {}
        @Override public void setInteractionImage(String arg0, String arg1) {}
        @Override public void setLightSource(SectorEntityToken arg0, Color arg1) {}
        @Override public void setLocation(float arg0, float arg1) {}
        @Override public void setMarket(MarketAPI arg0) {}
        @Override public void setMemory(MemoryAPI arg0) {}
        @Override public void setName(String arg0) {}
        @Override public void setOrbit(OrbitAPI arg0) {}
        @Override public void setOrbitFocus(SectorEntityToken arg0) {}
        @Override public void setSalvageXP(Float arg0) {}
        @Override public void setSensorProfile(Float arg0) {}
        @Override public void setSensorStrength(Float arg0) {}
        @Override public void setSkipForJumpPointAutoGen(boolean arg0) {}
        @Override public void setTransponderOn(boolean arg0) {}
        @Override public void addDropRandom(String arg0, int arg1) {}
    }

    // lol lmao
    // unused. keeping just in case
    public static class DummySystem implements StarSystemAPI {
        private static final Vector2f loc = new Vector2f(-1f,-1f);

        @Override public Vector2f getLocation() {return loc;}
        @Override public PlanetAPI initStar(String var1, String var2, float var3, float var4, float var5, float var6, float var7) {return null;}
        @Override public PlanetAPI initStar(String var1, String var2, float var3, float var4) {return null;}
        @Override public void generateAnchorIfNeeded() {}
        @Override public PlanetAPI initStar(String var1, String var2, Color var3, float var4, float var5) {return null;}
        @Override public PlanetAPI initStar(String var1, String var2, float var3, float var4, float var5, float var6) {return null;}
        @Override public SectorEntityToken getHyperspaceAnchor() {return null;}
        @Override public void setHyperspaceAnchor(SectorEntityToken var1) {}
        @Override public PlanetAPI getStar() {return null;}
        @Override public void autogenerateHyperspaceJumpPoints() {}
        @Override public void autogenerateHyperspaceJumpPoints(boolean var1, boolean var2) {}
        @Override public Color getLightColor() {return null;}
        @Override public void setLightColor(Color var1) {}
        @Override public String getBaseName() {return null;}
        @Override public float getMaxRadiusInHyperspace() {return 0f;}
        @Override public SectorEntityToken initNonStarCenter() {return null;}
        @Override public SectorEntityToken getCenter() {return null;}
        @Override public void setStar(PlanetAPI var1) {}
        @Override public void setBaseName(String var1) {}
        @Override public PlanetAPI getSecondary() {return null;}
        @Override public void setSecondary(PlanetAPI var1) {}
        @Override public PlanetAPI getTertiary() {return null;}
        @Override public void setTertiary(PlanetAPI var1) {}
        @Override public List<JumpPointAPI> getAutogeneratedJumpPointsInHyper() {return null;}
        @Override public StarSystemGenerator.StarSystemType getType() {return null;}
        @Override public void setType(StarSystemGenerator.StarSystemType var1) {}
        @Override public Constellation getConstellation() {return null;}
        @Override public boolean isInConstellation() {return false;}
        @Override public void setConstellation(Constellation var1) {}
        @Override public void setCenter(SectorEntityToken var1) {}
        @Override public void autogenerateHyperspaceJumpPoints(boolean var1, boolean var2, boolean var3) {}
        @Override public void setProcgen(boolean var1) {}
        @Override public boolean isProcgen() {return false;}
        @Override public StarAge getAge() {return null;}
        @Override public void setAge(StarAge var1) {}
        @Override public Boolean hasSystemwideNebula() {return null;}
        @Override public void setHasSystemwideNebula(Boolean var1) {}
        @Override public boolean isEnteredByPlayer() {return false;}
        @Override public void setEnteredByPlayer(boolean var1) {}
        @Override public long getLastPlayerVisitTimestamp() {return 0L;}
        @Override public float getDaysSinceLastPlayerVisit() {return 0f;}
        @Override public boolean hasPulsar() {return false;}
        @Override public Boolean getDoNotShowIntelFromThisLocationOnMap() {return null;}
        @Override public void setDoNotShowIntelFromThisLocationOnMap(Boolean var1) {}
        @Override public boolean hasBlackHole() {return false;}
        @Override public Float getMapGridWidthOverride() {return null;}
        @Override public void setMapGridWidthOverride(Float var1) {}
        @Override public Float getMapGridHeightOverride() {return null;}
        @Override public void setMapGridHeightOverride(Float var1) {}
        @Override public void setMaxRadiusInHyperspace(float var1) {}
        @Override public String getOptionalUniqueId() {return null;}
        @Override public void setOptionalUniqueId(String var1) {}
        @Override public List<NascentGravityWellAPI> getAutogeneratedNascentWellsInHyper() {return null;}
        @Override public String getId() {return null;}
        @Override public boolean activeThisFrame() {return false;}
        @Override public String getBackgroundTextureFilename() {return null;}
        @Override public void setBackgroundTextureFilename(String var1) {}
        @Override public void addSpawnPoint(SpawnPointPlugin var1) {}
        @Override public void removeSpawnPoint(SpawnPointPlugin var1) {}
        @Override public List<SpawnPointPlugin> getSpawnPoints() {return null;}
        @Override public void spawnFleet(SectorEntityToken var1, float var2, float var3, CampaignFleetAPI var4) {}
        @Override public SectorEntityToken createToken(float var1, float var2) {return null;}
        @Override public SectorEntityToken createToken(Vector2f var1) {return null;}
        @Override public void addEntity(SectorEntityToken var1) {}
        @Override public void removeEntity(SectorEntityToken var1) {}
        @Override public PlanetAPI addPlanet(String var1, SectorEntityToken var2, String var3, String var4, float var5, float var6, float var7, float var8) {return null;}
        @Override public SectorEntityToken addAsteroidBelt(SectorEntityToken var1, int var2, float var3, float var4, float var5, float var6) {return null;}
        @Override public SectorEntityToken addAsteroidBelt(SectorEntityToken var1, int var2, float var3, float var4, float var5, float var6, String var7, String var8) {return null;}
        @Override public void addOrbitalJunk(SectorEntityToken var1, String var2, int var3, float var4, float var5, float var6, float var7, float var8, float var9, float var10, float var11) {}
        @Override public RingBandAPI addRingBand(SectorEntityToken var1, String var2, String var3, float var4, int var5, Color var6, float var7, float var8, float var9) {return null;}
        @Override public SectorEntityToken addRingBand(SectorEntityToken var1, String var2, String var3, float var4, int var5, Color var6, float var7, float var8, float var9, String var10, String var11) {return null;}
        @Override public CustomCampaignEntityAPI addCustomEntity(String var1, String var2, String var3, String var4) {return null;}
        @Override public CustomCampaignEntityAPI addCustomEntity(String var1, String var2, String var3, String var4, float var5, float var6, float var7) {return null;}
        @Override public SectorEntityToken addTerrain(String var1, Object var2) {return null;}
        @Override public List getEntities(Class var1) {return null;}
        @Override public List<SectorEntityToken> getEntitiesWithTag(String var1) {return null;}
        @Override public List<CampaignFleetAPI> getFleets() {return null;}
        @Override public List<PlanetAPI> getPlanets() {return null;}
        @Override public List<SectorEntityToken> getOrbitalStations() {return null;}
        @Override public List<SectorEntityToken> getAsteroids() {return null;}
        @Override public SectorEntityToken getEntityByName(String var1) {return null;}
        @Override public SectorEntityToken getEntityById(String var1) {return null;}
        @Override public boolean isHyperspace() {return false;}
        @Override public void addScript(EveryFrameScript var1) {}
        @Override public void removeScriptsOfClass(Class var1) {}
        @Override public void removeScript(EveryFrameScript var1) {}
        @Override public String getName() {return "";}
        @Override public void setName(String var1) {}
        @Override public List<SectorEntityToken> getAllEntities() {return null;}
        @Override public SectorEntityToken addCorona(SectorEntityToken var1, float var2, float var3, float var4, float var5) {return null;}
        @Override public SectorEntityToken addCorona(SectorEntityToken var1, String var2, float var3, float var4, float var5, float var6) {return null;}
        @Override public List<CampaignTerrainAPI> getTerrainCopy() {return null;}
        @Override public Map<String, Object> getPersistentData() {return null;}
        @Override public AsteroidAPI addAsteroid(float var1) {return null;}
        @Override public void setBackgroundOffset(float var1, float var2) {}
        @Override public SectorEntityToken addRadioChatter(SectorEntityToken var1, float var2) {return null;}
        @Override public void updateAllOrbits() {}
        @Override public boolean isNebula() {return false;}
        @Override public String getNameWithLowercaseType() {return "";}
        @Override public List<FleetStubAPI> getFleetStubs() {return null;}
        @Override public void removeFleetStub(FleetStubAPI var1) {}
        @Override public void addFleetStub(FleetStubAPI var1) {}
        @Override public String getNameWithTypeIfNebula() {return "";}
        @Override public Collection<String> getTags() {return null;}
        @Override public boolean hasTag(String var1) {return false;}
        @Override public void addTag(String var1) {}
        @Override public void removeTag(String var1) {}
        @Override public void clearTags() {}
        @Override public CustomCampaignEntityAPI addCustomEntity(String var1, String var2, String var3, String var4, float var5, float var6, float var7, Object var8) {return null;}
        @Override public CustomCampaignEntityAPI addCustomEntity(String var1, String var2, String var3, String var4, Object var5) {return null;}
        @Override public List<SectorEntityToken> getJumpPoints() {return null;}
        @Override public List<CustomCampaignEntityAPI> getCustomEntitiesWithTag(String var1) {return null;}
        @Override public List<EveryFrameScript> getScripts() {return null;}
        @Override public void addHitParticle(Vector2f var1, Vector2f var2, float var3, float var4, float var5, Color var6) {}
        @Override public void renderingLayersUpdated(SectorEntityToken var1) {}
        @Override public MemoryAPI getMemoryWithoutUpdate() {return memory;}
        @Override public ParticleControllerAPI addParticle(Vector2f var1, Vector2f var2, float var3, float var4, float var5, float var6, Color var7) {return null;}
        @Override public String getNameWithNoType() {return "";}
        @Override public boolean isCurrentLocation() {return false;}
        @Override public String getNameWithLowercaseTypeShort() {return "";}
        @Override public String getNameWithTypeShort() {return "";}
        @Override public List<NascentGravityWellAPI> getGravityWells() {return null;}
        @Override public List<CustomCampaignEntityAPI> getCustomEntities() {return null;}
        @Override public ColorShifterAPI getBackgroundColorShifter() {return null;}
        @Override public ColorShifterAPI getBackgroundParticleColorShifter() {return null;}
        @Override public boolean isDeepSpace() {return false;}
    }

    private static final MemoryAPI memory = new MemoryAPI() {
        @Override public void addRequired(String arg0, String arg1) {}
        @Override public void advance(float arg0) {}
        @Override public boolean between(String arg0, float arg1, float arg2) {return false;}
        @Override public void clear() {}
        @Override public boolean contains(String arg0) {return false;}
        @Override public void expire(String arg0, float arg1) {}
        @Override public Object get(String arg0) {return null;}
        @Override public boolean getBoolean(String arg0) {return false;}
        @Override public SectorEntityToken getEntity(String arg0) {return null;}
        @Override public float getExpire(String arg0) {return 0f;}
        @Override public CampaignFleetAPI getFleet(String arg0) {return null;}
        @Override public float getFloat(String arg0) {return 0f;}
        @Override public int getInt(String arg0) {return 0;}
        @Override public Collection<String> getKeys() {return null;}
        @Override public long getLong(String arg0) {return 0L;}
        @Override public Set<String> getRequired(String arg0) {return null;}
        @Override public String getString(String arg0) {return null;}
        @Override public Vector2f getVector2f(String arg0) {return null;}
        @Override public boolean is(String arg0, Object arg1) {return false;}
        @Override public boolean is(String arg0, float arg1) {return false;}
        @Override public boolean is(String arg0, boolean arg1) {return false;}
        @Override public boolean isEmpty() {return true;}
        @Override public void removeAllRequired(String arg0) {}
        @Override public void removeRequired(String arg0, String arg1) {}
        @Override public void set(String arg0, Object arg1) {}
        @Override public void set(String arg0, Object arg1, float arg2) {}
        @Override public void unset(String arg0) {}
    };
}
