plugins {
  scala
  id("io.gatling.gradle") version "3.11.5"
}

group = "com.firstpay"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  gatling("io.gatling.highcharts:gatling-charts-highcharts:3.11.5")
}

gatling {
  jvmArgs = listOf("-Xmx2g")
}
