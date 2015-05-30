#!/usr/bin/env bash

SVG_FILES="dfuntest_bw2 dfuntest_deployment KademliaClassDiagram SecurityClassDiagram regtest fintest"

for SVG_FILE in ${SVG_FILES}
do
  inkscape -D -z --file=img/${SVG_FILE}.svg --export-pdf=${SVG_FILE}.pdf --export-latex
done
