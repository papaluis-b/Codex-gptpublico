plugins { application }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
dependencies { implementation("es.sonda:dsp:0.2.0") }
application { mainClass.set("es.sonda.example.DspExample") }
