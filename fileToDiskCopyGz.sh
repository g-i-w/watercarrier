echo "Extracting and cloning '$1' to '$2'..."
gunzip -c $1 | dd of=$2 bs=4M status=progress
echo "Refreshing partitions on '$2'..."
partprobe $2
echo "Creating mountpoint directory '/mnt$22'..."
mkdir -p /mnt$22
echo "Mounting '$22' at '/mnt$22'..."
mount $22 /mnt$22
echo "Copying '$1' to '/mnt$22/home/servant/'..."
rsync --info=progress2 $1 /mnt$22/home/servant/
echo "Unmounting '/mnt$22'..."
umount /mnt$22
echo "Done."
