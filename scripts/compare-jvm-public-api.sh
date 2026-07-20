#!/usr/bin/env bash
#
# The MIT License (MIT)
#
# Copyright (c) 2015-2026 Stephan Pauxberger
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

set -euo pipefail

if [[ $# -ne 3 ]]; then
    echo "Usage: $0 <baseline-build-root> <candidate-build-root> <report-directory>" >&2
    exit 2
fi

baseline_root=$1
candidate_root=$2
report_directory=$3

for required in "$baseline_root" "$candidate_root"; do
    if [[ ! -d "$required" ]]; then
        echo "Build root does not exist: $required" >&2
        exit 2
    fi
done

mkdir -p "$report_directory"

snapshot() {
    local build_root=$1
    local jar artifact class_file
    local -a class_names=()

    while IFS= read -r jar; do
        artifact=$(basename "$(dirname "$(dirname "$(dirname "$jar")")")")
        class_names=()
        while IFS= read -r class_file; do
            class_file=${class_file%.class}
            class_names+=("${class_file//\//.}")
        done < <(jar tf "$jar" | grep '\.class$' | grep -v '/module-info\.class$')
        if (( ${#class_names[@]} > 0 )); then
            { javap -public -s -classpath "$jar" "${class_names[@]}" 2>/dev/null || true; } |
                awk -v artifact="$artifact" '
                    /^(public |protected |private |final |abstract |sealed |non-sealed |class |interface |enum |@interface )/ && / (class|interface|enum|@interface) / {
                        class_name = ""
                        if ($0 ~ /^(public|protected) /) {
                            class_name = $0
                            sub(/^.* (class|interface|enum|@interface) /, "", class_name)
                            sub(/[ <{].*$/, "", class_name)
                            print artifact "|" class_name "|type|" $0
                        }
                        next
                    }
                    /^  (public|protected) / {
                        signature = $0
                        getline
                        if ($0 ~ /^    descriptor:/ && class_name != "")
                            print artifact "|" class_name "|member|" signature " " $0
                    }
                '
        fi
    done < <(find "$build_root" -path "$build_root/klum-ast-*/build/libs/*.jar" -type f ! -name '*-sources.jar' ! -name '*-javadoc.jar' | LC_ALL=C sort)
}

snapshot "$baseline_root" | LC_ALL=C sort -u > "$report_directory/baseline-public-api.txt"
snapshot "$candidate_root" | LC_ALL=C sort -u > "$report_directory/candidate-public-api.txt"
comm -13 "$report_directory/baseline-public-api.txt" "$report_directory/candidate-public-api.txt" \
    > "$report_directory/added-public-api.txt"
comm -23 "$report_directory/baseline-public-api.txt" "$report_directory/candidate-public-api.txt" \
    > "$report_directory/removed-public-api.txt"

printf 'Wrote %s\n' "$report_directory/baseline-public-api.txt"
printf 'Wrote %s\n' "$report_directory/candidate-public-api.txt"
printf 'Wrote %s\n' "$report_directory/added-public-api.txt"
printf 'Wrote %s\n' "$report_directory/removed-public-api.txt"
