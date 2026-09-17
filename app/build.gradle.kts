plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "br.maxymus.agenda"
    compileSdk = 34
    defaultConfig {
        applicationId = "br.maxymus.agenda"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }
    // Mesma chave do camera-estudo (secrets do repo): o cadastro OAuth do Google fica preso à SHA-1 dela.
    val ksCaminho = System.getenv("AGENDA_KEYSTORE")
    val ksSenha = System.getenv("AGENDA_KEYSTORE_SENHA")
    signingConfigs {
        create("release") {
            if (ksCaminho != null && ksSenha != null) {
                storeFile = file(ksCaminho); storePassword = ksSenha; keyAlias = "camera"; keyPassword = ksSenha
            }
        }
    }
    buildTypes {
        release {
            // sem R8 por enquanto: a biblioteca do Google Agenda monta os objetos por reflexão e quebra fácil quando encolhida
            isMinifyEnabled = false
            signingConfig = if (ksCaminho != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST") } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // login Google + API do Google Agenda
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.api-client:google-api-client-android:2.6.0")
    implementation("com.google.apis:google-api-services-calendar:v3-rev20240705-2.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.44.2")
}
