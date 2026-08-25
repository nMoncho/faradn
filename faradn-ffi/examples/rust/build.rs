// Links against the shared library built by
//   mvn -Pnative-shared -pl faradn-ffi -am -DskipTests package
fn main() {
    let target = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../../target");
    println!("cargo:rustc-link-search=native={}", target.display());
    println!("cargo:rustc-link-lib=dylib=faradn");
    println!("cargo:rustc-link-arg=-Wl,-rpath,{}", target.display());
}
