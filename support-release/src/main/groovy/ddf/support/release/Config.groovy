package ddf.support.release

import static org.ho.yaml.Yaml.load

class Config {
    String projectDir
    String projectName
    File configFile
    String sourceRemote
    String destRemote
    String ref
    String destBranch
    String startVersion
    String releaseVersion
    String nextVersion
    String mavenRepo
    String nextSnapshotsFilter
    String releaseName
    boolean quickBuild
    boolean gitPush
    boolean dryRun
    boolean force

    public loadConfig = { ->
        def configLoader = load(configFile)

        for (node in configLoader) {
            println "Node: ${node.value}"
            def project = node.value
            def git = project.git
            def source = git.source
            def destination = git.destination
            def maven = project.maven
            def settings = project.settings
            def versions = project.versions
            sourceRemote = source.remote
            destRemote = destination.remote
            ref = source.ref
            destBranch = destination.branch
            releaseVersion = versions.release
            nextVersion = versions.development
            mavenRepo = maven.repo
            nextSnapshotsFilter = maven.snapshotfilter
            quickBuild = maven.quickbuild
            gitPush = destination.push
            dryRun = settings.dryRun
            force = settings.force
        }
    }

    public printConfig = { ->
        println "============== RELEASE PARAMETERS =============="
        println "Project Directory: ${projectDir}"
        println "Project Name: ${projectName}"
        println "Config File: ${configFile}"
        println "Source git Remote: ${sourceRemote}"
        println "Source git Ref: ${ref}"
        println "Destination git Remote: ${destRemote}"
        println "Destination git Branch: ${destBranch}"
        println "Source Version: ${startVersion}"
        println "Release Version: ${releaseVersion}"
        println "Next Development Version: ${nextVersion}"
        println "Snapshot Update Filter: ${nextSnapshotsFilter}"
        println "Maven Release Repo: ${mavenRepo}"
        println "Quick Build: ${quickBuild}"
        println "Push Git tags/commits: ${gitPush}"
        println "Dry Run: ${dryRun}"
        println "============ END RELEASE PARAMETERS ============"
    }
}
