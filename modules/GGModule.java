package com.github.manolo8.darkbot.modules;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.Config;
import com.github.manolo8.darkbot.config.types.Editor;
import com.github.manolo8.darkbot.config.types.Options;
import com.github.manolo8.darkbot.config.types.suppliers.OptionList;
import com.github.manolo8.darkbot.core.entities.Npc;
import com.github.manolo8.darkbot.core.manager.HeroManager;
import com.github.manolo8.darkbot.core.manager.StarManager;
import com.github.manolo8.darkbot.core.utils.Drive;
import com.github.manolo8.darkbot.core.utils.Location;
import com.github.manolo8.darkbot.gui.tree.components.JListField;
import com.github.manolo8.darkbot.gui.tree.components.JShipConfigField;
import com.github.manolo8.darkbot.modules.utils.NpcAttacker;
import com.github.manolo8.darkbot.config.types.Option;
import com.github.manolo8.darkbot.core.itf.CustomModule;
import com.github.manolo8.darkbot.utils.ByteArrayToBase64TypeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import static java.lang.Double.max;
import static java.lang.Double.min;

public class GGModule implements CustomModule {
    private String version = "v1 Beta 18";
    private static final double TAU = Math.PI * 2;

    private Main main;
    private Config config;
    private List<Npc> npcs;
    private HeroManager hero;
    private Drive drive;
    private Location direction;
    private int radiusFix;
    private GGConfig ggConfig = new GGConfig();

    private boolean repairing;
    private int rangeNPCFix = 0;
    private long lastCheck = System.currentTimeMillis();
    private int lasNpcHealth = 0;
    private int lasPlayerHealth = 0;
    NpcAttacker attack;

    private final CollectorModule collectorModule;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .setLenient()
            .registerTypeHierarchyAdapter(byte[].class, new ByteArrayToBase64TypeAdapter()).create();
    private boolean configSave = false;


    public Object configuration() {
        return ggConfig;
    }

    public static class GGConfig {
        @Option(value = "Honor config", description = "Used on finish wave")
        @Editor(JShipConfigField.class)
        public Config.ShipConfig Honor = new Config.ShipConfig();

        @Option("GG Gate")
        @Editor(value = JListField.class)
        @Options(value = GGSuplier.class)
        public int idGate = 51;

        @Option("Take materials")
        public boolean takeBoxes = false;
    }

    @Override
    public String name() {
        return "GG Module";
    }

    public GGModule() {
        this.collectorModule = new CollectorModule();
    }


    @Override
    public void install(Main main) {
        this.main = main;
        this.config = main.config;
        this.attack = new NpcAttacker(main);
        this.hero = main.hero;
        this.drive = main.hero.drive;
        this.npcs = main.mapManager.entities.npcs;

        collectorModule.install(main);
        loadConfig();
    }

    public static class GGSuplier implements Supplier<OptionList> {
        @Override
        public OptionList<Integer> get() {
            return new GGList();
        }
    }

    public static class GGList extends OptionList<Integer> {
        private static final StarManager starManager = new StarManager();

        @Override
        public Integer getValue(String text) {
            return starManager.byName(text).id;
        }

        @Override
        public String getText(Integer value) {
            return starManager.byId(value).name;
        }

        @Override
        public List<String> getOptions() {
            return new ArrayList<>(starManager.getGGMaps());
        }
    }

    private void loadConfig() {
        File config = new File("ggconfig.json");
        if (!config.exists()) {
            saveConfig();
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(config), StandardCharsets.UTF_8)) {
            this.ggConfig = GSON.fromJson(reader, GGConfig.class);
            if (this.ggConfig == null) this.ggConfig = new GGConfig();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveConfig() {
        File config = new File("ggconfig.json");
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(config), StandardCharsets.UTF_8)) {
            GSON.toJson(this.ggConfig, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean canRefresh() {
        return attack.target == null;
    }

    @Override
    public String status() {
        return name() + " " + version + " | " + (repairing ? "Repairing" :
                attack.hasTarget() ? attack.status() : "Roaming") + " | NPCs: "+this.npcs.size();
    }

    @Override
    public void tick() {
        if (!configSave) {
            loadConfig();
            configSave = true;
        }

        if (main.hero.map.gg) {
            main.guiManager.pet.setEnabled(true);
            if (findTarget()) {
                hero.attackMode();
                attack.doKillTargetTick();
                removeLowHeal();
                moveToAnSafePosition();
            } else if (!main.mapManager.entities.portals.isEmpty() && collectorModule.isNotWaiting()) {
                hero.roamMode();

                if (ggConfig.takeBoxes) {
                    collectorModule.findBox();
                }
                if (!collectorModule.tryCollectNearestBox() && (!drive.isMoving() || drive.isOutOfMap())) {
                    if (hero.health.hpPercent() >= config.GENERAL.SAFETY.REPAIR_TO_HP) {
                        repairing = false;
                        this.main.setModule(new MapModule()).setTarget(main.starManager.byId(main.mapManager.entities.portals.get(0).id));
                    } else {
                        drive.moveRandom();
                        repairing = true;
                    }
                }
            } else if (!drive.isMoving()) {
                hero.setMode(ggConfig.Honor);
                drive.moveRandom();
            }
        } else if ( main.hero.map.id == 1 || main.hero.map.id == 5 || main.hero.map.id == 9) {
            if (ggConfig.idGate == 73){ ggConfig.idGate = 71; }
            hero.roamMode();
            for (int i=0; i < main.mapManager.entities.portals.size();i++){
                if (main.mapManager.entities.portals.get(i).target.id == ggConfig.idGate){
                    this.main.setModule(new MapModule()).setTarget(main.starManager.byId(ggConfig.idGate));
                    return;
                }
            }
        } else {
            hero.roamMode();
            this.main.setModule(new MapModule()).setTarget(this.main.starManager.byName("Home Map"));
        }
    }

    private boolean findTarget() {
        if (attack.target == null || attack.target.removed) {
            if (!npcs.isEmpty()) {
                if (!allLowLife()) {
                    attack.target = bestNpc(hero.locationInfo.now);
                } else {
                    attack.target = closestNpc(hero.locationInfo.now);
                }
            } else {
                attack.target = null;
            }
        } else if (attack.target.health.hpPercent() < 0.25 && !allLowLife()) {
            attack.target = null;
        }
        return attack.target != null;
    }

    private void removeLowHeal() {
        if (main.mapManager.isTarget(attack.target) && (attack.target.health.hpPercent() < 0.25)) {
            if (!allLowLife()) {
                if(isLowHealh(attack.target)){
                    attack.target = null;
                    return;
                }
            }
        }
    }

    public boolean isLowHealh(Npc npc){
        return npc.health.hpPercent() < 0.25;
    }

    private boolean allLowLife(){
        int npcsLowLife = 0;

        for (int i=0; i < npcs.size();i++) {
            if (isLowHealh(npcs.get(i)) || npcs.get(i).ish) {
                npcsLowLife++;
            }
        }

        return npcsLowLife >= npcs.size();
    }

    private void moveToAnSafePosition() {
        Npc target = attack.target;
        if (!hero.drive.isMoving()) direction = null;
        Location heroLoc = hero.locationInfo.now;
        if (target == null ||target.locationInfo == null) return;
        Location targetLoc = target.locationInfo.destinationInTime(400);

        double angle = targetLoc.angle(heroLoc), distance = heroLoc.distance(targetLoc), radius = target.npcInfo.radius;;

        dinamicNPCRange(distance);

        radius += rangeNPCFix;

        if (distance > radius) {
            radiusFix -= (distance - radius) / 2;
            radiusFix = (int) max(radiusFix, -target.npcInfo.radius / 2);
        } else {
            radiusFix += (radius - distance) / 6;
            radiusFix = (int) min(radiusFix, target.npcInfo.radius / 2);
        }
        distance = (radius += radiusFix);
        angle += Math.max((hero.shipInfo.speed * 0.625) + (min(200, target.locationInfo.speed) * 0.625)
                - heroLoc.distance(Location.of(targetLoc, angle, radius)), 0) / radius;

        direction = Location.of(targetLoc, angle, distance);
        while (!drive.canMove(direction) && distance < 10000)
            direction.toAngle(targetLoc, angle += 0.3, distance += 2);
        if (distance >= 10000) direction.toAngle(targetLoc, angle, 500);

        drive.move(direction);
    }

    private void dinamicNPCRange(double distance){
        if (hero.health.hpPercent() <= config.GENERAL.SAFETY.REPAIR_HP){
            rangeNPCFix = 1000;
            repairing = true;
        } else if  (hero.health.hpPercent() >= config.GENERAL.SAFETY.REPAIR_TO_HP){
            rangeNPCFix = 0;
            repairing = false;
        }

        if (lastCheck <= System.currentTimeMillis()-8000 && distance <= 1000) {
            if (lasPlayerHealth > hero.health.hp && rangeNPCFix < 500) {
                rangeNPCFix += 50;
            } else if (lasNpcHealth == attack.target.health.hp) {
                rangeNPCFix -= 50;
            }
            lasPlayerHealth =  hero.health.hp;
            lasNpcHealth = attack.target.health.hp;
            lastCheck = System.currentTimeMillis();
        }
    }

    private Npc closestNpc(Location location) {
        return this.npcs.stream()
                .filter(n -> (!n.ish))
                .min(Comparator.<Npc>comparingDouble(n -> n.locationInfo.now.distance(location))
                        .thenComparing(n -> n.npcInfo.priority)
                        .thenComparing(n -> n.health.hpPercent())).orElse(null);
    }

    private Npc bestNpc(Location location) {
        return this.npcs.stream()
                .filter(n -> (!n.ish && n.health.hpPercent() > 0.25))
                .min(Comparator.<Npc>comparingDouble(n -> (n.npcInfo.priority))
                        .thenComparing(n -> (n.locationInfo.now.distance(location)))).orElse(null);
    }


}

