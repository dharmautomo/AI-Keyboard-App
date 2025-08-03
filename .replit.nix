
{ pkgs }: {
  deps = [
    pkgs.android-studio
    pkgs.android-tools
    pkgs.jdk17
    pkgs.gradle
    pkgs.git
    pkgs.which
    pkgs.unzip
    pkgs.zip
  ];

  env = {
    ANDROID_HOME = "${pkgs.android-studio}/share/android-studio/bin/studio.sh";
    ANDROID_SDK_ROOT = "/nix/store/android-sdk";
    JAVA_HOME = "${pkgs.jdk17}/lib/openjdk";
    PATH = "${pkgs.android-tools}/bin:${pkgs.gradle}/bin:${pkgs.jdk17}/bin:$PATH";
  };
}
