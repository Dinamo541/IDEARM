#!/usr/bin/env sh
# Launch the modular CLI after "mvn package" (Linux and macOS; scripts/idearm.ps1 on Windows).
# Tool installations stay outside the project file.
set -e
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cli_jar=$(ls "$project_root"/idearm-cli/target/idearm-cli-*.jar 2>/dev/null | grep -v -e '-sources\.jar$' -e '-javadoc\.jar$' -e '-tests\.jar$' | head -n 1)
libraries="$project_root/idearm-cli/target/lib"
if [ ! -f "$cli_jar" ] || [ ! -d "$libraries" ]; then
    echo 'Build IDEARM first: mvn package' >&2
    exit 2
fi
exec java --enable-native-access=io.github.dinamo541.idearm.infrastructure --module-path "$cli_jar:$libraries" \
    -m io.github.dinamo541.idearm.cli/io.github.dinamo541.idearm.cli.Main "$@"
