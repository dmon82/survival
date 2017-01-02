package org.talor.wurmunlimited.mods.survival;

import com.wurmonline.math.TilePos;
import com.wurmonline.server.Items;
import com.wurmonline.server.WurmCalendar;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.Vehicle;
import com.wurmonline.server.behaviours.Vehicles;
import com.wurmonline.server.bodys.BodyTemplate;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.NoArmourException;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.NoSpaceException;
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
import java.util.logging.Level;
import java.util.logging.Logger;


public class Survival implements WurmServerMod, Configurable, ServerStartedListener, Initable, PreInitable {

    private static Logger logger = Logger.getLogger(Survival.class.getName());

    // Configuration default values
    private boolean enableTemperatureSurvival = true;
    private boolean newPlayerProtection = false;
    private boolean gmProtection = true;
    private boolean verboseLogging = false;

    // List of body parts
    private byte[] bodyParts =  new byte[] { BodyTemplate.head, BodyTemplate.torso, BodyTemplate.leftArm, BodyTemplate.leftHand, BodyTemplate.rightArm, BodyTemplate.rightHand, BodyTemplate.legs, BodyTemplate.leftFoot, BodyTemplate.rightFoot  };

    @Override
	public void onServerStarted() {
	}

	@Override
	public void configure(Properties properties) {
        // Check .properties file for configuration values
        enableTemperatureSurvival = Boolean.parseBoolean(properties.getProperty("enableTemperatureSurvival", Boolean.toString(enableTemperatureSurvival)));
        newPlayerProtection = Boolean.parseBoolean(properties.getProperty("newPlayerProtection", Boolean.toString(newPlayerProtection)));
        verboseLogging = Boolean.parseBoolean(properties.getProperty("verboseLogging", Boolean.toString(verboseLogging)));
        gmProtection = Boolean.parseBoolean(properties.getProperty("gmProtection", Boolean.toString(gmProtection)));
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

                        Player player = (Player) object;

                        if (enableTemperatureSurvival && !(player.hasSpellEffect((byte) 75) && newPlayerProtection) && !(player.getPower() >= 2 && gmProtection) && !player.isDead() && player.secondsPlayed % 15.0F == 0.0F) {

                            // Fetches temperature effects for the polled player
                            TempEffects temperatureEffects = getTemperatureEffects(player);

                            // Cycles through body parts for the polled player and applies cooling/warming and frost wounds where appropriate
                            pollBodyPartTemperature(player, true, true, temperatureEffects);
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

                        Player player = (Player) object;
                        Body body = player.getBody();

                        // Reset temperature of all body parts to very warm
                        for (byte y : bodyParts) {
                            body.getBodyPart(y).setTemperature((short)200);
                        }

                        return method.invoke(object, args);
                    }
                };
            }
        });


        HookManager.getInstance().registerHook("com.wurmonline.server.creatures.Communicator", "sendSafeServerMessage", "(Ljava/lang/String;)V", new InvocationHandlerFactory() {

            @Override
            public InvocationHandler createInvocationHandler() {
                return new InvocationHandler() {

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String HoldSentText = (String) args[0];
                        if (HoldSentText.startsWith("Unknown command: /mytemp")) {
                            return null;
                        }
                        return method.invoke(proxy, args);
                    }
                };
            }

        });

        HookManager.getInstance().registerHook("com.wurmonline.server.creatures.Communicator", "reallyHandle_CMD_MESSAGE", "(Ljava/nio/ByteBuffer;)V", new InvocationHandlerFactory() {

            @Override
            public InvocationHandler createInvocationHandler() {
                return new InvocationHandler() {

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        method.invoke(proxy, args);
                        Communicator ComObject = (Communicator) proxy;
                        String commandMessage = ComObject.getCommandMessage();
                        Player player = ComObject.player;
                        if (commandMessage.charAt(0) == '/') {
                            if (enableTemperatureSurvival) {
                                if (commandMessage.startsWith("/mytemp")) {
                                    TempEffects tempEffects = getTemperatureEffects(player);
                                    // Find average temperature and temperature delta, but do not apply wounds or generate warning messages
                                    tempEffects = pollBodyPartTemperature(player, false, false, tempEffects);
                                    String message = "";

                                    // Produce a user-friendly summary of temperature and temperature delta

                                    if (tempEffects.averageTemperature > 200) {
                                        message = message + "You are hot.";
                                    } else {
                                        if (tempEffects.averageTemperature == 0) {
                                            message = message + "You are freezing cold,";
                                        } else if (tempEffects.averageTemperature < 70) {
                                            message = message + "You are very cold,";
                                        } else if (tempEffects.averageTemperature < 130) {
                                            message = message + "You are cold,";
                                        } else if (tempEffects.averageTemperature < 180) {
                                            message = message + "You are warm,";
                                        } else {
                                            message = message + "You are very warm,";
                                        }

                                        if (tempEffects.averageModifiedTemperatureDelta == 0 || (tempEffects.averageTemperature < 130 && tempEffects.averageModifiedTemperatureDelta < 0) || (tempEffects.averageTemperature >= 130 && tempEffects.averageModifiedTemperatureDelta > 0)) {
                                            message = message + " and ";
                                        } else {
                                            message = message + " but ";
                                        }

                                        if (tempEffects.averageModifiedTemperatureDelta < -3) {
                                            message = message + "you are rapidly getting colder.";
                                        } else if (tempEffects.averageModifiedTemperatureDelta < 0) {
                                            message = message + "you are getting colder.";
                                        } else if (tempEffects.averageModifiedTemperatureDelta == 0) {
                                            message = message + "this is unlikely to change.";
                                        } else if (tempEffects.averageModifiedTemperatureDelta <= 3) {
                                            message = message + "you are getting warmer.";
                                        } else {
                                            message = message + "you are rapidly getting warmer.";
                                        }
                                    }

                                    player.getCommunicator().sendNormalServerMessage(message);

                                }
                            }
                        }
                        return null;
                    }
                };
            }
        });

        HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.MethodsItems", "eat", "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;F)Z", new InvocationHandlerFactory() {

            @Override
            public InvocationHandler createInvocationHandler () {
                return new InvocationHandler() {

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

                        Boolean result = (Boolean) method.invoke(proxy, args);

                        Action act = (Action) args[0];
                        Creature player = (Creature) args[1];
                        Item food = (Item) args[2];

                        if (!result && player.isPlayer() && act.currentSecond() % 5 == 0 && food.getTemperature() > 1000) {
                            warmAllBodyParts((Player)player, (short)5);
                            player.getCommunicator().sendNormalServerMessage("The " + food.getName() + " warms you up.");
                            if (verboseLogging) logger.log(Level.INFO, player.getName() + " is warmed by eating some " + food.getName());
                        }
                        return result;
                    }
                };
            }
        });

        HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.MethodsItems", "drink", "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;F)Z", new InvocationHandlerFactory() {

            @Override
            public InvocationHandler createInvocationHandler () {
                return new InvocationHandler() {

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

                        Boolean result = (Boolean) method.invoke(proxy, args);

                        Action act = (Action) args[0];
                        Creature player = (Creature) args[1];
                        Item drink = (Item) args[2];

                        if (!result && player.isPlayer() && act.currentSecond() % 2 == 0 && drink.getTemperature() > 600) {
                            warmAllBodyParts((Player)player, (short)5);
                            player.getCommunicator().sendNormalServerMessage("The " + drink.getName() + " warms you up.");
                            if (verboseLogging) logger.log(Level.INFO, player.getName() + " is warmed by drinking some " + drink.getName());
                        }
                        return result;
                    }
                };
            }
        });

    }

    private void warmAllBodyParts(Player player, short change) {

        Body body = player.getBody();

        for(byte y : bodyParts ) {
            try {
                Item bodyPart = body.getBodyPart(y);
                bodyPart.setTemperature((short)(bodyPart.getTemperature() + change));
            } catch (NoSpaceException nse) {
                logger.log(Level.WARNING, nse.getMessage());
            }
        }
    }

    private TempEffects pollBodyPartTemperature(Player player, boolean applyTemperatureAndWounds, boolean warningMessages, TempEffects tempEffects) {

        try {
            String message = null;
            boolean urgentAlert = false;
            short totalTemperature = 0;
            double totalTemperatureDelta = 0;
            short countBodyParts = 0;

            Body body = player.getBody();

            for (byte y : bodyParts) {

                try {
                    Item bodyPart = body.getBodyPart(y);
                    short temperature = bodyPart.getTemperature();
                    Item armour = null;
                    double armourGeneralBonus = 0;
                    double armourSwimBonus = 0;
                    double armourWindBonus = 0;
                    double armourRainBonus = 0;
                    double armourEffects = 0;

                    try{
                        armour = player.getArmour(y);
                    } catch (NoArmourException nae) {
                        if (verboseLogging) logger.log(Level.INFO, nae.getMessage());
                    }

                    if(armour != null) {
                         switch (armour.getMaterial()) {
                             case 9:    // Steel
                             case 11:   // Iron
                             case 56:   // Adamantine
                             case 57:   // Glimmersteel
                             case 67:   // Seryll
                                armourGeneralBonus = 0.4;
                                break;
                             case 16:   //Leather (includes drake hide and dragon scale)
                                armourGeneralBonus = 0.4;
                                armourWindBonus = 0.5;
                                armourRainBonus = 0.5;
                                break;
                             case 17:   // Cloth
                                armourGeneralBonus = 0.6;
                                armourWindBonus = 0.25;
                                break;
                             case 69:   // Wool
                                armourGeneralBonus = 1;
                                armourRainBonus = 0.25;
                                armourSwimBonus = 1;
                                break;
                        }

                        switch (armour.getTemplateId()) {
                            case 960:   // Leather adventurer's hat
                                armourRainBonus = 0.75;
                                break;
                            case 959:   // Brown bear helm
                                armourGeneralBonus = 1;
                                armourRainBonus = 0.25;
                                armourWindBonus = 0;
                                armourSwimBonus = 1;
                                break;
                        }

                        // Higher quality clothing gives better bonuses
                        armourGeneralBonus   = (armourGeneralBonus * 0.4) + (armourGeneralBonus * 0.6 * (armour.getCurrentQualityLevel()/100));
                        armourRainBonus      = (armourRainBonus * 0.4) + (armourRainBonus * 0.6 * (armour.getCurrentQualityLevel()/100));
                        armourWindBonus      = (armourWindBonus * 0.4) + (armourWindBonus * 0.6 * (armour.getCurrentQualityLevel()/100));
                        armourSwimBonus      = (armourSwimBonus * 0.4) + (armourSwimBonus * 0.6 * (armour.getCurrentQualityLevel()/100));

                        if (verboseLogging) logger.log(Level.INFO, player.getName() + " - " + bodyPart.getName() + "(" + temperature + ") slot: " + armour.getName());
                    }

                    armourEffects = armourGeneralBonus + Math.min(0,(double)tempEffects.swimMod + armourSwimBonus) + Math.min(0,(double)tempEffects.rainMod + armourRainBonus) + Math.min(0, (double)tempEffects.windMod + armourWindBonus) + (double) tempEffects.altitudeMod;

                    // Apply temperature
                    double doubleDelta = tempEffects.baseTemperatureDelta + armourEffects ;
                    short temperatureDelta = (short) Math.round(doubleDelta);
                    totalTemperatureDelta = totalTemperatureDelta + temperatureDelta;

                    temperature = (short) Math.min(250, Math.max(0, Math.round(temperature + temperatureDelta)));

                    if (verboseLogging) logger.log(Level.INFO, player.getName() + " - double delta: " + doubleDelta + " rounded to " + temperatureDelta);

                    if (applyTemperatureAndWounds) bodyPart.setTemperature(temperature);
                    totalTemperature+= temperature;
                    countBodyParts++;

                    // Display warning messages if player is very cold
                    if (temperatureDelta < 0) {
                        if (warningMessages && temperature < 50) {
                            if (message == null) {
                                message = "You are very cold and should find warmth";
                                urgentAlert = true;
                            }
                        }
                    } else {
                        if (temperature < 50) {
                            message = "You are warming up.";
                        }
                    }

                    // Give the player some cold wounds if they are freezing and display a warning message
                    if (applyTemperatureAndWounds && temperature == 0) {
                        if (Server.rand.nextInt(1000) > 750) {
                            int dmg = Server.rand.nextInt(2000);
                            // Only apply wounds every other poll
                            if (player.secondsPlayed % 30.0F == 0.0F) {
                                player.addWoundOfType(null, (byte) 8, y, false, 1.0F, false, dmg);
                            }
                            if (warningMessages) {
                                message = "You are freezing cold! Find warmth quickly!";
                                urgentAlert = true;
                            }
                        }
                    }
                } catch (NoSpaceException nse) {
                    logger.log(Level.WARNING, nse.getMessage());
                }

            }
            // Send the message to the events tab
            if (urgentAlert) {
                player.getCommunicator().sendNormalServerMessage(message, (byte)4);
            } else if (message != null) {
                player.getCommunicator().sendNormalServerMessage(message);
            }
            // Calculate average temperature and temperature delta and return these values
            tempEffects.averageModifiedTemperatureDelta = (short)(totalTemperatureDelta/countBodyParts);
            tempEffects.averageTemperature = (short) (totalTemperature/countBodyParts);
            return tempEffects;
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage());
            return null;
        }
    }


    private TempEffects getTemperatureEffects(Player player) {
        try {
            int tileX = player.getCurrentTile().getTileX();
            int tileY = player.getCurrentTile().getTileY();
            double hour = (double)WurmCalendar.getHour();
            int day = (int)(WurmCalendar.currentTime % (long)29030400 / (long)86400);
            double starfall = (double)WurmCalendar.getStarfall() + ((double)day%28 / (double)28);
            boolean isIndoors = !player.getCurrentTile().isOnSurface() || (player.getCurrentTile().getStructure() != null && player.getCurrentTile().getStructure().isFinished());
            boolean isOnBoat = false;
            if (player.getVehicle() != (long)-10) {
                Vehicle vehicle = Vehicles.getVehicleForId(player.getVehicle());
                if (!vehicle.isCreature() && Items.getItem(player.getVehicle()).isBoat()) isOnBoat = true;
            }

            // Approximation of seasonal heat differences
            // Produces number between -4 and 4
            double monthTempMod = 4 * Math.sin((starfall - 3) / 1.91);

            // Approximation of day/night heat differences
            // Produces number between -2 and 2
            double hourTempMod = 2 * Math.sin((hour - 6)/3.82);

            // Colder if strong wind or gale
            // Produces -1 or 0
            short windMod = !isIndoors && Math.abs(Server.getWeather().getWindPower()) > 0.3 ? (short)-1 : 0;

            // Colder if swimming
            // Produces -2 or 0
            short swimMod = !isOnBoat && Zones.calculateHeight(player.getPosX(), player.getPosY(), player.isOnSurface()) < 0 ? (short)-3 : 0;

            // Colder if raining
            // Produces -1 or 0
            short rainMod = !isIndoors && Server.getWeather().getRain() > 0.5 ? (short)-1 : 0;

            // Colder at very high altitudes
            // Produces -1 or 0
            short altitudeMod = Zones.calculateHeight(player.getPosX(), player.getPosY(), player.isOnSurface()) > 180 ? (short)-1 : 0;

            // Positive value indicates warming, negative value indicates cooling
            // Produces within a rough range of -10 to 5
            double baseTemperatureDelta = monthTempMod + hourTempMod;

            // Make PVP servers warmer
            if(Zones.isOnPvPServer(tileX,tileY)) baseTemperatureDelta++;
            if (verboseLogging) logger.log(Level.INFO, player.getName() + " has following modifiers... calendar mod: " + monthTempMod + ", day/night mod: " + hourTempMod + ", windMod : " + windMod + ", swimMod: " + swimMod + ", rainMod: " + rainMod +", altitudeMod: " + altitudeMod + ", pvp: " + Zones.isOnPvPServer(tileX,tileY) + ", indoors: " + isIndoors);

            // Search nearby for heat sources
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
                VolaTile t = Zones.getTileOrNull(xx, yy, player.isOnSurface());
                if ((t != null)) {
                    for (Item item : t.getItems()) {
                        short effectiveTemperature = 0;
                        // Closer heat sources provide more heat. Uses pythagorean theorem to find distance, then uses inverse square law to determine intensity
                        if (item.isOnFire()) {
                            effectiveTemperature = (short) (item.getTemperature() * (1 / Math.pow(Math.max(1, Math.sqrt(Math.pow(Math.abs(tileX - xx), 2) + Math.pow(Math.abs(tileY - yy), 2))),2)));
                            effectiveTemperature = item.isLight() ? (short)Math.round(effectiveTemperature/6) : effectiveTemperature;
                        }
                        // Only pay attention to the heat sources providing the biggest effect (i.e. heat sources do not stack)
                        if (effectiveTemperature > targetTemperature) {
                            targetTemperature = effectiveTemperature;
                        }
                    }
                }
            }
            // Add warming effect from heat source
            baseTemperatureDelta += (short) Math.round(Math.min((double)7,(double) targetTemperature / (double)1800));

            return new TempEffects(baseTemperatureDelta, swimMod, windMod, rainMod, altitudeMod);
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage());
            return null;
        }
    }

}
