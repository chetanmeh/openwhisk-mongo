#!/bin/bash
set -e

# Build script for Travis-CI.

SCRIPTDIR=$(cd $(dirname "$0") && pwd)
ROOTDIR="$SCRIPTDIR/../.."
WHISKDIR="$ROOTDIR/../openwhisk"

cd $WHISKDIR
GRADLE_PROJS_SKIP="-x :actionRuntimes:pythonAction:distDocker  -x :actionRuntimes:python2Action:distDocker -x actionRuntimes:swift3.1.1Action:distDocker -x :actionRuntimes:javaAction:distDocker"

TERM=dumb ./gradlew install distDocker -PdockerImagePrefix=testing $GRADLE_PROJS_SKIP

export OPENWHISK_HOME=$WHISKDIR

cd $ROOTDIR
TERM=dumb ./gradlew :tests:reportScoverage distDocker -PdockerImagePrefix=testing

# Push coverage reports to codecov
bash <(curl -s https://codecov.io/bash)
