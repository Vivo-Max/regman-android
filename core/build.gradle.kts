plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm") version "2.0.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)          // 仅用于邮箱/代理测速等简单 HTTP；注册主链路走 HttpClient 抽象
    implementation(libs.jakarta.mail)    // IMAP 邮箱池
}
