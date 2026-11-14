pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // TODO(Pleos): Pleos Connect SDK 저장소 주소를 개발 환경 설정 가이드대로 추가
    }
}
rootProject.name = "Chahu"
include(":app")
