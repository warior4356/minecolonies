package patches.buildTypes

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.vcsLabeling
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.ui.*

/*
This patch script was generated by TeamCity on settings change in UI.
To apply the patch, change the buildType with id = 'Release_Release'
accordingly, and delete the patch script.
*/
changeBuildType(RelativeId("Release_Release")) {
    expectSteps {
        gradle {
            name = "Compile"
            id = "RUNNER_9"
            tasks = "build createChangelog curseforge publish"
            buildFile = "build.gradle"
            enableStacktrace = true
            dockerImage = "gradle:%env.GRADLE_VERSION%-%env.JDK_VERSION%"
            dockerImagePlatform = GradleBuildStep.ImagePlatform.Linux
            dockerRunParameters = """
                -v /opt/buildagent/gradle/caches:/home/gradle/.gradle/caches
                -u 0
            """.trimIndent()
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
            param("org.jfrog.artifactory.selectedDeployableServer.deployReleaseFlag", "true")
            param("org.jfrog.artifactory.selectedDeployableServer.deployReleaseText", "%Project.Type%")
            param("org.jfrog.artifactory.selectedDeployableServer.envVarsExcludePatterns", "*password*,*secret*")
            param("org.jfrog.artifactory.selectedDeployableServer.publishBuildInfo", "true")
            param("org.jfrog.artifactory.selectedDeployableServer.resolvingRepo", "modding")
            param("org.jfrog.artifactory.selectedDeployableServer.targetRepo", "libraries")
            param("org.jfrog.artifactory.selectedDeployableServer.urlId", "2")
        }
    }
    steps {
        update<GradleBuildStep>(0) {
            id = "RUNNER_9"
            clearConditions()
            dockerRunParameters = """
                -v /opt/buildagent/gradle:/home/gradle/.gradle
                -u 0
            """.trimIndent()
        }
    }

    features {
        remove {
            vcsLabeling {
                id = "BUILD_EXT_11"
                vcsRootId = "${DslContext.settingsRoot.id}"
                labelingPattern = "%env.Version%"
                successfulOnly = true
                branchFilter = ""
            }
        }
    }
}
