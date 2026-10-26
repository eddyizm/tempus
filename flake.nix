{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-parts.url = "github:hercules-ci/flake-parts";
    devshell = {
      url = "github:numtide/devshell";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    inputs@{ flake-parts, ... }:
    flake-parts.lib.mkFlake { inherit inputs; } {
      imports = [ inputs.devshell.flakeModule ];
      systems = [ "x86_64-linux" ];
      perSystem =
        {
          system,
          pkgs,
          lib,
          self',
          ...
        }:
        let
          buildToolsVersion = "36.1.0";
          jdk = pkgs.jdk21;
          gradle = pkgs.gradle_9.override { java = jdk; };
          androidSdk =
            (pkgs.androidenv.composeAndroidPackages {
              platformVersions = [ "37.0" ];
              buildToolsVersions = [ buildToolsVersion ];
              includeEmulator = false;
            }).androidsdk;
          androidHome = "${androidSdk}/libexec/android-sdk";
          aapt2Override = "android.aapt2FromMavenOverride=${androidHome}/build-tools/${buildToolsVersion}/aapt2";

          releaseTasks = [
            "assembleTempusRelease"
            "assembleDegoogledRelease"
          ];
          debugTasks = [ "assembleTempusDebug" ];
          testTasks = [
            "testTempusDebugUnitTest"
            "testDegoogledDebugUnitTest"
          ];

          emulator = pkgs.androidenv.emulateApp {
            name = "tempus-emulator";
            app = self'.packages.debug;
            platformVersion = "36";
            abiVersion = "x86_64";
            systemImageType = "google_apis";
            package = "com.eddyizm.tempus.debug";
            activity = "com.eddyizm.tempus.ui.activity.MainActivity";
            # Without it the host keyboard never reaches the emulator.
            configOptions."hw.keyboard" = "yes";
          };

          # Both install commands take the flavor and the target serial; nothing is installed
          # unless that device is connected and authorized.
          requireDevice = name: ''
            adb=${lib.getExe' androidSdk "adb"}
            if [ "$#" -ne 2 ]; then
              echo "usage: ${name} <tempus|degoogled> <serial>" >&2
              "''${adb}" devices -l >&2
              exit 1
            fi
            flavor="''${1}"
            export ANDROID_SERIAL="''${2}"
            if [ "$("''${adb}" get-state 2>/dev/null)" != device ]; then
              echo "device ''${ANDROID_SERIAL} is not connected or not authorized" >&2
              "''${adb}" devices -l >&2
              exit 1
            fi
          '';

          # Release APKs come out unsigned; signing with the local debug key makes them
          # installable, but they won't update over an official GitHub/F-Droid install.
          installRelease = pkgs.writeShellApplication {
            name = "tempus-install-release";
            text = ''
              ${requireDevice "install-release"}
              keystore="''${ANDROID_USER_HOME:-''${HOME}/.android}/debug.keystore"
              if [ ! -f "''${keystore}" ]; then
                mkdir -p "$(dirname "''${keystore}")"
                ${lib.getExe' jdk "keytool"} -genkeypair -keystore "''${keystore}" \
                  -storepass android -keypass android -alias androiddebugkey \
                  -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -validity 10000
              fi
              tmp="$(mktemp -d)"
              trap 'rm -rf "''${tmp}"' EXIT
              ${lib.getExe pkgs.apksigner} sign \
                --ks "''${keystore}" --ks-pass pass:android --out "''${tmp}/tempus.apk" \
                "${self'.packages.default}/app-''${flavor}-release-unsigned.apk"
              "''${adb}" install -r "''${tmp}/tempus.apk"
            '';
          };

          # The stock script writes deps.json relative to the working directory. This absolute
          # path is expanded by the script at run time; "#/" avoids a doubled leading slash.
          updateDeps = self'.packages.default.mitmCache.updateScript.override {
            data = "/\${PRJ_ROOT#/}/deps.json";
          };
        in
        {
          _module.args.pkgs = import inputs.nixpkgs {
            inherit system;
            config = {
              allowUnfree = true;
              android_sdk.accept_license = true;
            };
          };

          packages.default = pkgs.stdenv.mkDerivation {
            pname = "tempus";
            version = "4.31.0";

            src = lib.fileset.toSource {
              root = ./.;
              fileset = lib.fileset.unions [
                ./app
                ./libs
                ./gradle
                ./build.gradle
                ./settings.gradle
                ./gradle.properties
              ];
            };

            nativeBuildInputs = [ gradle ];

            # One lockfile shared by every gradle derivation below, so the update
            # task must exercise all of their tasks. The default nixDownloadDeps
            # task fails on AGP's ambiguous variant configurations.
            mitmCache = gradle.fetchDeps {
              pkg = self'.packages.default;
              data = ./deps.json;
            };
            gradleUpdateTask = releaseTasks ++ debugTasks ++ testTasks;

            env = {
              ANDROID_HOME = androidHome;
              JAVA_HOME = jdk;
            };

            gradleBuildTask = releaseTasks;
            gradleFlags = [
              "-Porg.gradle.java.installations.auto-download=false"
              "-P${aapt2Override}"
            ];

            # AGP writes the debug keystore under ANDROID_USER_HOME (~/.android by default).
            preBuild = ''
              export ANDROID_USER_HOME=$(mktemp -d)
            '';

            installPhase = ''
              runHook preInstall
              install -Dm644 -t $out app/build/outputs/apk/*/release/app-*-release-unsigned.apk
              runHook postInstall
            '';
          };

          packages.debug = self'.packages.default.overrideAttrs {
            pname = "tempus-debug";
            gradleBuildTask = debugTasks;
            # emulateApp installs $app/*.apk, so this must stay a single APK.
            installPhase = ''
              runHook preInstall
              install -Dm644 -t $out app/build/outputs/apk/tempus/debug/app-tempus-debug.apk
              runHook postInstall
            '';
          };

          checks.unit-tests = self'.packages.default.overrideAttrs {
            pname = "tempus-unit-tests";
            gradleBuildTask = testTasks;
            installPhase = ''
              cp -r app/build/reports/tests $out
            '';
          };

          apps.emulator = {
            type = "app";
            program = "${emulator}/bin/run-test-emulator";
          };

          apps.install-release = {
            type = "app";
            program = lib.getExe installRelease;
          };
          devshells.default = {
            packages = [
              jdk
              androidSdk
            ];
            env = [
              {
                name = "ANDROID_HOME";
                value = androidHome;
              }
              {
                name = "JAVA_HOME";
                value = jdk;
              }
              {
                name = "GRADLE_OPTS";
                value = "-Dorg.gradle.project.${aapt2Override}";
              }
            ];
            commands = [
              {
                name = "build-debug";
                help = "assemble a debug APK [tempus|degoogled] (default: tempus)";
                command = ''flavor="''${1:-tempus}"; "''${PRJ_ROOT}/gradlew" -p "''${PRJ_ROOT}" "assemble''${flavor^}Debug"'';
              }
              {
                name = "build-release";
                help = "assemble an unsigned release APK [tempus|degoogled] (default: tempus)";
                command = ''flavor="''${1:-tempus}"; "''${PRJ_ROOT}/gradlew" -p "''${PRJ_ROOT}" "assemble''${flavor^}Release"'';
              }
              {
                name = "emulator-run";
                help = "boot an emulator and launch the debug build (nix run .#emulator)";
                # The emulator outlives the script and keeps writing to its stdout, so it all goes to a file.
                command = ''
                  log="''${TMPDIR:-/tmp}/tempus-emulator.log"
                  echo "booting; emulator log in $log"
                  nix run "''${PRJ_ROOT}#emulator" &>"$log" || { tail -n 20 "$log" >&2; exit 1; }
                '';
              }
              {
                name = "install-release";
                help = "sign a release APK with the local debug key and adb install it <tempus|degoogled> <serial>";
                command = ''nix run "''${PRJ_ROOT}#install-release" -- "$@"'';
              }
              {
                name = "install-debug";
                help = "build a debug APK and install it next to the official app <tempus|degoogled> <serial>";
                command = ''
                  ${requireDevice "install-debug"}
                  "''${PRJ_ROOT}/gradlew" -p "''${PRJ_ROOT}" "install''${flavor^}Debug"
                '';
              }
              {
                name = "update-deps";
                help = "regenerate deps.json after changing gradle dependencies";
                command = "${updateDeps}";
              }
            ];
          };
        };
    };
}
