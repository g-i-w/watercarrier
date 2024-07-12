gunzip -c $1 | dd of=$2 bs=4M status=progress iflag=fullblock
