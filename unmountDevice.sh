while mount | grep -q "$1"; do
        umount "$1"
        sleep 0.1
done
