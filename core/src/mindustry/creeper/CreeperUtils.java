package mindustry.creeper;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.EnumSet;
import arc.struct.*;
import arc.util.Timer;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.campaign.Accelerator.*;
import mindustry.world.blocks.campaign.LaunchPad.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.storage.CoreBlock.*;
import mindustry.world.meta.*;

import java.util.*;

import static mindustry.Vars.*;

public class CreeperUtils{
    public static final float updateInterval = 0.03f; // Base update interval in seconds
    public static final float transferRate = 0.25f; // Base transfer rate NOTE: keep below 0.25f
    public static final float creeperDamage = 0.2f; // Base creeper damage
    public static final float creeperEvaporationUponDamagePercent = 0.98f; // Creeper percentage that will remain upon damaging something
    public static final float creeperUnitDamage = 2f;
    public static final float maxTileCreep = 10.5f;
    public static final float creeperBlockDamageMultiplier = 0.75f;

    /*
    public static BulletType sporeType = new ArtilleryBulletType(3f, 20, "shell") {{
        hitEffect = Fx.flakExplosion;
        knockback = 0.8f;
        lifetime = 80f;
        width = height = 11f;
        collidesTiles = false;
        splashDamageRadius = 25f * 0.75f;
        splashDamage = 33f;
    }};
     */

    public static BulletType sporeType = Bullets.placeholder;

    public static float sporeMaxRangeMultiplier = 30f;
    public static float sporeAmount = 20f;
    public static float sporeRadius = 5f;
    public static float sporeSpeedMultiplier = 0.15f;
    public static float sporeHealthMultiplier = 10f;
    public static float sporeTargetOffset = 256f;

    public static float unitShieldDamageMultiplier = 1.5f;
    public static float buildShieldDamageMultiplier = 1.5f;
    public static float shieldBoostProtectionMultiplier = 0.5f;
    public static float shieldCreeperDropAmount = 7f;
    public static float shieldCreeperDropRadius = 4f;

    public static float nullifierRange = 16 * tilesize;

    public static float radarBeamDamage = 300f; // damage the radar creeper beam deals to units

    public static float creepTowerDeposit = 0.3f; // amount of creep deposited by the creep tower per tick
    public static float creepTowerRange = 300f; // just slightly bigger than ripple's range


    public static float nullifyDamage = 1500f; // Damage that needs to be applied for the core to be suspended
    public static float nullifyTimeout = 180f; // The amount of ticks a core remains suspended (resets upon enough damage applied)

    public static float nullificationPeriod = 10f; // How many seconds all cores have to be nullified (suspended) in order for the game to end
    public static int tutorialID;
    private static int nullifiedCount = 0;
    private static int pulseOffset = 0;

    public static Team creeperTeam = Team.blue;

    public static HashMap<Integer, Block> creeperBlocks = new HashMap<>();
    public static HashMap<Block, Integer> creeperLevels = new HashMap<>();

    public static Seq<Emitter> creeperEmitters = new Seq<>();
    public static Seq<ChargedEmitter> chargedEmitters = new Seq<>();
    public static Seq<Tile> creeperableTiles = new Seq<>();
    public static Seq<ForceProjector.ForceBuild> shields = new Seq<>();

    public static Timer.Task runner;
    public static Timer.Task fixedRunner;

    public static final String[][] tutContinue = {{"[#49e87c]\uE829 Continue[]"}};
    public static final String[][] tutFinal = {{"[#49e87c]\uE829 Finish[]"}};
    public static final String[][] tutStart = {{"[#49e87c]\uE875 Take the tutorial[]"}, {"[#e85e49]⚠ Skip (not recommended)[]"}};
    public static final String[] tutEntries = {
    "[accent]\uE875[] Tutorial 1/6", "In [#e056f0]\uE83B the flood[] there are [scarlet]no units[] to defeat.\nInstead, your goal is to suspend all [accent]emitters[], which are simply [accent]enemy cores, launchpads and accelerators.[]",
    "[accent]\uE875[] Tutorial 2/6", "[scarlet]⚠ beware![]\n[accent]Emitters[] spawn [#e056f0]\uE83B the flood[], which when in proximity to friendly buildings or units, damages them.",
    "[accent]\uE875[] Tutorial 3/6", "[scarlet]⚠ beware![]\n[accent]Charged Emitters[] spawn [#e056f0]\uE83B the flood[] much faster, but they are only active for small periods.",
    "[accent]\uE875[] Tutorial 4/6", "You can [accent]suspend emitters[] by constantly dealing damage to them, and destroy [accent]charged emitters[] to remove them.",
    "[accent]\uE875[] Tutorial 5/6", "If [accent]emitters[] are sufficiently suspended, you can [accent]nullify them[] by building an \uF871 [accent]Impact Reactor[] near them and activating it.",
    "[accent]\uE875[] Tutorial 6/6", "If [accent]emitters[] are surrounded by the maximum creep, they will begin [stat]upgrading[]. You can stop the upgrade by suspending them.",
    "[white]\uF872[]", "[accent]Spore Launchers[]\n[accent]Thorium Reactors[] shoot long distance artillery that on impact, releases [accent]a huge amount of flood[], you can defend against this with segments \uF80E.",
    "[white]\uF682[]", "[accent]Flood Projector[]\n[accent]Shockwave Towers[] rapidly deposit flood at any nearby buildings, forcing a [accent]different approach[] than turret spam.\nRange is slightly larger than Ripples.",
    "[white]\uF6AD[]", "[accent]Flood Radar[]\n[accent]Radars[] focus on the closest unit, and after a short time of charging, [accent]shoot[] at that unit, forcing a [accent]different approach[] than unit spam.\nRange is slightly larger than Ripples.",
    "[white]\uF898[]", "[accent]Flood Shield[]\n[accent]Force Projectors[] and [accent]unit shields[] actively absorb [#e056f0]the flood[], but [accent]explode[] when they are full.",
    "[white]\uF7FA[]", "[accent]Flood Creep[]\n[accent]Spider-Type units[] explode when in contact of friendly buildings and release tons of [#e056f0]the flood[].",
    "[white]\uF7F5[]", "[accent]Horizons[] are immune to the flood but [orange]do not deal any damage[]. Use them to carry [accent]resources[] over the flood. They are not immune to emitters and spore launchers.",
    };

    public static String getTrafficlightColor(double value){
        return "#" + Integer.toHexString(java.awt.Color.HSBtoRGB((float)value / 3f, 1f, 1f)).substring(2);
    }

    public static float[] targetSpore(){
        float[] ret = null;
        int iterations = 0;

        while(ret == null && iterations < 10000 && Groups.player.size() > 0){
            iterations++;
            Player player = Groups.player.index(Mathf.random(0, Groups.player.size() - 1));
            if(player.unit() == null || player.x == 0 && player.y == 0)
                continue;

            Unit unit = player.unit();
            ret = new float[]{unit.x + Mathf.random(-sporeTargetOffset, sporeTargetOffset), unit.y + Mathf.random(-sporeTargetOffset, sporeTargetOffset)};
            Tile retTile = world.tileWorld(ret[0], ret[1]);

            // target creeperableTiles only
            if(creeperableTiles.contains(retTile)){
                return ret;
            }
        }

        return (ret != null ? ret : new float[]{0, 0});
    }

    public static void sporeCollision(Bullet bullet, float x, float y){
        Tile tile = world.tileWorld(x, y);
        if(invalidTile(tile))
            return;

        Call.effect(Fx.sapExplosion, x, y, sporeRadius, Color.blue);

        depositCreeper(tile, sporeRadius, sporeAmount);
    }

    public static void init(){
        sporeType.isCreeper = true;


        // old walls since conveyors no longer work :{
        creeperBlocks.put(0, Blocks.air);
        creeperBlocks.put(1, Blocks.scrapWall);
        creeperBlocks.put(2, Blocks.titaniumWall);
        creeperBlocks.put(3, Blocks.thoriumWall);
        creeperBlocks.put(4, Blocks.plastaniumWall);

        // new erekir walls
        creeperBlocks.put(5, Blocks.phaseWall);
        creeperBlocks.put(6, Blocks.surgeWall);
        creeperBlocks.put(7, Blocks.reinforcedSurgeWall);
        creeperBlocks.put(8, Blocks.berylliumWall);
        creeperBlocks.put(9, Blocks.tungstenWall);
        creeperBlocks.put(10, Blocks.carbideWall);

        // this is purely for damage multiplication
        creeperBlocks.put(12, Blocks.thoriumReactor);

        creeperBlocks.put(20, Blocks.coreShard);
        creeperBlocks.put(25, Blocks.coreFoundation);
        creeperBlocks.put(30, Blocks.coreNucleus);

        creeperBlocks.put(75, Blocks.coreBastion);
        creeperBlocks.put(76, Blocks.coreCitadel);
        creeperBlocks.put(77, Blocks.coreAcropolis);

        for(var set : creeperBlocks.entrySet()){
            BlockFlag[] newFlags = new BlockFlag[set.getValue().flags.size + 1];
            int i = 0;
            for(BlockFlag flag : set.getValue().flags.array){
                newFlags[i++] = flag;
            }
            newFlags[i] = BlockFlag.generator;
            set.getValue().flags = EnumSet.of(newFlags);
            creeperLevels.put(set.getValue(), set.getKey());
        }

        Emitter.init();
        ChargedEmitter.init();

        int menuID = 0;
        for(int i = tutEntries.length; --i >= 0; ){
            final int j = i;
            int current = menuID;
            menuID = Menus.registerMenu((player, selection) -> {
                if(selection == 1) return;
                if(j == tutEntries.length / 2) return;
                Call.menu(player.con, current, tutEntries[2 * j], tutEntries[2 * j + 1], j == tutEntries.length / 2 - 1 ? tutFinal : tutContinue);
            });
        }

        tutorialID = menuID;
        Events.on(EventType.PlayerJoin.class, e -> {
            if(e.player.getInfo().timesJoined > 1) return;
            Call.menu(e.player.con, tutorialID, "[accent]Welcome![]", "Looks like it's your first time playing..", tutStart);
        });

        Events.on(EventType.GameOverEvent.class, e -> {
            if(runner != null)
                runner.cancel();
            if(fixedRunner != null)
                fixedRunner.cancel();

            creeperableTiles.clear();
            creeperEmitters.clear();
            chargedEmitters.clear();
            shields.clear();
        });

        Events.on(EventType.PlayEvent.class, e -> {
            creeperableTiles.clear();
            chargedEmitters.clear();
            creeperEmitters.clear();

            for(Tile tile : world.tiles){
                if(!tile.floor().isDeep() && tile.floor().placeableOn && (tile.breakable() || tile.block() == Blocks.air || tile.block() instanceof TreeBlock)){
                    creeperableTiles.add(tile);
                }
            }

            for(Building build : Groups.build){
                if(build.team != creeperTeam) continue;
                if(build instanceof CoreBuild){
                    creeperEmitters.add(new Emitter(build));
                }else if(build instanceof LaunchPadBuild || build instanceof AcceleratorBuild){
                    chargedEmitters.add(new ChargedEmitter(build));
                }
            }

            Log.info(creeperableTiles.size + " creeperable tiles");
            Log.info(creeperEmitters.size + " emitters");
            Log.info(chargedEmitters.size + " charged emitters");

            runner = Timer.schedule(CreeperUtils::updateCreeper, 0, updateInterval);
            fixedRunner = Timer.schedule(CreeperUtils::fixedUpdate, 0, 1);
        });

        Events.on(EventType.BlockDestroyEvent.class, e -> {
            if(creeperBlocks.containsValue(e.tile.block())){
                e.tile.creep = 0;
            }
        });

        Timer.schedule(() -> {
            if(!state.isGame()) return;
            Call.infoPopup(
            Strings.format(
                "\uE88B [@] @/@ []emitters suspended\n\uE88B [@] @ []charged emitters remaining",
                getTrafficlightColor(Mathf.clamp((nullifiedCount / Math.max(1.0, creeperEmitters.size)), 0f, 1f)), nullifiedCount, creeperEmitters.size,
                (chargedEmitters.size > 0 ? "red" : "green"), chargedEmitters.size
            ), 10f, 20, 50, 20, 527, 0);
            // check for gameover
            if(nullifiedCount == creeperEmitters.size){
                Timer.schedule(() -> {
                    if(nullifiedCount == creeperEmitters.size && chargedEmitters.size <= 0){
                        // gameover
                        state.gameOver = true;
                        Events.fire(new EventType.GameOverEvent(state.rules.defaultTeam));
                    }
                    // failed to win, core got unsuspended
                }, nullificationPeriod);
            }
        }, 0, 10);
    }

    public static void depositCreeper(Tile tile, float radius, float amount){
        Geometry.circle(tile.x, tile.y, (int)radius, (cx, cy) -> {
            Tile ct = world.tile(cx, cy);
            if(invalidTile(ct) || (tile.block() instanceof StaticWall || (tile.floor() != null && !tile.floor().placeableOn || tile.floor().isDeep() || tile.block() instanceof Cliff)))
                return;

            ct.creep = Math.min(ct.creep + amount, 10);
        });
    }

    public static void fixedUpdate(){
        // dont update anything if game is paused
        if(!state.isPlaying() || state.serverPaused) return;

        int newcount = 0;
        for(Emitter emitter : creeperEmitters){
            emitter.fixedUpdate();
            if(emitter.nullified)
                newcount++;
        }
        chargedEmitters.forEach(ChargedEmitter::fixedUpdate);

        for(ForceProjector.ForceBuild shield : shields){
            if(shield == null || shield.dead || shield.health <= 0f || shield.healthLeft <= 0f){
                shields.remove(shield);
                if(shield == null) continue;
                Core.app.post(shield::kill);

                float percentage = 1f - shield.healthLeft / ((ForceProjector)shield.block).shieldHealth;
                depositCreeper(shield.tile, shieldCreeperDropRadius, shieldCreeperDropAmount * percentage);

                continue;
            }

            double percentage = shield.healthLeft / ((ForceProjector)shield.block).shieldHealth;
            Call.label("[" + getTrafficlightColor(percentage) + "]" + (int)(percentage * 100) + "%" + (shield.phaseHeat > 0.1f ? " [#f4ba6e]\uE86B +" + ((int)((1f - CreeperUtils.shieldBoostProtectionMultiplier) * 100f)) + "%" : ""), 1f, shield.x, shield.y);
        }

        nullifiedCount = newcount;
    }

    public static void updateCreeper(){
        // dont update anything if game is paused
        if(!state.isPlaying() || state.serverPaused) return;

        // update emitters
        for(Emitter emitter : creeperEmitters){
            if(!emitter.update())
                creeperEmitters.remove(emitter);
        }
        for(ChargedEmitter emitter : chargedEmitters){
            if(!emitter.update()){
                chargedEmitters.remove(emitter);
            }
        }

        // no emitters so game over
        if(creeperEmitters.size == 0
        || closestEmitter(world.tile(0, 0)) == null){
            return;
        }

        // update creeper flow
        if(++pulseOffset == 64) pulseOffset = 0;
        for(Tile tile : creeperableTiles){
            if(tile == null){
                creeperableTiles.remove((Tile)null);
                continue;
            }

            // spread creep and apply damage
            transferCreeper(tile);
            applyDamage(tile);

            if((closestEmitterDist(tile) - pulseOffset) % 64 == 0){
                drawCreeper(tile);
            }
        }
    }

    public static int closestEmitterDist(Tile tile){
        Emitter ce = closestEmitter(tile);
        ChargedEmitter cce = closestChargedEmitter(tile);
        if(ce == null){
            if(cce == null) return -1;
            return (int)cce.dst(tile);
        }else{
            if(cce == null) return (int)ce.dst(tile);
            return (int)Math.min(ce.dst(tile), cce.dst(tile));
        }
    }

    public static Emitter closestEmitter(Tile tile){
        return Geometry.findClosest(tile.getX(), tile.getY(), creeperEmitters);
    }

    public static ChargedEmitter closestChargedEmitter(Tile tile){
        return Geometry.findClosest(tile.getX(), tile.getY(), chargedEmitters);
    }

    public static void drawCreeper(Tile tile){
        Core.app.post(() -> {
            if(tile.creep < 1f){
                return;
            }
            int currentLvl = creeperLevels.getOrDefault(tile.block(), 11);

            if((tile.build == null || tile.block().alwaysReplace || (tile.build.team == creeperTeam && currentLvl <= 10)) && (currentLvl < (int)tile.creep || currentLvl > (int)tile.creep + 0.1f)){
                tile.setNet(creeperBlocks.get(Mathf.clamp((int)tile.creep, 0, 10)), creeperTeam, Mathf.random(0, 3));
            }
        });
    }

    public static void applyDamage(Tile tile){
        if(tile.build != null && tile.build.team != creeperTeam && tile.creep > 1f){
            Core.app.post(() -> {
                if(tile.build == null) return;

                if(Mathf.chance(0.02d)){
                    Call.effect(Fx.bubble, tile.build.x, tile.build.y, 0, creeperTeam.color);
                }
                tile.build.damage(creeperDamage * tile.creep);
                tile.creep *= creeperEvaporationUponDamagePercent;
            });
        }
    }

    public static boolean invalidTile(Tile tile){
        return tile == null;
    }

    public static void transferCreeper(Tile source){
        if(source.build == null || source.creep < 1f) return;

        float total = 0f;
        for(int i = source.build.id; i < source.build.id + 4; i++){
            Tile target = source.nearby(i % 4);
            if(cannotTransfer(source, target)) continue;

            // creeper delta, cannot transfer more than 1/4 source creep or less than 0.001f. Target creep cannot exceed max creep
            float delta = Mathf.clamp((source.creep - target.creep) * transferRate, 0, Math.min(source.creep * transferRate, maxTileCreep - target.creep));
            if(delta > 0.001f){
                target.creep += delta;
                total += delta;
            }
        }

        if(total > 0.001f){
            source.creep -= total;
        }
    }

    public static boolean cannotTransfer(Tile source, Tile target){
        if(source == null
        || target == null
        || target.creep >= maxTileCreep
        || source.creep <= target.creep
        || target.block() instanceof StaticWall
        || target.block() instanceof Cliff
        || (target.floor() != null && (!target.floor().placeableOn || target.floor().isDeep()))){
            return true;
        }
        if(source.build != null && source.build.team != creeperTeam){
            applyDamage(source);
            return true;
        }

        return false;
    }
}
