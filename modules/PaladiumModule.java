package com.github.manolo8.darkbot.modules;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.Config;
import com.github.manolo8.darkbot.config.types.Option;
import com.github.manolo8.darkbot.core.entities.Box;
import com.github.manolo8.darkbot.core.entities.Npc;
import com.github.manolo8.darkbot.core.entities.Ship;
import com.github.manolo8.darkbot.core.itf.CustomModule;
import com.github.manolo8.darkbot.backpage.HangarManager;
import com.github.manolo8.darkbot.core.manager.HeroManager;
import com.github.manolo8.darkbot.core.manager.StatsManager;
import com.github.manolo8.darkbot.core.objects.LocationInfo;
import com.github.manolo8.darkbot.core.utils.Drive;
import com.github.manolo8.darkbot.core.utils.Location;
import com.github.manolo8.darkbot.modules.utils.NpcAttacker;
import com.github.manolo8.darkbot.modules.utils.SafetyFinder;

import java.util.Comparator;
import java.util.List;

import static com.github.manolo8.darkbot.Main.API;
import static java.lang.Double.max;
import static java.lang.Double.min;
import static java.lang.Math.random;

public class PaladiumModule implements CustomModule {

    /**
     * Paladium Module Test v0.0.7
     * Made by @Dm94Dani
     */

    private HeroManager hero;
    private Drive drive;
    private Config config;
    private StatsManager statsManager;

    private String hangarActive = "";
    private long lastCheckupHangar = 0;
    private Main main;
    private long disconectTime = System.currentTimeMillis();
    private PaladiumConfig configPa;
    private HangarManager hangarManager;

    private final LootModule lootModule;
    private final CollectorModule collectorModule;

    /**
     * Collector Module
     */

    Box current;
    private long waiting;
    private List<Box> boxes;
    private int DISTANCE_FROM_DANGEROUS = 1500;

    /**
     * Loot Module
     */

    private SafetyFinder safety;
    NpcAttacker attack;
    private List<Ship> ships;
    private List<Npc> npcs;
    private int radiusFix;

    public Object configuration() {
        return new PaladiumConfig();
    }

    public static class PaladiumConfig {
        @Option("Exit Key")
        public char exitKey = 'l';

        @Option(value = "Hangar Palladium", description = "Ship 5-3 Hangar ID")
        public String hangarPalladium = "";

        @Option(value = "Hangar Base", description = "Ship 5-2 Hangar ID")
        public String hangarBase = "";

    }

    @Override
    public String name() {
        return "Paladium Module";
    }

    public PaladiumModule() {
        this.lootModule = new LootModule();
        this.collectorModule = new CollectorModule();
    }

    @Override
    public void install(Main main) {

        this.main = main;
        this.hero = main.hero;
        this.drive = main.hero.drive;
        this.config = main.config;
        this.statsManager = main.statsManager;
        this.configPa = new PaladiumConfig();
        this.hangarManager = new HangarManager(main,main.backpage);

        lootModule.install(main);
        collectorModule.install(main);

        this.attack = new NpcAttacker(main);
        this.safety = new SafetyFinder(main);
        this.npcs = main.mapManager.entities.npcs;

        this.ships = main.mapManager.entities.ships;

        this.boxes = main.mapManager.entities.boxes;

        hangarManager.updateHangars();
        hangarActive = hangarManager.getActiveHangar();
    }

    @Override
    public String status() {
        return "Loot: " + lootModule.status() + " - Collect: " + collectorModule.status();
    }

    @Override
    public boolean canRefresh() {
        if(collectorModule.isNotWaiting()) {
            return lootModule.canRefresh();
        }

        return false;
    }

    @Override
    public void tick() {
        if (lastCheckupHangar <= System.currentTimeMillis() - 300000 && main.backpage.sidStatus().contains("OK")) {
            hangarManager.updateHangars();
            hangarActive = hangarManager.getActiveHangar();
            lastCheckupHangar = System.currentTimeMillis();
        }

        if (statsManager.deposit >= statsManager.depositTotal) {
            if (hangarActive.equalsIgnoreCase(configPa.hangarBase)) {
                if (this.hero.map.id == 92){

                } else {
                    hero.roamMode();
                    this.main.setModule(new MapModule()).setTarget(this.main.starManager.byId(92));
                }

            } else {
                disconectAndChangeHangar(configPa.hangarBase);
            }

        } else if(hangarActive.equalsIgnoreCase(configPa.hangarPalladium)) {
            if (this.hero.map.id == 93){
                if (collectorModule.isNotWaiting() && lootModule.checkDangerousAndCurrentMap()) {
                    main.guiManager.pet.setEnabled(true);

                    if (lootModule.findTarget()) {

                        collectorModule.findBox();

                        Box box = collectorModule.current;

                        if (box == null || box.locationInfo.distance(hero) > config.LOOT_COLLECT.RADIUS
                                || lootModule.attack.target.health.hpPercent() < 0.25) {
                            lootModule.moveToAnSafePosition();
                        } else {
                            collectorModule.tryCollectNearestBox();
                        }

                        lootModule.ignoreInvalidTarget();
                        lootModule.attack.doKillTargetTick();

                    } else {
                        hero.roamMode();
                        collectorModule.findBox();

                        if (!collectorModule.tryCollectNearestBox() && (!drive.isMoving() || drive.isOutOfMap())) {
                            drive.moveRandom();
                        }

                    }

                }
            } else {
                hero.roamMode();
                this.main.setModule(new MapModule()).setTarget(this.main.starManager.byId(93));
                return;
            }
        } else {
            disconectAndChangeHangar(configPa.hangarPalladium);
        }
    }

    public void disconectAndChangeHangar(String hangar) {
        if (this.disconectTime == 0) {
            disconnect();
        } else if (this.disconectTime <= System.currentTimeMillis() - 21000) {
            hangarManager.changeHangar(hangar);
        } else {
            return;
        }
    }

    public void disconnect() {
        API.keyboardClick(this.configPa.exitKey);
        this.disconectTime = System.currentTimeMillis();
    }

}
