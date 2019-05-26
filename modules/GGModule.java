package com.github.manolo8.darkbot.modules;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.Config;
import com.github.manolo8.darkbot.core.entities.Npc;
import com.github.manolo8.darkbot.core.manager.HeroManager;
import com.github.manolo8.darkbot.core.utils.Drive;
import com.github.manolo8.darkbot.core.utils.Location;
import com.github.manolo8.darkbot.modules.utils.NpcAttacker;
import com.github.manolo8.darkbot.config.types.Option;
import com.github.manolo8.darkbot.core.itf.CustomModule;

import java.util.Comparator;
import java.util.List;

import static com.github.manolo8.darkbot.Main.API;
import static java.lang.Double.max;
import static java.lang.Double.min;

public class GGModule implements CustomModule {
    private String version = "v1 Beta 10";
    private static final double TAU = Math.PI * 2;

    private Main main;
    private Config config;
    private List<Npc> npcs;
    private HeroManager hero;
    private Drive drive;
    private Location direction;
    private int radiusFix;

    private boolean repairing;
    private int rangeNPCFix = 0;
    private long lastCheck = System.currentTimeMillis();
    private int lasNpcHealth = 0;
    private int lasPlayerHealth = 0;
    NpcAttacker attack;

    public Object configuration() {
        return new GGConfig();
    }

    public static class GGConfig {
        @Option("Honor Formation")
        public char honorFormation = '9';

    }

    @Override
    public String name() {
        return "GG Module";
    }

    @Override
    public void install(Main main) {
        this.main = main;
        this.config = main.config;
        this.attack = new NpcAttacker(main);
        this.hero = main.hero;
        this.drive = main.hero.drive;
        this.npcs = main.mapManager.entities.npcs;
        main.starManager.getGGMaps();
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
        if (main.hero.map.gg) {
            main.guiManager.pet.setEnabled(true);
            if (findTarget()) {
                hero.attackMode();
                attack.doKillTargetTick();
                removeLowHeal();
                moveToAnSafePosition();
            } else if (!main.mapManager.entities.portals.isEmpty()) {
                hero.roamMode();
                this.main.setModule(new MapModule()).setTarget(main.starManager.byId(main.mapManager.entities.portals.get(0).id));
            } else if (!drive.isMoving()) {
                drive.moveRandom();
                API.keyboardClick(new GGConfig().honorFormation);
                hero.runMode();
            }
        } else if ( main.hero.map.id == 1 || main.hero.map.id == 5 || main.hero.map.id == 9) {
            hero.roamMode();
            for (int i=0; i < main.mapManager.entities.portals.size();i++){
                if (main.mapManager.entities.portals.get(i).target.gg && main.mapManager.entities.portals.get(i).target.id != 203){
                    this.main.setModule(new MapModule()).setTarget(main.starManager.byId(main.mapManager.entities.portals.get(i).id));
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
            if (isLowHealh(npcs.get(i))) {
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
        if (radius < 500) {
            radius = 550;
        }
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
                .min(Comparator.<Npc>comparingDouble(n -> n.locationInfo.now.distance(location))
                        .thenComparing(n -> n.npcInfo.priority)
                        .thenComparing(n -> n.health.hpPercent())).orElse(null);
    }

    private Npc bestNpc(Location location) {
        return this.npcs.stream()
                .max(Comparator.<Npc>comparingDouble(n -> n.health.hpPercent())
                        .thenComparing(n -> (n.npcInfo.priority * -1))
                        .thenComparing(n -> (n.locationInfo.now.distance(location) * -1))).orElse(null);
    }

}

