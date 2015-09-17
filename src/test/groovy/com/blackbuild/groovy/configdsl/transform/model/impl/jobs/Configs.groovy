package com.blackbuild.groovy.configdsl.transform.model.impl.jobs

import com.blackbuild.groovy.configdsl.transform.DSL

@DSL
class Config {

    Map<String, System> systems

    Map<String, Container> containers

    Map<String, MavenProject> mavenProjects

}

@DSL(key = "name")
class System {
    String name

    Map<String, Environment> environments
}

@DSL(key = "name", owner = "system")
class Environment {

    String name
    String description
    List<String> smokeTests

    String versionFilter

    List<String> auth

    Mail mail

    System system

    String getFullName() {
        return "${system}_${name}"
    }

    List<String> authForComponentDeploy

    boolean isAllowComponentDeploy() {
        return !authForComponentDeploy.isEmpty()
    }
}

@DSL
class Mail {

    List<String> recipients

}

@DSL(key = "name")
class Container {

    String name



}

@DSL(key = "name")
abstract class Buildable {

    String name
    String gitRepo

    String getGitRepo() {
        return gitRepo ?: name
    }
}

@DSL
class MavenProject extends Buildable {

    String parent


}

