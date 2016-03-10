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
# prepare-lzo-test.sh <RUNTIME>
#
# + binary compile lzo-lib.lzx
# + rename lzo-lib.lzx to make sure it is out of the way
# 


# lzo_arg path/to/file.lzo
# checks to make sure .lzo name is correct, and returns the same
lzo_arg() {
    lzo="$1"

    # quick sanity check before we start removing files
    case "$lzo" in
        *.lzo ) ;;
        * ) echo "ERROR: prepare-lzo-test.sh: prepare_lzo must have .lzo arg"
            exit 1
            ;;
    esac
    echo "$lzo"
}

# lzx_arg path/to/file.lzo
# returns corresponding .lzx name
lzx_arg() {
    echo "$1" | sed -e 's/lzo$/lzx/'
}

# proto_arg path/to/file.lzo
# returns corresponding .lzx.proto name
proto_arg() {
    echo "$1" | sed -e 's/lzo$/lzx.proto/'
}

# We break the preparation into two functions (prepare_lzo_setup,
# prepare_lzo_compile), since there is a test that wants to test
# compilation in a particular order, but after files have been copied

# prepare_lzo_setup path/to/file.lzo
prepare_lzo_setup() {
    lzo=`lzo_arg "$1"`
    lzx=`lzx_arg "$1"`
    proto=`proto_arg "$1"`

    rm -f "$lzo"
    cp "$proto" "$lzx"
}

# prepare_lzo_compile path/to/file.lzo
prepare_lzo_compile() {
    lzo=`lzo_arg "$1"`
    lzx=`lzx_arg "$1"`
    proto=`proto_arg "$1"`

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
    rm -f "$lzx"
}

# prepare_lzo path/to/file.lzo
prepare_lzo() {
    prepare_lzo_setup "$@"
    prepare_lzo_compile "$@"
}

prepare_lzo test/lztest/lzodir/inc1/lzo-instance.lzo
prepare_lzo test/lztest/lzodir/inc2/lzo-include-inst.lzo
prepare_lzo test/lztest/lzodir/inc3/lzo-include-inst.lzo
prepare_lzo test/lztest/lzodir/lzo-lib-class-def.lzo
prepare_lzo test/lztest/lzodir/lzo-lib-extref.lzo
prepare_lzo test/lztest/lzodir/lzo-lib-onemixin.lzo
prepare_lzo test/lztest/lzodir/lzo-lib-othermixin.lzo
prepare_lzo test/lztest/lzodir/lzo-lib-mixin-def.lzo
if [ "$aflag" = '' ]; then
    prepare_lzo test/lztest/lzodir/lzo-lib-withthis.lzo
fi
prepare_lzo test/lztest/lzodir/lzo-lib-otherdir.lzo --dir test/lztest/lzodir2

# Here we are testing lzo compilation order
prepare_lzo_setup test/lztest/lzodir/lzo-lib-shared-interstitials-def.lzo
prepare_lzo_setup test/lztest/lzodir3/lzo-double-lib.lzo
prepare_lzo_setup test/lztest/lzodir2/lzo-lib.lzo

prepare_lzo_compile test/lztest/lzodir/lzo-lib-shared-interstitials-def.lzo
# Intentionally compile this library before its included library has been compiled
prepare_lzo_compile test/lztest/lzodir3/lzo-double-lib.lzo
prepare_lzo_compile test/lztest/lzodir2/lzo-lib.lzo


exit $exitcode

################################################################


# * P_LZ_COPYRIGHT_BEGIN ******************************************************
# * Copyright 2009-2011 Laszlo Systems, Inc.  All Rights Reserved.            *
# * Use is subject to license terms.                                          *
# * P_LZ_COPYRIGHT_END ********************************************************
