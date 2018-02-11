#!/bin/bash
set -e

# Build script for Travis-CI.

SCRIPTDIR=$(cd $(dirname "$0") && pwd)
ROOTDIR="$SCRIPTDIR/../.."
WHISKDIR="$ROOTDIR/../openwhisk"

cd $WHISKDIR
GRADLE_PROJS_SKIP="-x :actionRuntimes:pythonAction:distDocker  -x :actionRuntimes:python2Action:distDocker -x actionRuntimes:swift3.1.1Action:distDocker -x :actionRuntimes:javaAction:distDocker"

# TODO Remove createKeystore
TERM=dumb ./gradlew install distDocker -PdockerImagePrefix=testing -x test -x createKeystore $GRADLE_PROJS_SKIP

cd $ROOTDIR
TERM=dumb ./gradlew :tests:reportScovergae

# Push coverage reports to codecov
bash <(curl -s https://codecov.io/bash)
