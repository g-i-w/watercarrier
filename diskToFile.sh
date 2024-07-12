dd if=$1 bs=4M status=progress iflag=fullblock | gzip > $2
