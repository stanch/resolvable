rm -f *.html && rm -f *.json && rm -rf ./gitbook && rm -rf ./guide && rm -rf ./recipes && rm -rf ./tutorial && gitbook build -t Resolvable -g resolvable/resolvable -o book _src && mv book/* .