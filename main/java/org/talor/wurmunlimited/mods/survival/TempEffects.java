package org.talor.wurmunlimited.mods.survival;

public class TempEffects {

    public double baseTemperatureDelta;
    public short swimMod;
    public short windMod;
    public short rainMod;
    public short averageModifiedTemperatureDelta;
    public short averageTemperature;

    public TempEffects(double b, short s, short w, short r) {
        baseTemperatureDelta = b;
        swimMod = s;
        windMod = w;
        rainMod = r;
    }

}

