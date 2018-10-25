#!/usr/bin/env fish
for it in Assets.xcassets/**/*.png
    convert "$it" -fill "#FFFFFF" -draw 'color 0,0 reset' "$it"
end
