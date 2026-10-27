#!/bin/bash

rm -rf target
mkdir -p target

cp -r src/gui target/

javac \
  -source 21 \
  -target 21 \
  -cp "lib/*:src" \
  -d target \
  $(find src -name "*.java")

echo "Kompilasi Selesai. Hasil ada di folder 'target'."