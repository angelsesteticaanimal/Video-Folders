dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Document access (pasta de vídeos)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Media player ESTÁVEL (não usar versão 1.4.x no GitHub Actions)
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
}
