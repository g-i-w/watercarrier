mkdir -p /mnt$2 && mount $2 /mnt$2 && rsync -rhkL --update --info=progress2 $1 /mnt$2
