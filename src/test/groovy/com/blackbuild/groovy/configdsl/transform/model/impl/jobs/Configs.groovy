package com.blackbuild.groovy.configdsl.transform.model.impl.jobs

import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.groovy.configdsl.transform.Field
import com.blackbuild.groovy.configdsl.transform.Key
import com.blackbuild.groovy.configdsl.transform.Owner

@DSL
class Config {

    Map<String, System> systems

    Map<String, Container> containers

    @Field(alternatives = [MavenProject, GradleProject])
    Map<String, Buildable> projects

}

@DSL
class System {
    @Key
    String name

    Map<String, Environment> environments
}

@DSL
class Environment {
    @Key String name
    @Owner System system

    String description
    List<String> smokeTests

    String versionFilter

    List<String> auth

    Mail mail


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

@DSL
class Container {

    @Key String name



}

@DSL
abstract class Buildable {

    @Key String name
    String gitRepo

    String getGitRepo() {
        return gitRepo ?: name
    }
}

@DSL
class MavenProject extends Buildable {

    String parent
}

@DSL
class GradleProject extends Buildable {
    String cli
}

