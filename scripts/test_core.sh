#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p core/build
"${CXX:-g++}" -std=c++17 -Wall -Wextra -Werror -pedantic -fsanitize=address,undefined -fno-omit-frame-pointer -g -Icore/include core/src/geometry.cpp core/tests/geometry_test.cpp -o core/build/core_tests
core/build/core_tests
