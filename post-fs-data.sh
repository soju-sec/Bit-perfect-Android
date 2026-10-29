#!/system/bin/sh
MODDIR=${0%/*}

# Bind mount patched configurations to vendor partition
if [ -f "$MODDIR/system/vendor/etc/audio_policy_configuration.xml" ]; then
    mount -o bind "$MODDIR/system/vendor/etc/audio_policy_configuration.xml" /vendor/etc/audio_policy_configuration.xml
fi

if [ -f "$MODDIR/system/vendor/etc/audio_effects.xml" ]; then
    mount -o bind "$MODDIR/system/vendor/etc/audio_effects.xml" /vendor/etc/audio_effects.xml
fi
