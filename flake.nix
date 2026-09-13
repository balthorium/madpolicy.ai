{
  description = "Diplomacy move adjudication engine";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forAllSystems = nixpkgs.lib.genAttrs systems;
    in {
      packages = forAllSystems (system:
        let pkgs = nixpkgs.legacyPackages.${system};
        in {
          default = pkgs.maven.buildMavenPackage {
            pname = "diplomacy";
            version = "0.0.1-SNAPSHOT";
            src = ./.;
            mvnHash = "sha256-lrhwdGO25oT3+4bE5arEkqEEL39tF5FPYTcc7Rt+/q4=";
            mvnJdk = pkgs.jdk21;
            nativeBuildInputs = [ pkgs.makeWrapper ];
            installPhase = ''
              install -Dm644 target/diplomacy-*.jar $out/share/diplomacy.jar
              makeWrapper ${pkgs.jdk21}/bin/java $out/bin/diplomacy --add-flags "-jar $out/share/diplomacy.jar"
            '';
          };
        });

      devShells = forAllSystems (system:
        let pkgs = import nixpkgs { inherit system; config.allowUnfree = true; };
        in {
          default = pkgs.mkShell {
            packages = with pkgs; [ jdk21 maven jetbrains.idea ];
          };
        });
    };
}
