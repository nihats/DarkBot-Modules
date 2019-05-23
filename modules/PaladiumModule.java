package com.github.manolo8.darkbot.modules;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.Config;
import com.github.manolo8.darkbot.core.entities.Box;
import com.github.manolo8.darkbot.core.itf.Module;
import com.github.manolo8.darkbot.core.manager.HangarManager;
import com.github.manolo8.darkbot.core.manager.HeroManager;
import com.github.manolo8.darkbot.core.manager.PetManager;
import com.github.manolo8.darkbot.core.manager.StatsManager;
import com.github.manolo8.darkbot.core.utils.Drive;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

import static com.github.manolo8.darkbot.Main.API;

public class PaladiumModule implements Module {

    /**
     * Paladium Module Test v0.0.4
     * Made by @Dm94Dani
     */

    private LootModule lootModule;
    private CollectorModule collectorModule;

    private HeroManager hero;
    private Drive drive;
    private Config config;
    private StatsManager statsManager;

    private String hangarPalladium = "0000000"; /* Put your hangar id */
    private String hangerBase = "0000000"; /* Put your hangar id */
    private String hangarActive = "";
    private long lastCheckupHangar = 0;
    private Main main;
    private long disconectTime = System.currentTimeMillis();
    private Character exitKey = 'l';

    public PaladiumModule() {
        this.lootModule = new LootModule();
        this.collectorModule = new CollectorModule();
    }

    @Override
    public void install(Main main) {
        lootModule = new LootModule();
        collectorModule = new CollectorModule();
        lootModule.install(main);
        collectorModule.install(main);

        this.main = main;
        this.hero = main.hero;
        this.drive = main.hero.drive;
        this.config = main.config;
        this.statsManager = main.statsManager;
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
            this.main.backpage.hangarManager.updateHangars();
            hangarActive = this.main.backpage.hangarManager.getActiveHangar();
            lastCheckupHangar = System.currentTimeMillis();
        }

        if (statsManager.deposit >= statsManager.depositTotal && statsManager.depositTotal != 0) {
            if (hangarActive == hangerBase) {
                if (this.hero.map.id == 92){

                } else {
                    hero.roamMode();
                    this.main.setModule(new MapModule()).setTarget(this.main.starManager.byId(92));
                }

            } else {
                disconectAndChangeHangar(hangerBase);
            }

        } else if(hangarActive == hangarPalladium) {
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
            }
        } else {
            disconectAndChangeHangar(hangarPalladium);
        }
    }

    public void disconectAndChangeHangar(String hangar) {
        if (this.disconectTime == 0) {
            disconnect();
        } else if (this.disconectTime <= System.currentTimeMillis() - 20000) {
            this.main.backpage.hangarManager.changeHangar(hangar);
        } else {
            return;
        }
    }

    public void disconnect() {
        API.keyboardClick(this.exitKey);
        this.disconectTime = System.currentTimeMillis();
    }

}
