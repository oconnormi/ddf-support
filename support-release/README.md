# Libero
_I Release You_

Maven Release Helper script

This script is to help automate the process of performing a maven release against a git repo without resorting to the maven-release-plugin.

## Features

* supports releasing on a forked repo, while still using the upstream changes
  * uses multiple remotes
* release from any supported git revision
* commit release to any git branch on any remote
* disable automatic git pushing
* release dry run (runs everything except the maven deploy process)
* quick build (for testing purposes only, not recommended for production uses)
* supports command line parameters for all options
* supports a config file for all options
  * config files can be specified as a parameter
  * searches in `<projectdir>/.libero.yml`
  * searches in `~/.libero/<project-name>/.libero.yml`

## Cli

```man
usage: libero -[fhpt] [src]
I Release You!
 -b,--dest-branch <destBranch>           Branch to push to on the
                                         destination remote
 -c,--config <configFile>                Path to yaml config file, if none
                                         provided, will search the project
                                         dir for .libero.yml, then in
                                         ~/.libero/[projectName].yaml. If
                                         none found, it will look for
                                         command line parameters for all
                                         options
 -d,--dest-remote <destRepo>             Destination repo for the release
                                         process. Can be either a full
                                         url, or a repo slug for github
                                         repos. If not specified the
                                         destination will be the same as
                                         the source repo
 -f,--force                              Skips confirmation of options and
                                         executes the release
 -h,--help                               Show usage information
 -m,--maven-repo <mavenRepo>             Maven release repository to
                                         deploy the release artifacts to.
                                         use the form ID:LAYOUT:URL
 -n,--next-version <nextVersion>         Next version after the release,
                                         default: auto-increment
 -p,--push                               Push commits and tags
 -q,--quick-build                        Executes a quick build instead of
                                         a full build (Not Recommended!!!)
 -r,--ref <ref>                          Git ref to release from, can be
                                         any supported git ref
 -s,--source-remote <sourceRepo>         Git remote repo to use for the
                                         initial checkout for the release
                                         process
 -t,--test                               Run in dry run mode
```
 
## Config file

```yaml
 project:
   git:
     source:
       remote: "origin"
       ref: "master"
     destination:
       remote: "origin"
       branch: "master"
       push: false
   maven:
     repo: "releases:default:http://nexus.fake.site/nexus/content/repositories/releases/"
     quickbuild: true
   settings:
     dryRun: true
     force: false
   versions:
     release: "1.2.3"
     development: "1.2.4-SNAPSHOT"
```
