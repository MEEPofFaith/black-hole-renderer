package blackhole.graphics;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.Pool;
import arc.util.pooling.Pools;
import mindustry.game.EventType.*;
import mindustry.graphics.*;

import static arc.Core.*;
import static mindustry.Vars.*;

/**
 * Handles rendering of gravitational lensing and the glow around the center.
 * @author MEEPofFaith
 * */
public class BlackHoleRenderer{
    private final Seq<BlackHoleZone> zones = new Seq<>();
    private final Seq<BlackHoleStar> stars = new Seq<>();
    private static BlackHoleRenderer bRenderer;
    private boolean advanced = true;

    private FrameBuffer buffer;

    private static final float[][] initHoles = new float[510][];
    private static final float[][] initColors = new float[510][];
    //private static final Pool<BlackHoleZone> zonePool = Pools.get(BlackHoleZone.class, BlackHoleZone::new);
    //private static final Pool<BlackHoleStar> starPool = Pools.get(BlackHoleStar.class, BlackHoleStar::new);


    protected BlackHoleRenderer(boolean advanced){
        BHShaders.createBlackHoleShaders();
        advanced(advanced);

        Events.run(Trigger.draw, () -> {
            if(this.advanced){
                advancedDraw();
            }else{
                simplifiedDraw();
            }
        });
    }

    public static void init(boolean advanced){
        if(bRenderer == null) bRenderer = new BlackHoleRenderer(advanced);

        if(!advanced) return;
        for(int i = 0; i < 510; i++){
            initHoles[i] = new float[i * 4];
            initColors[i] = new float[i * 4];
        }
    }

    public static void toggleAdvanced(boolean advanced){
        if(bRenderer != null) bRenderer.advanced(advanced);
    }

    /**
     * Adds a black hole to the renderer.
     *
     * @param x x-coordinate of the center
     * @param y y-coordinate of the center
     * @param inRadius size of the black hole (radius of where it's black)
     * @param outRadius end of the gravitational lensing range
     * @param color color of the glowing rim
     */
    public static void addBlackHole(float x, float y, float inRadius, float outRadius, Color color){
        bRenderer.addBH(x, y, inRadius, outRadius, color);
    }

    public static void addStar(float x, float y, float w, float h, float angleOffset, Color in, Color out){
        bRenderer.addS(x, y, w, h, angleOffset, in, out);
    }

    public static void addStar(float x, float y, float w, float h, Color in, Color out){
        addStar(x, y, w, h, 0, in, out);
    }

    private void advancedDraw(){
        Draw.draw(BHLayer.begin, () -> {
            buffer.resize(graphics.getWidth(), graphics.getHeight());
            buffer.begin();
        });

        Draw.draw(BHLayer.end, () -> {
            buffer.end();

            if(zones.size >= BHShaders.maxCount) BHShaders.createBlackHoleShaders();
            if(zones.size >= 510) return;

            float[] blackholes = initHoles[zones.size];
            float[] colors = initColors[zones.size];
            for(int i = 0; i < zones.size; i++){
                BlackHoleZone zone = zones.get(i);
                blackholes[i * 4] = zone.x;
                blackholes[i * 4 + 1] = zone.y;
                blackholes[i * 4 + 2] = zone.inRadius;
                blackholes[i * 4 + 3] = zone.outRadius;

                Tmp.c1.abgr8888(zone.color);
                colors[i * 4] = Tmp.c1.r;
                colors[i * 4 + 1] = Tmp.c1.g;
                colors[i * 4 + 2] = Tmp.c1.b;
                colors[i * 4 + 3] = Tmp.c1.a;
            }
            BHShaders.lensingShader.blackHoles = blackholes;
            buffer.blit(BHShaders.lensingShader);

            BHShaders.rimShader.blackHoles = blackholes;
            BHShaders.rimShader.colors = colors;
            buffer.begin();
            Draw.rect();
            buffer.end();

            Bloom bloom = renderer.bloom;
            if(bloom != null){
                bloom.capture();
                buffer.blit(BHShaders.rimShader);
                drawStars();
                bloom.render();
            }else{
                buffer.blit(BHShaders.rimShader);
                drawStars();
            }

            //zonePool.freeAll(zones);
            zones.clear();
        });
    }

    private void simplifiedDraw(){
        Draw.draw(Layer.max, () -> {
            Draw.color(Color.black);
            for(BlackHoleZone zone : zones){
                Fill.circle(zone.x, zone.y, zone.inRadius);
            }
            Draw.color();

            Bloom bloom = renderer.bloom;
            if(bloom != null){
                bloom.capture();
                simplifiedRims();
                drawStars();
                bloom.render();
            }else{
                simplifiedRims();
                drawStars();
            }

            //zonePool.freeAll(zones);
            zones.clear();
        });
    }

    private void simplifiedRims(){
        for(BlackHoleZone zone : zones){
            float rad = Mathf.lerp(zone.inRadius, zone.outRadius, 0.125f);
            int vert = Lines.circleVertices(rad);
            float space = 360f / vert;

            Tmp.c1.abgr8888(zone.color);
            float c1 = Tmp.c1.toFloatBits();
            float c2 = Tmp.c1.a(0).toFloatBits();

            for(int i = 0; i < vert; i++){
                float sin1 = Mathf.sinDeg(i * space), sin2 = Mathf.sinDeg((i + 1) * space);
                float cos1 = Mathf.cosDeg(i * space), cos2 = Mathf.cosDeg((i + 1) * space);

                Fill.quad(
                        zone.x + cos1 * zone.inRadius, zone.y + sin1 * zone.inRadius, c1,
                        zone.x + cos2 * zone.inRadius, zone.y + sin2 * zone.inRadius, c1,
                        zone.x + cos2 * rad, zone.y + sin2 * rad, c2,
                        zone.x + cos1 * rad, zone.y + sin1 * rad, c2
                );
            }
        }
    }

    private void drawStars(){
        for(BlackHoleStar star : stars){
            BHDrawf.drawStar(star.x, star.y, star.w, star.h, star.angleOffset, star.inColor, star.outColor);
        }

        //starPool.freeAll(stars);
        stars.clear();
    }

    private void advanced(boolean advanced){
        this.advanced = advanced;
        if(advanced){
            buffer = new FrameBuffer();
        }else{
            if(buffer != null) buffer.dispose();
        }
    }

    private void addBH(float x, float y, float inRadius, float outRadius, Color color){
        if(inRadius > outRadius || outRadius <= 0) return;

        float res = Color.toFloatBits(color.r, color.g, color.b, 1);

        zones.add(Pools.obtain(BlackHoleZone.class, BlackHoleZone::new).set(x, y, res, inRadius, outRadius));
    }

    private void addS(float x, float y, float w, float h, float angleOffset, Color in, Color out){
        if(w <= 0 || h <= 0) return;

        stars.add(Pools.obtain(BlackHoleStar.class, BlackHoleStar::new).set(x, y, w, h, angleOffset, in.toFloatBits(), out.toFloatBits()));
    }

    private static class BlackHoleZone{
        float x, y, color, inRadius, outRadius;

        public BlackHoleZone set(float x, float y, float color, float inRadius, float outRadius){
            this.x = x;
            this.y = y;
            this.color = color;
            this.inRadius = inRadius;
            this.outRadius = outRadius;
            return this;
        }

        public BlackHoleZone(){

        }
    }


    private static class BlackHoleStar{
        float x, y, w, h, angleOffset, inColor, outColor;

        public BlackHoleStar set(float x, float y, float w, float h, float angleOffset, float inColor, float outColor){
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.angleOffset = angleOffset;
            this.inColor = inColor;
            this.outColor = outColor;
            return this;
        }

        public BlackHoleStar(){

        }
    }
}
