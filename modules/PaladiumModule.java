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
     * Paladium Module Test v0.0.6
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

    @Override
    public void install(Main main) {

        this.main = main;
        this.hero = main.hero;
        this.drive = main.hero.drive;
        this.config = main.config;
        this.statsManager = main.statsManager;
        this.configPa = new PaladiumConfig();
        this.hangarManager = new HangarManager(main,main.backpage);

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
        return "Loot: " + lootStatus() + " - Collect: " + collectorStatus();
    }

    @Override
    public boolean canRefresh() {

        if(isNotWaiting()) {
            return attack.target == null;
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
                if (isNotWaiting() && checkDangerousAndCurrentMap()) {
                    main.guiManager.pet.setEnabled(true);

                    if (findTarget()) {

                        findBox();

                        Box box = current;

                        if (box == null || box.locationInfo.distance(hero) > config.LOOT_COLLECT.RADIUS
                                || attack.target.health.hpPercent() < 0.25) {
                            moveToAnSafePosition();
                        } else {
                            tryCollectNearestBox();
                        }

                        ignoreInvalidTarget();
                        attack.doKillTargetTick();

                    } else {
                        hero.roamMode();
                        findBox();

                        if (!tryCollectNearestBox() && (!drive.isMoving() || drive.isOutOfMap())) {
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

    /**
     * Collector Module
     */

    public boolean isNotWaiting() {
        return System.currentTimeMillis() > waiting;
    }

    public String collectorStatus() {
        if (current == null) return "Roaming";

        return current.isCollected() ? "Collecting " + current.type + " " + (waiting - System.currentTimeMillis()) + "ms"
                : "Moving to " + current.type;
    }

    public boolean tryCollectNearestBox() {

        if (current != null) {
            collectBox();
            return true;
        }

        return false;
    }

    private void collectBox() {
        double distance = hero.locationInfo.distance(current);

        if (distance < 200) {
            drive.stop(false);
            current.clickable.setRadius(800);
            drive.clickCenter(true, current.locationInfo.now);
            current.clickable.setRadius(0);

            current.setCollected(true);

            waiting = System.currentTimeMillis() + current.boxInfo.waitTime + hero.timeTo(distance) + 30;

        } else {
            drive.move(current);
        }
    }

    public void findBox() {
        LocationInfo locationInfo = hero.locationInfo;

        Box best = boxes.stream()
                .filter(this::canCollect)
                .min(Comparator.comparingDouble(locationInfo::distance)).orElse(null);
        this.current = current == null || best == null || current.isCollected() || isBetter(best) ? best : current;
    }

    private boolean canCollect(Box box) {
        return box.boxInfo.collect
                && !box.isCollected()
                && (drive.canMove(box.locationInfo.now));
    }

    private Location findClosestEnemyAndAddToDangerousList() {
        for (Ship ship : ships) {
            if (ship.playerInfo.isEnemy()
                    && !ship.invisible
                    && ship.locationInfo.distance(hero) < DISTANCE_FROM_DANGEROUS) {

                if (ship.isInTimer()) {
                    return ship.locationInfo.now;
                } else if (ship.isAttacking(hero)) {
                    ship.setTimerTo(400_000);
                    return ship.locationInfo.now;
                }

            }
        }

        return null;
    }

    private boolean isBetter(Box box) {

        double currentDistance = current.locationInfo.distance(hero);
        double newDistance = box.locationInfo.distance(hero);

        return currentDistance > 100 && currentDistance - 150 > newDistance;
    }

    /**
     * Loot Module
     */

    public String lootStatus() {
        return safety.state() != SafetyFinder.Escaping.NONE ? safety.status() :
                attack.hasTarget() ? attack.status() : "Roaming";
    }

    boolean checkDangerousAndCurrentMap() {
        return safety.tick();
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

    private boolean isAttackedByOthers(Npc npc) {
        for (Ship ship : this.ships) {
            if (ship.address == hero.address || ship.address == hero.pet.address
                    || !ship.isAttacking(npc)) continue;
            npc.setTimerTo(20_000);
            return true;
        }
        return npc.isInTimer();
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
}
