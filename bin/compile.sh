#!/bin/bash

native-image -cp $CLASSPATH -H:JNIConfigurationFiles=JGroups/conf/jni.json -H:+ReportUnsupportedElementsAtRuntime -H:+JNI --no-server -H:+PrintAnalysisCallTree  org.jgroups.demos.ProgrammaticChat