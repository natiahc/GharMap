#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p finance/build
"${CXX:-g++}" -std=c++17 -Wall -Wextra -Werror -pedantic \
  -fsanitize=address,undefined -fno-omit-frame-pointer -g \
  -Ifinance/include finance/src/finance.cpp finance/tests/finance_test.cpp \
  -o finance/build/finance_tests
finance/build/finance_tests
