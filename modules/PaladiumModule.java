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

    private String version = "Alpha v0.0.10";

    private HeroManager hero;
    private Drive drive;
    private Config config;
    private StatsManager statsManager;

    private String hangarActive = "";
    private long lastCheckupHangar = 0;
    private Main main;
    private long disconectTime = 0;
    private enum State {
        LOADING ("Loading"),
        HANGAR_AND_MAP_BASE ( "Selling palladium"),
        HANGAR_BASE_OTHER_MAP ( "Hangar Base - To 5-2"),
        DEPOSIT_FULL_SWITCHING_HANGAR("Deposit full, switching hangar"),
        LOOT_PALADIUM("Loot paladium"),
        HANGAR_PALA_OTHER_MAP("Hangar paladium - To 5-3"),
        SWITCHING_PALA_HANGAR("Switching to the palladium hangar"),
        DISCONNECTING("Disconnecting"),
        SWITCHING_HANGAR("Switching Hangar"),
        RELOAD_GAME("Reloading the game");

        private String message;

        State(String message) {
            this.message = message;
        }
    }
    private State currentStatus;
    private PaladiumConfig configPa;
    private HangarManager hangarManager;

    private final CollectorModule collectorModule;

    private List<Ship> ships;
    private List<Npc> npcs;
    private int radiusFix;
    NpcAttacker attack;

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

        this.ships = main.mapManager.entities.ships;
        this.npcs = main.mapManager.entities.npcs;
        this.attack = new NpcAttacker(main);
        currentStatus = State.LOADING;

        collectorModule.install(main);

        hangarManager.updateHangars();
        hangarActive = hangarManager.getActiveHangar();
    }

    @Override
    public String status() {
        return  name() + " " + version + " | Status: " + currentStatus.message;
    }

    @Override
    public boolean canRefresh() {
        if(collectorModule.isNotWaiting()) {
            return lootCanRefresh();
        }

        return false;
    }

    @Override
    public void tick() {
        if (lastCheckupHangar <= System.currentTimeMillis() - 300000){
            updateDataHangars();
        }

        if (statsManager.deposit >= statsManager.depositTotal) {
            if (hangarActive.equalsIgnoreCase(configPa.hangarBase)) {
                if (this.hero.map.id == 92){
                    this.currentStatus = State.HANGAR_AND_MAP_BASE;
                } else {
                    this.currentStatus = State.HANGAR_BASE_OTHER_MAP;
                    hero.roamMode();
                    this.main.setModule(new MapModule()).setTarget(this.main.starManager.byId(92));
                }

            } else {
                this.currentStatus = State.DEPOSIT_FULL_SWITCHING_HANGAR;
                disconectAndChangeHangar(configPa.hangarBase);
            }

        } else if(hangarActive.equalsIgnoreCase(configPa.hangarPalladium)) {
            if (this.hero.map.id == 93){
                this.currentStatus = State.LOOT_PALADIUM;
                if (collectorModule.isNotWaiting()) {
                    main.guiManager.pet.setEnabled(true);

                    if (findTarget()) {

                        collectorModule.findBox();

                        Box box = collectorModule.current;

                        if (box == null || box.locationInfo.distance(hero) > config.LOOT_COLLECT.RADIUS
                                || attack.target.health.hpPercent() < 0.25) {
                            moveToAnSafePosition();
                        } else {
                            collectorModule.tryCollectNearestBox();
                        }

                        ignoreInvalidTarget();
                        attack.doKillTargetTick();

                    } else {
                        hero.roamMode();
                        collectorModule.findBox();

                        if (!collectorModule.tryCollectNearestBox() && (!drive.isMoving() || drive.isOutOfMap())) {
                            drive.moveRandom();
                        }

                    }

                }
            } else {
                this.currentStatus = State.HANGAR_PALA_OTHER_MAP;
                hero.roamMode();
                this.main.setModule(new MapModule()).setTarget(this.main.starManager.byId(93));
                return;
            }
        } else {
            this.currentStatus = State.SWITCHING_PALA_HANGAR;
            disconectAndChangeHangar(configPa.hangarPalladium);
        }
    }

    public void disconectAndChangeHangar(String hangar) {
        if (this.disconectTime == 0 && this.currentStatus != State.DISCONNECTING) {
            this.currentStatus = State.DISCONNECTING;
            disconnect();
        } else if (this.disconectTime <= System.currentTimeMillis() - 21000 && this.currentStatus != State.SWITCHING_HANGAR) {
            this.currentStatus = State.SWITCHING_HANGAR;
            hangarManager.changeHangar(hangar);
        } else if (this.currentStatus == State.SWITCHING_HANGAR){
            this.currentStatus = State.RELOAD_GAME;
            this.disconectTime = 0;
            API.handleRefresh();
            updateDataHangars();
        }
    }

    public void disconnect() {
        API.keyboardClick(this.configPa.exitKey);
        this.disconectTime = System.currentTimeMillis();
    }

    private void updateDataHangars(){
        if (main.backpage.sidStatus().contains("OK")) {
            hangarManager.updateHangars();
            hangarActive = hangarManager.getActiveHangar();
            lastCheckupHangar = System.currentTimeMillis();
        }
    }

    /**
     * Loot Module
     */

    public boolean lootCanRefresh() {
        return attack.target == null;
    }
    boolean findTarget() {
        return (attack.target = closestNpc(hero.locationInfo.now)) != null;
    }

    void ignoreInvalidTarget() {
        if (!main.mapManager.isTarget(attack.target)) return;
        double closestDist;
        if (!(attack.target.npcInfo.ignoreOwnership || main.mapManager.isCurrentTargetOwned())
                || (hero.locationInfo.distance(attack.target) > config.LOOT.NPC_DISTANCE_IGNORE) // Too far away from ship
                || (closestDist = drive.closestDistance(attack.target.locationInfo.now)) > 650   // Too far into obstacle
                || (closestDist > 500 && !attack.target.locationInfo.isMoving() // Inside obstacle, waiting & and regen shields
                && (attack.target.health.shIncreasedIn(1000) || attack.target.health.shieldPercent() > 0.99))) {
            attack.target.setTimerTo(5000);
            hero.setTarget(attack.target = null);
        }
    }

    void moveToAnSafePosition() {
        Npc target = attack.target;
        Location direction = drive.movingTo();
        Location heroLoc = hero.locationInfo.now;
        Location targetLoc = target.locationInfo.destinationInTime(400);

        double distance = heroLoc.distance(target.locationInfo.now);
        double angle = targetLoc.angle(heroLoc);
        double radius = target.npcInfo.radius;

        if (target != hero.target || attack.castingAbility()) radius = Math.min(500, radius);
        if (!target.locationInfo.isMoving() && target.health.hpPercent() < 0.25) radius = Math.min(radius, 600);

        if (target.npcInfo.noCircle) {
            if (targetLoc.distance(direction) <= radius) return;
            distance = 100 + random() * (radius - 110);
            angle += (random() * 0.1) - 0.05;
        } else {
            if (distance > radius) {
                radiusFix -= (distance - radius) / 2;
                radiusFix = (int) max(radiusFix, -target.npcInfo.radius / 2);
            } else {
                radiusFix += (radius - distance) / 6;
                radiusFix = (int) min(radiusFix, target.npcInfo.radius / 2);
            }
            distance = (radius += radiusFix);
            // Moved distance + speed - distance to chosen radius same angle, divided by radius
            angle += Math.max((hero.shipInfo.speed * 0.625) + (min(200, target.locationInfo.speed) * 0.625)
                    - heroLoc.distance(Location.of(targetLoc, angle, radius)), 0) / radius;
        }
        direction = Location.of(targetLoc, angle, distance);

        while (!drive.canMove(direction) && distance < 10000)
            direction.toAngle(targetLoc, angle += 0.3, distance += 2);
        if (distance >= 10000) direction.toAngle(targetLoc, angle, 500);

        if (config.LOOT.RUN_CONFIG_IN_CIRCLE && target.health.hpPercent() < 0.25 &&
                heroLoc.distance(direction) > target.npcInfo.radius * 2) {
            hero.runMode();
        } else {
            hero.attackMode();
        }

        drive.move(direction);
    }
    private Npc closestNpc(Location location) {
        int extraPriority = attack.hasTarget() &&
                (hero.target == attack.target || hero.locationInfo.distance(attack.target) < 600)
                ? 20 - (int)(attack.target.health.hpPercent() * 10) : 0;
        return this.npcs.stream()
                .filter(n -> (n == attack.target && hero.isAttacking(attack.target)) ||
                        (drive.closestDistance(location) < 450 && shouldKill(n)))
                .min(Comparator.<Npc>comparingInt(n -> n.npcInfo.priority - (n == attack.target ? extraPriority : 0))
                        .thenComparing(n -> n.locationInfo.now.distance(location))).orElse(null);
    }

    private boolean shouldKill(Npc n) {
        boolean attacked = this.isAttackedByOthers(n);
        return n.npcInfo.kill &&
                (n.npcInfo.ignoreAttacked || !attacked) && // Either ignore attacked, or not being attacked
                (!n.npcInfo.attackSecond || attacked) &&    // Either don't want to attack second, or being attacked
                (!n.npcInfo.passive || n.isAttacking(hero));
    }

    private boolean isAttackedByOthers(Npc npc) {
        for (Ship ship : this.ships) {
            if (ship.address == hero.address || ship.address == hero.pet.address
                    || !ship.isAttacking(npc)) continue;
            npc.setTimerTo(20_000);
            return true;
        }
        return npc.isInTimer();
    }

}
