package org.talor.wurmunlimited.mods.survival;

import com.wurmonline.math.TilePos;
import com.wurmonline.server.WurmCalendar;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import com.wurmonline.server.bodys.Body;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.Server;

import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Properties;


public class Survival implements WurmServerMod, Configurable, ServerStartedListener, Initable, PreInitable {

    private boolean enableTemperatureSurvival = true;

	@Override
	public void onServerStarted() {
	}

	@Override
	public void configure(Properties properties) {
        enableTemperatureSurvival = Boolean.parseBoolean(properties.getProperty("enableTemperatureSurvival", Boolean.toString(enableTemperatureSurvival)));
	}

	@Override
	public void preInit() {
	}

	@Override
	public void init() {


        HookManager.getInstance().registerHook("com.wurmonline.server.players.Player", "poll", "()Z", new InvocationHandlerFactory() {

            @Override
            public InvocationHandler createInvocationHandler() {
                return new InvocationHandler() {

                    @Override
                    public Object invoke(Object object, Method method, Object[] args) throws Throwable {

                        Player p = (Player) object;
                        String message = null;

                        if (!p.isDead() && p.secondsPlayed % 15.0F == 0.0F) {

                            Body b =  p.getBody();

                            int hour = WurmCalendar.getHour();
                            double starfall = WurmCalendar.getStarfall() + ((double)WurmCalendar.getDay()/28);
                            boolean isIndoors = !p.getCurrentTile().isOnSurface() || (p.getCurrentTile().getStructure() != null && p.getCurrentTile().getStructure().isFinished());

                            // Approximation of seasonal heat differences
                            // Produces number between -5 and 2
                            double monthTempMod =  7 * Math.sin(starfall/3.82) - 5;

                            // Approximation of day/night heat differences
                            // Produces number between -2.5 and 2.5
                            double hourTempMod = 5 * Math.sin((float)hour/7.65) - 2.5;

                            // Colder if strong wind or gale
                            double windMod = !isIndoors && Math.abs(Server.getWeather().getWindPower()) > 0.3 ? -1 : 0;

                            // Colder if swimming
                            double swimMod = Zones.calculateHeight(p.getPosX(), p.getPosY(), p.isOnSurface()) < 0 ? -1 : 0;

                            // Colder if raining
                            double rainMod = !isIndoors && Server.getWeather().getRain() > 0.5 ? -1 : 0;

                            // Positive value indicates warming, negative value indicates cooling
                            // Produces within a rough range of -7.5 to 4.5
                            short temperatureDelta = (short) (monthTempMod + hourTempMod + windMod + swimMod + rainMod);

                            System.out.println(p.getName() + " has following modifiers... calendar mod: " + monthTempMod + ", day/night mod: " + hourTempMod + ", windMod : " + windMod + ", swimMod: " + swimMod + ", rainMod: " + rainMod + ", indoors: " + isIndoors);

                            // Search nearby for hottest heat source
                            int tileX = p.getCurrentTile().getTileX();
                            int tileY = p.getCurrentTile().getTileY();
                            int yy;
                            int dist = 5; // area to check for heat sources
                            int x1 = Zones.safeTileX(tileX - dist);
                            int x2 = Zones.safeTileX(tileX + dist);
                            int y1 = Zones.safeTileY(tileY - dist);
                            int y2 = Zones.safeTileY(tileY + dist);

                            short targetTemperature = 0;

                            for (TilePos tPos : TilePos.areaIterator(x1, y1, x2, y2)) {
                                int xx = tPos.x;
                                yy = tPos.y;
                                VolaTile t = Zones.getTileOrNull(xx, yy, true);
                                if ((t != null)) {
                                    for (Item item : t.getItems()) {

                                        short effectiveTemperature = (short)(item.getTemperature() / Math.max(1,Math.sqrt(Math.pow(Math.abs(tileX - xx),2) + Math.pow(Math.abs(tileY - yy),2))));

                                        if ((item.isBrazier() || item.isOnFire() || item.isFireplace()) && effectiveTemperature > targetTemperature) {
                                            targetTemperature = effectiveTemperature;
                                        }
                                    }
                                }
                            }

                            // Add warming effect from heat source
                            temperatureDelta += (short) Math.ceil((double)targetTemperature / 4000);

                            for (int x = 0; x < b.getSpaces().length; x++) {
                                if (b.getSpaces()[x] != null)
                                {
                                    Item[] itemarr = b.getSpaces()[x].getAllItems(false);

                                    for (int y = 0; y < itemarr.length; y++) {
                                        if (itemarr[y].isBodyPart()) {
                                            short temperature = itemarr[y].getTemperature();
                                            temperature = (short)Math.min(2500,Math.max(0, (int)temperature + (int)temperatureDelta));
                                            itemarr[y].setTemperature(temperature);

                                            if (temperatureDelta < 0) {

                                                if (itemarr[y].getTemperature() < 50) {
                                                    if (message == null) {
                                                        message = "You are very cold and should find warmth";
                                                    }
                                                }

                                            } else {
                                                itemarr[y].setTemperature(temperature);
                                                if (itemarr[y].getTemperature() < 50) {
                                                    message = "You are warming up.";
                                                }
                                            }

                                            if (temperature == 0) {
                                                if (Server.rand.nextInt(1000) > 750) {

                                                    byte woundPos = (short)0;

                                                    switch(itemarr[y].getName()) {

                                                        case "body":
                                                            woundPos = b.getCenterWoundPos();
                                                            break;
                                                        case "head":
                                                            woundPos = b.getRandomWoundPos((byte)7);
                                                            break;
                                                        case "left foot":
                                                            woundPos = b.getRandomWoundPos((byte)4);
                                                            break;
                                                        case "right foot":
                                                            woundPos = b.getRandomWoundPos((byte)3);
                                                            break;
                                                        case "right arm":
                                                            woundPos = b.getRandomWoundPos((byte)2);
                                                            break;
                                                        case "left arm":
                                                            woundPos = b.getRandomWoundPos((byte)5);
                                                            break;
                                                        case "right hand":
                                                            woundPos = b.getRandomWoundPos((byte)2);
                                                            break;
                                                        case "left hand":
                                                            woundPos = b.getRandomWoundPos((byte)5);
                                                            break;
                                                        case "back":
                                                            woundPos = b.getCenterWoundPos();
                                                            break;
                                                        case "face":
                                                            woundPos = b.getRandomWoundPos((byte)7);
                                                            break;
                                                        case "legs":
                                                            woundPos = b.getRandomWoundPos((byte)10);
                                                            break;
                                                    }
                                                    if (woundPos != (short)0) {
                                                        int dmg = Server.rand.nextInt(1500);
                                                        p.addWoundOfType(null, (byte)8, woundPos, false, 1.0F, true,dmg);
                                                        message = "You are freezing cold! Find warmth quickly.";
                                                    }
                                                }


                                            }
                                        }
                                    }
                                }
                            }

                        }
                        if (message != null) {
                            p.getCommunicator().sendNormalServerMessage(message);
                        }
                        return method.invoke(object, args);
                    }
                };
            }
        });

        HookManager.getInstance().registerHook("com.wurmonline.server.players.Player", "setDeathEffects", "(ZII)V", new InvocationHandlerFactory() {

            @Override
            public InvocationHandler createInvocationHandler() {
                return new InvocationHandler() {

                    @Override
                    public Object invoke(Object object, Method method, Object[] args) throws Throwable {

                        Player p = (Player) object;

                        Body b =  p.getBody();

                        for (int x = 0; x < b.getSpaces().length; x++) {
                            if (b.getSpaces()[x] != null)
                            {
                                Item[] itemarr = b.getSpaces()[x].getAllItems(false);
                                for (int y = 0; y < itemarr.length; y++) {
                                    if (itemarr[y].isBodyPart()) {
                                        short temperature = itemarr[y].getTemperature();
                                        itemarr[y].setTemperature((short)200);
                                    }
                                }
                            }
                        }

                        return method.invoke(object, args);
                    }
                };
            }
        });

    }


}
