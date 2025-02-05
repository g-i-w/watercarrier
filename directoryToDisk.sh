mkdir -p /mnt$2 && mount $2 /mnt$2 && rsync -av $1 /mnt$2
