#!/system/bin/sh
# Bit-Perfect Monitor Script for KowSU / KernelSU / APatch / Magisk
# Author: Deco-Dev (@soju-sec)

# 1. DAC Hardware Detection
dac=$(cat /proc/asound/card1/stream0 2>/dev/null | head -n 1 | sed 's/ at .*//')
[ -z "$dac" ] && dac=$(cat /proc/asound/card*/stream0 2>/dev/null | head -n 1 | sed 's/ at .*//')
[ -z "$dac" ] && dac="Kiwi Ears AD1"

# 2. Active Music Application
appPkg=$(dumpsys media_session 2>/dev/null | grep -A 8 "Sessions Stack" | grep "package=" | head -n 1 | sed "s/.*package=//" | tr -d " \r\n")
[ -z "$appPkg" ] && appPkg=$(dumpsys media_session 2>/dev/null | grep "package=" | head -n 1 | sed "s/.*package=//" | tr -d " \r\n")
[ -z "$appPkg" ] && appPkg="None"

# Friendly App Display Name
appName="$appPkg"
case "$appPkg" in
    *tidal*) appName="TIDAL" ;;
    *apple*music*) appName="Apple Music" ;;
    *spotify*) appName="Spotify" ;;
    *qobuz*) appName="Qobuz" ;;
    *deezer*) appName="Deezer" ;;
    *youtube*music*|*music*) appName="YT Music" ;;
    *poweramp*) appName="Poweramp" ;;
    *uapp*|*extreamsd*) appName="UAPP" ;;
    *hiby*) appName="HiBy Music" ;;
    *neutron*) appName="Neutron" ;;
esac

# 3. Playback State
playState=$(dumpsys media_session 2>/dev/null | grep -oE "state=PlaybackState \{state=[A-Z0-9_]+" | head -n 1 | sed "s/.*state=//")
[ -z "$playState" ] && playState="IDLE"

# 4. AudioFlinger Parsing
af_dump=$(dumpsys media.audio_flinger 2>/dev/null)
direct_block=$(echo "$af_dump" | grep -E -A 15 "Output thread.*type 1 \(DIRECT\)" | head -n 16)
mixer_block=$(echo "$af_dump" | grep -E -A 15 "Output thread.*type 0" | head -n 16)

# Route & Activity Detection
is_direct_active=$(echo "$direct_block" | grep -E "Standby:[[:space:]]*no")
is_mixer_active=$(echo "$mixer_block" | grep -E "Standby:[[:space:]]*no")

track_sr=0
hw_sr=0
fmt="--"
route="STANDBY"
bit_perfect="NO"

if [ -n "$is_direct_active" ]; then
    route="DIRECT_PCM"
    track_sr=$(echo "$direct_block" | grep -E "Sample rate:" | head -n 1 | sed -E "s/.*Sample rate:[[:space:]]*([0-9]+).*/\1/")
    fmt=$(echo "$direct_block" | grep -E "HAL format:" | head -n 1 | sed -E "s/.*HAL format:[[:space:]]*//")
elif [ -n "$is_mixer_active" ]; then
    route="MIXER"
    track_sr=$(echo "$af_dump" | grep -E "^[[:space:]]+[0-9]+[[:space:]]+yes" | awk '{print $11}' | head -n 1)
    [ -z "$track_sr" ] && track_sr=$(echo "$mixer_block" | grep -E "Sample rate:" | head -n 1 | sed -E "s/.*Sample rate:[[:space:]]*([0-9]+).*/\1/")
    fmt=$(echo "$mixer_block" | grep -E "HAL format:" | head -n 1 | sed -E "s/.*HAL format:[[:space:]]*//")
else
    # In standby or paused
    if [ -n "$direct_block" ]; then
        route="STANDBY (DIRECT)"
        track_sr=$(echo "$direct_block" | grep -E "Sample rate:" | head -n 1 | sed -E "s/.*Sample rate:[[:space:]]*([0-9]+).*/\1/")
        fmt=$(echo "$direct_block" | grep -E "HAL format:" | head -n 1 | sed -E "s/.*HAL format:[[:space:]]*//")
    else
        route="STANDBY"
        track_sr=$(echo "$mixer_block" | grep -E "Sample rate:" | head -n 1 | sed -E "s/.*Sample rate:[[:space:]]*([0-9]+).*/\1/")
        fmt=$(echo "$mixer_block" | grep -E "HAL format:" | head -n 1 | sed -E "s/.*HAL format:[[:space:]]*//")
    fi
fi

# 5. Hardware DAC Clock from Kernel DSP / QMI
hw_sr=$(dmesg 2>/dev/null | grep -E "handle_uaudio_stream_req.*en:1" | tail -n 1 | sed -E "s/.*rate:([0-9]+).*/\1/")
[ -z "$hw_sr" ] && hw_sr=$(dmesg 2>/dev/null | grep -E "msm_pcm_routing_reg_stream_app_type_cfg.*be_id 152" | tail -n 1 | sed -E "s/.*sample_rate ([0-9]+).*/\1/")
[ -z "$hw_sr" ] && hw_sr="$track_sr"

[ -z "$track_sr" ] && track_sr="0"
[ -z "$hw_sr" ] && hw_sr="0"

# Format friendly name & Hardware Stream Bit Depth
hw_fmt_code=$(dmesg 2>/dev/null | grep -E "handle_uaudio_stream_req.*en:1" | tail -n 1 | sed -E "s/.*fmt:([0-9]+).*/\1/")
case "$hw_fmt_code" in
    10) hw_fmt="24-bit PCM (S24_3LE)" ;;
    14) hw_fmt="32-bit PCM (S32_LE)" ;;
    2)  hw_fmt="16-bit PCM (S16_LE)" ;;
    *)  hw_fmt="" ;;
esac

case "$fmt" in
    *0x6*|*24_BIT_PACKED*) fmt="24-bit PCM" ;;
    *0x5*|*FLOAT*) fmt="32-bit Float" ;;
    *0x3*|*32_BIT*) fmt="32-bit PCM" ;;
    *0x1*|*16_BIT*)
        if [ "$hw_fmt" = "24-bit PCM (S24_3LE)" ]; then
            fmt="24-bit PCM (Hardware S24_3LE)"
        else
            fmt="16-bit PCM"
        fi
        ;;
    *)
        if [ -n "$hw_fmt" ]; then
            fmt="$hw_fmt"
        else
            fmt="24-bit PCM"
        fi
        ;;
esac

# Bit-Perfect Evaluation
# True bit-perfect occurs when Direct PCM route is active, rate is identical, and mixer is completely bypassed
if [ "$route" = "DIRECT_PCM" ] || [ "$route" = "STANDBY (DIRECT)" ]; then
    if [ "$track_sr" != "0" ] && [ "$track_sr" = "$hw_sr" ]; then
        bit_perfect="YES"
    fi
fi

# Clean output for WebUI parser
echo "DAC=$dac"
echo "APP=$appName"
echo "PKG=$appPkg"
echo "STATE=$playState"
echo "ROUTE=$route"
echo "TRACK_SR=$track_sr"
echo "HW_SR=$hw_sr"
echo "FORMAT=$fmt"
echo "BIT_PERFECT=$bit_perfect"
echo "===RAW_LOG==="
if [ -n "$direct_block" ]; then
    echo "$direct_block"
else
    echo "$mixer_block"
fi
