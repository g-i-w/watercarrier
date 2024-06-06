printf "$1\n$2\n$2\n"
printf "$1\n$2\n$2\n" | sudo -i -u servant passwd && echo "$2" | sha512sum > /home/servant/watercarrier/password.sha512
