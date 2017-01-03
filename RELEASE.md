# OpenZipkin Release Process

This repo uses semantic versions. Please keep this in mind when choosing version numbers.

1. **Alert others you are releasing**

   There should be no commits made to master while the release is in progress (about 10 minutes). Before you start
   a release, alert others on [gitter](https://gitter.im/opentracing/public) so that they don't accidentally merge
   anything. If they do, and the build fails because of that, you'll have to recreate the release tag described below.

1. **Push a git tag**

   The tag should be of the format `release-N.M.L`, for example `release-3.7.1`.

1. **Wait for Travis CI**

   This part is controlled by [`travis/publish.sh`](travis/publish.sh). It creates a bunch of new commits, bumps
   the version, publishes artifacts, and syncs to Maven Central.

## Credentials

Credentials of various kind are needed for the release process to work. If you notice something
failing due to unauthorized, re-encrypt them using instructions at the bottom of the `.travis.yml`

Ex You'll see comments like this:
```yaml
env:
  global:
  # Ex. travis encrypt BINTRAY_USER=your_github_account
  - secure: "VeTO...
```

To re-encrypt, you literally run the commands with relevant values and replace the "secure" key with the output:

```bash
$ travis encrypt BINTRAY_USER=adrianmole
Please add the following to your .travis.yml file:

  secure: "mQnECL+dXc5l9wCYl/wUz+AaYFGt/1G31NAZcTLf2RbhKo8mUenc4hZNjHCEv+4ZvfYLd/NoTNMhTCxmtBMz1q4CahPKLWCZLoRD1ExeXwRymJPIhxZUPzx9yHPHc5dmgrSYOCJLJKJmHiOl9/bJi123456="
```

## First release of the year

The license plugin verifies license headers of files include a copyright notice indicating the years a file was affected.
This information is taken from git history. There's a once-a-year problem with files that include version numbers (pom.xml).
When a release tag is made, it increments version numbers, then commits them to git. On the first release of the year,
further commands will fail due to the version increments invalidating the copyright statement. The way to sort this out is
the following:

Before you do the first release of the year, move the SNAPSHOT version back and forth from whatever the current is.
In-between, re-apply the licenses.
```bash
$ ./mvnw versions:set -DnewVersion=1.3.3-SNAPSHOT -DgenerateBackupPoms=false
$ ./mvnw com.mycila:license-maven-plugin:format
$ ./mvnw versions:set -DnewVersion=1.3.2-SNAPSHOT -DgenerateBackupPoms=false
$ git commit -am"Adjusts copyright headers for this year"
```
