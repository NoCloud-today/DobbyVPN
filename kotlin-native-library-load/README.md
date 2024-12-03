# Calling a Dynamic Library in Kotlin/Native

1) Navigate to the `libs` folder and generate a `.so`, `.dll`, or `.dylib` file depending on your system (by convention, these should be placed in `src/nativeInterop/cinterop`).

2) Update the path to the library in `Main.kt` (currently, an absolute path is used for simplicity).

3) From the project root, run `./gradlew nativeBinaries`.

4) You can find the `.kexe` file at `build/bin/native/releaseExecutable`. Run it as an administrator.


