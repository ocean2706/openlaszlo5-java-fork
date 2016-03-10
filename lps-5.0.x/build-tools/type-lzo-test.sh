#!/bin/bash

runtime=
runtimelist=
aflag=
exitcode=0

# each command line arg is a runtime.  If there are multiple, use the -a flag
for arg; do
    if [ "$arg" = '-a' ]; then
        aflag=-a
    else
        runtime="$runtime --runtime=$arg"
        runtimelist="$runtimelist $arg"
    fi
done
if [ "$runtime" = '' ]; then
    runtime='--runtime=dhtml'
    runtimelist=dhtml
fi

################################################################
# prepare binary library test case
#
# usage:
# lzo-type-test.sh <RUNTIME>
#
# 


# lzo_arg path/to/file.lzo
# checks to make sure .lzo name is correct, and returns the same
lzo_arg() {
    echo "$1" | sed -e 's/lzx$/lzo/'
}

# lzx_arg path/to/file.lzo
# returns corresponding .lzx name
lzx_arg() {
    echo "$1" | sed -e 's/lzo$/lzx/'
}


zip_arg() {
    echo "$1" | sed -e 's/lzx$/zip/'
}

# prepare_lzo_compile path/to/file.lzo
prepare_lzo_compile() {
    lzo=`lzo_arg "$1"`
    lzx=`lzx_arg "$1"`

    shift
    extra=
    withthis="-DwarnWithThis -DerrorWithThis"
    if [ "$#" != 0 -o "$aflag" != '' ]; then
        extra=" with extra compilation args: $aflag $@"
    fi
    if [ "$aflag" != '' ]; then
        withthis=""
    fi
    echo "==== compiling $lzx for runtimes: $runtimelist$extra ===="
    # compile that file with lzc
    $LPS_HOME/WEB-INF/lps/server/bin/lzc $runtime $aflag $withthis -c "$@" "$lzx"
    if [ "$?" != 0 ]; then
        exitcode=1
    fi
    # get rid of the .lzx file, so only .lzo remains
}

# prepare_lzo path/to/file.lzo
prepare_lzo() {
    prepare_lzo_compile "$@"
}

#mv lzo to zip package
lzo_zip () {
    lzo=`lzo_arg "$1"`
    zip=`zip_arg "$1"`

echo "lzo file is $lzo"
echo "zip file is $zip"
    cp $lzo $zip
    unzip $zip
}

#Benchmark file
benchmark_arg () {
    echo "$1" | sed -e 's/lzx$/benchmark/'
}

#Diff file
diff_arg () {
    echo "$1" | sed -e 's/lzx$/diff/'
}

#Compare lzo file with benchmark
compare_lzo() {
    benchmark=`benchmark_arg "$1"`
    DIF=`diff_arg "$1"`
    diff lzo $benchmark > $DIF
    
    if [[ ! -s "$DIF" ]]
    then
        echo "$1 compile to lzo as expectation"
        echo "=========Passed=========="
    else
        echo "=========Failed=========="
        exitcode=1
    fi 
}

#clean the test
clean_lzo() {
    lzo=`lzo_arg "$1"`
    zip=`zip_arg "$1"`

    rm -rf $lzo $zip lzo
}

#Test lztest-type.lzx test case
prepare_lzo test/lztest/lzotype/lztest-type.lzx 
lzo_zip test/lztest/lzotype/lztest-type.lzx 
compare_lzo test/lztest/lzotype/lztest-type.lzx 
clean_lzo test/lztest/lzotype/lztest-type.lzx

sleep 5

#Test lztest-type-include.lzx test case
prepare_lzo test/lztest/lzotype/lztest-type-include/lztest-type-include.lzx 
lzo_zip test/lztest/lzotype/lztest-type-include/lztest-type-include.lzx 
compare_lzo test/lztest/lzotype/lztest-type-include/lztest-type-include.lzx 
clean_lzo test/lztest/lzotype/lztest-type-include/lztest-type-include.lzx 
# Here we are testing lzo compilation order

exit $exitcode

################################################################


# * P_LZ_COPYRIGHT_BEGIN ******************************************************
# * Copyright 2009-2012 Laszlo Systems, Inc.  All Rights Reserved.            *
# * Use is subject to license terms.                                          *
# * P_LZ_COPYRIGHT_END ********************************************************
