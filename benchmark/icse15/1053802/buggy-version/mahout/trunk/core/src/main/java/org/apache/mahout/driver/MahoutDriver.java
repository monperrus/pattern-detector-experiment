/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mahout.driver;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.hadoop.util.ProgramDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * General-purpose driver class for Mahout programs.  Utilizes org.apache.hadoop.util.ProgramDriver to run
 * main methods of other classes, but first loads up default properties from a properties file.
 * <p/>
 * To run locally:
 *
 * <pre>$MAHOUT_HOME/bin/mahout run shortJobName [over-ride ops]</pre>
 * <p/>
 * Works like this: by default, the file "driver.classes.props" is loaded from the classpath, which
 * defines a mapping between short names like "vectordump" and fully qualified class names.
 * The format of driver.classes.props is like so:
 * <p/>
 *
 * <pre>fully.qualified.class.name = shortJobName : descriptive string</pre>
 * <p/>
 * The default properties to be applied to the program run is pulled out of, by default, "<shortJobName>.props"
 * (also off of the classpath).
 * <p/>
 * The format of the default properties files is as follows:
 * <pre>
  i|input = /path/to/my/input
  o|output = /path/to/my/output
  m|jarFile = /path/to/jarFile
  # etc - each line is shortArg|longArg = value
 </pre>
 *
 * The next argument to the Driver is supposed to be the short name of the class to be run (as defined in the
 * driver.classes.props file).
 * <p/>
 * Then the class which will be run will have it's main called with
 *
 *   <pre>main(new String[] { "--input", "/path/to/my/input", "--output", "/path/to/my/output" });</pre>
 *
 * After all the "default" properties are loaded from the file, any further command-line arguments are taken in,
 * and over-ride the defaults.
 * <p/>
 * So if your driver.classes.props looks like so:
 *
 * <pre>org.apache.mahout.utils.vectors.VectorDumper = vecDump : dump vectors from a sequence file</pre>
 *
 * and you have a file core/src/main/resources/vecDump.props which looks like
 * <pre>
  o|output = /tmp/vectorOut
  s|seqFile = /my/vector/sequenceFile
  </pre>
 *
 * And you execute the command-line:
 *
 * <pre>$MAHOUT_HOME/bin/mahout run vecDump -s /my/otherVector/sequenceFile</pre>
 *
 * Then org.apache.mahout.utils.vectors.VectorDumper.main() will be called with arguments:
 *   <pre>{"--output", "/tmp/vectorOut", "-s", "/my/otherVector/sequenceFile"}</pre>
 */
public final class MahoutDriver {

  private static final Logger log = LoggerFactory.getLogger(MahoutDriver.class);

  private MahoutDriver() {
  }

  public static void main(String[] args) throws Throwable {
    ProgramDriver programDriver = new ProgramDriver();
    Properties mainClasses = new Properties();
    InputStream propsStream = Thread.currentThread()
                                    .getContextClassLoader()
                                    .getResourceAsStream("driver.classes.props");

    try {
      mainClasses.load(propsStream);
    } catch (IOException e) {
      //try getting the default one
      propsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("driver.classes.default.props");
      mainClasses.load(propsStream);
    }

    boolean foundShortName = false;
    for (Object key :  mainClasses.keySet()) {
      String keyString = (String) key;
      if (args.length > 0 && shortName(mainClasses.getProperty(keyString)).equals(args[0])) {
        foundShortName = true;
      }
      addClass(programDriver, keyString, mainClasses.getProperty(keyString));
    }
    if (args.length < 1 || args[0] == null || args[0].equals("-h") || args[0].equals("--help")) {
      programDriver.driver(args);
    }
    String progName = args[0];
    if (!foundShortName) {
      addClass(programDriver, progName, progName);
    }
    shift(args);

    InputStream defaultsStream = Thread.currentThread()
                                       .getContextClassLoader()
                                       .getResourceAsStream(progName + ".props");

    Properties mainProps = new Properties();
    if (defaultsStream != null) { // can't find props file, use empty props.
      mainProps.load(defaultsStream);
    } else {
      log.warn("No " + progName + ".props found on classpath, will use command-line arguments only");
    }
    Map<String,String[]> argMap = new HashMap<String,String[]>();
    int i = 0;
    while (i < args.length && args[i] != null) {
      List<String> argValues = new ArrayList<String>();
      String arg = args[i];
      i++;
      if (arg.length() > 2 && arg.charAt(1) == 'D') { // '-Dkey=value' or '-Dkey=value1,value2,etc' case
        String[] argSplit = arg.split("=");
        arg = argSplit[0];
        if (argSplit.length == 2) {
          argValues.add(argSplit[1]);
        }
      } else {                                      // '-key [values]' or '--key [values]' case.
        while (i < args.length && args[i] != null) {
          if (args[i].length() > 0 && args[i].charAt(0) != '-') {
            argValues.add(args[i]);
            i++;
          } else {
            break;
          }
        }
      }
      argMap.put(arg, argValues.toArray(new String[argValues.size()]));
    }
    for (String key : mainProps.stringPropertyNames()) {
      String[] argNamePair = key.split("\\|");
      String shortArg = '-' + argNamePair[0].trim();
      String longArg = argNamePair.length < 2 ? null : "--" + argNamePair[1].trim();
      if (!argMap.containsKey(shortArg) && (longArg == null || !argMap.containsKey(longArg))) {
        argMap.put(longArg, new String[] {mainProps.getProperty(key)});
      }
    }
    List<String> argsList = new ArrayList<String>();
    argsList.add(progName);
    for (String arg : argMap.keySet()) {
      if (arg.startsWith("-D")) { // arg is -Dkey - if value for this !isEmpty(), then arg -> -Dkey + "=" + value
        if (argMap.get(arg).length > 0 && !argMap.get(arg)[0].trim().isEmpty()) {
          arg += '=' + argMap.get(arg)[0].trim();
        }
      }
      argsList.add(arg);
      if (!arg.startsWith("-D")) {
        argsList.addAll(Arrays.asList(argMap.get(arg)));
      }
    }
    long start = System.currentTimeMillis();
    programDriver.driver(argsList.toArray(new String[argsList.size()]));
    long finish = System.currentTimeMillis();
    if (log.isInfoEnabled()) {
      log.info("Program took " + (finish - start) + " ms");
    }
  }

  private static String[] shift(String[] args) {
    System.arraycopy(args, 1, args, 0, args.length - 1);
    args[args.length - 1] = null;
    return args;
  }

  private static String shortName(String valueString) {
    return valueString.contains(":") ? valueString.substring(0, valueString.indexOf(':')).trim() : valueString;
  }

  private static String desc(String valueString) {
    return valueString.contains(":") ? valueString.substring(valueString.indexOf(':')).trim() : valueString;
  }

  private static void addClass(ProgramDriver driver, String classString, String descString) {
    try {
      Class<?> clazz = Class.forName(classString);
      driver.addClass(shortName(descString), clazz, desc(descString));
    } catch (ClassNotFoundException e) {
      log.warn("Unable to add class: " + classString, e);
    } catch (Throwable t) {
      log.warn("Unable to add class: " + classString, t);
    }
  }

}
