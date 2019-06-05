package com.github.manolo8.darkbot.modules;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.Config;
import com.github.manolo8.darkbot.config.types.Option;
import com.github.manolo8.darkbot.core.entities.Box;
import com.github.manolo8.darkbot.core.itf.CustomModule;
import com.github.manolo8.darkbot.backpage.HangarManager;
import com.github.manolo8.darkbot.core.manager.HeroManager;
import com.github.manolo8.darkbot.core.manager.StatsManager;
import com.github.manolo8.darkbot.core.utils.Drive;
import com.github.manolo8.darkbot.modules.utils.NpcAttacker;
import com.github.manolo8.darkbot.modules.utils.SafetyFinder;


import static com.github.manolo8.darkbot.Main.API;

public class PaladiumModule implements CustomModule {

    /**
     * Paladium Module Test v0.0.8
     * Made by @Dm94Dani
     */

    private HeroManager hero;
    private Drive drive;
    private Config config;
    private StatsManager statsManager;

    private String hangarActive = "";
    private long lastCheckupHangar = 0;
    private Main main;
    private long disconectTime = 0;
    private int state = 0;
    private PaladiumConfig configPa;
    private HangarManager hangarManager;

    private final LootModule lootModule;
    private final CollectorModule collectorModule;

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
                    this.state = 1;
                } else {
                    this.state = 2;
                    hero.roamMode();
                    this.main.setModule(new MapModule()).setTarget(this.main.starManager.byId(92));
                }

            } else {
                this.state = 3;
                disconectAndChangeHangar(configPa.hangarBase);
            }

        } else if(hangarActive.equalsIgnoreCase(configPa.hangarPalladium)) {
            if (this.hero.map.id == 93){
                this.state = 4;
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
                this.state = 5;
                hero.roamMode();
                this.main.setModule(new MapModule()).setTarget(this.main.starManager.byId(93));
                return;
            }
        } else {
            this.state = 6;
            disconectAndChangeHangar(configPa.hangarPalladium);
        }
    }

    public void disconectAndChangeHangar(String hangar) {
        if (this.disconectTime == 0 && this.state != 7) {
            this.state = 7;
            disconnect();
        } else if (this.disconectTime <= System.currentTimeMillis() - 21000 && this.state != 8) {
            hangarManager.changeHangar(hangar);
            this.state = 8;
        } else if (this.state == 8){
            this.state = 9;
            this.disconectTime = 0;
            API.handleRefresh();
        }
    }

    public void disconnect() {
        API.keyboardClick(this.configPa.exitKey);
        this.disconectTime = System.currentTimeMillis();
    }

}
