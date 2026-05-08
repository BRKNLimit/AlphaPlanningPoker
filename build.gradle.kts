plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    application
}

group = "com.teamalpha"
version = "0.0.1"

kotlin {
    jvmToolchain(21) // We'll try 21 first, Gradle should be able to find it or use 25 as compatible
}

application {
    mainClass.set("com.teamalpha.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    val ktor_version = "2.3.12"
    val logback_version = "1.4.14"

    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.0")
}
