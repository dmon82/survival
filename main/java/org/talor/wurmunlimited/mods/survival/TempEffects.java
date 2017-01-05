package org.talor.wurmunlimited.mods.survival;

public class TempEffects {

    public double baseTemperatureDelta;
    public short swimMod;
    public short windMod;
    public short rainMod;
    public short altitudeMod;
    public short tileMod;
    public short averageModifiedTemperatureDelta;
    public short averageTemperature;

    public TempEffects(double b, short s, short w, short r, short a, short t) {
        baseTemperatureDelta = b;
        swimMod = s;
        windMod = w;
        rainMod = r;
        altitudeMod = a;
        tileMod = t;
    }

}

