echo "extracting and cloning $1 to $2 ..."
gunzip -c $1 | dd of=$2 bs=4M status=progress
echo "creating mountpoint directory /mnt$2 ..."
mkdir -p /mnt$2
echo "mounting $2 at /mnt$2 ..."
mount $2 /mnt$2
echo "copying $1 to /mnt$2$1 ..."
rsync --info=progress2 $1 /mnt$2$1
echo "unmounting /mnt$2 ..."
umount /mnt$2
echo "Done."
