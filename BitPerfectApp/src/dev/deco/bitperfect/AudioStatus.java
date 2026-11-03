package dev.deco.bitperfect;

public class AudioStatus {
    public String dacName = "USB Audio DAC";
    public int sourceRate = 0;
    public int dacRate = 0;
    public String format = "PCM_24_BIT";
    public boolean isBitPerfect = false;
    public String activeApp = "None";
    public String activePkg = "";
    public boolean isDirectPcm = false;
    public int targetPreferredRate = 0;

    @Override
    public String toString() {
        return "AudioStatus{" +
                "dac='" + dacName + '\'' +
                ", sourceRate=" + sourceRate +
                ", dacRate=" + dacRate +
                ", format='" + format + '\'' +
                ", isBitPerfect=" + isBitPerfect +
                ", activeApp='" + activeApp + '\'' +
                '}';
    }
}
