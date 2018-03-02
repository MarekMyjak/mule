/*
 * $Id$
 * --------------------------------------------------------------------------------------
 *
 * (c) 2003-2010 MuleSoft, Inc. This software is protected under international copyright
 * law. All use of this software is subject to MuleSoft's Master Subscription Agreement
 * (or other master license agreement) separately entered into in writing between you and
 * MuleSoft. If such an agreement is not in place, you may not use the software.
 */

import org.apache.commons.lang.SystemUtils
import java.util.jar.JarEntry
import java.util.jar.JarFile

if (args.size() != 1)
{
    usage()
    System.exit(-1)
}

muleHome = System.getenv()['MULE_HOME']
tempFolder = System.getProperty('java.io.tmpdir')
m2repo = args[0]
println "\nPopulating local Maven 2 repository at: $m2repo"
println "Working directory: ${new File('.').canonicalPath}\n"

ant = new AntBuilder()

File muleLibDir = new File("${muleHome}/lib/mule")
File mulePerAppLibDir = new File("${muleHome}/lib/mule/per-app")
File muleBootDir = new File("${muleHome}/lib/boot")
File muleModuleDir = new File("${muleHome}/lib/module")
File muleOptDir = new File("${muleHome}/lib/opt")

class FileFilter implements FilenameFilter {
    public boolean accept(File f, String filename) {
        return filename.startsWith("icu4j-normalizer_transliterator") || filename.startsWith("sardine")
    }
}

//Installing icu4j-normalizer_transliterator 4.8.1.1 and sardine 248 that are not publically available
muleOptDir.listFiles(new FileFilter()).each { File f ->
    splash "Installing $f.name"

    JarFile libJar = new JarFile(f)

    Properties pomProps = new Properties()
    if(f.name.startsWith("icu4j-normalizer_transliterator")){
        pomProps.put('groupId',"com.ibm.icu")
        pomProps.put('artifactId',"icu4j-normalizer_transliterator")
        pomProps.put('version',extractVersion(f,'4.8.1.1'))
    } else{
        pomProps.put('groupId',"com.googlecode.sardine")
        pomProps.put('artifactId',"sardine")
        pomProps.put('version',extractVersion(f,'248'))
    }
    // install pom via maven
    def args = ["install:install-file", "-DgroupId=${pomProps.groupId}", "-DartifactId=${pomProps.artifactId}", "-Dversion=${pomProps.version}", "-Dpackaging=jar", "-Dfile=${f.canonicalPath}"]
    mvn(args)

}

class TruelicenseFileFilter implements FilenameFilter {
    public boolean accept(File f, String filename) {
        return filename.startsWith("truelicense")
    }
}
//Installing truelicense 1.29  that is not publically available
muleBootDir.listFiles(new TruelicenseFileFilter()).each { File f ->
    splash "Installing $f.name"

    JarFile libJar = new JarFile(f)

    Properties pomProps = new Properties()
    pomProps.put('groupId',"de.schlichtherle.truelicense")
    pomProps.put('artifactId',"truelicense")
    pomProps.put('version',extractVersion(f, '1.29'))


    // install pom via maven
    def args = ["install:install-file", "-DgroupId=${pomProps.groupId}", "-DartifactId=${pomProps.artifactId}", "-Dversion=${pomProps.version}", "-Dpackaging=jar", "-Dfile=${f.canonicalPath}"]
    mvn(args)

}

// locate and load poms from the jar
JarFile jar = new JarFile(muleLibDir.listFiles().find {
    it.name.startsWith('mule-ee-parent-poms')
})
def poms = jar.entries().findAll
{
    // list all POMs in mule-ee-parent-poms jar
    !it.directory && it.name ==~ /.*\.pom$/
}

assert !poms.empty : "Parent EE poms not found in the jar"

// Process and install each pom
poms.each { pom ->
    println "installing pom $pom"

    def project = new XmlSlurper().parse(jar.getInputStream(pom))
    splash "Installing ${project.groupId}:${project.artifactId}"

    String version = project.version.size() == 0 ? project.parent.version : project.version

    // save pom to a temp file for external mvn call
    def localPom = writeTempFile(jar.getInputStream(pom))

    // install pom via maven
    mvn(["install:install-file", "-DgroupId=${project.groupId}", "-DartifactId=${project.artifactId}", "-Dversion=${version}", "-Dpackaging=pom", "-Dfile=${localPom.canonicalPath}"])
}

// Filter jars in $MULE_HOME/lib/mule first
def muleLibs = muleLibDir.listFiles().findAll
{
    !it.directory &&
        !it.name.startsWith('mule-ee-parent-poms') &&
        !(it.name == 'mule-local-install.jar') && // not in EE, but just in case of mixed non-standard installation
        (it.name ==~ /^mule.*\.jar/ || it.name ==~ /^mmc.*\.jar/ )
}

def perAppsLibs = mulePerAppLibDir.listFiles().findAll
{
    !it.directory &&
        it.name ==~ /^mule.*\.jar/
}
muleLibs.addAll(perAppsLibs)

// also make sure that the mule-tests-functional lib will be put into the local repo
def testLibs = new File("${muleHome}/lib/user").listFiles().findAll
{
    !it.directory &&
        it.name ==~/^mule-tests-functional.*\.jar/
}
muleLibs << testLibs

def bootLibs = muleBootDir.listFiles().findAll
{
    !it.directory &&
        (it.name ==~ /^mule.*\.jar/ || it.name ==~ /^licm.*\.jar/ )
}
muleLibs.addAll(bootLibs)

def moduleLibs = muleModuleDir.listFiles().findAll
{
    !it.directory &&
        it.name ==~ /^mule.*\.jar/
}
muleLibs.addAll(moduleLibs)

def optLibs = muleOptDir.listFiles().findAll
{
    !it.directory && it.name ==~ /^mmc.*\.jar/
}
muleLibs.addAll(optLibs)

// Process and install Mule EE jars
muleLibs.each { File f ->
    splash "Installing $f.name"

    JarFile libJar = new JarFile(f)

    // save pom to a temp file for external mvn call
    JarEntry pomXml = libJar.entries().find { entry -> entry.name.endsWith('pom.xml') }
    assert pomXml != null : "Mule EE library ${libJar.name} doesn't contain Maven 2 meta-information"
    def localPom = writeTempFile(libJar.getInputStream(pomXml))

    def pom = libJar.entries().find { entry -> entry.name.endsWith('pom.properties') }
    Properties pomProps = new Properties()
    pomProps.load(libJar.getInputStream(pom))

    // install pom via maven
    def args = ["install:install-file", "-DgroupId=${pomProps.groupId}", "-DartifactId=${pomProps.artifactId}", "-Dversion=${pomProps.version}", "-Dpackaging=jar", "-Dfile=${f.canonicalPath}", "-DpomFile=${localPom.canonicalPath}"]
    if (f.name.endsWith("-tests.jar")) {
        args << "-Dclassifier=tests"
    }
    mvn(args)
}
System.exit(0)

// writing input stream to a temporary file
File writeTempFile(InputStream is)
{
    def localPom = File.createTempFile('mule-populate-repo', null)
    localPom.deleteOnExit()
    localPom << is
}

/**
    Maven 2 launcher wrapper with some defaults pre-set,
*/
def mvn(List mvnArgs)
{
    boolean isWin = SystemUtils.IS_OS_WINDOWS

    if (isWin)
    {
        // adding quotes: -Dkey="value with spaces"
        mvnArgs = mvnArgs.collect {String arg ->
            if(arg.indexOf('=') != -1 && arg.indexOf(' ') != -1)
            {
                String[] ss = arg.split('=')
                return ss[0] + "=\"${ss[1]}\""
            }
            return arg
        }
    }
    else
    {
        // escaping spaces: -Dkey=value\ with\ spaces
        mvnArgs = mvnArgs*.replaceAll(' ', "\\\\ ")
    }

    String commandLine = "${isWin ? 'cmd /c': ''} mvn -B -Dmaven.repo.local=${isWin ? '\"': ''}$m2repo${isWin ? '\"': ''} ${mvnArgs.join(' ')}"
    Process proc = commandLine.execute()

    // printing maven output and searching for error string
    boolean errorFound = false;
    proc.in.eachLine { String line ->
        println line
        if (line.startsWith('[ERROR]'))
        {
            errorFound = true
        }
    }
    proc.waitForOrKill(300000) // kill after 5 mins to prevent hangs

    if (errorFound)
    {
        System.exit(1)
    }
}
def extractVersion(file, deafultVersion){
    String fileName = file.getName().substring(0, file.getName().lastIndexOf("."));
    int start = Math.max(fileName.lastIndexOf("-"), fileName.indexOf("_")) + 1;
    String versionNumber = fileName.substring(start);
    if(isVersionNumber(versionNumber)){
        return versionNumber;
    }
    return deafultVersion;
}

def isVersionNumber(versionNumber){
    for(c in versionNumber){
        if ("0123456789.".indexOf(c) < 0){
            return false;
        }
    }
    return true;
}

/**
    Print usage information.
*/
def usage()
{
    println '''

Populate a local Maven 2 repository with Mule EE artifacts.

Usage: populate_m2_repo <m2_repo_home>

'''
}

/**
    A helper splash message method.
*/
def splash(text) {
    println()
    println '=' * 62
    println "  $text"
    println '=' * 62
}
