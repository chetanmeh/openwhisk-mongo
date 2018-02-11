#!/bin/bash
SCRIPTDIR=$(cd $(dirname "$0") && pwd)
HOMEDIR="$SCRIPTDIR/../../../"

cd $HOMEDIR

# shallow clone OpenWhisk repo.
# TODO - Switch to apache master once classtag change is merged
git clone --depth 1 -b artifactstore-classtag --single-branch https://github.com/chetanmeh/incubator-openwhisk.git openwhisk

cd openwhisk
./tools/travis/setup.sh
