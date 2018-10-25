#!/bin/sh

# You must install this first: https://github.com/muzzley/mobile-icon-resizer

cp "$1" "tempImage.png"
mobile-icon-resizer -i "tempImage.png" --platforms ios --config config.json
rm "tempImage.png"
