package dev.deco.bitperfect;

public class AudioStatus {
    public String dacName = "USB Audio DAC";
    public int sourceRate = 0;
    public String sourceBitDepth = "16-bit";
    public int dacRate = 0;
    public String dacFormat = "S24_3LE • Altset 2";
    public String format = "PCM_24_BIT";
    public boolean isBitPerfect = false;
    public String activeApp = "None";
    public String activePkg = "";
    public String routeType = "STANDBY";
    public boolean isAppBypassed = false;
    public boolean isMixerBypassed = true;
    public String matchStatus = "Standby • Menunggu Pemutaran Audio";

    @Override
    public String toString() {
        return "AudioStatus{" +
                "dac='" + dacName + '\'' +
                ", sourceRate=" + sourceRate +
                ", dacRate=" + dacRate +
                ", format='" + format + '\'' +
                ", routeType='" + routeType + '\'' +
                ", isBitPerfect=" + isBitPerfect +
                ", activeApp='" + activeApp + '\'' +
                '}';
    }
}
