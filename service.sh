#!/system/bin/sh
MODDIR=${0%/*}

# Ensure patched policy and effects are bind-mounted
if [ -f "$MODDIR/system/vendor/etc/audio_policy_configuration.xml" ]; then
    mount -o bind "$MODDIR/system/vendor/etc/audio_policy_configuration.xml" /vendor/etc/audio_policy_configuration.xml
fi

if [ -f "$MODDIR/system/vendor/etc/audio_io_policy.conf" ]; then
    mount -o bind "$MODDIR/system/vendor/etc/audio_io_policy.conf" /vendor/etc/audio_io_policy.conf
fi

if [ -f "$MODDIR/system/vendor/etc/audio_effects.xml" ]; then
    mount -o bind "$MODDIR/system/vendor/etc/audio_effects.xml" /vendor/etc/audio_effects.xml
fi

if [ -f "$MODDIR/system/lib64/libaudiopolicymanagerdefault.so" ]; then
    mount -o bind "$MODDIR/system/lib64/libaudiopolicymanagerdefault.so" /system/lib64/libaudiopolicymanagerdefault.so
fi

if [ -f "$MODDIR/monitor.sh" ]; then
    chmod 755 "$MODDIR/monitor.sh"
fi

while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

sleep 2

# Apply audio properties dynamically
resetprop -n ro.audio.samplerates "44100,48000,88200,96000,176400,192000,352800,384000"
resetprop -n persist.vendor.audio.samplerates "44100,48000,88200,96000,176400,192000,352800,384000"
resetprop -n ro.audio.flinger_standbytime_ms 1000
resetprop -n vendor.audio.feature.usb_offload.enable true
resetprop -n vendor.audio.feature.usb_offload_burst_mode.enable false
resetprop -n vendor.audio.feature.hifi_audio.enable true
resetprop -n vendor.audio.usb.super_high_res true
resetprop -n audio.deep_buffer.media false
resetprop -n audio.safemedia.bypass true
resetprop -n persist.vendor.audio.format.24bit true
resetprop -n persist.vendor.audio_hal.dsp_bit_width_enforce_mode 24
resetprop -n af.fast_track_multiplier 2
resetprop -n aaudio.mmap_policy 2
resetprop -n aaudio.mmap_exclusive_policy 2

# Clean audioserver restart & stale player session cleanup
killall -9 audioserver
sleep 1
am force-stop com.aspiro.tidal 2>/dev/null
am force-stop com.apple.android.music 2>/dev/null
