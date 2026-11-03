package dev.deco.bitperfect;

public class AudioStatus {
    public boolean isDacConnected = false;
    public String dacName = "Tidak Ada DAC USB";
    public int sourceRate = 0;
    public String sourceBitDepth = "";
    public int dacRate = 0;
    public String dacFormat = "--";
    public String format = "";
    public boolean isBitPerfect = false;
    public String activeApp = "None";
    public String activePkg = "";
    public String trackInfo = "";
    public String routeType = "DISCONNECTED";
    public boolean isAppBypassed = false;
    public boolean isMixerBypassed = false;
    public String matchStatus = "DAC Terputus";

    @Override
    public String toString() {
        return "AudioStatus{" +
                "isDacConnected=" + isDacConnected +
                ", dac='" + dacName + '\'' +
                ", sourceRate=" + sourceRate +
                ", dacRate=" + dacRate +
                ", routeType='" + routeType + '\'' +
                ", isBitPerfect=" + isBitPerfect +
                ", activeApp='" + activeApp + '\'' +
                '}';
    }
}
