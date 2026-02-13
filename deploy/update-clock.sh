#! /bin/sh
#
# update_clock.sh
# Copyright (C) 2016 asitk <asitk@ak-ubuntu>
#
# Distributed under terms of the MIT license.
#

sudo service ntp stop
sudo ntpdate -s time.nist.gov
sudo service ntp start
