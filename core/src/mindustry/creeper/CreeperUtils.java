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
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.meta.*;

import java.util.*;

import static mindustry.Vars.*;

public class CreeperUtils{
    public static float updateInterval = 0.02f; // Base update interval in seconds
    public static float transferRate = 0.2f; // Base transfer rate NOTE: keep below 0.25f
    public static float creeperDamage = 1f; // Base creeper damage
    public static float damageEvaporationRate = 0.96f; // Creeper percentage that will remain upon damaging something
    public static float creeperUnitDamage = 2f;
    public static float minCreeper = 0.99f; // Minimum amount of creeper required for transfer

    public static BulletType sporeType = Bullets.artilleryDense;
    public static float sporeMaxRangeMultiplier = 30f;
    public static float sporeAmount = 20f;
    public static float sporeRadius = 5f;
    public static float sporeSpeedMultiplier = 0.15f;
    public static float sporeHealthMultiplier = 10f;
    public static float sporeTargetOffset = 256f;

    public static float unitShieldDamageMultiplier = 1f;
    public static float buildShieldDamageMultiplier = 1.5f;
    public static float shieldBoostProtectionMultiplier = 0.5f;
    public static float shieldCreeperDropAmount = 7f;
    public static float shieldCreeperDropRadius = 4f;

    public static float nullifierRange = 80f;


    public static float nullifyDamage = 1500f; // Damage that needs to be applied for the core to be suspended
    public static float nullifyTimeout = 180f; // The amount of time a core remains suspended (resets upon enough damage applied)

    public static float nullificationPeriod = 10f; // How many seconds all cores have to be nullified (suspended) in order for the game to end
    public static int tutorialID;
    private static int nullifiedCount = 0;
    private static int pulseOffset = 0;

    public static Team creeperTeam = Team.blue;

    public static HashMap<Integer, Block> creeperBlocks = new HashMap<>();
    public static HashMap<Block, Integer> creeperLevels = new HashMap<>();

    public static HashMap<Block, Emitter> emitterBlocks = new HashMap<>();
    public static HashMap<Block, ChargedEmitter> chargedEmitterBlocks = new HashMap<>();

    public static Seq<Emitter> creeperEmitters = new Seq<>();
    public static Seq<Tile> creeperableTiles = new Seq<>();
    public static Seq<ForceProjector.ForceBuild> shields = new Seq<>();

    public static Timer.Task runner;
    public static Timer.Task fixedRunner;

    public static final String[][] tutContinue = {{"[#49e87c]\uE829 Continue[]"}};
    public static final String[][] tutFinal = {{"[#49e87c]\uE829 Finish[]"}};
    public static final String[][] tutStart = {{"[#49e87c]\uE875 Take the tutorial[]"}, {"[#e85e49]⚠ Skip (not recommended)[]"}};
    public static final String[] tutEntries = {
    "[accent]\uE875[] Tutorial 1/4", "In [#e056f0]\uE83B the flood[] there are [scarlet]no units[] to defeat.\nInstead, your goal is to suspend all [accent]emitters[], which are simply [accent]enemy cores.[]",
    "[accent]\uE875[] Tutorial 2/4", "[scarlet]⚠ beware![]\n[accent]Emitters[] spawn [#e056f0]\uE83B the flood[], which when in proximity to friendly buildings or units, damages them.",
    "[accent]\uE875[] Tutorial 3/4", "You can [accent]suspend emitters[] by constantly dealing damage to them.",
    "[accent]\uE875[] Tutorial 4/4", "If [accent]emitters[] are sufficiently suspended, you can [accent]nullify them[] by building an \uF871 [accent]Impact Reactor[] near them and activating it.",
    "[white]\uF872[]", "[accent]Spore Launchers[]\n[accent]Thorium Reactors[] shoot long distance artillery that on impact, releases [accent]a huge amount of flood[], you can defend against this with segments \uF80E.",
    "[white]\uF898[]", "[accent]Flood Shield[]\n[accent]Force Projectors[] actively absorb [#e056f0]the flood[], but [accent]explode[] when they are full.",
    "[white]\uF7FA[]", "[accent]Flood Creep[]\n[accent]Spider-Type units[] explode when in contact of friendly buildings and release tons of [#e056f0]the flood[].",
    "[white]\uF7F5[]", "[accent]Horizons[] are immune to the flood but [orange]do not deal any damage[]. Use them to carry [accent]resources[] over the flood.",
    };

    public static String getTrafficlightColor(double value){
        return "#" + Integer.toHexString(java.awt.Color.HSBtoRGB((float)value / 3f, 1f, 1f)).substring(2);
    }


    public static float[] targetSpore(){
        float[] ret = null;
        int iterations = 0;

        while(ret == null && iterations < 1000 && Groups.player.size() > 0){
            iterations++;
            Player player = Groups.player.index(Mathf.random(0, Groups.player.size() - 1));
            if(player.unit() == null || player.x == 0 && player.y == 0)
                continue;

            Unit unit = player.unit();
            ret = new float[]{unit.x + Mathf.random(-sporeTargetOffset, sporeTargetOffset), unit.y + Mathf.random(-sporeTargetOffset, sporeTargetOffset)};
            Tile retTile = world.tileWorld(ret[0], ret[1]);

            // dont target static walls or deep water
            if(retTile != null && retTile.breakable() && !retTile.floor().isDeep() && retTile.floor().placeableOn){
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


        creeperBlocks.put(0, Blocks.air);
        creeperBlocks.put(1, Blocks.conveyor);
        creeperBlocks.put(2, Blocks.titaniumConveyor);
        creeperBlocks.put(3, Blocks.armoredConveyor);
        creeperBlocks.put(4, Blocks.plastaniumConveyor);
        creeperBlocks.put(5, Blocks.scrapWall);
        creeperBlocks.put(6, Blocks.titaniumWall);
        creeperBlocks.put(7, Blocks.thoriumWall);
        creeperBlocks.put(8, Blocks.plastaniumWall);
        creeperBlocks.put(9, Blocks.phaseWall);
        creeperBlocks.put(10, Blocks.surgeWall);

        // this is purely for damage multiplication
        creeperBlocks.put(12, Blocks.thoriumReactor);

        creeperBlocks.put(20, Blocks.coreShard);
        creeperBlocks.put(35, Blocks.coreFoundation);
        creeperBlocks.put(50, Blocks.coreNucleus);

        creeperBlocks.put(75, Blocks.launchPad);
        creeperBlocks.put(100, Blocks.interplanetaryAccelerator);

        for(var set : creeperBlocks.entrySet()){
            BlockFlag[] newFlags = new BlockFlag[set.getValue().flags.size() + 1];
            int i = 0;
            for(BlockFlag flag : set.getValue().flags){
                newFlags[i++] = flag;
            }
            newFlags[i] = BlockFlag.generator;
            set.getValue().flags = EnumSet.of(newFlags);
            creeperLevels.put(set.getValue(), set.getKey());
        }

        // max amt is 10
        emitterBlocks.put(Blocks.coreShard, new Emitter(30, 3));
        emitterBlocks.put(Blocks.coreFoundation, new Emitter(20, 5));
        emitterBlocks.put(Blocks.coreNucleus, new Emitter(15, 7));

        chargedEmitterBlocks.put(Blocks.launchPad, new ChargedEmitter(1, 10, 180, 600));
        chargedEmitterBlocks.put(Blocks.interplanetaryAccelerator, new ChargedEmitter(0, 10, 380, 1800));

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
            shields.clear();
        });

        Events.on(EventType.PlayEvent.class, e -> {
            creeperableTiles.clear();
            creeperEmitters.clear();

            for(Tile tile : world.tiles){
                if(!tile.floor().isDeep() && tile.floor().placeableOn && (tile.breakable() || tile.block() == Blocks.air || tile.block() instanceof TreeBlock)){
                    creeperableTiles.add(tile);
                }
            }

            for(Building core : creeperTeam.cores()){
                creeperEmitters.add(new Emitter(core));
            }

            Log.info(creeperableTiles.size + " creeperableTiles");
            Log.info(creeperEmitters.size + " emitters");

            runner = Timer.schedule(CreeperUtils::updateCreeper, 0, updateInterval);
            fixedRunner = Timer.schedule(CreeperUtils::fixedUpdate, 0, 1);
        });

        Events.on(EventType.BlockDestroyEvent.class, e -> {
            if(CreeperUtils.creeperBlocks.containsValue(e.tile.block())){
                e.tile.creep = 0;
            }
        });

        Events.on(EventType.UnitCreateEvent.class, e -> { // Horizons can't shoot but also don't die to flood
            if(e.unit.type == UnitTypes.horizon) e.unit.apply(StatusEffects.disarmed, Float.MAX_VALUE);
        });

        Timer.schedule(() -> {
            if(!state.isGame()) return;
            Call.infoPopup("\uE88B [" + getTrafficlightColor(Mathf.clamp((CreeperUtils.nullifiedCount / Math.max(1.0, creeperEmitters.size)), 0f, 1f)) + "]" + CreeperUtils.nullifiedCount + "/" + CreeperUtils.creeperEmitters.size + "[] emitters suspended", 10f, 20, 50, 20, 527, 0);
            // check for gameover
            if(CreeperUtils.nullifiedCount == CreeperUtils.creeperEmitters.size){
                Timer.schedule(() -> {
                    if(CreeperUtils.nullifiedCount == CreeperUtils.creeperEmitters.size){
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

        // update creeper flow
        if(++pulseOffset == 64) pulseOffset = 0;
        for(Tile tile : creeperableTiles){
            if(tile == null){
                creeperableTiles.remove(tile);
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
        return (int)closestEmitter(tile).dst(tile);
    }

    public static Emitter closestEmitter(Tile tile){
        return Geometry.findClosest(tile.getX(), tile.getY(), creeperEmitters);
    }

    // creates appropriate blocks for creeper OR damages the tile that it wants to take
    public static void drawCreeper(Tile tile){
        Core.app.post(() -> {
            if(tile.creep < 1f){
                return;
            }
            int currentLvl = creeperLevels.getOrDefault(tile.block(), 11);
            int creepLvl = Math.round(tile.creep);

            if((tile.build == null || tile.build.team == creeperTeam) && currentLvl <= 10 && (currentLvl < creepLvl || currentLvl > creepLvl + 1)){
                tile.setNet(creeperBlocks.get(Mathf.clamp(Math.round(tile.creep), 0, 10)), creeperTeam, Mathf.random(0, 3));
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
                tile.creep *= damageEvaporationRate;
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

            float delta = Mathf.clamp((source.creep - target.creep) * transferRate, 0, Math.min(source.creep * transferRate, 10 - target.creep));
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
        || target.creep >= 10
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
