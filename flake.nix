{
  description = "Restoid development shell";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };
      goVersion = pkgs.lib.strings.trim (builtins.readFile ./.go-version);
      buildGradleKts = builtins.readFile ./app/build.gradle.kts;
      ndkVersionMatch = builtins.match ".*ndkVersion = \"([^\"]+)\".*" buildGradleKts;
      ndkVersion = if ndkVersionMatch != null then builtins.head ndkVersionMatch else "29.0.14206865";
      goArchive = pkgs.fetchurl {
        url = "https://go.dev/dl/go${goVersion}.linux-amd64.tar.gz";
        # renovate: datasource=custom.go-official depName=go versioning=semver currentValue=1.26.2
        hash = "sha256:990e6b4bbba816dc3ee129eaeaf4b42f17c2800b88a2166c265ac1a200262282";
      };
      goOfficial = pkgs.stdenvNoCC.mkDerivation {
        pname = "go-official";
        version = goVersion;
        src = goArchive;
        dontConfigure = true;
        dontBuild = true;
        unpackPhase = ''
          runHook preUnpack
          tar -xzf "$src"
          runHook postUnpack
        '';
        installPhase = ''
          runHook preInstall
          mkdir -p "$out"
          cp -R go/. "$out/"
          runHook postInstall
        '';
      };
      androidComposition = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "36" ];
        buildToolsVersions = [ "36.0.0" ];
        abiVersions = [ "arm64-v8a" "x86_64" ];
        includeEmulator = false;
        includeSystemImages = false;
        includeNDK = true;
        ndkVersions = [ ndkVersion ];
        includeCmake = false;
      };
      androidSdk = androidComposition.androidsdk;
    in {
      devShells.${system}.default = pkgs.mkShell {
        packages = with pkgs; [
          jdk21
          gradle
          android-tools
          androidSdk
          git
        ] ++ [
          goOfficial
        ];

        JAVA_HOME = pkgs.jdk21;
        ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
        ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk}/libexec/android-sdk/build-tools/36.0.0/aapt2";

        shellHook = '' # bash
          export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
          export GRADLE_USER_HOME="$PWD/.gradle"
          export ANDROID_USER_HOME="$PWD/.android"

          mkdir -p "$GRADLE_USER_HOME" "$ANDROID_USER_HOME"

          echo "sdk.dir=$ANDROID_HOME" > local.properties

          echo "Go: $(go version)"
          echo "Java: $(java -version 2>&1 | head -n 1)"
          echo "Gradle: $(gradle --version | sed -n '3p')"
          echo "Android SDK: $ANDROID_HOME"
          echo "adb: $(command -v adb)"
        '';
      };
    };
}
