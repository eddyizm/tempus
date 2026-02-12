#!/usr/bin/env bash

# This small script can be used to generate the icon-red.svg from icon-green.svg
# in the case the icon is changed.

sed icon-green.svg -e 's/#62a43b/#ed5564/g' -e 's/#8cc152/#da4453/g' >icon-red.svg
