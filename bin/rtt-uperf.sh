## Uses byteman to measure RTT times for UPerf

#!/bin/bash

PGM=org.jgroups.tests.perf.UPerf

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
LIB=`dirname $SCRIPT_DIR`/lib
SCRIPT=`dirname $SCRIPT_DIR`/conf/scripts/RttTest/rtt.btm


jgroups.sh -javaagent:$LIB/byteman.jar=script:$SCRIPT $PGM $*
