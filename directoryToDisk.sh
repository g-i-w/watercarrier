mkdir -p /mnt$2 && mount $2 /mnt$2 && rsync -a --info=progress2 $1 /mnt$2
