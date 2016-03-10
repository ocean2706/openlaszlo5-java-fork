#!/bin/bash
# Run several tests in the rhino runtime.
# usage 
# runlztest.sh [file-with-list-of-tests]
# Copyright 2007, 2008, 2009, 2010, 2011, 2012 Laszlo Systems

# Use a default value for the tests file if its not specified on the command line
tests=${1:-"test/lztest/smoketest.txt"}
shift
ctags=$1
if [ "${ctags}" = "" ]; then
    ctags=ANY
fi
errored=0

# If we can't find java, look in some other likely places for it
JAVA_EXEC=`which java`
if [ ! -x $JAVA_EXEC ]; then
    echo "java executable not found in path; trying $JAVA_HOME"
    JAVA_EXEC=${JAVA_HOME}/bin/java
    if [ ! -x $JAVA_EXEC ]; then
        echo "Can't java executable in path or in $JAVA_EXEC. FAILING lztest."
        return -1
    fi
fi

function runtest() {

echo "Entering runlztest.sh with LPS_HOME=${LPS_HOME} tags=${tags}"


# Strip out comments from list file
    paths=`sed -e /^#/d ${tests}`
    
# write several tests into that file
    for i in $paths; do
	
	if [ ! -f $i ]; then
            echo "Test file does not exist: $i"
            echo "Please edit the list of tests, which is $tests. "
            exit 1;
	fi
	
    # filter for the tags
	if [ $tags != "ANY" ]; then
    # replace commas by \| for regexp 
	    tagpat=`echo $tags | sed 's/,/\\\|/g'`
    # echo "checking $i for coverage of tags ${tagpat}..."
    # search for the tags regexp in the test file source   
	    grep ".*covers-tags.*\(${tagpat}\)" $i
	    if [ $? != 0 ]; then
		echo "skipping ${i}, does not match tags ${tagpat}"
		continue
	    else 
		echo "testing ${i}..........."
	    fi
	fi
	
	
	outfile=${i//\//_}
	outfile=${outfile%lzx}js
	outfile=tmp/$outfile
        
    # Add some code which makes the rhino runtime provide the names
    # and services that the swf and dhtml runtimes provide. 
	cat lps/utils/rhino.js > $outfile
	
    # compile that file with lzc
    # make sure it compiles for swf10
    # even though we can only run dhtml in rhino
	$LPS_HOME/WEB-INF/lps/server/bin/lzc --runtime=swf10 $i &&
	$LPS_HOME/WEB-INF/lps/server/bin/lzc --runtime=dhtml $i
	
    # If the compile failed, the tests should fail.
	if [ $? != 0 ] ; then
            echo "FAILED to compile test $i in runlztest.sh"
            errored="$errored,$i"
            echo "errored is $errored"
	fi
	
    # make the file with all the tests load in that compiled file
	echo "load(\"${i%lzx}js\");" >> $outfile
	
	echo "for (var k = 0; k < 100; k++) lz.Idle.onidle.sendEvent();" >> $outfile
	
	echo "if (LzTestManager.failedsuites > 0) quit(3);" >> $outfile
	
    # load that file into rhino
	$JAVA_EXEC -jar 3rd-party/jars/custom_rhino.jar -opt -1 $outfile
	
    # Stop testing if we failed a test
	if [ $? != 0 ] ; then
            echo "FAILED a test in runlztest.sh: $i";
            errored="$errored,$i"
            echo "ERRORS in tests: $errored"
	fi
	
    done
    
    if [ $errored != 0 ]; then 
	echo "ERRORS in tests $errored"
	exit 1
    fi
    
    echo "no errors. done."
}


# Build the lzo's using flex swf10 compiler, as it is more strict, to help catch any compile-time errors.
# The compiler will select the runtime-independent portion of the lzo to use when compiling the test apps
build-tools/prepare-lzo-test.sh swf10 || exit 1
tags=lzo  # just run the lzo tests for this pass
runtest

# build with with both swf10 and dhtml
build-tools/prepare-lzo-test.sh -a swf10 dhtml || exit 1
tags=lzo  # just run the lzo tests for this pass
runtest

# build with dhtml-specific contents, the compiler will use the dhtml-specific file in the lzo
build-tools/prepare-lzo-test.sh dhtml || exit 1
tags=$ctags
runtest

# build to lzo for specific <type/> tag both for swf10 and dhtml
build-tools/type-lzo-test.sh swf10 || exit 1
build-tools/type-lzo-test.sh dhtml || exit 1
