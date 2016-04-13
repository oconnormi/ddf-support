package ddf.support.release

import java.nio.file.Paths

// TODO: What should be the process when the build fails? How should the fixes be handled?
//       should that be part of the script? Should the script have an option to only do a build?
// TODO: Add check for existing tag
// TODO: Add option to clone the repository
// TODO: Check if remote is a url or just a name
// TODO: add option to clean artifacts from local .m2
// TODO: add user prompts to get better information when user provides bad input (maybe)
// TODO: add verification step for git ref
//  see git rev-parse for validation
// TODO: change currentVersion finder to use git show rev:file to get the xml content

def executeCommand(String command, String workingDir) {
    File directory = new File(workingDir)
    println "Running Command: ${command}"
    def process = new ProcessBuilder(addShellPrefix(command))
            .directory(directory)
            .redirectErrorStream(true)
            .start()
    process.inputStream.eachLine {println it}
    process.waitFor()

    exitCode = process.exitValue()
    if (exitCode > 0) {
        println "Command: ${command} failed to execute properly"
        System.exit(exitCode)
    }
}

private def addShellPrefix(String command) {
    commandArray = new String[3]
    commandArray[0] = "sh"
    commandArray[1] = "-c"
    commandArray[2] = command
    return commandArray
}

def String readStartVersion(String projectDir) {
    def pom = new XmlSlurper().parse(new File(projectDir, "pom.xml"))
    pom.version.toString()
}

def String createReleaseVersion(startVersion) {
    startVersion.replace("-SNAPSHOT", "")
}

def String incrementVersion(releaseVersion) {
    // TODO: check for expected pattern
    version = releaseVersion.split('\\.')
    version[2] = ((version[2] as Integer) + 1) as String
    incrementedVersion = version.join('.')
    String newVersion = "${incrementedVersion}-SNAPSHOT"
}

def printConfig(Config config) {

}

/**
 * Prepares the release by updating pom versions and creating git tags
 * @param config Libero config
 * @return
 */
def prepareRelease(Config config) {

    // Define Commands
    String gitCommand = "git"
    String mavenCommand = "mvn"
    config.releaseName = "${config.projectName}-${config.releaseVersion}"
    String checkoutCommand = "${gitCommand} checkout ${config.ref}"
    String setReleaseVersionCommand = "${mavenCommand} versions:set -DnewVersion=${config.releaseVersion}"
    //TODO: check for alternatives to use-release (look at maven enforcer)
    // String updateSnapshotsCommand = "${mavenCommand} versions:use-releases -DincludesList=${config.nextSnapshotsFilter}"
    String mavenCommitVersionCommand = "${mavenCommand} versions:commit"
    String gitAddCommand = "${gitCommand} add ."
    String gitCommitReleaseCommand = "${gitCommand} commit -m \"[libero] prepare release ${config.releaseName}\""
    String gitTagCommand = "${gitCommand} tag ${config.releaseName}"
    String setDevVersionCommand = "${mavenCommand} versions:set -DnewVersion=${config.nextVersion}"
    // String nextSnapshotsCommand = "${mavenCommand} versions:use-next-snapshots -DincludesList=${config.nextSnapshotsFilter}"
    String gitCommitDevCommand = "${gitCommand} commit -m \"[libero] prepare for next development iteration\""


    executeCommand(checkoutCommand, config.projectDir)
    executeCommand(setReleaseVersionCommand, config.projectDir)
    executeCommand(mavenCommitVersionCommand, config.projectDir)

    // Pre-Release property updates
    config.preProps.each{ k, v ->
      executeCommand("sed -i '' \"s|<${k}>[^<>]*</${k}>|<${k}>${v}</${k}>|g\" pom.xml", config.projectDir)
    }
    executeCommand(gitAddCommand, config.projectDir)
    executeCommand(gitCommitReleaseCommand, config.projectDir)
    executeCommand(gitTagCommand, config.projectDir)
    executeCommand(setDevVersionCommand, config.projectDir)
    executeCommand(mavenCommitVersionCommand, config.projectDir)
    // Post-Release property updates
    config.postProps.each{ k, v ->
      executeCommand("sed -i '' \"s|<${k}>[^<>]*</${k}>|<${k}>${v}</${k}>|g\" pom.xml", config.projectDir)
    }
    executeCommand(gitAddCommand, config.projectDir)
    executeCommand(gitCommitDevCommand, config.projectDir)

}

/**
 * Executes the build and deploy of the release
 * @param config Libero configuration
 */
def runBuild(Config config) {
    String gitCommand = "git"
    String gitCheckoutCommand = "${gitCommand} checkout ${config.releaseName}"
    String gitPushCommand = "${gitCommand} push ${config.destRemote}"
    String gitMasterCommand = "${gitCommand} checkout master"
    String mavenCommand = "mvn -f ${config.projectDir + "/pom.xml"}"
    String quickBuildParams = "-Dfindbugs.skip=true -DskipTests=true -Dpmd.skip=true -Djacoco.skip=true -DskipTestScope=true -DskipProvidedScope=true -DskipRuntimeScope=true"
    String deployCommand = "${mavenCommand} ${quickBuildParams} -T2C deploy"
    String buildCommand

    // Check out the git tag
    executeCommand(gitCheckoutCommand, config.projectDir)

    if (config.quickBuild) {
        buildCommand = "${mavenCommand} ${quickBuildParams} clean install"
    }
    else {
        buildCommand = "${mavenCommand} clean install"
    }
    executeCommand(buildCommand, config.projectDir)

   if (!config.dryRun) {
       if (config.mavenRepo) {
           deployCommand = "${deployCommand} -DaltDeploymentRepository=${config.mavenRepo}"
       }
       executeCommand(deployCommand, config.projectDir)
       if (config.push) {
         executeCommand(gitPushCommand, config.projectDir)
       }
   }

    // TODO: change to checkout original branch
    executeCommand(gitMasterCommand, config.projectDir)

    println "Release Process Complete!!"
}

/**
 * Executes the release process
 * @param config Libero Configuration
 * @return
 */
def executeRelease(Config config) {
    prepareRelease(config)
    runBuild(config)
}

def libero(Config config) {

    if (!config.force) {

        println "Preparing for Release Cycle!"
        config.printConfig()

        USER_CONFIRMED = System.console().readLine 'Do you wish to continue? [y/N]: ' as String
        switch (USER_CONFIRMED) {
            case "n":
                System.exit(1)
                break
            case "y":
                executeRelease(config)
                break
            default:
                println "Response must be either [y/n]"
                System.exit(1)
        }
    }
    else {
        executeRelease(config)
    }
}

def stringToMap(data) {
  if (data) {
    data.split(',').inject([:]) { map, token ->
      token.split('=').with { map[it[0]] = it[1]}
      map
    }
  }
  else {
    null
  }
}

def run(args) {
    def cli = new CliBuilder(usage: 'libero -[fhpt] [src]', header: 'I Release You!')

    cli.with {
        c longOpt: 'config', args: 1, argName: 'configFile', 'Path to yaml config file, if none provided, ' +
                'will search the project dir for .libero.yml, then in ~/.libero/[projectName].yaml. If none found, ' +
                'it will look for command line parameters for all options'
        d longOpt: 'dest-remote', args: 1, argName: 'destRepo', 'Destination repo for the release process. ' +
                'Can be either a full url, or a repo slug for github repos. If not specified the destination ' +
                'will be the same as the source repo'
        m longOpt: 'maven-repo', args: 1, argName: 'mavenRepo', 'Maven release repository to deploy the release artifacts to. use the form ID:LAYOUT:URL'
        r longOpt: 'ref', args: 1, argName: 'ref', 'Git ref to release from, can be any supported git ref'
        s longOpt: 'source-remote', args: 1, argName: 'sourceRepo', 'Git remote repo to use for the initial checkout for the release process'
        v longOpt: 'release-version', args: 1, argName: 'releaseVersion', 'Target version for the release'
        n longOpt: 'next-version', args: 1, argName: 'nextVersion', 'Next version after the release, default: auto-increment'
        b longOpt: 'dest-branch', args: 1, argName: 'destBranch', 'Branch to push to on the destination remote'
        _ longOpt: 'pre-props', args: 1, argName: 'preProps', 'comma separated list of pre-release property updates, specify in the form "propertyName=propertyValue"'
        _ longOpt: 'post-props', args: 1, argName: 'postProps', 'comma separated list of post-release property updates, specify in the form "propertyName=propertyValue"'

        h longOpt: 'help', 'Show usage information'
        p longOpt: 'push', 'Push commits and tags'
        t longOpt: 'test', 'Run in dry run mode'
        f longOpt: 'force', 'Skips confirmation of options and executes the release'
        q longOpt: 'quick-build', 'Executes a quick build instead of a full build (Not Recommended!!!)'
    }

    // Show Usage when -h or --help is specified, or if no arguments are given
    def options = cli.parse(args)

    Config config = new Config()

    String WORKING_DIRECTORY = System.getProperty('user.dir')
    String USER_HOME = System.getProperty('user.home')

    config.projectDir = options.arguments()[0] ?: WORKING_DIRECTORY
    config.projectName = new File(config.projectDir).getName()

    if (options.h || !new File(config.projectDir, "pom.xml").exists()) {
        cli.usage()
        System.exit(0)
    }

    // Get Config
    if (options.c) {
        if (!new File((String)options.c).exists()) {
            println "Config File: ${options.c} does not exist"
            System.exit(1)
        }
        else {
            configFile = new File((String)options.c)
            config.loadConfig()
        }
    }
    else
    {
        if (new File(Paths.get(config.projectDir, ".libero.yml").toUri()).exists()) {
            config.configFile = new File(Paths.get(config.projectDir, ".libero.yml").toUri())
            config.loadConfig()
        }
        if (new File(Paths.get(USER_HOME, ".libero", config.projectName).toUri()).exists()) {
            config.configFile = new File(Paths.get(USER_HOME, ".libero", config.projectName, ".libero.yml").toUri())
            config.loadConfig()
        }
    }

    config.sourceRemote = options.s ?: config.sourceRemote ?: "origin"
    config.destRemote = options.d ?: config.destRemote ?: config.sourceRemote
    config.ref = options.r ?: config.ref ?: "refs/remotes/${config.sourceRemote}/master"
    config.destBranch = options.b ?: config.destBranch ?: "master"
    config.mavenRepo = options.m ?: config.mavenRepo ?: null
    config.preProps = stringToMap(options."pre-props") ?: config.preProps ?: null
    config.postProps = stringToMap(options."post-props") ?: config.postProps ?: null
    config.startVersion = readStartVersion(config.projectDir)
    config.releaseVersion = options.v ?: config.releaseVersion ?: createReleaseVersion(config.startVersion)
    config.nextVersion = options.n ?: config.nextVersion ?: incrementVersion(config.releaseVersion)
    config.gitPush = options.p ?: config.gitPush ?: false
    config.quickBuild = options.q ?: config.quickBuild ?: false
    // TODO: dry run should do everything except maven deploy and git push (also remove tags and reset after run)
    config.dryRun = options.t ?: config.dryRun ?: config.dryRun ?: false
    config.force = options.f ?: config.force ?: config.force ?: false

    libero(config)
}

run(args)
