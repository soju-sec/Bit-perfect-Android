#!/system/bin/sh
MODDIR=${0%/*}

# Ensure patched policy and effects are bind-mounted
if [ -f "$MODDIR/system/vendor/etc/audio_policy_configuration.xml" ]; then
    mount -o bind "$MODDIR/system/vendor/etc/audio_policy_configuration.xml" /vendor/etc/audio_policy_configuration.xml
fi

if [ -f "$MODDIR/system/vendor/etc/audio_effects.xml" ]; then
    mount -o bind "$MODDIR/system/vendor/etc/audio_effects.xml" /vendor/etc/audio_effects.xml
fi

while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

sleep 2

# Apply audio properties dynamically
resetprop -n ro.audio.samplerates "44100,48000,88200,96000,176400,192000,352800,384000"
resetprop -n persist.vendor.audio.samplerates "44100,48000,88200,96000,176400,192000,352800,384000"
resetprop -n vendor.audio.feature.usb_offload.enable false
resetprop -n vendor.audio.feature.usb_offload_burst_mode.enable false
resetprop -n vendor.audio.usb.super_high_res true
resetprop -n audio.safemedia.bypass true

# Restart audioserver cleanly so the new AudioPolicy takes effect
killall -9 audioserver
